package com.aicommerce.platform.asset.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.audit.application.AuditWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class AssetTransactionIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired AssetCommandService service; @Autowired JdbcTemplate jdbc; @MockitoBean AuditWriter auditWriter;
 @Test void auditFailureRollsBackAssetMutation(){
  UUID product=UUID.randomUUID(); jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status,version) VALUES (?,?,?,?,0)",product,"PROD-00008888","Product","ACTIVE");
  doThrow(new IllegalStateException("audit failure")).when(auditWriter).append(any());
  assertThatThrownBy(()->service.create(product,new CreateAssetCommand(null,null,AssetType.IMAGE,null,null,null,null,null,null,null,null,null),"rollback-request")).isInstanceOf(IllegalStateException.class);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE product_uuid=?",Long.class,product)).isZero();
 }
}
