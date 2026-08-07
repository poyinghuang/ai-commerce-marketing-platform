package com.aicommerce.platform.creativeplan.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import java.util.UUID;
import com.aicommerce.platform.audit.application.AuditWriter;
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
class CreativePlanTransactionIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired CreativePlanCommandService service; @Autowired JdbcTemplate jdbc;
 @MockitoBean AuditWriter auditWriter;
 @Test void auditFailureRollsBackCreativePlanMutation(){UUID product=UUID.randomUUID();jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status,version) VALUES (?,?,?,?,0)",product,"PROD-00009999","Product","ACTIVE");doThrow(new IllegalStateException("audit failure")).when(auditWriter).append(any());assertThatThrownBy(()->service.create(product,new CreateCreativePlanCommand("Launch",null,null,null,null,null,null,null,null,null,null,null),"rollback-request")).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_plans WHERE product_uuid=?",Long.class,product)).isZero();}
}
