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
class Milestone3ASchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void latestSchemaRetainsApprovedFoundationTablesAndHibernateValidates() {
        assertThat(List.of(flyway.info().applied()).stream()
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "6.1", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
        assertThat(aiTables()).contains(
                "ai_budget_ledger",
                "ai_generation_batches",
                "ai_generation_jobs",
                "ai_prompt_template_versions",
                "ai_prompt_templates");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void directSqlRejectsInvalidEnumsMoneyCurrencyJsonAndRelationships() {
        Fixture fixture = fixture();
        assertRejected("""
                INSERT INTO ai_prompt_templates
                    (prompt_template_uuid, template_key, generation_type, display_name)
                VALUES (?, 'invalid-type', 'VIDEO', 'Invalid')
                """, UUID.randomUUID());
        assertRejected("""
                INSERT INTO ai_prompt_template_versions
                    (prompt_template_version_uuid, prompt_template_uuid, version_number, template_text,
                     input_schema, content_sha256, created_by)
                VALUES (?, ?, 2, 'Text', '[]'::jsonb, ?, 'tester')
                """, UUID.randomUUID(), fixture.templateUuid(), "b".repeat(64));
        assertRejected("""
                INSERT INTO ai_generation_batches
                    (generation_batch_uuid, product_uuid, status, currency, estimated_cost,
                     reserved_cost, requested_job_count, created_by)
                VALUES (?, ?, 'CREATED', 'usd', 1, 1, 1, 'tester')
                """, UUID.randomUUID(), fixture.productUuid());
        assertRejected("""
                INSERT INTO ai_generation_jobs
                    (generation_job_uuid, generation_batch_uuid, product_uuid,
                     prompt_template_version_uuid, generation_type, provider_key, model_key,
                     rendered_prompt, estimated_cost, reserved_cost, currency)
                VALUES (?, ?, ?, ?, 'IMAGE', 'stub', 'model', 'Prompt', 1, 1, 'USD')
                """, UUID.randomUUID(), fixture.batchUuid(), fixture.productUuid(), fixture.versionUuid());
        assertRejected("""
                INSERT INTO ai_budget_ledger
                    (budget_ledger_uuid, generation_job_uuid, budget_date, entry_type, amount, currency, entry_order)
                VALUES (?, ?, CURRENT_DATE, 'RESERVE', 0, 'USD', 0)
                """, UUID.randomUUID(), fixture.jobUuid());
        UUID unreservedJob = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_jobs
                    (generation_job_uuid, generation_batch_uuid, product_uuid,
                     prompt_template_version_uuid, generation_type, provider_key, model_key,
                     rendered_prompt, estimated_cost, reserved_cost, currency)
                VALUES (?, ?, ?, ?, 'TEXT', 'stub', 'model', 'Prompt', 1, 1, 'USD')
                """, unreservedJob, fixture.batchUuid(), fixture.productUuid(), fixture.versionUuid());
        assertRejected("""
                INSERT INTO ai_budget_ledger
                    (budget_ledger_uuid, generation_job_uuid, budget_date, entry_type, amount, currency, entry_order)
                VALUES (?, ?, CURRENT_DATE, 'COMMIT', 1, 'USD', 1)
                """, UUID.randomUUID(), unreservedJob);
        assertRejected("""
                INSERT INTO ai_budget_ledger
                    (budget_ledger_uuid, generation_job_uuid, budget_date, entry_type, amount, currency, entry_order)
                VALUES (?, ?, CURRENT_DATE, 'RELEASE', 0.5, 'USD', 1)
                """, UUID.randomUUID(), fixture.jobUuid());
        UUID otherProduct = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products(product_uuid, product_id, product_name, lifecycle_status, version)
                VALUES (?, ?, 'Other Product', 'ACTIVE', 0)
                """, otherProduct,
                "PROD-" + String.format("%08d", Math.abs(otherProduct.hashCode()) % 100000000));
        UUID wrongPlan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO creative_plans(creative_plan_uuid, product_uuid, plan_name)
                VALUES (?, ?, 'Wrong Product Plan')
                """, wrongPlan, otherProduct);
        assertRejected("""
                INSERT INTO ai_generation_batches
                    (generation_batch_uuid, product_uuid, creative_plan_uuid, status, currency,
                     estimated_cost, reserved_cost, requested_job_count, created_by)
                VALUES (?, ?, ?, 'CREATED', 'USD', 1, 1, 1, 'tester')
                """, UUID.randomUUID(), fixture.productUuid(), wrongPlan);
    }

    @Test
    void directSqlCannotBypassIdentityAppendOnlyOrDeleteProtection() {
        Fixture fixture = fixture();
        assertRejected("UPDATE ai_prompt_templates SET template_key='changed' WHERE prompt_template_uuid=?",
                fixture.templateUuid());
        assertRejected("UPDATE ai_prompt_template_versions SET template_text='changed' WHERE prompt_template_version_uuid=?",
                fixture.versionUuid());
        assertRejected("DELETE FROM ai_prompt_template_versions WHERE prompt_template_version_uuid=?",
                fixture.versionUuid());
        assertRejected("UPDATE ai_generation_batches SET product_uuid=? WHERE generation_batch_uuid=?",
                UUID.randomUUID(), fixture.batchUuid());
        assertRejected("DELETE FROM ai_generation_batches WHERE generation_batch_uuid=?", fixture.batchUuid());
        assertRejected("UPDATE ai_generation_jobs SET rendered_prompt='changed' WHERE generation_job_uuid=?",
                fixture.jobUuid());
        assertRejected("UPDATE ai_generation_jobs SET provider_job_id='provider-early' WHERE generation_job_uuid=?",
                fixture.jobUuid());
        jdbc.update("""
                UPDATE ai_generation_jobs
                   SET provider_job_id='provider-1', status='SUBMITTED', submitted_at=CURRENT_TIMESTAMP
                 WHERE generation_job_uuid=?
                """, fixture.jobUuid());
        assertRejected("UPDATE ai_generation_jobs SET provider_job_id='provider-2' WHERE generation_job_uuid=?",
                fixture.jobUuid());
        assertRejected("DELETE FROM ai_generation_jobs WHERE generation_job_uuid=?", fixture.jobUuid());
        assertRejected("UPDATE ai_budget_ledger SET amount=2 WHERE generation_job_uuid=?", fixture.jobUuid());
        assertRejected("DELETE FROM ai_budget_ledger WHERE generation_job_uuid=?", fixture.jobUuid());
        assertRejected("DELETE FROM ai_prompt_templates WHERE prompt_template_uuid=?", fixture.templateUuid());
    }

    private Fixture fixture() {
        UUID productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products(product_uuid, product_id, product_name, lifecycle_status, version)
                VALUES (?, ?, 'AI Foundation Product', 'ACTIVE', 0)
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
                """, versionUuid, templateUuid, "a".repeat(64));
        UUID batchUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_batches
                    (generation_batch_uuid, product_uuid, status, currency, estimated_cost,
                     reserved_cost, requested_job_count, created_by)
                VALUES (?, ?, 'CREATED', 'USD', 0.5, 1, 1, 'tester')
                """, batchUuid, productUuid);
        UUID jobUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_jobs
                    (generation_job_uuid, generation_batch_uuid, product_uuid,
                     prompt_template_version_uuid, generation_type, provider_key, model_key,
                     rendered_prompt, input_snapshot, estimated_cost, reserved_cost, currency)
                VALUES (?, ?, ?, ?, 'TEXT', 'stub', 'stub-text', 'Write copy', '{}'::jsonb, 0.5, 1, 'USD')
                """, jobUuid, batchUuid, productUuid, versionUuid);
        jdbc.update("""
                INSERT INTO ai_budget_ledger
                    (budget_ledger_uuid, generation_job_uuid, budget_date, entry_type, amount, currency, entry_order)
                VALUES (?, ?, CURRENT_DATE, 'RESERVE', 1, 'USD', 0)
                """, UUID.randomUUID(), jobUuid);
        return new Fixture(productUuid, templateUuid, versionUuid, batchUuid, jobUuid);
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

    private record Fixture(UUID productUuid, UUID templateUuid, UUID versionUuid,
            UUID batchUuid, UUID jobUuid) {
    }
}
