package com.aicommerce.platform.asset.application;

import static org.assertj.core.api.Assertions.*;
import com.aicommerce.platform.asset.domain.*;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.Product;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class AssetPersistenceIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired AssetCommandService commands; @Autowired AssetQueryService queries; @Autowired ProductCommandService productCommands; @Autowired JdbcTemplate jdbc;
 @Test void provesLifecycleFiltersStablePagesTransactionalAuditAndMetadataConfidentiality(){
  Product p=productCommands.create(new CreateProductCommand(null,"Asset Product",null,null,null,null,null,null,null,null,null),"product-create");
  CreateAssetCommand create=new CreateAssetCommand(null,null,AssetType.IMAGE,"Hero","s3","file-1","https://cdn.example/a.jpg","image/jpeg","a.jpg",10L,null,Map.of("region","eu","nested",List.of(Map.of("safe","value"))));
  Asset first=commands.create(p.getProductUuid(),create,"asset-create"); Asset second=commands.create(p.getProductUuid(),create,"asset-create-2"); UUID firstId=first.getAssetUuid();
  assertAudit(firstId,"CREATE","asset-create");
  Map<String,Object> providerAudit=jdbc.queryForMap("SELECT c.new_value FROM audit_log_changes c JOIN audit_logs l ON l.audit_uuid=c.audit_uuid WHERE l.entity_uuid=? AND c.field_name='provider_metadata'",firstId);
  assertThat(providerAudit.get("new_value").toString()).matches("\\[SHA256:[0-9a-f]{64}]").doesNotContain("region").doesNotContain("eu");
  var absent=FieldPatch.<String>absent(); var absentUuid=FieldPatch.<Map<String,Object>>absent();
  PatchAssetCommand patch=new PatchAssetCommand(FieldPatch.absent(),FieldPatch.present("Updated"),absent,absent,absent,absent,absent,FieldPatch.absent(),absent,absentUuid);
  first=commands.patch(p.getProductUuid(),firstId,0,patch,"asset-update"); assertThat(first.getVersion()).isEqualTo(1); assertAudit(firstId,"UPDATE","asset-update");
  long beforeNoop=count(firstId); commands.patch(p.getProductUuid(),firstId,1,patch,"noop"); assertThat(count(firstId)).isEqualTo(beforeNoop);
  assertThatThrownBy(()->commands.patch(p.getProductUuid(),firstId,0,patch,"stale")).isInstanceOf(AssetPreconditionFailedException.class); assertThat(count(firstId)).isEqualTo(beforeNoop);
  first=commands.archive(p.getProductUuid(),firstId,1,"asset-archive"); assertThat(first.getVersion()).isEqualTo(2); assertAudit(firstId,"ARCHIVE","asset-archive");
  long afterArchive=count(firstId); commands.archive(p.getProductUuid(),firstId,2,"archive-noop"); assertThat(count(firstId)).isEqualTo(afterArchive);
  assertThatThrownBy(()->commands.patch(p.getProductUuid(),firstId,2,patch,"blocked")).isInstanceOf(AssetArchivedException.class);
  Sort stable=Sort.by("updatedAt").descending().and(Sort.by("assetUuid").ascending());
  assertThat(queries.list(p.getProductUuid(),LifecycleStatus.ACTIVE,null,null,null,null,PageRequest.of(0,10,stable)).getContent()).extracting(Asset::getAssetUuid).containsExactly(second.getAssetUuid());
  assertThat(queries.list(p.getProductUuid(),LifecycleStatus.ARCHIVED,null,null,null,null,PageRequest.of(0,10,stable)).getContent()).extracting(Asset::getAssetUuid).containsExactly(firstId);
  assertThat(queries.list(p.getProductUuid(),null,AssetType.IMAGE,null,null,"s3",PageRequest.of(0,1,stable)).getTotalElements()).isEqualTo(2);
  first=commands.restore(p.getProductUuid(),firstId,2,"asset-restore"); assertThat(first.getVersion()).isEqualTo(3); assertAudit(firstId,"RESTORE","asset-restore");
 }
 @Test void postgresRejectsInvalidMetadataAndImmutableOwnership(){
  Product p=productCommands.create(new CreateProductCommand(null,"Constraint Product",null,null,null,null,null,null,null,null,null),"product");
  Asset a=commands.create(p.getProductUuid(),new CreateAssetCommand(null,null,AssetType.OTHER,null,null,null,null,null,null,null,null,null),"asset");
  assertThatThrownBy(()->jdbc.update("UPDATE assets SET product_uuid=? WHERE asset_uuid=?",UUID.randomUUID(),a.getAssetUuid())).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE assets SET asset_uuid=? WHERE asset_uuid=?",UUID.randomUUID(),a.getAssetUuid())).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE assets SET checksum_sha256='ABC' WHERE asset_uuid=?",a.getAssetUuid())).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE assets SET provider_metadata='[]'::jsonb WHERE asset_uuid=?",a.getAssetUuid())).isInstanceOf(DataIntegrityViolationException.class);
 }
 @Test void provesOptionalOwnershipActiveBoundariesAndArchiveCleanupRule(){
  Product owner=productCommands.create(new CreateProductCommand(null,"Owner",null,null,null,null,null,null,null,null,null),"owner");
  Product other=productCommands.create(new CreateProductCommand(null,"Other",null,null,null,null,null,null,null,null,null),"other");
  UUID plan=UUID.randomUUID(), campaign=UUID.randomUUID(), association=UUID.randomUUID();
  jdbc.update("INSERT INTO creative_plans(creative_plan_uuid,product_uuid,plan_name) VALUES (?,?,?)",plan,owner.getProductUuid(),"Plan");
  jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name) VALUES (?,?)",campaign,"Campaign");
  jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) VALUES (?,?,?)",association,campaign,owner.getProductUuid());
  CreateAssetCommand linked=new CreateAssetCommand(plan,campaign,AssetType.IMAGE,null,null,null,null,null,null,null,null,null);
  Asset asset=commands.create(owner.getProductUuid(),linked,"linked");
  assertThatThrownBy(()->commands.create(other.getProductUuid(),linked,"wrong-owner")).isInstanceOf(AssetRelationshipConflictException.class);
  jdbc.update("UPDATE creative_plans SET lifecycle_status='ARCHIVED',archived_at=now() WHERE creative_plan_uuid=?",plan);
  assertThatThrownBy(()->commands.create(owner.getProductUuid(),linked,"plan-archived")).isInstanceOf(AssetRelationshipConflictException.class);
  asset=commands.archive(owner.getProductUuid(),asset.getAssetUuid(),asset.getVersion(),"cleanup");
  assertThat(asset.getLifecycleStatus()).isEqualTo(LifecycleStatus.ARCHIVED);
  long version=asset.getVersion(); UUID assetId=asset.getAssetUuid();
  assertThatThrownBy(()->commands.restore(owner.getProductUuid(),assetId,version,"restore-blocked")).isInstanceOf(AssetRelationshipConflictException.class);
  jdbc.update("UPDATE creative_plans SET lifecycle_status='ACTIVE',archived_at=null WHERE creative_plan_uuid=?",plan);
  jdbc.update("UPDATE campaign_products SET lifecycle_status='ARCHIVED',archived_at=now() WHERE campaign_product_uuid=?",association);
  assertThatThrownBy(()->commands.restore(owner.getProductUuid(),assetId,version,"association-archived")).isInstanceOf(AssetRelationshipConflictException.class);
  assertThat(count(assetId)).isEqualTo(2);
 }
 private long count(UUID id){return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='ASSET' AND entity_uuid=?",Long.class,id);}
 private void assertAudit(UUID id,String action,String requestId){
  Map<String,Object> log=jdbc.queryForMap("SELECT actor_id,request_id FROM audit_logs WHERE entity_type='ASSET' AND entity_uuid=? AND action=?",id,action);
  assertThat(log).containsEntry("actor_id","local-admin").containsEntry("request_id",requestId);
  List<Integer> orders=jdbc.queryForList("SELECT c.change_order FROM audit_log_changes c JOIN audit_logs l ON l.audit_uuid=c.audit_uuid WHERE l.entity_uuid=? AND l.action=? ORDER BY c.change_order",Integer.class,id,action);
  assertThat(orders).containsExactlyElementsOf(IntStream.range(0,orders.size()).boxed().toList());
 }
}
