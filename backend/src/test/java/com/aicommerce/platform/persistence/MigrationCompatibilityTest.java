package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationCompatibilityTest {

    private static final String V1_SHA256 = "ee4614654b5d47d1bebe40e451754413962d86b447d25354e1dc6cb70b03e2b9";
    private static final String V2_SHA256 = "8f944bfdb655ca90cffa37215e5e7bc8134fb13e021e261acf399e080b78d243";
    private static final String V3_SHA256 = "d1a5bc74fb4f2c57b711049fc6bb80e74dac1b7d5b4ff1905b95dec6ac204a93";
    private static final String V4_SHA256 = "d1b71029d5fa5cd9b283370281d57099495b3809e6c9fe43dc6afbc50f969ee9";
    private static final String V5_SHA256 = "8bdf970eac44dbb14724dcda6ae1056439a2b241e53cf0988c737f00a89dee22";
    private static final String V6_SHA256 = "b8acba2394208517870bc105d651da2dfe003fbc18dc8b5869c46ea37515fe03";
    private static final String V6_1_SHA256 = "4682d9dfbb9e194824064460242d81a665fc79bad6d41f5fb5059abf1fa18b67";
    private static final String V7_SHA256 = "74a0fc97fb1315a98336f54f7391e18011d53daffcace7b83805a910461d4cac";
    private static final String V8_SHA256 = "046d604295d83e94fba93fb54943fc832b3944ced5ebcc989ca475bb8bcef9f4";
    private static final String V9_SHA256 = "7c7e14faae71394182ecca06010dd8b97f42598480530abfeb13ccacefca7367";
    private static final String V10_SHA256 = "8d67fd339eb4cc0189e71394feb903a02bf51897fc0287bb2da1d8f78365f7d8";
    private static final String V11_SHA256 = "761371c64dc2283c7ba3f644802d0b523a50ab5fe342e89da8c8c6b9befc0a1c";
    private static final String V12_SHA256 = "828be0d98a681501e0572ad038698002275f72fd66c0095b44def10da7ddfcf3";
    private static final String V13_SHA256 = "5078ef1c025b512bd4f99008c240122689d423e8b8188c09becc37c953f1497c";
    private static final String V14_SHA256 = "03acc103faa2bd1cf94324cd2e7255e31d0e583cc5da7a88db9fab803289ad25";
    private static final String V15_SHA256 = "5af45b88aa833faf4e6c533511a854ddb5109ec91e52c0adfd8016861c7be986";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Test
    void emptyDatabaseRunsV1ThroughV12AndRepeatMigrationHasNoPendingWork() {
        Flyway flyway = flyway("empty_case", null);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(16);
        assertThat(List.of(flyway.info().applied()).stream()
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "6.1", "7", "8", "9", "10", "11", "12", "13", "14", "15");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void populatedMilestone2BDataSurvivesUpgradeToV4() {
        String schema = "upgrade_case";
        Flyway v3 = flyway(schema, MigrationVersion.fromVersion("3"));
        assertThat(v3.migrate().migrationsExecuted).isEqualTo(3);
        JdbcTemplate jdbc = jdbcTemplate();
        UUID activeProductUuid = UUID.randomUUID();
        UUID archivedProductUuid = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO upgrade_case.products
                    (product_uuid, product_id, sku, product_name, lifecycle_status, created_at, updated_at, version)
                VALUES (?, 'PROD-00000042', 'LEGACY-SKU', 'Legacy Product', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 7)
                """,
                new Object[] {activeProductUuid},
                new int[] {Types.OTHER});
        jdbc.update(
                """
                INSERT INTO upgrade_case.products
                    (product_uuid, product_id, product_name, lifecycle_status, archived_at,
                     created_at, updated_at, version)
                VALUES (?, 'PROD-00000043', 'Archived Product', 'ARCHIVED', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 3)
                """,
                new Object[] {archivedProductUuid},
                new int[] {Types.OTHER});
        UUID auditUuid = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO upgrade_case.audit_logs
                    (audit_uuid, operation_uuid, request_id, actor_type, actor_id, source,
                     action, entity_type, entity_uuid, product_uuid, occurred_at)
                VALUES (?, ?, 'upgrade-request', 'SYSTEM', 'migration-test', 'SYSTEM',
                        'UPDATE', 'PRODUCT', ?, ?, CURRENT_TIMESTAMP)
                """,
                auditUuid, UUID.randomUUID(), activeProductUuid, activeProductUuid);
        jdbc.update(
                """
                INSERT INTO upgrade_case.audit_log_changes
                    (audit_change_uuid, audit_uuid, field_name, old_value, new_value, value_type, change_order)
                VALUES (?, ?, 'sku', 'OLD', 'LEGACY-SKU', 'STRING', 0)
                """,
                UUID.randomUUID(), auditUuid);

        Flyway latest = flyway(schema, MigrationVersion.fromVersion("4"));
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);

        var row = jdbc.queryForMap(
                """
                SELECT product_uuid, product_id, sku, product_name, lifecycle_status, version
                FROM upgrade_case.products
                WHERE product_uuid = ?
                """,
                activeProductUuid);
        assertThat(row.get("product_uuid")).isEqualTo(activeProductUuid);
        assertThat(row.get("product_id")).isEqualTo("PROD-00000042");
        assertThat(row.get("sku")).isEqualTo("LEGACY-SKU");
        assertThat(row.get("product_name")).isEqualTo("Legacy Product");
        assertThat(row.get("lifecycle_status")).isEqualTo("ACTIVE");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(7L);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM upgrade_case.products WHERE product_uuid = ?",
                Long.class, archivedProductUuid)).isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM upgrade_case.audit_logs WHERE audit_uuid = ?",
                Integer.class, auditUuid)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT value_type FROM upgrade_case.audit_log_changes WHERE audit_uuid = ?",
                String.class, auditUuid)).isEqualTo("STRING");
        assertThat(latest.info().pending()).isEmpty();
    }

    @Test
    void populatedMilestone2CDataSurvivesUpgradeToV5AndReceivesProjections() {
        String schema = "v5_upgrade_case";
        Flyway v4 = flyway(schema, MigrationVersion.fromVersion("4"));
        assertThat(v4.migrate().migrationsExecuted).isEqualTo(4);
        JdbcTemplate jdbc = jdbcTemplate();
        UUID productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v5_upgrade_case.products
                    (product_uuid, product_id, sku, product_name, sale_price, currency,
                     lifecycle_status, created_at, updated_at, version)
                VALUES (?, 'PROD-00000088', 'V5-UPGRADE', 'V5 Product', 10.0000, 'TWD',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 9)
                """, productUuid);
        UUID knowledgeUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v5_upgrade_case.product_knowledge
                    (knowledge_uuid, product_uuid, knowledge_type, title, content, version)
                VALUES (?, ?, 'FEATURE', 'Feature', 'Content', 4)
                """, knowledgeUuid, productUuid);

        Flyway latest = flyway(schema, MigrationVersion.fromVersion("5"));
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM v5_upgrade_case.products WHERE product_uuid = ?",
                Long.class, productUuid)).isEqualTo(9L);
        assertThat(jdbc.queryForObject("SELECT version FROM v5_upgrade_case.product_knowledge WHERE knowledge_uuid = ?",
                Long.class, knowledgeUuid)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM v5_upgrade_case.quality_scores WHERE product_uuid = ?",
                Integer.class, productUuid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM v5_upgrade_case.workflow_status WHERE product_uuid = ?",
                Integer.class, productUuid)).isEqualTo(1);
        assertThat(latest.info().pending()).isEmpty();
    }

    @Test
    void populatedMilestone2DDataSurvivesUpgradeToV6() {
        String schema = "v6_upgrade_case";
        Flyway v5 = flyway(schema, MigrationVersion.fromVersion("5"));
        assertThat(v5.migrate().migrationsExecuted).isEqualTo(5);
        JdbcTemplate jdbc = jdbcTemplate();
        UUID productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v6_upgrade_case.products
                    (product_uuid, product_id, product_name, lifecycle_status, created_at, updated_at, version)
                VALUES (?, 'PROD-00000099', 'V6 Upgrade Product', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 11)
                """, productUuid);
        UUID scoreUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v6_upgrade_case.quality_scores
                    (quality_score_uuid, product_uuid, product_master_score, system_score, final_score)
                VALUES (?, ?, 35, 35, 35)
                """, scoreUuid, productUuid);

        Flyway latest = flyway(schema, MigrationVersion.fromVersion("6"));
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT version FROM v6_upgrade_case.products WHERE product_uuid = ?",
                Long.class, productUuid)).isEqualTo(11L);
        assertThat(jdbc.queryForObject(
                "SELECT final_score FROM v6_upgrade_case.quality_scores WHERE quality_score_uuid = ?",
                Integer.class, scoreUuid)).isEqualTo(35);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                Integer.class, schema, "sheet_import_jobs")).isEqualTo(1);
        assertThat(latest.info().pending()).isEmpty();
    }

    @Test
    void populatedV6ImportJobBackfillsAllHeadersBeforeDefaultIsRemoved() {
        String schema = "v6_1_upgrade_case";
        Flyway v6 = flyway(schema, MigrationVersion.fromVersion("6"));
        assertThat(v6.migrate().migrationsExecuted).isEqualTo(6);
        JdbcTemplate jdbc = jdbcTemplate();
        UUID jobUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v6_1_upgrade_case.sheet_import_jobs
                    (import_job_uuid, provider, spreadsheet_id, sheet_name, source_range,
                     source_fingerprint, status, total_rows, valid_rows, invalid_rows, created_by)
                VALUES (?, 'GOOGLE_SHEETS', 'legacy-sheet', 'Products', 'Products!A:M',
                        ?, 'PREVIEWED', 0, 0, 0, 'local-admin')
                """, jobUuid, "a".repeat(64));

        Flyway latest = flyway(schema, MigrationVersion.fromVersion("6.1"));
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT header_presence_mask FROM v6_1_upgrade_case.sheet_import_jobs WHERE import_job_uuid = ?",
                Integer.class, jobUuid)).isEqualTo(8191);
        assertThat(jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'sheet_import_jobs'
                  AND column_name = 'header_presence_mask'
                """, String.class, schema)).isNull();
        assertThat(List.of(latest.info().applied()).stream().filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion())).containsExactly("1", "2", "3", "4", "5", "6", "6.1");
    }

    @Test
    void populatedV6Point1DataSurvivesUpgradeToV7() {
        String schema = "v7_upgrade_case";
        Flyway v6Point1 = flyway(schema, MigrationVersion.fromVersion("6.1"));
        assertThat(v6Point1.migrate().migrationsExecuted).isEqualTo(7);
        JdbcTemplate jdbc = jdbcTemplate();
        UUID productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v7_upgrade_case.products
                    (product_uuid, product_id, product_name, lifecycle_status, version)
                VALUES (?, 'PROD-00000107', 'V7 Upgrade Product', 'ACTIVE', 4)
                """, productUuid);

        Flyway v7 = flyway(schema, MigrationVersion.fromVersion("7"));
        assertThat(v7.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM v7_upgrade_case.products WHERE product_uuid=?",
                Long.class, productUuid)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name IN ('product_storage_folders','product_storage_subfolders')",
                Integer.class, schema)).isEqualTo(2);
        assertThat(v7.info().pending()).isEmpty();
    }

    @Test
    void populatedV7DataSurvivesUpgradeToV8() {
        String schema = "v8_upgrade_case";
        Flyway v7 = flyway(schema, MigrationVersion.fromVersion("7"));
        assertThat(v7.migrate().migrationsExecuted).isEqualTo(8);
        JdbcTemplate jdbc = jdbcTemplate();
        UUID productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v8_upgrade_case.products
                    (product_uuid, product_id, product_name, lifecycle_status, version)
                VALUES (?, 'PROD-00000108', 'V8 Upgrade Product', 'ACTIVE', 12)
                """, productUuid);
        UUID folderUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v8_upgrade_case.product_storage_folders
                    (storage_folder_uuid, product_uuid, storage_provider, root_folder_id, product_folder_id)
                VALUES (?, ?, 'GOOGLE_DRIVE', 'root-v8', 'product-v8')
                """, folderUuid, productUuid);

        Flyway v8 = flyway(schema, MigrationVersion.fromVersion("8"));
        assertThat(v8.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM v8_upgrade_case.products WHERE product_uuid=?",
                Long.class, productUuid)).isEqualTo(12L);
        assertThat(jdbc.queryForObject(
                "SELECT product_folder_id FROM v8_upgrade_case.product_storage_folders WHERE storage_folder_uuid=?",
                String.class, folderUuid)).isEqualTo("product-v8");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=? AND table_name LIKE 'ai_%'
                """, Integer.class, schema)).isEqualTo(5);
        assertThat(v8.info().pending()).isEmpty();
    }

    @Test
    void populatedV8DataAndTextOutputSurviveUpgradeThroughV11() {
        String schema = "v9_upgrade_case";
        Flyway v8 = flyway(schema, MigrationVersion.fromVersion("8"));
        assertThat(v8.migrate().migrationsExecuted).isEqualTo(9);
        JdbcTemplate jdbc = jdbcTemplate(schema);
        UUID productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v9_upgrade_case.products
                    (product_uuid, product_id, product_name, lifecycle_status, version)
                VALUES (?, 'PROD-00000109', 'V9 Upgrade Product', 'ACTIVE', 13)
                """, productUuid);
        UUID templateUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v9_upgrade_case.ai_prompt_templates
                    (prompt_template_uuid, template_key, generation_type, display_name)
                VALUES (?, 'text.v9-upgrade', 'TEXT', 'V9 Upgrade')
                """, templateUuid);
        UUID versionUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v9_upgrade_case.ai_prompt_template_versions
                    (prompt_template_version_uuid, prompt_template_uuid, version_number, template_text,
                     input_schema, content_sha256, created_by)
                VALUES (?, ?, 1, 'Write copy', '{}'::jsonb, ?, 'tester')
                """, versionUuid, templateUuid, "9".repeat(64));
        UUID batchUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v9_upgrade_case.ai_generation_batches
                    (generation_batch_uuid, product_uuid, status, currency, estimated_cost,
                     reserved_cost, requested_job_count, created_by)
                VALUES (?, ?, 'CREATED', 'USD', 0.5, 1, 1, 'tester')
                """, batchUuid, productUuid);
        UUID jobUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v9_upgrade_case.ai_generation_jobs
                    (generation_job_uuid, generation_batch_uuid, product_uuid,
                     prompt_template_version_uuid, generation_type, provider_key, model_key,
                     rendered_prompt, input_snapshot, estimated_cost, reserved_cost, currency)
                VALUES (?, ?, ?, ?, 'TEXT', 'stub', 'stub-text', 'Write copy', '{}'::jsonb, 0.5, 1, 'USD')
                """, jobUuid, batchUuid, productUuid, versionUuid);

        Flyway v9 = flyway(schema, MigrationVersion.fromVersion("9"));
        assertThat(v9.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM v9_upgrade_case.ai_generation_jobs WHERE generation_job_uuid=?",
                Long.class, jobUuid)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=? AND table_name='ai_generation_outputs'
                """, Integer.class, schema)).isEqualTo(1);
        UUID outputUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO v9_upgrade_case.ai_generation_outputs
                    (generation_output_uuid, generation_job_uuid, generation_batch_uuid, product_uuid,
                     generation_type, text_content, model_label, input_units, output_units,
                     actual_cost, currency)
                VALUES (?, ?, ?, ?, 'TEXT', 'Existing text', 'stub', 1, 2, 0, 'USD')
                """, outputUuid, jobUuid, batchUuid, productUuid);

        Flyway v10 = flyway(schema, MigrationVersion.fromVersion("10"));
        assertThat(v10.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT text_content FROM v9_upgrade_case.ai_generation_outputs WHERE generation_output_uuid=?",
                String.class, outputUuid)).isEqualTo("Existing text");
        Flyway v11 = flyway(schema, MigrationVersion.fromVersion("11"));
        assertThat(v11.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT review_status FROM v9_upgrade_case.ai_generation_outputs WHERE generation_output_uuid=?",
                String.class, outputUuid)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=? AND table_name='ai_review_decisions'
                """, Integer.class, schema)).isEqualTo(1);
        assertThat(v11.info().pending()).isEmpty();
    }

    @Test
    void populatedV11RowsAndApprovedEvidenceSurviveUpgradeToV12() {
        String schema = "v12_upgrade_case";
        assertThat(flyway(schema, MigrationVersion.fromVersion("11")).migrate().migrationsExecuted).isEqualTo(12);
        JdbcTemplate jdbc = jdbcTemplate(schema);
        UUID product=UUID.randomUUID(), campaignPlan=UUID.randomUUID(), campaignProduct=UUID.randomUUID(), asset=UUID.randomUUID(), template=UUID.randomUUID(), templateVersion=UUID.randomUUID();
        UUID batch=UUID.randomUUID(), job=UUID.randomUUID(), output=UUID.randomUUID(), decision=UUID.randomUUID(), legacyAudit=UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status,version) VALUES (?,'PROD-00000412','V12 Upgrade','ACTIVE',5)",product);
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,objective,budget_daily,currency,version) VALUES (?,'Preserved Campaign','sales',12.3456,'TWD',7)",campaignPlan);
        jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid,version) VALUES (?,?,?,6)",campaignProduct,campaignPlan,product);
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256,version) VALUES (?,?,?,'IMAGE','PRESERVED',?,8)",asset,product,campaignPlan,"d".repeat(64));
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,'v12.upgrade','TEXT','V12')",template);
        jdbc.update("INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) VALUES (?,?,1,'copy','{}'::jsonb,?,'tester')",templateVersion,template,"a".repeat(64));
        jdbc.update("INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) VALUES (?,?,'COMPLETED','TWD',0,0,1,1,'tester')",batch,product);
        jdbc.update("INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) VALUES (?,?,?,?,'TEXT','stub','stub','SUCCEEDED','copy','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)",job,batch,product,templateVersion);
        jdbc.update("INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,text_content,model_label,input_units,output_units,actual_cost,currency) VALUES (?,?,?,?,'TEXT','approved','stub',1,1,0,'TWD')",output,job,batch,product);
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            boolean auto=connection.getAutoCommit(); connection.setAutoCommit(false);
            try (var insert=connection.prepareStatement("INSERT INTO ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) VALUES (?,?,'APPROVED','LOCAL_ADMIN','tester','v12-upgrade',0,current_timestamp)"); var approve=connection.prepareStatement("UPDATE ai_generation_outputs SET review_status='APPROVED',version=1 WHERE generation_output_uuid=?")) {
                insert.setObject(1,decision); insert.setObject(2,output); insert.executeUpdate(); approve.setObject(1,output); approve.executeUpdate(); connection.commit();
            } catch(Exception failure) { connection.rollback(); throw failure; } finally { connection.setAutoCommit(auto); }
            return null;
        });
        jdbc.update("INSERT INTO audit_logs(audit_uuid,operation_uuid,request_id,actor_type,actor_id,source,action,entity_type,entity_uuid,product_uuid) VALUES (?,?,'v12-audit','SYSTEM','migration-test','SYSTEM','UPDATE','PRODUCT',?,?)",legacyAudit,UUID.randomUUID(),product,product);
        Flyway latest=flyway(schema,null);
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT review_status FROM ai_generation_outputs WHERE generation_output_uuid=?",String.class,output)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT decision FROM ai_review_decisions WHERE review_decision_uuid=?",String.class,decision)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForMap("SELECT campaign_name,objective,budget_daily,currency,version FROM campaign_plans WHERE campaign_uuid=?",campaignPlan)).containsEntry("campaign_name","Preserved Campaign").containsEntry("objective","sales").containsEntry("currency","TWD").containsEntry("version",7L);
        assertThat(jdbc.queryForMap("SELECT product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256,version FROM assets WHERE asset_uuid=?",asset)).containsEntry("product_uuid",product).containsEntry("campaign_uuid",campaignPlan).containsEntry("asset_type","IMAGE").containsEntry("purpose","PRESERVED").containsEntry("checksum_sha256","d".repeat(64)).containsEntry("version",8L);
        assertThat(jdbc.queryForObject("SELECT version FROM products WHERE product_uuid=?",Long.class,product)).isEqualTo(5L);
        assertThat(jdbc.queryForObject("SELECT stage4b_operation_ordinal FROM audit_logs WHERE audit_uuid=?",Short.class,legacyAudit)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name LIKE 'platform_%'",Integer.class,schema)).isEqualTo(10);
        assertThat(latest.info().pending()).isEmpty();
    }

    @Test
    void canonicalV1ThroughV12ContentRemainsStable() throws Exception {
        assertThat(sha256("db/migration/V1__create_product_foundation.sql")).isEqualTo(V1_SHA256);
        assertThat(sha256("db/migration/V2__create_audit_foundation.sql")).isEqualTo(V2_SHA256);
        assertThat(sha256("db/migration/V3__add_product_master_fields.sql")).isEqualTo(V3_SHA256);
        assertThat(sha256("db/migration/V4__create_knowledge_plans_campaigns_assets.sql")).isEqualTo(V4_SHA256);
        assertThat(sha256("db/migration/V5__create_quality_and_workflow.sql")).isEqualTo(V5_SHA256);
        assertThat(sha256("db/migration/V6__create_sheet_import_foundation.sql")).isEqualTo(V6_SHA256);
        assertThat(sha256("db/migration/V6_1__add_sheet_import_header_presence.sql")).isEqualTo(V6_1_SHA256);
        assertThat(sha256("db/migration/V7__create_product_storage_folders.sql")).isEqualTo(V7_SHA256);
        assertThat(sha256("db/migration/V8__create_ai_generation_foundation.sql")).isEqualTo(V8_SHA256);
        assertThat(sha256("db/migration/V9__create_ai_text_outputs.sql")).isEqualTo(V9_SHA256);
        assertThat(sha256("db/migration/V10__add_ai_image_outputs.sql")).isEqualTo(V10_SHA256);
        assertThat(sha256("db/migration/V11__create_ai_review_decisions.sql")).isEqualTo(V11_SHA256);
        assertThat(sha256("db/migration/V12__create_platform_operation_foundation.sql")).isEqualTo(V12_SHA256);
        assertThat(sha256("db/migration/V13__add_platform_budget_authorization_ledger.sql")).isEqualTo(V13_SHA256);
        assertThat(sha256("db/migration/V14__add_stage4c_ad_publication_integrity.sql")).isEqualTo(V14_SHA256);
        assertThat(sha256("db/migration/V15__add_platform_metric_as_of_indexes.sql")).isEqualTo(V15_SHA256);
    }

    @Test
    void failedV12MigrationRollsBackEveryPartialV12Object() {
        String schema="atomic_v12_case";
        assertThat(flyway(schema,MigrationVersion.fromVersion("11")).migrate().migrationsExecuted).isEqualTo(12);
        JdbcTemplate jdbc=jdbcTemplate();
        jdbc.execute("CREATE TABLE "+schema+".platform_accounts(sentinel integer primary key)");

        assertThatThrownBy(()->flyway(schema,null).migrate()).isInstanceOf(FlywayException.class);

        for(String table:List.of("platform_campaigns","platform_ad_sets","platform_ads","platform_operations",
                "platform_operation_attempts","platform_metric_snapshots")){
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?",Integer.class,schema,table)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='platform_accounts'",Integer.class,schema)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=? AND p.proname IN ('is_valid_platform_evidence','is_valid_platform_attempt_result','is_valid_platform_request')",Integer.class,schema)).isZero();
        assertThat(flyway(schema,MigrationVersion.fromVersion("11")).info().current().getVersion().getVersion()).isEqualTo("11");
    }

    @Test
    void failedV13MigrationRollsBackEveryPartialV13Object() {
        String schema="atomic_v13_case";
        assertThat(flyway(schema,MigrationVersion.fromVersion("12")).migrate().migrationsExecuted).isEqualTo(13);
        JdbcTemplate jdbc=jdbcTemplate();
        jdbc.execute("CREATE TABLE "+schema+".platform_operation_batches(sentinel integer primary key)");

        assertThatThrownBy(()->flyway(schema,null).migrate()).isInstanceOf(FlywayException.class);

        for(String table:List.of("platform_budget_reservations","platform_account_budget_days")){
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?",Integer.class,schema,table)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='platform_operation_batches'",Integer.class,schema)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='audit_logs' AND column_name='stage4b_operation_ordinal'",Integer.class,schema)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=? AND p.proname LIKE 'platform_%budget%'",Integer.class,schema)).isZero();
        assertThat(flyway(schema,MigrationVersion.fromVersion("12")).info().current().getVersion().getVersion()).isEqualTo("12");
    }

    @Test
    void failedV14MigrationRollsBackEveryPartialV14Object() {
        String schema="atomic_v14_case";
        assertThat(flyway(schema,MigrationVersion.fromVersion("13")).migrate().migrationsExecuted).isEqualTo(14);
        JdbcTemplate jdbc=jdbcTemplate();
        jdbc.execute("CREATE FUNCTION "+schema+".is_stage4c_owned_operation(p uuid) RETURNS boolean LANGUAGE sql AS $$ SELECT false $$");

        assertThatThrownBy(()->flyway(schema,null).migrate()).isInstanceOf(FlywayException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=? AND p.proname IN ('is_stage4c_new_create_ad','is_approved_stage4c_account','stage4c_create_ad_canonical_json')",Integer.class,schema)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND t.tgname LIKE 'ct_platform_ad_%'",Integer.class,schema)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=? AND p.proname='is_stage4c_owned_operation'",Integer.class,schema)).isEqualTo(1);
        assertThat(flyway(schema,MigrationVersion.fromVersion("13")).info().current().getVersion().getVersion()).isEqualTo("13");
    }

    @Test
    void failedV15MigrationRollsBackEveryPartialV15Object() {
        String schema="atomic_v15_case";
        assertThat(flyway(schema,MigrationVersion.fromVersion("14")).migrate().migrationsExecuted).isEqualTo(15);
        JdbcTemplate jdbc=jdbcTemplate();
        jdbc.execute("CREATE INDEX idx_platform_metrics_campaign_as_of ON "+schema+".platform_metric_snapshots (platform_campaign_uuid)");

        assertThatThrownBy(()->flyway(schema,null).migrate()).isInstanceOf(FlywayException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname='idx_platform_metrics_ad_set_as_of'",Integer.class,schema)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname='idx_platform_metrics_ad_as_of'",Integer.class,schema)).isZero();
        assertThat(flyway(schema,MigrationVersion.fromVersion("14")).info().current().getVersion().getVersion()).isEqualTo("14");
    }

    @Test
    void failedForwardOnlyV16CollisionLeavesEveryV15ObjectInPlace() {
        String schema="forward_v16_case";
        assertThat(flyway(schema,null).migrate().migrationsExecuted).isEqualTo(16);
        var colliding=org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .locations("classpath:db/migration","classpath:db/test-forward-v16")
                .defaultSchema(schema).schemas(schema).createSchemas(true).cleanDisabled(true).load();
        assertThatThrownBy(colliding::migrate).isInstanceOf(FlywayException.class);
        JdbcTemplate jdbc=jdbcTemplate(schema);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname='idx_platform_metrics_campaign_as_of'",Integer.class,schema)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname='idx_platform_metrics_ad_set_as_of'",Integer.class,schema)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname='idx_platform_metrics_ad_as_of'",Integer.class,schema)).isEqualTo(1);
        assertThat(colliding.info().current().getVersion().getVersion()).isEqualTo("15");
    }

    @Test
    void populatedV14MetricSnapshotSurvivesUpgradeToV15() {
        String schema="v15_upgrade_case";
        assertThat(flyway(schema,MigrationVersion.fromVersion("14")).migrate().migrationsExecuted).isEqualTo(15);
        JdbcTemplate jdbc=jdbcTemplate(schema);
        UUID account=UUID.randomUUID(), plan=UUID.randomUUID(), campaign=UUID.randomUUID(), snapshot=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",
                account,"v15-"+account,account.toString().replace("-","").repeat(2));
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name) VALUES (?,'V15')",plan);
        jdbc.update("INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) VALUES (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
        jdbc.update("""
                INSERT INTO platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,impressions,spend,revision_number,fetched_at,freshness_status,source_fingerprint)
                VALUES (?,?,'CAMPAIGN',?,'2026-08-16T00:00:00Z'::timestamptz,'2026-08-17T00:00:00Z'::timestamptz,'Asia/Taipei','TWD',10000,25.000000,1,'2026-08-17T02:00:00Z'::timestamptz,'FRESH',?)
                """, snapshot, account, campaign, "a".repeat(64));
        var before=jdbc.queryForMap("SELECT metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,impressions,spend,revision_number,freshness_status,source_fingerprint FROM platform_metric_snapshots WHERE metric_snapshot_uuid=?",snapshot);
        assertThat(flyway(schema,null).migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,impressions,spend,revision_number,freshness_status,source_fingerprint FROM platform_metric_snapshots WHERE metric_snapshot_uuid=?",snapshot)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname IN ('idx_platform_metrics_campaign_as_of','idx_platform_metrics_ad_set_as_of','idx_platform_metrics_ad_as_of')",Integer.class,schema)).isEqualTo(3);
    }

    @Test
    void populatedLegacyCreateAdBytesSurviveUpgradeToV14() {
        String schema="v14_upgrade_case";
        assertThat(flyway(schema,MigrationVersion.fromVersion("13")).migrate().migrationsExecuted).isEqualTo(14);
        JdbcTemplate jdbc=jdbcTemplate(schema);
        UUID product=UUID.randomUUID(), plan=UUID.randomUUID(), source=UUID.randomUUID(), asset=UUID.randomUUID();
        UUID template=UUID.randomUUID(), templateVersion=UUID.randomUUID(), batch=UUID.randomUUID(), job=UUID.randomUUID();
        UUID output=UUID.randomUUID(), review=UUID.randomUUID(), account=UUID.randomUUID();
        UUID campaign=UUID.randomUUID(), adSet=UUID.randomUUID(), ad=UUID.randomUUID(), operation=UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status) VALUES (?,?,'V14 legacy','ACTIVE')",
                product,"PROD-"+String.format("%08d", Math.abs(product.hashCode())%100_000_000));
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,objective,budget_daily,currency) VALUES (?,'V14 Campaign','sales',12.3456,'TWD')",plan);
        jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) VALUES (?,?,?)",UUID.randomUUID(),plan,product);
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','SOURCE',?)",source,product,plan,"d".repeat(64));
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','GENERATED',?)",asset,product,plan,"e".repeat(64));
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,'v14.legacy','IMAGE','V14')",template);
        jdbc.update("INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) VALUES (?,?,1,'image','{}'::jsonb,?,'tester')",templateVersion,template,"a".repeat(64));
        jdbc.update("INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) VALUES (?,?,'COMPLETED','TWD',0,0,1,1,'tester')",batch,product);
        jdbc.update("INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) VALUES (?,?,?,?,'IMAGE','stub','stub','SUCCEEDED','image','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)",job,batch,product,templateVersion);
        jdbc.update("""
                INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,model_label,input_units,output_units,actual_cost,currency,safety_findings,provider_metadata,source_asset_uuid,generated_asset_uuid,generation_mode,workflow_key,workflow_version,image_width,image_height,media_type,size_bytes,source_checksum_sha256,output_checksum_sha256,protected_pixels_sha256,preservation_algorithm,preservation_status,preservation_details)
                VALUES (?,?,?,?,'IMAGE','stub',0,0,0,'TWD','[]'::jsonb,'{}'::jsonb,?,?,'BACKGROUND_COMPOSITE','sql-v1','1',1,1,'image/png',1,?,?,?,'RGBA_MASK_EXACT_V1','PASSED','{"changedPixelCount":0,"protectedPixelCount":1}'::jsonb)
                """, output, job, batch, product, source, asset, "d".repeat(64), "e".repeat(64), "f".repeat(64));
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            boolean auto=connection.getAutoCommit(); connection.setAutoCommit(false);
            try (var insert=connection.prepareStatement("INSERT INTO ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) VALUES (?,?,'APPROVED','LOCAL_ADMIN','tester','v14-upgrade',0,current_timestamp)");
                 var approve=connection.prepareStatement("UPDATE ai_generation_outputs SET review_status='APPROVED',version=1 WHERE generation_output_uuid=?")) {
                insert.setObject(1,review); insert.setObject(2,output); insert.executeUpdate();
                approve.setObject(1,output); approve.executeUpdate(); connection.commit();
            } catch(Exception failure) { connection.rollback(); throw failure; } finally { connection.setAutoCommit(auto); }
            return null;
        });
        String fingerprint=account.toString().replace("-","")+"c".repeat(32);
        jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"legacy-"+account,fingerprint);
        jdbc.update("INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,desired_state,account_timezone) VALUES (?,?,?,'OUTCOME_SALES','PAUSED','Asia/Taipei')",campaign,plan,account);
        jdbc.update("INSERT INTO platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key,desired_state) VALUES (?,?,?,'DAILY',20,'TWD','Asia/Taipei','OFFSITE_CONVERSIONS','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1','PAUSED')",adSet,campaign,account);
        jdbc.update("INSERT INTO platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) VALUES (?,?,?,?,?,?,?,?, 'IMAGE_PRIMARY_V1')",ad,adSet,account,product,asset,output,review,"e".repeat(64));
        String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_AD\",\"entityType\":\"AD\",\"entityUuid\":\""+ad
                +"\",\"platformAdUuid\":\""+ad+"\",\"platformAdSetUuid\":\""+adSet+"\",\"productUuid\":\""+product
                +"\",\"assetUuid\":\""+asset+"\",\"generationOutputUuid\":\""+output+"\",\"reviewDecisionUuid\":\""+review
                +"\",\"approvedChecksumSha256\":\""+"e".repeat(64)+"\",\"creativeMappingKey\":\"IMAGE_PRIMARY_V1\",\"desiredState\":\"PAUSED\"}";
        String sha="b".repeat(64);
        jdbc.update("""
                INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id)
                VALUES (?,?, 'CREATE_AD','AD',?,?, ?, ?::jsonb, ?,'LOCAL_ADMIN','local-admin','v14-legacy')
                """, operation, account, ad, UUID.randomUUID(), "c".repeat(64), payload, sha);
        String before=jdbc.queryForObject("SELECT request_payload::text FROM platform_operations WHERE operation_uuid=?",String.class,operation);
        assertThat(flyway(schema,null).migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT request_payload::text FROM platform_operations WHERE operation_uuid=?",String.class,operation)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT request_sha256 FROM platform_operations WHERE operation_uuid=?",String.class,operation)).isEqualTo(sha);
        assertThat(jdbc.queryForObject("SELECT jsonb_exists(request_payload,'expectedParentVersion') FROM platform_operations WHERE operation_uuid=?",Boolean.class,operation)).isFalse();
        assertThat(jdbc.queryForObject("SELECT request_payload->>'creativeMappingKey' FROM platform_operations WHERE operation_uuid=?",String.class,operation)).isEqualTo("IMAGE_PRIMARY_V1");
        assertThat(jdbc.queryForObject("SELECT is_stage4c_owned_operation(?)",Boolean.class,operation)).isFalse();
        assertThatThrownBy(()->jdbc.update("""
                INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id)
                VALUES (?,?, 'CREATE_AD','AD',?,?, ?, ?::jsonb, ?,'LOCAL_ADMIN','local-admin','v14-new-legacy')
                """, UUID.randomUUID(), account, ad, UUID.randomUUID(), "d".repeat(64), payload, "e".repeat(64)))
                .satisfies(failure -> {
                    Throwable current=failure;
                    while(current.getCause()!=null) current=current.getCause();
                    assertThat(current).isInstanceOf(java.sql.SQLException.class);
                    assertThat(((java.sql.SQLException)current).getSQLState()).isEqualTo("23514");
                });
    }

    @Test
    void failedV4MigrationRollsBackAllPartialObjects() {
        String schema = "atomic_case";
        Flyway v3 = flyway(schema, MigrationVersion.fromVersion("3"));
        assertThat(v3.migrate().migrationsExecuted).isEqualTo(3);
        JdbcTemplate jdbc = jdbcTemplate();
        jdbc.execute("CREATE TABLE atomic_case.assets (sentinel INTEGER PRIMARY KEY)");

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(FlywayException.class);

        for (String table : List.of("product_knowledge", "creative_plans", "campaign_plans", "campaign_products")) {
            assertThat(jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = ?
                    """,
                    Integer.class, schema, table)).isZero();
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'assets'",
                Integer.class, schema)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.check_constraints
                WHERE constraint_schema = ? AND constraint_name = 'ck_audit_log_changes_value_type'
                  AND check_clause LIKE '%DECIMAL%'
                """,
                Integer.class, schema)).isZero();
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

    private JdbcTemplate jdbcTemplate(String currentSchema) {
        return new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl() + "&currentSchema=" + currentSchema,
                postgres.getUsername(), postgres.getPassword()));
    }

    private String sha256(String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration resource: " + resource);
            }
            String canonicalContent = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalContent.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
