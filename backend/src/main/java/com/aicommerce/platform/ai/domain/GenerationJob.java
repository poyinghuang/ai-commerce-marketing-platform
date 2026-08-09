package com.aicommerce.platform.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_generation_jobs")
public class GenerationJob extends MutableEntity {

    @Id @Column(name = "generation_job_uuid", nullable = false, updatable = false)
    private UUID generationJobUuid;
    @Column(name = "generation_batch_uuid", nullable = false, updatable = false)
    private UUID generationBatchUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Column(name = "creative_plan_uuid", updatable = false)
    private UUID creativePlanUuid;
    @Column(name = "prompt_template_version_uuid", nullable = false, updatable = false)
    private UUID promptTemplateVersionUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type", nullable = false, updatable = false, length = 16)
    private GenerationType generationType;
    @Column(name = "provider_key", nullable = false, updatable = false, length = 64)
    private String providerKey;
    @Column(name = "model_key", nullable = false, updatable = false, length = 128)
    private String modelKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private GenerationJobStatus status;
    @Column(name = "rendered_prompt", nullable = false, updatable = false, length = 16000)
    private String renderedPrompt;
    @Column(name = "negative_prompt", updatable = false, length = 8000)
    private String negativePrompt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", updatable = false, columnDefinition = "jsonb")
    private String inputSnapshot;
    @Column(name = "provider_job_id", length = 256)
    private String providerJobId;
    @Column(name = "estimated_cost", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal estimatedCost;
    @Column(name = "reserved_cost", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal reservedCost;
    @Column(name = "actual_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal actualCost;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "failure_message", length = 1000)
    private String failureMessage;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected GenerationJob() {
    }

    private GenerationJob(UUID id, UUID batchId, UUID productUuid, UUID creativePlanUuid,
            UUID templateVersionUuid, GenerationType generationType, String providerKey, String modelKey,
            String renderedPrompt, String negativePrompt, String inputSnapshot, BigDecimal estimatedCost,
            BigDecimal reservedCost, String currency) {
        this.generationJobUuid = Objects.requireNonNull(id, "generationJobUuid is required");
        this.generationBatchUuid = Objects.requireNonNull(batchId, "generationBatchUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.creativePlanUuid = creativePlanUuid;
        this.promptTemplateVersionUuid = Objects.requireNonNull(templateVersionUuid,
                "promptTemplateVersionUuid is required");
        this.generationType = Objects.requireNonNull(generationType, "generationType is required");
        this.providerKey = AiDomainRules.required(providerKey, "providerKey", 64);
        this.modelKey = AiDomainRules.required(modelKey, "modelKey", 128);
        this.renderedPrompt = AiDomainRules.required(renderedPrompt, "renderedPrompt", 16000);
        this.negativePrompt = AiDomainRules.optional(negativePrompt, "negativePrompt", 8000);
        this.inputSnapshot = AiDomainRules.optional(inputSnapshot, "inputSnapshot", 32768);
        this.estimatedCost = AiDomainRules.money(estimatedCost, "estimatedCost", false);
        this.reservedCost = AiDomainRules.money(reservedCost, "reservedCost", false);
        if (this.estimatedCost.compareTo(this.reservedCost) > 0) {
            throw new IllegalArgumentException("estimatedCost cannot exceed reservedCost");
        }
        this.currency = AiDomainRules.currency(currency);
        this.status = GenerationJobStatus.CREATED;
        this.actualCost = BigDecimal.ZERO.setScale(6);
    }

    public static GenerationJob create(UUID id, UUID batchId, UUID productUuid, UUID creativePlanUuid,
            UUID templateVersionUuid, GenerationType generationType, String providerKey, String modelKey,
            String renderedPrompt, String negativePrompt, String inputSnapshot, BigDecimal estimatedCost,
            BigDecimal reservedCost, String currency) {
        return new GenerationJob(id, batchId, productUuid, creativePlanUuid, templateVersionUuid,
                generationType, providerKey, modelKey, renderedPrompt, negativePrompt, inputSnapshot,
                estimatedCost, reservedCost, currency);
    }

    public boolean rejectBudget(Instant rejectedAt) {
        if (status == GenerationJobStatus.BUDGET_REJECTED) return false;
        if (status != GenerationJobStatus.CREATED) {
            throw new IllegalStateException("Only a created job can be budget rejected");
        }
        status = GenerationJobStatus.BUDGET_REJECTED;
        completedAt = Objects.requireNonNull(rejectedAt, "rejectedAt is required");
        failureCode = "AI_BUDGET_EXCEEDED";
        failureMessage = "Generation was rejected before provider submission";
        return true;
    }

    public boolean recordActualCost(BigDecimal amount) {
        BigDecimal normalized = AiDomainRules.money(amount, "actualCost", false);
        if (normalized.equals(actualCost)) return false;
        this.actualCost = normalized;
        return true;
    }

    public UUID getGenerationJobUuid() { return generationJobUuid; }
    public UUID getGenerationBatchUuid() { return generationBatchUuid; }
    public UUID getProductUuid() { return productUuid; }
    public UUID getCreativePlanUuid() { return creativePlanUuid; }
    public UUID getPromptTemplateVersionUuid() { return promptTemplateVersionUuid; }
    public GenerationType getGenerationType() { return generationType; }
    public String getProviderKey() { return providerKey; }
    public String getModelKey() { return modelKey; }
    public GenerationJobStatus getStatus() { return status; }
    public String getRenderedPrompt() { return renderedPrompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public String getInputSnapshot() { return inputSnapshot; }
    public String getProviderJobId() { return providerJobId; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public BigDecimal getReservedCost() { return reservedCost; }
    public BigDecimal getActualCost() { return actualCost; }
    public String getCurrency() { return currency; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
