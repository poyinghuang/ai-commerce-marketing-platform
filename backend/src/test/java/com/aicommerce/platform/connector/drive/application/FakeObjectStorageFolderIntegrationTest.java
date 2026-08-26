package com.aicommerce.platform.connector.drive.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicommerce.platform.connector.drive.infrastructure.provider.FakeObjectStorageProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "platform.storage.provider=fake-object")
class FakeObjectStorageFolderIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired ProductStorageFolderService service;
    @Autowired ProductStorageFolderQueryService queries;
    @Autowired StorageProvider provider;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ensureWithFakeObjectIsIdempotentWritesSixRolesAndTrustedAudit() {
        assertThat(provider).isExactlyInstanceOf(FakeObjectStorageProvider.class);
        UUID product = product("ACTIVE", "PROD-00007201");
        var first = service.ensure(product, "fake-object-request");
        var second = service.ensure(product, "fake-object-request-2");
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.folder().storageFolderUuid()).isEqualTo(first.folder().storageFolderUuid());
        assertThat(first.folder().subfolders()).hasSize(6)
                .containsKeys("ORIGINAL", "IMAGES", "VIDEOS", "DOCUMENTS", "CAMPAIGNS", "ARCHIVE");
        assertThat(first.folder().storageProvider()).isEqualTo("GOOGLE_DRIVE");
        assertThat(first.folder().productFolderId()).startsWith("fake-object-");
        assertThat(first.folder().rootFolderId()).isEqualTo("fake-object-root");
        assertThat(queries.get(product).productFolderId()).isEqualTo(first.folder().productFolderId());
        assertThat(jdbc.queryForObject(
                "SELECT storage_provider FROM product_storage_folders WHERE product_uuid=?",
                String.class, product)).isEqualTo("GOOGLE_DRIVE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_storage_folders WHERE product_uuid=?", Long.class, product)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_storage_subfolders WHERE storage_folder_uuid=?",
                Long.class, first.folder().storageFolderUuid())).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='PRODUCT_STORAGE_FOLDER' AND product_uuid=?",
                Long.class, product)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT request_id FROM audit_logs WHERE entity_type='PRODUCT_STORAGE_FOLDER' AND product_uuid=?",
                String.class, product)).isEqualTo("fake-object-request");
    }

    private UUID product(String status, String productId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status,archived_at,version) VALUES (?,?,?,?,CASE WHEN ?='ARCHIVED' THEN CURRENT_TIMESTAMP ELSE NULL END,0)",
                id, productId, "Fake Object Product", status, status);
        return id;
    }
}
