package com.aicommerce.platform.knowledge;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.knowledge.application.*;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
@Testcontainers @SpringBootTest @ActiveProfiles("test")
class KnowledgeIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired ProductCommandService products; @Autowired KnowledgeCommandService commands; @Autowired KnowledgeQueryService queries; @Autowired JdbcTemplate jdbc;
 @Test void lifecycleConcurrencyOwnershipAndAuditAreTransactional(){Product p=product(); long before=count(); var k=commands.create(p.getProductUuid(),new CreateKnowledgeCommand(KnowledgeType.FEATURE,"Title","Content","Source"),"knowledge-create"); assertThat(k.getVersion()).isZero(); assertThat(count()).isEqualTo(before+1); var audit=jdbc.queryForMap("select action,actor_id,request_id from audit_logs where entity_uuid=?",k.getKnowledgeUuid());assertThat(audit).containsEntry("action","CREATE").containsEntry("actor_id","local-admin").containsEntry("request_id","knowledge-create"); var updated=commands.patch(p.getProductUuid(),k.getKnowledgeUuid(),0,new PatchKnowledgeCommand(FieldPatch.absent(),FieldPatch.present("Updated"),FieldPatch.absent(),FieldPatch.absent()),"knowledge-update"); assertThat(updated.getVersion()).isEqualTo(1); assertThat(count()).isEqualTo(before+2); assertThatThrownBy(()->commands.patch(p.getProductUuid(),k.getKnowledgeUuid(),0,new PatchKnowledgeCommand(FieldPatch.absent(),FieldPatch.present("Stale"),FieldPatch.absent(),FieldPatch.absent()),"stale")).isInstanceOf(KnowledgePreconditionFailedException.class); assertThat(count()).isEqualTo(before+2); var archived=commands.archive(p.getProductUuid(),k.getKnowledgeUuid(),1,"archive"); assertThat(archived.getVersion()).isEqualTo(2); assertThatThrownBy(()->commands.patch(p.getProductUuid(),k.getKnowledgeUuid(),2,new PatchKnowledgeCommand(FieldPatch.absent(),FieldPatch.present("Blocked"),FieldPatch.absent(),FieldPatch.absent()),"archived-patch")).isInstanceOf(KnowledgeArchivedException.class); commands.archive(p.getProductUuid(),k.getKnowledgeUuid(),2,"archive-noop"); assertThat(count()).isEqualTo(before+3); var restored=commands.restore(p.getProductUuid(),k.getKnowledgeUuid(),2,"restore"); assertThat(restored.getVersion()).isEqualTo(3); var noChange=commands.patch(p.getProductUuid(),k.getKnowledgeUuid(),3,new PatchKnowledgeCommand(FieldPatch.absent(),FieldPatch.present("Updated"),FieldPatch.absent(),FieldPatch.absent()),"no-change");assertThat(noChange.getVersion()).isEqualTo(3);assertThat(count()).isEqualTo(before+4); Product other=product();assertThatThrownBy(()->queries.get(other.getProductUuid(),k.getKnowledgeUuid())).isInstanceOf(KnowledgeNotFoundException.class); assertThat(queries.list(p.getProductUuid(),null,PageRequest.of(0,20)).getTotalElements()).isEqualTo(1); }
 @Test void archivedProductIsReadableButBlocksMutation(){Product p=product(); var k=commands.create(p.getProductUuid(),new CreateKnowledgeCommand(KnowledgeType.FAQ,"Question","Answer",null),"create"); products.archive(p.getProductUuid(),p.getVersion(),"product-archive"); assertThat(queries.get(p.getProductUuid(),k.getKnowledgeUuid())).isNotNull(); long before=count(); assertThatThrownBy(()->commands.patch(p.getProductUuid(),k.getKnowledgeUuid(),0,new PatchKnowledgeCommand(FieldPatch.absent(),FieldPatch.present("Blocked"),FieldPatch.absent(),FieldPatch.absent()),"blocked")).isInstanceOf(ProductArchivedException.class); assertThat(count()).isEqualTo(before); }
 private Product product(){return products.create(new CreateProductCommand(null,"Knowledge Product",null,null,null,null,null,null,null,null,null),"product-create");}
 private long count(){return jdbc.queryForObject("select count(*) from audit_logs where entity_type='PRODUCT_KNOWLEDGE'",Long.class);}
}
