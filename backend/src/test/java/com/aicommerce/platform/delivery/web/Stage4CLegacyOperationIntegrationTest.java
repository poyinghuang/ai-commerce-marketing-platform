package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Stage4CLegacyOperationIntegrationTest.LegacyFixtureMigration.class)
class Stage4CLegacyOperationIntegrationTest {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-4000-8000-00000000005b");
    private static final UUID PLAN = UUID.fromString("00000000-0000-4000-8000-0000000004c1");
    private static final UUID CAMPAIGN = UUID.fromString("00000000-0000-4000-8000-0000000004c2");
    private static final UUID AD_SET = UUID.fromString("00000000-0000-4000-8000-0000000004c3");
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-4000-8000-0000000004c4");
    private static final UUID SOURCE = UUID.fromString("00000000-0000-4000-8000-0000000004c5");
    private static final UUID ASSET = UUID.fromString("00000000-0000-4000-8000-0000000004c6");
    private static final UUID TEMPLATE = UUID.fromString("00000000-0000-4000-8000-0000000004c7");
    private static final UUID TEMPLATE_VERSION = UUID.fromString("00000000-0000-4000-8000-0000000004c8");
    private static final UUID AI_BATCH = UUID.fromString("00000000-0000-4000-8000-0000000004c9");
    private static final UUID AI_JOB = UUID.fromString("00000000-0000-4000-8000-0000000004ca");
    private static final UUID AI_OUTPUT = UUID.fromString("00000000-0000-4000-8000-0000000004cb");
    private static final UUID REVIEW = UUID.fromString("00000000-0000-4000-8000-0000000004cc");
    private static final UUID AD = UUID.fromString("00000000-0000-4000-8000-0000000004cd");
    private static final List<UUID> STATE_OPERATIONS = List.of(
            UUID.fromString("00000000-0000-4000-8000-0000000004d0"),
            UUID.fromString("00000000-0000-4000-8000-0000000004d1"),
            UUID.fromString("00000000-0000-4000-8000-0000000004d2"),
            UUID.fromString("00000000-0000-4000-8000-0000000004d3"),
            UUID.fromString("00000000-0000-4000-8000-0000000004d4"),
            UUID.fromString("00000000-0000-4000-8000-0000000004d5"),
            UUID.fromString("00000000-0000-4000-8000-0000000004d6"));
    private static final List<String> EXPECTED_STATES = List.of(
            "CREATED", "SUBMITTING", "FAILED_RETRYABLE", "FAILED_TERMINAL", "SUCCEEDED", "UNKNOWN_OUTCOME", "RECONCILING");
    private static final List<String> GRAPH_TABLES = List.of(
            "platform_accounts", "campaign_plans", "products", "campaign_products", "assets",
            "ai_prompt_templates", "ai_prompt_template_versions", "ai_generation_batches", "ai_generation_jobs",
            "ai_generation_outputs", "ai_review_decisions", "platform_campaigns", "platform_ad_sets", "platform_ads",
            "platform_operations", "platform_operation_attempts");
    private static final Map<String, String> V13_TABLES = new LinkedHashMap<>();

    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DeterministicFakePlatformAdapter fake;

    @Test void populatedV13CreateAdRowsAreBytePreservedReadableAndRetryReconcileAreInert() throws Exception {
        GRAPH_TABLES.forEach(table -> assertThat(rows(table)).as(table).isEqualTo(V13_TABLES.get(table)));
        assertThat(STATE_OPERATIONS.stream().map(operation -> jdbc.queryForObject("select status from platform_operations where operation_uuid=?", String.class, operation)).toList())
                .containsExactlyElementsOf(EXPECTED_STATES);
        Map<String, String> before = snapshot();
        int calls = fake.invocationCount();
        for (int index = 0; index < STATE_OPERATIONS.size(); index++) {
            UUID operation = STATE_OPERATIONS.get(index);
            long version = jdbc.queryForObject("select version from platform_operations where operation_uuid=?", Long.class, operation);
            Map<String, String> beforeGet = snapshot();
            mvc.perform(get("/api/platform-operations/" + operation)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.operationUuid").value(operation.toString()))
                    .andExpect(jsonPath("$.status").value(EXPECTED_STATES.get(index)))
                    .andExpect(jsonPath("$.operationType").value("CREATE_AD"));
            assertThat(snapshot()).as("GET legacy CREATE_AD " + operation).isEqualTo(beforeGet);
            Map<String, String> beforeRetry = snapshot();
            mvc.perform(post("/api/platform-operations/" + operation + "/retry").header("If-Match", "W/\"" + version + "\""))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_LEGACY_OPERATION_INERT"));
            assertThat(snapshot()).as("retry legacy CREATE_AD " + operation).isEqualTo(beforeRetry);
            Map<String, String> beforeReconcile = snapshot();
            mvc.perform(post("/api/platform-operations/" + operation + "/reconcile").header("If-Match", "W/\"" + version + "\""))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_LEGACY_OPERATION_INERT"));
            assertThat(snapshot()).as("reconcile legacy CREATE_AD " + operation).isEqualTo(beforeReconcile);
        }
        assertThat(snapshot()).isEqualTo(before);
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    private Map<String, String> snapshot() {
        Map<String, String> graph = new LinkedHashMap<>();
        GRAPH_TABLES.forEach(table -> graph.put(table, rows(table)));
        return Map.copyOf(graph);
    }

    private String rows(String table) {
        String where = switch (table) {
            case "ai_prompt_templates" -> " where prompt_template_uuid='" + TEMPLATE + "'";
            case "ai_prompt_template_versions" -> " where prompt_template_version_uuid='" + TEMPLATE_VERSION + "'";
            default -> "";
        };
        return jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(t) order by " + order(table) + "),'[]')::text from " + table + " t" + where, String.class);
    }

    private static String order(String table) {
        return switch (table) {
            case "platform_accounts" -> "platform_account_uuid";
            case "campaign_plans" -> "campaign_uuid";
            case "products" -> "product_uuid";
            case "campaign_products" -> "campaign_product_uuid";
            case "assets" -> "asset_uuid";
            case "ai_prompt_templates" -> "prompt_template_uuid";
            case "ai_prompt_template_versions" -> "prompt_template_version_uuid";
            case "ai_generation_batches" -> "generation_batch_uuid";
            case "ai_generation_jobs" -> "generation_job_uuid";
            case "ai_generation_outputs" -> "generation_output_uuid";
            case "ai_review_decisions" -> "review_decision_uuid";
            case "platform_campaigns" -> "platform_campaign_uuid";
            case "platform_ad_sets" -> "platform_ad_set_uuid";
            case "platform_ads" -> "platform_ad_uuid";
            case "platform_operations" -> "operation_uuid";
            case "platform_operation_attempts" -> "operation_attempt_uuid";
            default -> throw new IllegalArgumentException(table);
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LegacyFixtureMigration {
        @Bean
        FlywayMigrationStrategy v13CreateAdThenV14(DataSource dataSource) {
            return latest -> {
                Flyway v13 = Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("13")).load();
                v13.migrate();
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST','stage4b-test',?,'TWD','Asia/Taipei')", ACCOUNT, "9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2");
                jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) values (?,'Legacy 4C',current_date+10,current_date+20,'OUTCOME_SALES','META',100,300,'TWD')", PLAN);
                jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,schedule_start,schedule_end,account_timezone) values (?,?,?,'OUTCOME_SALES',current_timestamp+interval '10 days',current_timestamp+interval '20 days','Asia/Taipei')", CAMPAIGN, PLAN, ACCOUNT);
                jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,schedule_start,schedule_end,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'DAILY',25,'TWD',current_timestamp+interval '10 days',current_timestamp+interval '20 days','Asia/Taipei','OFFSITE_CONVERSIONS','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1')", AD_SET, CAMPAIGN, ACCOUNT);
                jdbc.update("insert into products(product_uuid,product_id,product_name,lifecycle_status) values (?,'PROD-00000414','Legacy 4C product','ACTIVE')", PRODUCT);
                jdbc.update("insert into campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) values (?,?,?)", UUID.randomUUID(), PLAN, PRODUCT);
                jdbc.update("insert into assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) values (?,?,?,'IMAGE','SOURCE',?)", SOURCE, PRODUCT, PLAN, "d".repeat(64));
                jdbc.update("insert into assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) values (?,?,?,'IMAGE','GENERATED',?)", ASSET, PRODUCT, PLAN, "e".repeat(64));
                jdbc.update("insert into ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) values (?,'legacy.4c.image','IMAGE','Legacy 4C')", TEMPLATE);
                jdbc.update("insert into ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) values (?,?,1,'image','{}'::jsonb,?,'legacy')", TEMPLATE_VERSION, TEMPLATE, "a".repeat(64));
                jdbc.update("insert into ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) values (?,?,'COMPLETED','TWD',0,0,1,1,'legacy')", AI_BATCH, PRODUCT);
                jdbc.update("insert into ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) values (?,?,?,?,'IMAGE','stub','stub','SUCCEEDED','image','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)", AI_JOB, AI_BATCH, PRODUCT, TEMPLATE_VERSION);
                jdbc.update("insert into ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,model_label,input_units,output_units,actual_cost,currency,safety_findings,provider_metadata,source_asset_uuid,generated_asset_uuid,generation_mode,workflow_key,workflow_version,image_width,image_height,media_type,size_bytes,source_checksum_sha256,output_checksum_sha256,protected_pixels_sha256,preservation_algorithm,preservation_status,preservation_details) values (?,?,?,?, 'IMAGE','stub-image',0,0,0,'TWD','[]'::jsonb,'{}'::jsonb,?,?,'BACKGROUND_COMPOSITE','legacy-image-v1','1',1,1,'image/png',1,?,?,?,'RGBA_MASK_EXACT_V1','PASSED','{\"changedPixelCount\":0,\"protectedPixelCount\":1}'::jsonb)", AI_OUTPUT, AI_JOB, AI_BATCH, PRODUCT, SOURCE, ASSET, "d".repeat(64), "e".repeat(64), "f".repeat(64));
                TransactionTemplate evidenceTx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                evidenceTx.executeWithoutResult(s -> {
                    jdbc.update("insert into ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) values (?,?,'APPROVED','LOCAL_ADMIN','legacy','legacy-review',0,current_timestamp)", REVIEW, AI_OUTPUT);
                    jdbc.update("update ai_generation_outputs set review_status='APPROVED',version=1 where generation_output_uuid=?", AI_OUTPUT);
                });
                jdbc.update("insert into platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) values (?,?,?,?,?,?,?,?, 'IMAGE_PRIMARY_V1')", AD, AD_SET, ACCOUNT, PRODUCT, ASSET, AI_OUTPUT, REVIEW, "e".repeat(64));
                TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                for (UUID operation : STATE_OPERATIONS) insertOperation(jdbc, operation);
                claimSubmit(jdbc, tx, STATE_OPERATIONS.get(1));
                claimSubmit(jdbc, tx, STATE_OPERATIONS.get(2));
                finalizeSubmit(jdbc, tx, STATE_OPERATIONS.get(2), "FAILED_RETRYABLE", "PLATFORM_RATE_LIMITED", "{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"FAILED_RETRYABLE\",\"retryAfterSeconds\":60}");
                claimSubmit(jdbc, tx, STATE_OPERATIONS.get(3));
                finalizeSubmit(jdbc, tx, STATE_OPERATIONS.get(3), "FAILED_TERMINAL", "PLATFORM_VALIDATION_FAILED", "{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"FAILED_TERMINAL\"}");
                claimSubmit(jdbc, tx, STATE_OPERATIONS.get(4));
                tx.executeWithoutResult(s -> {
                    String external = "legacy-ad-external";
                    String fingerprint = jdbc.queryForObject("select encode(sha256(convert_to(?,'UTF8')),'hex')", String.class, external);
                    String success = "{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"SUCCEEDED\",\"externalIdFingerprint\":\"" + fingerprint + "\",\"observedState\":\"PAUSED\"}";
                    jdbc.update("update platform_operation_attempts set status='SUCCEEDED',evidence=?::jsonb,completed_at=statement_timestamp(),version=1 where operation_uuid=? and attempt_kind='SUBMIT'", success, STATE_OPERATIONS.get(4));
                    jdbc.update("update platform_operations set status='SUCCEEDED',external_id=?,outcome_evidence=?::jsonb,claimed_at=null,completed_at=statement_timestamp(),updated_at=statement_timestamp(),version=2 where operation_uuid=?", external, success, STATE_OPERATIONS.get(4));
                    jdbc.update("update platform_ads set external_id=?,observed_state='PAUSED',version=1,updated_at=statement_timestamp() where platform_ad_uuid=?", external, AD);
                });
                claimSubmit(jdbc, tx, STATE_OPERATIONS.get(5));
                finalizeSubmit(jdbc, tx, STATE_OPERATIONS.get(5), "UNKNOWN_OUTCOME", "PLATFORM_RESPONSE_AMBIGUOUS", "{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"UNKNOWN_OUTCOME\"}");
                claimSubmit(jdbc, tx, STATE_OPERATIONS.get(6));
                finalizeSubmit(jdbc, tx, STATE_OPERATIONS.get(6), "UNKNOWN_OUTCOME", "PLATFORM_RESPONSE_AMBIGUOUS", "{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"UNKNOWN_OUTCOME\"}");
                claimReconcile(jdbc, tx, STATE_OPERATIONS.get(6));
                for (String table : GRAPH_TABLES) {
                    String where = switch (table) {
                        case "ai_prompt_templates" -> " where prompt_template_uuid='" + TEMPLATE + "'";
                        case "ai_prompt_template_versions" -> " where prompt_template_version_uuid='" + TEMPLATE_VERSION + "'";
                        default -> "";
                    };
                    V13_TABLES.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(t) order by " + order(table) + "),'[]')::text from " + table + " t" + where, String.class));
                }
                latest.migrate();
            };
        }

        private static void insertOperation(JdbcTemplate jdbc, UUID operation) {
            UUID request = UUID.nameUUIDFromBytes(operation.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String payload = "{\"schemaVersion\":1,\"operationType\":\"CREATE_AD\",\"entityType\":\"AD\",\"entityUuid\":\"" + AD
                    + "\",\"platformAdUuid\":\"" + AD + "\",\"platformAdSetUuid\":\"" + AD_SET + "\",\"productUuid\":\"" + PRODUCT
                    + "\",\"assetUuid\":\"" + ASSET + "\",\"generationOutputUuid\":\"" + AI_OUTPUT + "\",\"reviewDecisionUuid\":\"" + REVIEW
                    + "\",\"approvedChecksumSha256\":\"" + "e".repeat(64) + "\",\"creativeMappingKey\":\"IMAGE_PRIMARY_V1\",\"desiredState\":\"PAUSED\"}";
            jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) values (?,?, 'CREATE_AD','AD',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin',?)",
                    operation, ACCOUNT, AD, request, hex(operation), payload, hex(request), "legacy-" + operation.toString().substring(30));
        }

        private static void claimSubmit(JdbcTemplate jdbc, TransactionTemplate tx, UUID operation) {
            tx.executeWithoutResult(s -> {
                jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=attempt_count+1,claimed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 where operation_uuid=?", operation);
                jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) select ?,operation_uuid,'SUBMIT',attempt_count,'STARTED',claimed_at,0 from platform_operations where operation_uuid=?", UUID.randomUUID(), operation);
            });
        }

        private static void finalizeSubmit(JdbcTemplate jdbc, TransactionTemplate tx, UUID operation, String status, String code, String evidence) {
            tx.executeWithoutResult(s -> {
                jdbc.update("update platform_operation_attempts set status=?,normalized_error_code=?,safe_provider_trace_id='legacy-trace',evidence=?::jsonb,completed_at=statement_timestamp(),version=1 where operation_uuid=? and attempt_kind='SUBMIT'", status, code, evidence, operation);
                jdbc.update("update platform_operations set status=?,normalized_error_code=?,safe_provider_trace_id='legacy-trace',outcome_evidence=?::jsonb,next_attempt_at=case when ?='FAILED_RETRYABLE' then (select completed_at+interval '60 seconds' from platform_operation_attempts where operation_uuid=? and attempt_kind='SUBMIT') else null end,claimed_at=null,completed_at=case when ?='FAILED_TERMINAL' then statement_timestamp() else null end,updated_at=statement_timestamp(),version=version+1 where operation_uuid=?", status, code, evidence, status, operation, status, operation);
            });
        }

        private static void claimReconcile(JdbcTemplate jdbc, TransactionTemplate tx, UUID operation) {
            tx.executeWithoutResult(s -> {
                jdbc.update("update platform_operations set status='RECONCILING',reconciliation_count=reconciliation_count+1,normalized_error_code=null,safe_provider_trace_id=null,outcome_evidence=null,claimed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 where operation_uuid=?", operation);
                jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) select ?,operation_uuid,'RECONCILE',reconciliation_count,'STARTED',claimed_at,0 from platform_operations where operation_uuid=?", UUID.randomUUID(), operation);
            });
        }

        private static String hex(UUID id) { return id.toString().replace("-", "").repeat(2); }
    }
}
