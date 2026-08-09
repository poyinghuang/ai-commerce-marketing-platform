package com.aicommerce.platform.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AiGenerationFoundationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @DynamicPropertySource
    static void budget(DynamicPropertyRegistry registry) {
        registry.add("AI_BUDGET_CURRENCY", () -> "USD");
        registry.add("AI_MAX_JOB_COST", () -> "6.000000");
        registry.add("AI_MAX_BATCH_COST", () -> "10.000000");
        registry.add("AI_MAX_DAILY_COST", () -> "10.000000");
    }

    @Autowired AiPromptTemplateService templateService;
    @Autowired AiGenerationFoundationService generationService;
    @Autowired AiBudgetLedgerService budgetLedgerService;
    @Autowired AuditOperationContextFactory contextFactory;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    UUID productUuid;
    UUID templateVersionUuid;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM quality_score_blockers");
        jdbc.update("DELETE FROM workflow_status");
        jdbc.update("DELETE FROM quality_scores");
        // AI and audit tables are database-protected; every test gets its own unique Product and template.
        productUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products(product_uuid, product_id, product_name, lifecycle_status, version)
                VALUES (?, ?, 'AI Test Product', 'ACTIVE', 0)
                """, productUuid, productId(productUuid));
        AuditOperationContext context = contextFactory.forSystem("ai-foundation-test");
        var template = templateService.createTemplate(
                new CreatePromptTemplateCommand("copy." + productUuid.toString().substring(0, 8),
                        GenerationType.TEXT, "Copy Template"), context);
        templateVersionUuid = templateService.appendVersion(template.getPromptTemplateUuid(),
                new AppendPromptTemplateVersionCommand("Write {{productName}}", null,
                        "{\"productName\":{\"type\":\"string\"}}"), context)
                .getPromptTemplateVersionUuid();
    }

    @AfterEach
    void releaseActiveTestReservations() {
        jdbc.update("""
                INSERT INTO ai_budget_ledger
                    (budget_ledger_uuid, generation_job_uuid, budget_date, entry_type, amount, currency, entry_order)
                SELECT gen_random_uuid(), reserve.generation_job_uuid, reserve.budget_date,
                       'RELEASE', reserve.amount, reserve.currency, 1
                  FROM ai_budget_ledger reserve
                 WHERE reserve.entry_type='RESERVE'
                   AND NOT EXISTS (
                       SELECT 1 FROM ai_budget_ledger settled
                        WHERE settled.generation_job_uuid=reserve.generation_job_uuid
                          AND settled.entry_type IN ('COMMIT','RELEASE')
                   )
                """);
    }

    @Test
    void acceptedBatchPersistsJobsReservationsAndAuditInOneOperation() {
        AuditOperationContext context = contextFactory.forSystem("ai-generation-test");
        GenerationFoundationResult result = generationService.create(command("2.000000"), context);

        assertThat(result.budgetAccepted()).isTrue();
        assertThat(result.jobs()).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_budget_ledger WHERE generation_job_uuid=? AND entry_type='RESERVE'",
                Integer.class, result.jobs().getFirst().getGenerationJobUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE operation_uuid=? AND entity_type LIKE 'AI_%'",
                Integer.class, context.operationUuid())).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT request_id) FROM audit_logs WHERE operation_uuid=?",
                Integer.class, context.operationUuid())).isEqualTo(1);
        assertThat(context.requestId()).isNotBlank();
    }

    @Test
    void rejectedBudgetPersistsRejectedStateAndAuditWithoutReservation() {
        AuditOperationContext context = contextFactory.forSystem("ai-generation-test");
        GenerationFoundationResult result = generationService.create(command("7.000000"), context);

        assertThat(result.budgetAccepted()).isFalse();
        assertThat(result.budgetRejectionCode()).isEqualTo("AI_BUDGET_EXCEEDED");
        assertThat(result.batch().getStatus().name()).isEqualTo("BUDGET_REJECTED");
        assertThat(result.jobs().getFirst().getStatus().name()).isEqualTo("BUDGET_REJECTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_budget_ledger WHERE generation_job_uuid=?",
                Integer.class, result.jobs().getFirst().getGenerationJobUuid())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE operation_uuid=? AND action='UPDATE'",
                Integer.class, context.operationUuid())).isEqualTo(2);
    }

    @Test
    void outerTransactionRollbackRemovesBatchJobsLedgerAndAuditTogether() {
        AuditOperationContext context = contextFactory.forSystem("ai-rollback-test");
        UUID[] ids = new UUID[2];
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            GenerationFoundationResult result = generationService.create(command("1.000000"), context);
            ids[0] = result.batch().getGenerationBatchUuid();
            ids[1] = result.jobs().getFirst().getGenerationJobUuid();
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_batches WHERE generation_batch_uuid=?",
                Integer.class, ids[0])).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_jobs WHERE generation_job_uuid=?",
                Integer.class, ids[1])).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_budget_ledger WHERE generation_job_uuid=?",
                Integer.class, ids[1])).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE operation_uuid=?",
                Integer.class, context.operationUuid())).isZero();
    }

    @Test
    void settlementCommitsActualReleasesUnusedAndFlagsProviderCostInvariantViolation() {
        var normal = generationService.create(command("2.000000"),
                contextFactory.forSystem("ai-settlement-create"));
        UUID normalJob = normal.jobs().getFirst().getGenerationJobUuid();
        AuditOperationContext normalContext = contextFactory.forSystem("ai-settlement-test");
        BudgetSettlementResult normalSettlement = transactions.execute(status -> budgetLedgerService.settle(
                normalJob, new BigDecimal("0.600000"), productUuid, normalContext));
        assertThat(normalSettlement.invariantViolation()).isFalse();
        assertThat(normalSettlement.releasedCost()).isEqualByComparingTo("1.400000");
        assertThat(jdbc.queryForList(
                "SELECT entry_type FROM ai_budget_ledger WHERE generation_job_uuid=? ORDER BY entry_order",
                String.class, normalJob)).containsExactly("RESERVE", "COMMIT", "RELEASE");
        assertThat(jdbc.queryForObject(
                "SELECT actual_cost FROM ai_generation_jobs WHERE generation_job_uuid=?",
                BigDecimal.class, normalJob)).isEqualByComparingTo("0.600000");
        assertThat(jdbc.queryForObject(
                "SELECT actual_cost FROM ai_generation_batches WHERE generation_batch_uuid=?",
                BigDecimal.class, normal.batch().getGenerationBatchUuid())).isEqualByComparingTo("0.600000");

        var anomalous = generationService.create(command("2.000000"),
                contextFactory.forSystem("ai-invariant-create"));
        UUID anomalousJob = anomalous.jobs().getFirst().getGenerationJobUuid();
        BudgetSettlementResult anomaly = transactions.execute(status -> budgetLedgerService.settle(
                anomalousJob, new BigDecimal("3.000000"), productUuid,
                contextFactory.forSystem("ai-invariant-test")));
        assertThat(anomaly.invariantViolation()).isTrue();
        assertThat(anomaly.releasedCost()).isEqualByComparingTo("0.000000");
        assertThat(jdbc.queryForList(
                "SELECT entry_type FROM ai_budget_ledger WHERE generation_job_uuid=? ORDER BY entry_order",
                String.class, anomalousJob)).containsExactly("RESERVE", "COMMIT");
        assertThat(jdbc.queryForObject(
                "SELECT actual_cost FROM ai_generation_jobs WHERE generation_job_uuid=?",
                BigDecimal.class, anomalousJob)).isEqualByComparingTo("3.000000");
    }

    @Test
    void concurrentReservationsCannotExceedDailyLimit() throws Exception {
        Callable<GenerationFoundationResult> create = () -> generationService.create(command("6.000000"),
                contextFactory.forSystem("ai-concurrency-test"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(create);
            var second = executor.submit(create);
            List<GenerationFoundationResult> results = List.of(first.get(), second.get());
            assertThat(results).extracting(GenerationFoundationResult::budgetAccepted)
                    .containsExactlyInAnyOrder(true, false);
        }
        BigDecimal activeReservations = jdbc.queryForObject("""
                WITH per_job AS (
                    SELECT generation_job_uuid,
                           MAX(amount) FILTER (WHERE entry_type='RESERVE') AS reserved,
                           MAX(amount) FILTER (WHERE entry_type='COMMIT') AS committed,
                           COALESCE(MAX(amount) FILTER (WHERE entry_type='RELEASE'), 0) AS released
                      FROM ai_budget_ledger
                     WHERE budget_date=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND currency='USD'
                     GROUP BY generation_job_uuid
                )
                SELECT COALESCE(SUM(
                    CASE WHEN committed IS NOT NULL THEN committed ELSE reserved-released END
                ), 0) FROM per_job
                """, BigDecimal.class);
        assertThat(activeReservations).isLessThanOrEqualTo(new BigDecimal("10.000000"));
    }

    @Test
    void promptSchemaRejectsNestedSecretKeysAndVersionsAreAppendOnly() {
        AuditOperationContext context = contextFactory.forSystem("ai-template-test");
        var template = templateService.createTemplate(
                new CreatePromptTemplateCommand("safe." + UUID.randomUUID().toString().substring(0, 8),
                        GenerationType.TEXT, "Safe"), context);
        assertThatThrownBy(() -> templateService.appendVersion(template.getPromptTemplateUuid(),
                new AppendPromptTemplateVersionCommand("Prompt", null,
                        "{\"properties\":{\"api_token\":{\"type\":\"string\"}}}"), context))
                .isInstanceOf(AiFoundationValidationException.class);

        var first = templateService.appendVersion(template.getPromptTemplateUuid(),
                new AppendPromptTemplateVersionCommand("Prompt one", null, "{}"), context);
        var second = templateService.appendVersion(template.getPromptTemplateUuid(),
                new AppendPromptTemplateVersionCommand("Prompt two", null, "{}"), context);
        assertThat(first.getVersionNumber()).isEqualTo(1);
        assertThat(second.getVersionNumber()).isEqualTo(2);
        assertThat(first.getContentSha256()).hasSize(64).isNotEqualTo(second.getContentSha256());
    }

    private CreateGenerationFoundationCommand command(String worstCase) {
        return new CreateGenerationFoundationCommand(productUuid, null, "USD", List.of(
                new GenerationJobFoundationRequest(templateVersionUuid, GenerationType.TEXT,
                        "stub", "stub-text", "Write a product caption", null,
                        "{\"productName\":\"AI Test Product\"}", new BigDecimal("0.500000"),
                        new BigDecimal(worstCase))));
    }

    private String productId(UUID uuid) {
        return "PROD-" + String.format("%08d", Math.abs(uuid.hashCode()) % 100000000);
    }
}
