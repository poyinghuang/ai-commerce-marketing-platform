package com.aicommerce.platform.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationJobStatus;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class TextGenerationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @DynamicPropertySource
    static void budget(DynamicPropertyRegistry registry) {
        registry.add("AI_BUDGET_CURRENCY", () -> "USD");
        registry.add("AI_MAX_JOB_COST", () -> "10.000000");
        registry.add("AI_MAX_BATCH_COST", () -> "10.000000");
        registry.add("AI_MAX_DAILY_COST", () -> "1000.000000");
    }

    @Autowired TextGenerationService service;
    @Autowired AiPromptTemplateService templateService;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired JdbcTemplate jdbc;

    UUID productUuid;
    UUID planUuid;
    String templateKey;

    @BeforeEach
    void setUp() {
        productUuid = UUID.randomUUID();
        planUuid = UUID.randomUUID();
        templateKey = "text.integration." + productUuid.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO products(product_uuid, product_id, sku, product_name, brand, lifecycle_status, version)
                VALUES (?, ?, 'SKU-TEXT', 'Text Product', 'Stage 03', 'ACTIVE', 0)
                """, productUuid, productId(productUuid));
        jdbc.update("""
                INSERT INTO creative_plans(creative_plan_uuid, product_uuid, plan_name, primary_audience)
                VALUES (?, ?, 'Launch Copy', 'Responsible shoppers')
                """, planUuid, productUuid);
        jdbc.update("""
                INSERT INTO product_knowledge(knowledge_uuid, product_uuid, knowledge_type, title, content)
                VALUES (?, ?, 'FEATURE', 'Material', 'Recycled material')
                """, UUID.randomUUID(), productUuid);
        var context = contexts.forSystem("text-generation-test");
        var template = templateService.createTemplate(
                new CreatePromptTemplateCommand(templateKey, GenerationType.TEXT, "Integration copy"), context);
        templateService.appendVersion(template.getPromptTemplateUuid(), new AppendPromptTemplateVersionCommand(
                "Write concise product copy.", null,
                "{\"type\":\"object\",\"properties\":{\"product\":{},\"knowledge\":{},\"creativePlan\":{},\"variationIndex\":{}}}"),
                context);
    }

    @Test
    void defaultBatchCreatesThreeServerRenderedJobsAndExecutesOneOutput() {
        GenerationFoundationResult created = service.createBatch(command(0), "request-text-1");

        assertThat(created.budgetAccepted()).isTrue();
        assertThat(created.jobs()).hasSize(3);
        assertThat(created.jobs()).allMatch(job -> job.getRenderedPrompt().contains("<untrusted-product-context>"));
        assertThat(created.jobs()).allMatch(job -> !job.getRenderedPrompt().contains("authorization"));

        var first = created.jobs().getFirst();
        var output = service.execute(first.getGenerationJobUuid(), first.getVersion(), "request-text-2");

        assertThat(output.getTextContent()).isEqualTo("stub-text-" + first.getGenerationJobUuid());
        assertThat(service.getJob(first.getGenerationJobUuid()).getStatus()).isEqualTo(GenerationJobStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_generation_outputs WHERE generation_job_uuid=?",
                Integer.class, first.getGenerationJobUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='AI_GENERATION_OUTPUT' AND entity_uuid=?",
                Integer.class, output.getGenerationOutputUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT operation_uuid) FROM audit_logs WHERE request_id='request-text-2'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void staleVersionAndDuplicateExecutionAreRejectedWithoutDuplicateOutput() {
        var job = service.createBatch(command(1), "request-stale-1").jobs().getFirst();
        assertThatThrownBy(() -> service.execute(job.getGenerationJobUuid(), job.getVersion() + 1, "request-stale-2"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(error -> ((AiGenerationException) error).code())
                .isEqualTo("AI_GENERATION_PRECONDITION_FAILED");
        service.execute(job.getGenerationJobUuid(), job.getVersion(), "request-stale-3");
        assertThatThrownBy(() -> service.execute(job.getGenerationJobUuid(),
                service.getJob(job.getGenerationJobUuid()).getVersion(), "request-stale-4"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(error -> ((AiGenerationException) error).code())
                .isEqualTo("AI_GENERATION_STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_outputs WHERE generation_job_uuid=?",
                Integer.class, job.getGenerationJobUuid())).isEqualTo(1);
    }

    @Test
    void archivedProductCannotCreateBatch() {
        jdbc.update("UPDATE products SET lifecycle_status='ARCHIVED', archived_at=CURRENT_TIMESTAMP WHERE product_uuid=?",
                productUuid);
        assertThatThrownBy(() -> service.createBatch(command(1), "request-archived"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(error -> ((AiGenerationException) error).code())
                .isEqualTo("PRODUCT_ARCHIVED");
    }

    @Test
    void deterministicPartialFailureDoesNotRollbackSuccessfulSiblings() {
        var created = service.createBatch(new CreateTextGenerationBatchCommand(
                productUuid, planUuid, templateKey, "PARTIAL_FAILURE_FIXTURE", 3), "request-partial-1");
        service.execute(created.jobs().get(0).getGenerationJobUuid(), 0, "request-partial-2");
        assertThatThrownBy(() -> service.execute(created.jobs().get(1).getGenerationJobUuid(), 0,
                "request-partial-3"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(error -> ((AiGenerationException) error).code())
                .isEqualTo("AI_PROVIDER_REJECTED");
        service.execute(created.jobs().get(2).getGenerationJobUuid(), 0, "request-partial-4");

        var batch = service.getBatch(created.batch().getGenerationBatchUuid());
        assertThat(batch.getStatus().name()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(batch.getSucceededJobCount()).isEqualTo(2);
        assertThat(batch.getFailedJobCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_generation_outputs WHERE generation_batch_uuid=?",
                Integer.class, batch.getGenerationBatchUuid())).isEqualTo(2);
    }

    @Test
    void actualCostAboveReservationIsRetainedAndBlocksRemainingAutomaticExecution() {
        var created = service.createBatch(new CreateTextGenerationBatchCommand(
                productUuid, planUuid, templateKey, "COST_INVARIANT_FIXTURE", 2), "request-cost-1");
        var first = created.jobs().get(0);
        var output = service.execute(first.getGenerationJobUuid(), 0, "request-cost-2");
        assertThat(output.getActualCost()).isEqualByComparingTo("3.000000");
        assertThat(service.getJob(first.getGenerationJobUuid()).getFailureCode())
                .isEqualTo("AI_COST_INVARIANT_VIOLATION");

        var second = created.jobs().get(1);
        assertThatThrownBy(() -> service.execute(second.getGenerationJobUuid(), 0, "request-cost-3"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(error -> ((AiGenerationException) error).code())
                .isEqualTo("AI_COST_INVARIANT_VIOLATION");
        assertThat(service.getJob(second.getGenerationJobUuid()).getStatus())
                .isEqualTo(GenerationJobStatus.CREATED);
    }

    private CreateTextGenerationBatchCommand command(int count) {
        return new CreateTextGenerationBatchCommand(productUuid, planUuid, templateKey, "STANDARD", count);
    }

    private String productId(UUID uuid) {
        return "PROD-" + String.format("%08d", Math.abs(uuid.hashCode()) % 100000000);
    }
}
