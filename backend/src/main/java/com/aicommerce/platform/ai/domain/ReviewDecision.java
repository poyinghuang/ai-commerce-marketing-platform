package com.aicommerce.platform.ai.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditActor;
import com.aicommerce.platform.audit.domain.AuditActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_review_decisions")
public class ReviewDecision {

    @Id
    @Column(name = "review_decision_uuid", nullable = false, updatable = false)
    private UUID reviewDecisionUuid;
    @Column(name = "generation_output_uuid", nullable = false, updatable = false)
    private UUID generationOutputUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false, length = 16)
    private ReviewDecisionType decision;
    @Column(name = "reason", updatable = false, length = 2000)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_type", nullable = false, updatable = false, length = 32)
    private AuditActorType reviewerType;
    @Column(name = "reviewer_id", nullable = false, updatable = false, length = 128)
    private String reviewerId;
    @Column(name = "request_id", nullable = false, updatable = false, length = 128)
    private String requestId;
    @Column(name = "reviewed_output_version", nullable = false, updatable = false)
    private long reviewedOutputVersion;
    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected ReviewDecision() {
    }

    public static ReviewDecision create(UUID id, UUID outputId, ReviewDecisionType decision, String reason,
            AuditActor reviewer, String requestId, long reviewedOutputVersion, Instant decidedAt) {
        ReviewDecision value = new ReviewDecision();
        value.reviewDecisionUuid = Objects.requireNonNull(id, "reviewDecisionUuid is required");
        value.generationOutputUuid = Objects.requireNonNull(outputId, "generationOutputUuid is required");
        value.decision = Objects.requireNonNull(decision, "decision is required");
        value.reason = decision == ReviewDecisionType.REJECTED
                ? AiDomainRules.required(reason, "reason", 2000) : null;
        if (decision == ReviewDecisionType.APPROVED && reason != null) {
            throw new IllegalArgumentException("Approval reason is not accepted");
        }
        AuditActor actor = Objects.requireNonNull(reviewer, "reviewer is required");
        if (actor.type() != AuditActorType.LOCAL_ADMIN && actor.type() != AuditActorType.TRUSTED_ACTOR) {
            throw new IllegalArgumentException("A trusted human reviewer is required");
        }
        value.reviewerType = actor.type();
        value.reviewerId = AiDomainRules.required(actor.id(), "reviewerId", 128);
        value.requestId = requiredRequestId(requestId);
        if (reviewedOutputVersion < 0) throw new IllegalArgumentException("reviewedOutputVersion is invalid");
        value.reviewedOutputVersion = reviewedOutputVersion;
        value.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt is required");
        return value;
    }

    private static String requiredRequestId(String value) {
        String normalized = AiDomainRules.required(value, "requestId", 128);
        if (!normalized.matches("[A-Za-z0-9._:-]{1,128}")) throw new IllegalArgumentException("requestId is invalid");
        return normalized;
    }

    public UUID getReviewDecisionUuid() { return reviewDecisionUuid; }
    public UUID getGenerationOutputUuid() { return generationOutputUuid; }
    public ReviewDecisionType getDecision() { return decision; }
    public String getReason() { return reason; }
    public AuditActorType getReviewerType() { return reviewerType; }
    public String getReviewerId() { return reviewerId; }
    public String getRequestId() { return requestId; }
    public long getReviewedOutputVersion() { return reviewedOutputVersion; }
    public Instant getDecidedAt() { return decidedAt; }
}
