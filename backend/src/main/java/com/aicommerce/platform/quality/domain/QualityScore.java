package com.aicommerce.platform.quality.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quality_scores")
public class QualityScore extends MutableEntity {

    @Id
    @Column(name = "quality_score_uuid", nullable = false, updatable = false)
    private UUID qualityScoreUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Column(name = "product_master_score", nullable = false)
    private int productMasterScore;
    @Column(name = "product_knowledge_score", nullable = false)
    private int productKnowledgeScore;
    @Column(name = "creative_plan_score", nullable = false)
    private int creativePlanScore;
    @Column(name = "asset_metadata_score", nullable = false)
    private int assetMetadataScore;
    @Column(name = "campaign_readiness_score", nullable = false)
    private int campaignReadinessScore;
    @Column(name = "system_score", nullable = false)
    private int systemScore;
    @Column(name = "ai_suggested_score")
    private Integer aiSuggestedScore;
    @Column(name = "manual_adjustment", nullable = false)
    private int manualAdjustment;
    @Column(name = "manual_adjustment_reason", length = 1000)
    private String manualAdjustmentReason;
    @Column(name = "manual_adjusted_by", length = 128)
    private String manualAdjustedBy;
    @Column(name = "manual_adjusted_at")
    private Instant manualAdjustedAt;
    @Column(name = "final_score", nullable = false)
    private int finalScore;
    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected QualityScore() {}

    private QualityScore(UUID qualityScoreUuid, UUID productUuid, QualityAssessment assessment, Instant calculatedAt) {
        this.qualityScoreUuid = Objects.requireNonNull(qualityScoreUuid, "qualityScoreUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        if (assessment.manualAdjustment() != 0) {
            throw new IllegalArgumentException("new quality score must start without a manual adjustment");
        }
        this.calculatedAt = Objects.requireNonNull(calculatedAt, "calculatedAt is required");
        applyAssessment(assessment, calculatedAt);
    }

    public static QualityScore create(UUID qualityScoreUuid, UUID productUuid,
            QualityAssessment assessment, Instant calculatedAt) {
        return new QualityScore(qualityScoreUuid, productUuid, assessment, calculatedAt);
    }

    public boolean applyAssessment(QualityAssessment assessment, Instant calculatedAt) {
        Objects.requireNonNull(assessment, "assessment is required");
        if (assessment.manualAdjustment() != manualAdjustment) {
            throw new IllegalArgumentException("assessment must retain the persisted manual adjustment");
        }
        if (productMasterScore == assessment.productMasterScore()
                && productKnowledgeScore == assessment.productKnowledgeScore()
                && creativePlanScore == assessment.creativePlanScore()
                && assetMetadataScore == assessment.assetMetadataScore()
                && campaignReadinessScore == assessment.campaignReadinessScore()
                && systemScore == assessment.systemScore()
                && finalScore == assessment.finalScore()) {
            return false;
        }
        this.productMasterScore = assessment.productMasterScore();
        this.productKnowledgeScore = assessment.productKnowledgeScore();
        this.creativePlanScore = assessment.creativePlanScore();
        this.assetMetadataScore = assessment.assetMetadataScore();
        this.campaignReadinessScore = assessment.campaignReadinessScore();
        this.systemScore = assessment.systemScore();
        this.finalScore = assessment.finalScore();
        this.calculatedAt = Objects.requireNonNull(calculatedAt, "calculatedAt is required");
        return true;
    }

    public boolean recordManualAdjustment(int adjustment, String reason, String actorId, Instant adjustedAt) {
        if (adjustment < -20 || adjustment > 20) throw new IllegalArgumentException("manualAdjustment must be between -20 and 20");
        if (adjustment == 0) {
            if (manualAdjustment == 0) return false;
            manualAdjustment = 0;
            manualAdjustmentReason = null;
            manualAdjustedBy = null;
            manualAdjustedAt = null;
            finalScore = systemScore;
            return true;
        }
        String normalizedReason = requireText(reason, "reason", 1000);
        String normalizedActor = requireText(actorId, "actorId", 128);
        if (manualAdjustment == adjustment
                && Objects.equals(manualAdjustmentReason, normalizedReason)
                && Objects.equals(manualAdjustedBy, normalizedActor)) return false;
        manualAdjustmentReason = normalizedReason;
        manualAdjustedBy = normalizedActor;
        manualAdjustedAt = Objects.requireNonNull(adjustedAt, "adjustedAt is required");
        manualAdjustment = adjustment;
        finalScore = Math.max(0, Math.min(100, systemScore + adjustment));
        return true;
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        return normalized;
    }

    public UUID getQualityScoreUuid() { return qualityScoreUuid; }
    public UUID getProductUuid() { return productUuid; }
    public int getProductMasterScore() { return productMasterScore; }
    public int getProductKnowledgeScore() { return productKnowledgeScore; }
    public int getCreativePlanScore() { return creativePlanScore; }
    public int getAssetMetadataScore() { return assetMetadataScore; }
    public int getCampaignReadinessScore() { return campaignReadinessScore; }
    public int getSystemScore() { return systemScore; }
    public Integer getAiSuggestedScore() { return aiSuggestedScore; }
    public int getManualAdjustment() { return manualAdjustment; }
    public String getManualAdjustmentReason() { return manualAdjustmentReason; }
    public String getManualAdjustedBy() { return manualAdjustedBy; }
    public Instant getManualAdjustedAt() { return manualAdjustedAt; }
    public int getFinalScore() { return finalScore; }
    public Instant getCalculatedAt() { return calculatedAt; }
}
