package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Milestone3BSchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void textOutputTableRemainsCompatibleAfterV10AndHibernateValidates() {
        assertThat(List.of(flyway.info().applied()).stream()
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "6.1", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
        assertThat(aiTables()).containsExactly(
                "ai_budget_ledger",
                "ai_generation_batches",
                "ai_generation_jobs",
                "ai_generation_outputs",
                "ai_prompt_template_versions",
                "ai_prompt_templates",
                "ai_review_decisions");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void directSqlRejectsInvalidTypeValuesJsonAndRelationship() {
        Fixture fixture = fixture();
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "IMAGE", "Copy", "stub-text", 1, 1, 0.1, "USD", "[]", "{}");
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "TEXT", " ", "stub-text", 1, 1, 0.1, "USD", "[]", "{}");
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "TEXT", "Copy", "stub-text", -1, 1, 0.1, "USD", "[]", "{}");
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "TEXT", "Copy", "stub-text", 1, 1, -0.1, "USD", "[]", "{}");
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "TEXT", "Copy", "stub-text", 1, 1, 0.1, "usd", "[]", "{}");
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "TEXT", "Copy", "stub-text", 1, 1, 0.1, "USD", "{}", "{}");
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "TEXT", "Copy", "stub-text", 1, 1, 0.1, "USD", "[]", "[]");
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), UUID.randomUUID(),
                fixture.productUuid(), "TEXT", "Copy", "stub-text", 1, 1, 0.1, "USD", "[]", "{}");
    }

    @Test
    void directSqlRejectsDuplicateOutputAndImmutableMutationOrDelete() {
        Fixture fixture = fixture();
        UUID outputUuid = insertValidOutput(fixture);
        assertRejected(insertOutputSql(), UUID.randomUUID(), fixture.jobUuid(), fixture.batchUuid(),
                fixture.productUuid(), "TEXT", "Other", "stub-text", 1, 1, 0.1, "USD", "[]", "{}");
        assertRejected("UPDATE ai_generation_outputs SET text_content='changed' WHERE generation_output_uuid=?",
                outputUuid);
        assertRejected("UPDATE ai_generation_outputs SET generation_job_uuid=? WHERE generation_output_uuid=?",
                UUID.randomUUID(), outputUuid);
        assertRejected("DELETE FROM ai_generation_outputs WHERE generation_output_uuid=?", outputUuid);
    }

    @Test
    void directSqlAcceptsOneValidPendingReviewTextOutput() {
        Fixture fixture = fixture();
        UUID outputUuid = insertValidOutput(fixture);
        assertThat(jdbc.queryForObject(
                "SELECT review_status FROM ai_generation_outputs WHERE generation_output_uuid=?",
                String.class, outputUuid)).isEqualTo("PENDING_REVIEW");
    }

    private UUID insertValidOutput(Fixture fixture) {
        UUID outputUuid = UUID.randomUUID();
        jdbc.update(insertOutputSql(), outputUuid, fixture.jobUuid(), fixture.batchUuid(), fixture.productUuid(),
                "TEXT", "Generated copy", "stub-text", 12, 24, 0.1, "USD", "[]", "{\"request\":\"stub\"}");
        return outputUuid;
    }

    private Fixture fixture() {
        UUID productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products(product_uuid, product_id, product_name, lifecycle_status, version)
                VALUES (?, ?, 'AI Text Product', 'ACTIVE', 0)
                """, productUuid, "PROD-" + String.format("%08d", Math.abs(productUuid.hashCode()) % 100000000));
        UUID templateUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_prompt_templates
                    (prompt_template_uuid, template_key, generation_type, display_name)
                VALUES (?, ?, 'TEXT', 'Text Template')
                """, templateUuid, "text." + templateUuid.toString().substring(0, 8));
        UUID versionUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_prompt_template_versions
                    (prompt_template_version_uuid, prompt_template_uuid, version_number, template_text,
                     input_schema, content_sha256, created_by)
                VALUES (?, ?, 1, 'Write copy', '{}'::jsonb, ?, 'tester')
                """, versionUuid, templateUuid, "b".repeat(64));
        UUID batchUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_batches
                    (generation_batch_uuid, product_uuid, status, currency, estimated_cost,
                     reserved_cost, requested_job_count, created_by)
                VALUES (?, ?, 'CREATED', 'USD', 0.1, 0.1, 1, 'tester')
                """, batchUuid, productUuid);
        UUID jobUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_jobs
                    (generation_job_uuid, generation_batch_uuid, product_uuid,
                     prompt_template_version_uuid, generation_type, provider_key, model_key,
                     rendered_prompt, input_snapshot, estimated_cost, reserved_cost, currency)
                VALUES (?, ?, ?, ?, 'TEXT', 'stub', 'stub-text', 'Write copy', '{}'::jsonb, 0.1, 0.1, 'USD')
                """, jobUuid, batchUuid, productUuid, versionUuid);
        return new Fixture(productUuid, batchUuid, jobUuid);
    }

    private String insertOutputSql() {
        return """
                INSERT INTO ai_generation_outputs
                    (generation_output_uuid, generation_job_uuid, generation_batch_uuid, product_uuid,
                     generation_type, text_content, model_label, input_units, output_units,
                     actual_cost, currency, safety_findings, provider_metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """;
    }

    private List<String> aiTables() {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema=current_schema() AND table_name LIKE 'ai_%'
                ORDER BY table_name
                """, String.class);
    }

    private void assertRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
    }

    private record Fixture(UUID productUuid, UUID batchUuid, UUID jobUuid) {
    }
}
