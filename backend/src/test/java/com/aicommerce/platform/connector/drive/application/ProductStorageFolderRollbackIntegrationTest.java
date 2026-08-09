package com.aicommerce.platform.connector.drive.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import com.aicommerce.platform.audit.application.AuditWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class ProductStorageFolderRollbackIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired ProductStorageFolderService service;@Autowired JdbcTemplate jdbc;@MockitoBean AuditWriter audit;
    @Test void auditFailureRollsBackFolderAndAllSubfolders(){UUID product=UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status,version) VALUES (?,?,?,?,0)",product,"PROD-00007103","Rollback Product","ACTIVE");
        doThrow(new IllegalStateException("audit failure")).when(audit).append(any());
        assertThatThrownBy(()->service.ensure(product,"drive-rollback")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_storage_folders WHERE product_uuid=?",Long.class,product)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_storage_subfolders",Long.class)).isZero();}
}
