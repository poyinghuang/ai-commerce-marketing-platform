package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.PlatformOperationService;
import com.aicommerce.platform.delivery.application.Stage4BTransactions;
import com.aicommerce.platform.delivery.application.Stage4CService;
import com.aicommerce.platform.delivery.application.Stage4CTransactions;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Stage4CDirectSqlIntegrityIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Stage4BTransactions stage4b;
    @Autowired Stage4CTransactions tx;
    @Autowired Stage4CService service;
    @Autowired PlatformOperationService operations;
    UUID plan;

    @BeforeEach void resetGraph() {
        jdbc.execute("TRUNCATE platform_budget_reservations, platform_operation_batches, platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ads, platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs RESTART IDENTITY CASCADE");
        plan = UUID.randomUUID();
        jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
          VALUES (?,'Stage 4C SQL',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
          """, plan, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
    }

    @Test void forgedParentVersionClaimRollsBackAsStaleNamedConstraint() {
        Graph g = graph();
        UUID operation = insertCreateAd(g, g.parentVersion + 1);
        String before = snapshot();
        assertNamed("23514", "ct_platform_ad_submit_claim_stale", () -> claim(operation));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?", Integer.class, operation)).isZero();
    }

    @Test void parentStateAndInvalidEvidenceClaimsRollBackNamedConstraints() {
        Graph g = graph();
        UUID evidenceOp = insertCreateAd(g, g.parentVersion);
        jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "c".repeat(64), g.asset);
        assertNamed("23514", "ct_platform_ad_submit_claim_evidence", () -> claim(evidenceOp));
        jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "e".repeat(64), g.asset);
        long campaignVersion = jdbc.queryForObject("SELECT version FROM platform_campaigns WHERE platform_campaign_uuid=?", Long.class, g.campaign);
        var campaignResume = stage4b.confirmState(com.aicommerce.platform.delivery.domain.PlatformEntityType.CAMPAIGN, g.campaign, UUID.randomUUID(), com.aicommerce.platform.delivery.domain.PlatformDesiredState.ACTIVE, campaignVersion, "sql-parent-campaign");
        operations.submit(campaignResume.operation().getOperationUuid(), campaignResume.operation().getVersion());
        long adSetVersion = jdbc.queryForObject("SELECT version FROM platform_ad_sets WHERE platform_ad_set_uuid=?", Long.class, g.adSet);
        var adSetResume = stage4b.confirmState(com.aicommerce.platform.delivery.domain.PlatformEntityType.AD_SET, g.adSet, UUID.randomUUID(), com.aicommerce.platform.delivery.domain.PlatformDesiredState.ACTIVE, adSetVersion, "sql-parent-adset");
        operations.submit(adSetResume.operation().getOperationUuid(), adSetResume.operation().getVersion());
        long parent = jdbc.queryForObject("SELECT version FROM platform_ad_sets WHERE platform_ad_set_uuid=?", Long.class, g.adSet);
        UUID parentStateOp = insertCreateAd(g, parent);
        assertNamed("23514", "ct_platform_ad_submit_claim_parent_state", () -> claim(parentStateOp));
    }

    @Test void falseCreateSuccessAndExternalIdSubstitutionRollBackDispatchConstraint() {
        Graph g = graph();
        var created = tx.confirmCreate(g.adSet, UUID.randomUUID(), g.product, g.asset, g.output, g.review, g.parentVersion, "sql-false-success");
        UUID operation = created.operation().getOperationUuid();
        UUID ad = created.operation().getEntityUuid();
        claim(operation);
        String before = snapshot();
        assertNamed("23514", "ct_platform_ad_dispatch_result", () -> tx(() -> {
            jdbc.update("""
                    UPDATE platform_operation_attempts SET status='SUCCEEDED',evidence='{"schemaVersion":1,"providerKey":"FAKE","attemptKind":"SUBMIT","resultKind":"SUCCEEDED","externalIdFingerprint":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}'::jsonb,
                    completed_at=statement_timestamp(),version=1 WHERE operation_uuid=? AND attempt_kind='SUBMIT'
                    """, operation);
            jdbc.update("""
                    UPDATE platform_operations SET status='SUCCEEDED',outcome_evidence='{"schemaVersion":1,"providerKey":"FAKE","attemptKind":"SUBMIT","resultKind":"SUCCEEDED","externalIdFingerprint":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}'::jsonb,
                    external_id='forged-external',claimed_at=null,completed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1
                    WHERE operation_uuid=?
                    """, operation);
            jdbc.update("UPDATE platform_ads SET external_id='other-external',desired_state='PAUSED',version=1,updated_at=statement_timestamp() WHERE platform_ad_uuid=?", ad);
        }, true));
        assertThat(snapshot()).isEqualTo(before);

        var succeeded = service.confirmCreate(g.adSet, UUID.randomUUID(), g.product, g.asset, g.output, g.review, g.parentVersion, "sql-id-sub");
        String afterCreate = snapshot();
        assertNamed("23514", "ct_platform_ad_dispatch_result", () -> tx(() ->
                jdbc.update("UPDATE platform_ads SET desired_state='ACTIVE',version=version+1,updated_at=statement_timestamp() WHERE platform_ad_uuid=?", succeeded.operation().entityUuid()), true));
        assertThat(snapshot()).isEqualTo(afterCreate);
    }

    @Test void correlatedResumeMismatchAndFingerprintEdgeRollBackDispatch() {
        Graph g = graph();
        var created = service.confirmCreate(g.adSet, UUID.randomUUID(), g.product, g.asset, g.output, g.review, g.parentVersion, "sql-resume-mismatch");
        UUID ad = created.operation().entityUuid();
        long campaignVersion = jdbc.queryForObject("SELECT version FROM platform_campaigns WHERE platform_campaign_uuid=?", Long.class, g.campaign);
        var campaignResume = stage4b.confirmState(com.aicommerce.platform.delivery.domain.PlatformEntityType.CAMPAIGN, g.campaign, UUID.randomUUID(), com.aicommerce.platform.delivery.domain.PlatformDesiredState.ACTIVE, campaignVersion, "sql-campaign-resume");
        operations.submit(campaignResume.operation().getOperationUuid(), campaignResume.operation().getVersion());
        long adSetVersion = jdbc.queryForObject("SELECT version FROM platform_ad_sets WHERE platform_ad_set_uuid=?", Long.class, g.adSet);
        var adSetResume = stage4b.confirmState(com.aicommerce.platform.delivery.domain.PlatformEntityType.AD_SET, g.adSet, UUID.randomUUID(), com.aicommerce.platform.delivery.domain.PlatformDesiredState.ACTIVE, adSetVersion, "sql-adset-resume");
        operations.submit(adSetResume.operation().getOperationUuid(), adSetResume.operation().getVersion());
        long version = jdbc.queryForObject("SELECT version FROM platform_ads WHERE platform_ad_uuid=?", Long.class, ad);
        var resume = tx.confirmState(ad, UUID.randomUUID(), com.aicommerce.platform.delivery.domain.PlatformDesiredState.ACTIVE, version, "sql-resume");
        UUID resumeOp = resume.operation().getOperationUuid();
        claim(resumeOp);
        String before = snapshot();
        assertNamed("23514", "ct_platform_ad_dispatch_result", () -> tx(() -> {
            String evidenceJson = "{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"SUCCEEDED\"}";
            jdbc.update("UPDATE platform_operation_attempts SET status='SUCCEEDED',evidence=?::jsonb,completed_at=statement_timestamp(),version=1 WHERE operation_uuid=? AND attempt_kind='SUBMIT'", evidenceJson, resumeOp);
            jdbc.update("UPDATE platform_operations SET status='SUCCEEDED',outcome_evidence=?::jsonb,claimed_at=null,completed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 WHERE operation_uuid=?", evidenceJson, resumeOp);
            jdbc.update("UPDATE platform_campaigns SET desired_state='PAUSED',version=version+1,updated_at=statement_timestamp() WHERE platform_campaign_uuid=?", g.campaign);
            jdbc.update("UPDATE platform_ads SET desired_state='ACTIVE',version=version+1,updated_at=statement_timestamp() WHERE platform_ad_uuid=?", ad);
        }, true));
        assertThat(snapshot()).isEqualTo(before);
    }

    private Graph graph() {
        var campaignCreated = stage4b.confirmCampaign(UUID.randomUUID(), plan, 0, "sql-campaign");
        operations.submit(campaignCreated.operation().getOperationUuid(), campaignCreated.operation().getVersion());
        long campaignVersion = jdbc.queryForObject("SELECT version FROM platform_campaigns WHERE platform_campaign_uuid=?", Long.class, campaignCreated.operation().getEntityUuid());
        var adSetCreated = stage4b.confirmAdSet(campaignCreated.operation().getEntityUuid(), UUID.randomUUID(), PlatformBudgetType.DAILY, new java.math.BigDecimal("25"), 0, campaignVersion, "sql-adset");
        operations.submit(adSetCreated.operation().getOperationUuid(), adSetCreated.operation().getVersion());
        UUID campaign = campaignCreated.operation().getEntityUuid();
        UUID adSet = adSetCreated.operation().getEntityUuid();
        Evidence evidence = evidence(adSet);
        long parent = jdbc.queryForObject("SELECT version FROM platform_ad_sets WHERE platform_ad_set_uuid=?", Long.class, adSet);
        return new Graph(campaign, adSet, parent, evidence.product, evidence.asset, evidence.output, evidence.review);
    }

    private UUID insertCreateAd(Graph g, long parentVersion) {
        UUID ad = UUID.randomUUID(), operation = UUID.randomUUID(), request = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key)
                VALUES (?,?, '00000000-0000-4000-8000-00000000005b',?,?,?,?,?,'APPROVED_IMAGE_ASSET_V1')
                """, ad, g.adSet, g.product, g.asset, g.output, g.review, "e".repeat(64));
        String payload = "{\"schemaVersion\":1,\"operationType\":\"CREATE_AD\",\"entityType\":\"AD\",\"entityUuid\":\"" + ad
                + "\",\"platformAdUuid\":\"" + ad + "\",\"platformAdSetUuid\":\"" + g.adSet + "\",\"expectedParentVersion\":" + parentVersion
                + ",\"productUuid\":\"" + g.product + "\",\"assetUuid\":\"" + g.asset + "\",\"generationOutputUuid\":\"" + g.output
                + "\",\"reviewDecisionUuid\":\"" + g.review + "\",\"approvedChecksumSha256\":\"" + "e".repeat(64)
                + "\",\"creativeMappingKey\":\"APPROVED_IMAGE_ASSET_V1\",\"desiredState\":\"PAUSED\"}";
        String sha = jdbc.queryForObject("SELECT encode(sha256(convert_to(stage4c_create_ad_canonical_json(?::jsonb),'UTF8')),'hex')", String.class, payload);
        jdbc.update("""
                INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id)
                VALUES (?, '00000000-0000-4000-8000-00000000005b','CREATE_AD','AD',?,?, ?, ?::jsonb, ?,'LOCAL_ADMIN','local-admin','sql-create')
                """, operation, ad, request, hex(operation), payload, sha);
        return operation;
    }

    private void claim(UUID operation) {
        tx(() -> {
            jdbc.update("UPDATE platform_operations SET status='SUBMITTING',attempt_count=attempt_count+1,claimed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 WHERE operation_uuid=?", operation);
            jdbc.update("INSERT INTO platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) SELECT ?,operation_uuid,'SUBMIT',attempt_count,'STARTED',claimed_at,0 FROM platform_operations WHERE operation_uuid=?", UUID.randomUUID(), operation);
        }, false);
    }

    private Evidence evidence(UUID adSet) {
        UUID product = UUID.randomUUID(), source = UUID.randomUUID(), asset = UUID.randomUUID(), template = UUID.randomUUID(),
                templateVersion = UUID.randomUUID(), batch = UUID.randomUUID(), job = UUID.randomUUID(),
                output = UUID.randomUUID(), review = UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status) VALUES (?,?,'SQL 4C','ACTIVE')",
                product, "PROD-" + String.format("%08d", Math.abs(product.hashCode()) % 100_000_000));
        jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) VALUES (?,?,?)", UUID.randomUUID(), plan, product);
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','SOURCE',?)", source, product, plan, "d".repeat(64));
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','GENERATED',?)", asset, product, plan, "e".repeat(64));
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,?,'IMAGE','sql4c')", template, "sql4c." + template);
        jdbc.update("INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) VALUES (?,?,1,'image','{}'::jsonb,?,'sql')", templateVersion, template, "a".repeat(64));
        jdbc.update("INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) VALUES (?,?,'COMPLETED','TWD',0,0,1,1,'sql')", batch, product);
        jdbc.update("INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) VALUES (?,?,?,?,'IMAGE','stub','stub','SUCCEEDED','image','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)", job, batch, product, templateVersion);
        jdbc.update("""
                INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,model_label,input_units,output_units,actual_cost,currency,safety_findings,provider_metadata,source_asset_uuid,generated_asset_uuid,generation_mode,workflow_key,workflow_version,image_width,image_height,media_type,size_bytes,source_checksum_sha256,output_checksum_sha256,protected_pixels_sha256,preservation_algorithm,preservation_status,preservation_details)
                VALUES (?,?,?,?,'IMAGE','stub',0,0,0,'TWD','[]'::jsonb,'{}'::jsonb,?,?,'BACKGROUND_COMPOSITE','sql-v1','1',1,1,'image/png',1,?,?,?,'RGBA_MASK_EXACT_V1','PASSED','{"changedPixelCount":0,"protectedPixelCount":1}'::jsonb)
                """, output, job, batch, product, source, asset, "d".repeat(64), "e".repeat(64), "f".repeat(64));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) VALUES (?,?,'APPROVED','LOCAL_ADMIN','sql','sql-review',0,current_timestamp)", review, output);
            jdbc.update("UPDATE ai_generation_outputs SET review_status='APPROVED',version=1 WHERE generation_output_uuid=?", output);
        });
        return new Evidence(product, asset, output, review);
    }

    private void tx(Runnable work, boolean forceDeferred) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            work.run();
            if (forceDeferred) jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        });
    }

    private String snapshot() {
        return jdbc.queryForObject("""
          SELECT jsonb_build_object(
            'ads',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_ad_uuid) FROM platform_ads t),
            'operations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_uuid) FROM platform_operations t),
            'attempts',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_attempt_uuid) FROM platform_operation_attempts t))::text
          """, String.class);
    }

    private static void assertNamed(String state, String name, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).satisfies(failure -> {
            Throwable current = failure;
            while (current.getCause() != null) current = current.getCause();
            assertThat(current).isInstanceOf(SQLException.class);
            assertThat(((SQLException) current).getSQLState()).isEqualTo(state);
            assertThat(current.getMessage()).contains(name);
        });
    }

    private static String hex(UUID id) { return id.toString().replace("-", "").repeat(2); }
    private record Graph(UUID campaign, UUID adSet, long parentVersion, UUID product, UUID asset, UUID output, UUID review) {}
    private record Evidence(UUID product, UUID asset, UUID output, UUID review) {}
}
