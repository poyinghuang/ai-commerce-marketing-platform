package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

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
class Milestone3DReviewSchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void directSqlAcceptsOneCoherentApprovalAndRejectsMutationOrDelete() {
        Fixture fixture = fixture();
        UUID decisionUuid = UUID.randomUUID();
        transaction(() -> {
            insertDecision(decisionUuid, fixture.outputUuid(), "APPROVED", null, "LOCAL_ADMIN", "review-approve", 0);
            jdbc.update("UPDATE ai_generation_outputs SET review_status='APPROVED', version=1 WHERE generation_output_uuid=?",
                    fixture.outputUuid());
        });
        assertThat(jdbc.queryForObject("SELECT review_status FROM ai_generation_outputs WHERE generation_output_uuid=?",
                String.class, fixture.outputUuid())).isEqualTo("APPROVED");
        assertRejected(() -> jdbc.update("UPDATE ai_review_decisions SET reviewer_id='other' WHERE review_decision_uuid=?",
                decisionUuid));
        assertRejected(() -> jdbc.update("DELETE FROM ai_review_decisions WHERE review_decision_uuid=?", decisionUuid));
        assertRejected(() -> jdbc.update("UPDATE ai_generation_outputs SET review_status='REJECTED', version=2 WHERE generation_output_uuid=?",
                fixture.outputUuid()));
        assertRejected(() -> jdbc.update("DELETE FROM ai_generation_outputs WHERE generation_output_uuid=?",
                fixture.outputUuid()));
        Fixture versionOnly = fixture();
        assertRejected(() -> jdbc.update("UPDATE ai_generation_outputs SET version=1 WHERE generation_output_uuid=?",
                versionOnly.outputUuid()));
    }

    @Test
    void deferredCoherenceRejectsOutputOnlyDecisionOnlyAndDuplicateDecisions() {
        Fixture outputOnly = fixture();
        assertRejected(() -> jdbc.update("UPDATE ai_generation_outputs SET review_status='APPROVED', version=1 WHERE generation_output_uuid=?",
                outputOnly.outputUuid()));

        Fixture decisionOnly = fixture();
        assertRejected(() -> insertDecision(UUID.randomUUID(), decisionOnly.outputUuid(), "REJECTED", "No",
                "LOCAL_ADMIN", "review-decision-only", 0));

        Fixture duplicate = fixture();
        transaction(() -> {
            insertDecision(UUID.randomUUID(), duplicate.outputUuid(), "REJECTED", "Not suitable",
                    "TRUSTED_ACTOR", "review-reject", 0);
            jdbc.update("UPDATE ai_generation_outputs SET review_status='REJECTED', version=1 WHERE generation_output_uuid=?",
                    duplicate.outputUuid());
        });
        assertRejected(() -> insertDecision(UUID.randomUUID(), duplicate.outputUuid(), "REJECTED", "Again",
                "LOCAL_ADMIN", "review-duplicate", 1));
    }

    @Test
    void directSqlRejectsInvalidDecisionReasonActorRequestAndReviewedVersion() {
        Fixture fixture = fixture();
        assertRejected(() -> insertDecision(UUID.randomUUID(), fixture.outputUuid(), "INVALID", null,
                "LOCAL_ADMIN", "review-invalid", 0));
        assertRejected(() -> insertDecision(UUID.randomUUID(), fixture.outputUuid(), "APPROVED", "not allowed",
                "LOCAL_ADMIN", "review-invalid", 0));
        assertRejected(() -> insertDecision(UUID.randomUUID(), fixture.outputUuid(), "REJECTED", " ",
                "LOCAL_ADMIN", "review-invalid", 0));
        assertRejected(() -> insertDecision(UUID.randomUUID(), fixture.outputUuid(), "REJECTED", "No",
                "SYSTEM", "review-invalid", 0));
        assertRejected(() -> insertDecision(UUID.randomUUID(), fixture.outputUuid(), "REJECTED", "No",
                "LOCAL_ADMIN", "bad request", 0));
        assertRejected(() -> insertDecision(UUID.randomUUID(), fixture.outputUuid(), "REJECTED", "No",
                "LOCAL_ADMIN", "review-invalid", -1));
    }

    private Fixture fixture() {
        UUID product = UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status,version) VALUES (?,?,?,'ACTIVE',0)",
                product, "PROD-" + String.format("%08d", Math.abs(product.hashCode()) % 100000000), "Review Product");
        UUID template = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,?,'TEXT','Review')",
                template, "review." + template.toString().substring(0, 8));
        UUID templateVersion = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,
                    version_number,template_text,input_schema,content_sha256,created_by)
                VALUES (?,?,1,'Review','{}'::jsonb,?,'tester')
                """, templateVersion, template, "d".repeat(64));
        UUID batch = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,
                    estimated_cost,reserved_cost,requested_job_count,created_by)
                VALUES (?,?,'COMPLETED','USD',0.1,0.1,1,'tester')
                """, batch, product);
        UUID job = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,
                    prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,
                    input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,
                    submitted_at,started_at,completed_at)
                VALUES (?,?,?,?,'TEXT','stub','stub-text','SUCCEEDED','Review','{}'::jsonb,0.1,0.1,0.1,'USD',
                    CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, job, batch, product, templateVersion);
        UUID output = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,
                    product_uuid,generation_type,text_content,model_label,input_units,output_units,actual_cost,currency)
                VALUES (?,?,?,?,'TEXT','Generated','stub-text',1,1,0.1,'USD')
                """, output, job, batch, product);
        return new Fixture(output);
    }

    private void insertDecision(UUID id, UUID output, String decision, String reason,
            String actorType, String requestId, long version) {
        jdbc.update("""
                INSERT INTO ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reason,
                    reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at)
                VALUES (?,?,?,?,?,'reviewer',?,?,CURRENT_TIMESTAMP)
                """, id, output, decision, reason, actorType, requestId, version);
    }

    private void transaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private void assertRejected(Runnable work) {
        assertThatThrownBy(() -> transaction(work)).isInstanceOf(RuntimeException.class);
    }

    private record Fixture(UUID outputUuid) {
    }
}
