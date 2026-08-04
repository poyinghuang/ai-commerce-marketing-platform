package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationCompatibilityTest {

    private static final String V1_SHA256 = "83fb7d23243362385d04dfdb51fc71df90e16dfa1edb009d8d2e016a60850727";
    private static final String V2_SHA256 = "3d55eeea5e9ef3525537c5249c9d484ce38309e3367c57304095fd76b559ae73";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Test
    void emptyDatabaseRunsV1ThroughV3AndRepeatMigrationHasNoPendingWork() {
        Flyway flyway = flyway("empty_case", null);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(List.of(flyway.info().applied()).stream()
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("1", "2", "3");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void existingMilestone2AProductSurvivesUpgradeToV3WithoutInventedMasterData() {
        String schema = "upgrade_case";
        Flyway v2 = flyway(schema, MigrationVersion.fromVersion("2"));
        assertThat(v2.migrate().migrationsExecuted).isEqualTo(2);
        JdbcTemplate jdbc = jdbcTemplate();
        UUID productUuid = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO upgrade_case.products
                    (product_uuid, product_id, sku, lifecycle_status, created_at, updated_at, version)
                VALUES (?, 'PROD-00000042', 'LEGACY-SKU', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 7)
                """,
                new Object[] {productUuid},
                new int[] {Types.OTHER});

        Flyway latest = flyway(schema, null);
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);

        var row = jdbc.queryForMap(
                """
                SELECT product_uuid, product_id, sku, product_name, lifecycle_status, version
                FROM upgrade_case.products
                WHERE product_uuid = ?
                """,
                productUuid);
        assertThat(row.get("product_uuid")).isEqualTo(productUuid);
        assertThat(row.get("product_id")).isEqualTo("PROD-00000042");
        assertThat(row.get("sku")).isEqualTo("LEGACY-SKU");
        assertThat(row.get("product_name")).isNull();
        assertThat(row.get("lifecycle_status")).isEqualTo("ACTIVE");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(7L);
        assertThat(latest.info().pending()).isEmpty();
    }

    @Test
    void mergedV1AndV2ResourcesRemainByteForByteStable() throws Exception {
        assertThat(sha256("db/migration/V1__create_product_foundation.sql")).isEqualTo(V1_SHA256);
        assertThat(sha256("db/migration/V2__create_audit_foundation.sql")).isEqualTo(V2_SHA256);
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .createSchemas(true)
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    private String sha256(String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration resource: " + resource);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }
}
