package com.aicommerce.platform.knowledge;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.util.UUID;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.knowledge.application.CreateKnowledgeCommand;
import com.aicommerce.platform.knowledge.application.KnowledgeCommandService;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
@Testcontainers @SpringBootTest @ActiveProfiles("test")
class KnowledgeAuditRollbackIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired KnowledgeCommandService commands; @Autowired ProductJpaRepository products; @Autowired ProductKnowledgeJpaRepository knowledge;
 @MockitoBean AuditWriter auditWriter;
 @Test void auditFailureRollsBackKnowledgeMutation(){UUID productUuid=UUID.randomUUID();products.saveAndFlush(Product.create(productUuid,"PROD-90000001",null,"Rollback Product",null,null,null,null,null,null,null,null,null));when(auditWriter.append(any())).thenThrow(new IllegalStateException("audit unavailable"));assertThatThrownBy(()->commands.create(productUuid,new CreateKnowledgeCommand(KnowledgeType.PROOF,"Proof","Evidence",null),"rollback-test")).isInstanceOf(IllegalStateException.class);assertThat(knowledge.findByProductUuidAndStatus(productUuid,null,org.springframework.data.domain.PageRequest.of(0,20))).isEmpty();}
}
