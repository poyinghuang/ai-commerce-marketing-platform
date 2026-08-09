package com.aicommerce.platform.ai.domain;

import java.math.BigDecimal;
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
@Table(name = "ai_generation_batches")
public class GenerationBatch extends MutableEntity {

    @Id
    @Column(name = "generation_batch_uuid", nullable = false, updatable = false)
    private UUID generationBatchUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Column(name = "creative_plan_uuid", updatable = false)
    private UUID creativePlanUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private GenerationBatchStatus status;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;
    @Column(name = "estimated_cost", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal estimatedCost;
    @Column(name = "reserved_cost", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal reservedCost;
    @Column(name = "actual_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal actualCost;
    @Column(name = "requested_job_count", nullable = false, updatable = false)
    private int requestedJobCount;
    @Column(name = "succeeded_job_count", nullable = false)
    private int succeededJobCount;
    @Column(name = "failed_job_count", nullable = false)
    private int failedJobCount;
    @Column(name = "rejected_job_count", nullable = false)
    private int rejectedJobCount;
    @Column(name = "created_by", nullable = false, updatable = false, length = 128)
    private String createdBy;

    protected GenerationBatch() {
    }

    private GenerationBatch(UUID id, UUID productUuid, UUID creativePlanUuid, String currency,
            BigDecimal estimatedCost, BigDecimal reservedCost, int requestedJobCount, String createdBy) {
        this.generationBatchUuid = Objects.requireNonNull(id, "generationBatchUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.creativePlanUuid = creativePlanUuid;
        this.currency = AiDomainRules.currency(currency);
        this.estimatedCost = AiDomainRules.money(estimatedCost, "estimatedCost", false);
        this.reservedCost = AiDomainRules.money(reservedCost, "reservedCost", false);
        if (this.estimatedCost.compareTo(this.reservedCost) > 0) {
            throw new IllegalArgumentException("estimatedCost cannot exceed reservedCost");
        }
        if (requestedJobCount <= 0) throw new IllegalArgumentException("requestedJobCount must be positive");
        this.requestedJobCount = requestedJobCount;
        this.createdBy = AiDomainRules.required(createdBy, "createdBy", 128);
        this.status = GenerationBatchStatus.CREATED;
        this.actualCost = BigDecimal.ZERO.setScale(6);
    }

    public static GenerationBatch create(UUID id, UUID productUuid, UUID creativePlanUuid, String currency,
            BigDecimal estimatedCost, BigDecimal reservedCost, int requestedJobCount, String createdBy) {
        return new GenerationBatch(id, productUuid, creativePlanUuid, currency, estimatedCost,
                reservedCost, requestedJobCount, createdBy);
    }

    public boolean rejectBudget() {
        if (status == GenerationBatchStatus.BUDGET_REJECTED) return false;
        if (status != GenerationBatchStatus.CREATED) {
            throw new IllegalStateException("Only a created batch can be budget rejected");
        }
        status = GenerationBatchStatus.BUDGET_REJECTED;
        rejectedJobCount = requestedJobCount;
        return true;
    }

    public boolean recordActualCost(BigDecimal amount) {
        BigDecimal normalized = AiDomainRules.money(amount, "actualCost", false);
        if (normalized.equals(actualCost)) return false;
        this.actualCost = normalized;
        return true;
    }

    public void refreshProgress(int succeeded, int failed, int rejected) {
        if (succeeded < 0 || failed < 0 || rejected < 0
                || succeeded + failed + rejected > requestedJobCount) {
            throw new IllegalArgumentException("Invalid batch progress counts");
        }
        succeededJobCount = succeeded;
        failedJobCount = failed;
        rejectedJobCount = rejected;
        int terminal = succeeded + failed + rejected;
        if (terminal == requestedJobCount) {
            status = failed > 0 || rejected > 0
                    ? GenerationBatchStatus.COMPLETED_WITH_ERRORS
                    : GenerationBatchStatus.COMPLETED;
        } else if (terminal > 0 || status == GenerationBatchStatus.CREATED) {
            status = GenerationBatchStatus.RUNNING;
        }
    }

    public UUID getGenerationBatchUuid() { return generationBatchUuid; }
    public UUID getProductUuid() { return productUuid; }
    public UUID getCreativePlanUuid() { return creativePlanUuid; }
    public GenerationBatchStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public BigDecimal getReservedCost() { return reservedCost; }
    public BigDecimal getActualCost() { return actualCost; }
    public int getRequestedJobCount() { return requestedJobCount; }
    public int getSucceededJobCount() { return succeededJobCount; }
    public int getFailedJobCount() { return failedJobCount; }
    public int getRejectedJobCount() { return rejectedJobCount; }
    public String getCreatedBy() { return createdBy; }
}
