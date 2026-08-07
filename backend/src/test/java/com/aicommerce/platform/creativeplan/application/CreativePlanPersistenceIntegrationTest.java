package com.aicommerce.platform.creativeplan.application;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class CreativePlanPersistenceIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired CreativePlanCommandService commands; @Autowired CreativePlanQueryService queries;
 @Autowired ProductCommandService productCommands; @Autowired JdbcTemplate jdbc;

 @Test void provesLifecycleQueriesOwnershipConcurrencyAndTransactionalAudit(){
  Product product=productCommands.create(productCommand("Product One"),"product-create");
  Product other=productCommands.create(productCommand("Product Two"),"other-create");
  CreateCreativePlanCommand create=new CreateCreativePlanCommand("Launch",null,null,null,null,null,null,null,null,null,null,null);
  CreativePlan first=commands.create(product.getProductUuid(),create,"plan-create-1");
  CreativePlan second=commands.create(product.getProductUuid(),create,"plan-create-2");
  java.util.UUID firstUuid=first.getCreativePlanUuid();
  assertThat(actions(first)).containsExactly("CREATE");

  var absent=FieldPatch.<String>absent();
  PatchCreativePlanCommand update=new PatchCreativePlanCommand(absent,FieldPatch.present("Parents"),absent,absent,absent,absent,absent,absent,absent,absent,absent,absent);
  first=commands.patch(product.getProductUuid(),first.getCreativePlanUuid(),0,update,"plan-update");
  assertThat(first.getVersion()).isEqualTo(1);
  assertThat(actions(first)).containsExactly("CREATE","UPDATE");
  assertThat(changeFields(first,"UPDATE")).containsExactly("primary_audience");
  long afterUpdate=auditCount();
  assertThatThrownBy(()->commands.patch(product.getProductUuid(),firstUuid,0,update,"stale"))
   .isInstanceOf(CreativePlanPreconditionFailedException.class);
  assertThat(auditCount()).isEqualTo(afterUpdate);
  commands.patch(product.getProductUuid(),first.getCreativePlanUuid(),1,update,"empty");
  assertThat(auditCount()).isEqualTo(afterUpdate);

  first=commands.archive(product.getProductUuid(),first.getCreativePlanUuid(),1,"archive");
  assertThat(first.getVersion()).isEqualTo(2); assertThat(actions(first)).containsExactly("CREATE","UPDATE","ARCHIVE");
  long afterArchive=auditCount(); commands.archive(product.getProductUuid(),first.getCreativePlanUuid(),2,"archive-noop");
  assertThat(auditCount()).isEqualTo(afterArchive);
  assertThatThrownBy(()->commands.patch(product.getProductUuid(),firstUuid,2,update,"archived-patch"))
   .isInstanceOf(CreativePlanArchivedException.class); assertThat(auditCount()).isEqualTo(afterArchive);

  Sort stable=Sort.by("planName").ascending().and(Sort.by("creativePlanUuid").ascending());
  assertThat(queries.list(product.getProductUuid(),LifecycleStatus.ACTIVE,PageRequest.of(0,10,stable)).getContent()).extracting(CreativePlan::getCreativePlanUuid).containsExactly(second.getCreativePlanUuid());
  assertThat(queries.list(product.getProductUuid(),LifecycleStatus.ARCHIVED,PageRequest.of(0,10,stable)).getContent()).extracting(CreativePlan::getCreativePlanUuid).containsExactly(firstUuid);
  var all=queries.list(product.getProductUuid(),null,PageRequest.of(0,1,stable));
  assertThat(all.getTotalElements()).isEqualTo(2); assertThat(all.getTotalPages()).isEqualTo(2);
  java.util.UUID pageZero=all.getContent().getFirst().getCreativePlanUuid();
  java.util.UUID repeatedPageZero=queries.list(product.getProductUuid(),null,PageRequest.of(0,1,stable)).getContent().getFirst().getCreativePlanUuid();
  java.util.UUID pageOne=queries.list(product.getProductUuid(),null,PageRequest.of(1,1,stable)).getContent().getFirst().getCreativePlanUuid();
  assertThat(repeatedPageZero).isEqualTo(pageZero);
  assertThat(List.of(pageZero,pageOne)).containsExactlyInAnyOrder(firstUuid,second.getCreativePlanUuid());
  assertThatThrownBy(()->queries.get(other.getProductUuid(),firstUuid)).isInstanceOf(CreativePlanNotFoundException.class);

  first=commands.restore(product.getProductUuid(),first.getCreativePlanUuid(),2,"restore");
  assertThat(first.getVersion()).isEqualTo(3); assertThat(actions(first)).containsExactly("CREATE","UPDATE","ARCHIVE","RESTORE");
  long afterRestore=auditCount(); commands.restore(product.getProductUuid(),first.getCreativePlanUuid(),3,"restore-noop");
  assertThat(auditCount()).isEqualTo(afterRestore);
  assertThatThrownBy(()->commands.create(product.getProductUuid(),new CreateCreativePlanCommand(" ",null,null,null,null,null,null,null,null,null,null,null),"invalid"))
   .isInstanceOf(CreativePlanValidationException.class); assertThat(auditCount()).isEqualTo(afterRestore);
  productCommands.archive(product.getProductUuid(),product.getVersion(),"product-archive");
  assertThatThrownBy(()->commands.create(product.getProductUuid(),create,"blocked"))
   .isInstanceOf(ProductArchivedException.class); assertThat(auditCount()).isEqualTo(afterRestore);
 }
 private CreateProductCommand productCommand(String name){return new CreateProductCommand(null,name,null,null,null,null,null,null,null,null,null);}
 private List<String> actions(CreativePlan plan){return jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_type='CREATIVE_PLAN' AND entity_uuid=? ORDER BY occurred_at,audit_uuid",String.class,plan.getCreativePlanUuid());}
 private List<String> changeFields(CreativePlan plan,String action){return jdbc.queryForList("SELECT c.field_name FROM audit_log_changes c JOIN audit_logs l ON l.audit_uuid=c.audit_uuid WHERE l.entity_uuid=? AND l.action=? ORDER BY c.change_order",String.class,plan.getCreativePlanUuid(),action);}
 private long auditCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='CREATIVE_PLAN'",Long.class);}
}
