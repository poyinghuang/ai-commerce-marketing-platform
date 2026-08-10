package com.aicommerce.platform.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.aicommerce.platform.ai.domain.GenerationOutputReviewStatus;
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
class ReviewDecisionIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired ReviewDecisionService reviews;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void approvesAndRejectsWithOneDecisionOutputVersionAndAuditTransaction() {
        Fixture approved = fixture("[]", null);
        var approval = reviews.approve(approved.outputUuid(), 0, "review-approve");
        assertThat(approval.output().getReviewStatus()).isEqualTo(GenerationOutputReviewStatus.APPROVED);
        assertThat(approval.output().getVersion()).isEqualTo(1);
        assertThat(approval.decision().getReviewerId()).isEqualTo("local-admin");
        assertThat(approval.blockers()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid=?",
                Integer.class, approved.outputUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id='review-approve'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT operation_uuid) FROM audit_logs WHERE request_id='review-approve'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT change.field_name
                  FROM audit_log_changes change
                  JOIN audit_logs log ON log.audit_uuid=change.audit_uuid
                 WHERE log.request_id='review-approve' AND log.entity_type='AI_REVIEW_DECISION'
                 ORDER BY change.change_order
                """, String.class)).containsExactly(
                        "generationOutputUuid", "decision", "reviewerType", "reviewerId");
        assertThat(jdbc.queryForList("""
                SELECT change.field_name || ':' || COALESCE(change.old_value, '<null>') || '->' || change.new_value
                  FROM audit_log_changes change
                  JOIN audit_logs log ON log.audit_uuid=change.audit_uuid
                 WHERE log.request_id='review-approve' AND log.entity_type='AI_GENERATION_OUTPUT'
                 ORDER BY change.change_order
                """, String.class)).containsExactly("reviewStatus:PENDING_REVIEW->APPROVED");

        Fixture rejected = fixture("[]", null);
        var rejection = reviews.reject(rejected.outputUuid(), 0, "Not suitable for this campaign", "review-reject");
        assertThat(rejection.output().getReviewStatus()).isEqualTo(GenerationOutputReviewStatus.REJECTED);
        assertThat(rejection.decision().getReason()).isEqualTo("Not suitable for this campaign");
        assertThat(jdbc.queryForObject("SELECT reason FROM ai_review_decisions WHERE generation_output_uuid=?",
                String.class, rejected.outputUuid())).isEqualTo("Not suitable for this campaign");
        assertThat(jdbc.queryForObject("""
                SELECT change.new_value
                  FROM audit_log_changes change
                  JOIN audit_logs log ON log.audit_uuid=change.audit_uuid
                 WHERE log.request_id='review-reject' AND log.entity_type='AI_REVIEW_DECISION'
                   AND change.field_name='reason'
                """, String.class)).isEqualTo("Not suitable for this campaign");
    }

    @Test
    void staleAndRepeatedDecisionsDoNotWriteAuditOrAnotherDecision() {
        Fixture fixture = fixture("[]", null);
        assertThatThrownBy(() -> reviews.approve(fixture.outputUuid(), 2, "review-stale"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(value -> ((AiGenerationException) value).code())
                .isEqualTo("AI_GENERATION_PRECONDITION_FAILED");
        reviews.approve(fixture.outputUuid(), 0, "review-first");
        assertThatThrownBy(() -> reviews.reject(fixture.outputUuid(), 1, "Again", "review-repeat"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(value -> ((AiGenerationException) value).code())
                .isEqualTo("AI_OUTPUT_ALREADY_DECIDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid=?",
                Integer.class, fixture.outputUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id IN ('review-stale','review-repeat')",
                Integer.class)).isZero();
    }

    @Test
    void safetyCostAndArchiveBlockApprovalButSafetyOutputCanBeRejected() {
        Fixture unsafe = fixture("[\"unsafe\"]", null);
        assertBlocked(unsafe.outputUuid(), "SAFETY_FINDINGS", "review-unsafe");
        assertThat(reviews.reject(unsafe.outputUuid(), 0, "Safety finding", "review-unsafe-reject")
                .output().getReviewStatus()).isEqualTo(GenerationOutputReviewStatus.REJECTED);

        Fixture cost = fixture("[]", "AI_COST_INVARIANT_VIOLATION");
        assertBlocked(cost.outputUuid(), "AI_COST_INVARIANT_VIOLATION", "review-cost");

        Fixture archived = fixture("[]", null);
        jdbc.update("UPDATE products SET lifecycle_status='ARCHIVED', archived_at=CURRENT_TIMESTAMP WHERE product_uuid=?",
                archived.productUuid());
        assertBlocked(archived.outputUuid(), "PRODUCT_ARCHIVED", "review-archived");
    }

    @Test
    void outerRollbackRemovesDecisionOutputTransitionAndAuditTogether() {
        Fixture fixture = fixture("[]", null);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            reviews.approve(fixture.outputUuid(), 0, "review-rollback");
            throw new IllegalStateException("force outer rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT review_status FROM ai_generation_outputs WHERE generation_output_uuid=?",
                String.class, fixture.outputUuid())).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid=?",
                Integer.class, fixture.outputUuid())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id='review-rollback'",
                Integer.class)).isZero();
    }

    @Test
    void concurrentReviewersProduceExactlyOneDecision() {
        Fixture fixture = fixture("[]", null);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> concurrentApprove(fixture.outputUuid(), "review-race-1", start), executor);
            var second = CompletableFuture.supplyAsync(() -> concurrentApprove(fixture.outputUuid(), "review-race-2", start), executor);
            start.countDown();
            assertThat(List.of(first.join(), second.join()))
                    .containsExactlyInAnyOrder("APPROVED", "AI_GENERATION_PRECONDITION_FAILED");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid=?",
                Integer.class, fixture.outputUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id IN ('review-race-1','review-race-2')",
                Integer.class)).isEqualTo(2);
    }

    private String concurrentApprove(UUID outputUuid, String requestId, CountDownLatch start) {
        try {
            start.await();
            return reviews.approve(outputUuid, 0, requestId).output().getReviewStatus().name();
        } catch (AiGenerationException exception) {
            return exception.code();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void assertBlocked(UUID outputUuid, String code, String requestId) {
        assertThatThrownBy(() -> reviews.approve(outputUuid, 0, requestId))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining(code)
                .extracting(value -> ((AiGenerationException) value).code())
                .isEqualTo("AI_REVIEW_BLOCKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid=?",
                Integer.class, outputUuid)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id=?",
                Integer.class, requestId)).isZero();
    }

    private Fixture fixture(String safetyFindings, String failureCode) {
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
                """, templateVersion, template, "e".repeat(64));
        UUID batch = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,
                    estimated_cost,reserved_cost,actual_cost,requested_job_count,succeeded_job_count,created_by)
                VALUES (?,?,'COMPLETED','USD',0.1,0.1,0.1,1,1,'tester')
                """, batch, product);
        UUID job = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,
                    prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,
                    input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,failure_code,
                    submitted_at,started_at,completed_at)
                VALUES (?,?,?,?,'TEXT','stub','stub-text','SUCCEEDED','Review','{}'::jsonb,
                    0.1,0.1,0.1,'USD',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, job, batch, product, templateVersion, failureCode);
        UUID output = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,
                    product_uuid,generation_type,text_content,model_label,input_units,output_units,actual_cost,currency,
                    safety_findings)
                VALUES (?,?,?,?,'TEXT','Generated','stub-text',1,1,0.1,'USD',?::jsonb)
                """, output, job, batch, product, safetyFindings);
        return new Fixture(product, output);
    }

    private record Fixture(UUID productUuid, UUID outputUuid) {
    }
}
