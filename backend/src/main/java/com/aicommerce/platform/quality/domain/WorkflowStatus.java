package com.aicommerce.platform.quality.domain;

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

@Entity
@Table(name = "workflow_status")
public class WorkflowStatus extends MutableEntity {
    @Id
    @Column(name = "workflow_status_uuid", nullable = false, updatable = false)
    private UUID workflowStatusUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32, updatable = false)
    private WorkflowStage stage;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReadinessStatus status;
    @Column(name = "status_reason", nullable = false, length = 512)
    private String statusReason;
    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected WorkflowStatus() {}

    public static WorkflowStatus create(UUID uuid, UUID productUuid, ReadinessStatus status,
            String reason, Instant evaluatedAt) {
        var workflow = new WorkflowStatus();
        workflow.workflowStatusUuid = Objects.requireNonNull(uuid, "uuid is required");
        workflow.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        workflow.stage = WorkflowStage.PRODUCT_READINESS;
        workflow.apply(status, reason, evaluatedAt);
        return workflow;
    }

    public boolean apply(ReadinessStatus status, String reason, Instant evaluatedAt) {
        Objects.requireNonNull(status, "status is required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        String normalized = reason.trim();
        if (normalized.length() > 512) throw new IllegalArgumentException("reason exceeds 512 characters");
        if (this.status == status && Objects.equals(this.statusReason, normalized)) return false;
        this.status = status;
        this.statusReason = normalized;
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");
        return true;
    }

    public UUID getWorkflowStatusUuid() { return workflowStatusUuid; }
    public UUID getProductUuid() { return productUuid; }
    public WorkflowStage getStage() { return stage; }
    public ReadinessStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
