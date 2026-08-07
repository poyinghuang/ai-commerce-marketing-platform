package com.aicommerce.platform.quality.domain;

import java.util.Set;
import java.util.Objects;

public record QualityAssessment(
        int productMasterScore,
        int productKnowledgeScore,
        int creativePlanScore,
        int assetMetadataScore,
        int campaignReadinessScore,
        int systemScore,
        int manualAdjustment,
        int finalScore,
        Set<QualityBlockerCode> blockers,
        ReadinessStatus readinessStatus) {

    public QualityAssessment {
        requireRange(productMasterScore, 0, 35, "productMasterScore");
        requireRange(productKnowledgeScore, 0, 25, "productKnowledgeScore");
        requireRange(creativePlanScore, 0, 25, "creativePlanScore");
        requireRange(assetMetadataScore, 0, 10, "assetMetadataScore");
        requireRange(campaignReadinessScore, 0, 5, "campaignReadinessScore");
        requireRange(manualAdjustment, -20, 20, "manualAdjustment");
        int expectedSystem = productMasterScore + productKnowledgeScore + creativePlanScore
                + assetMetadataScore + campaignReadinessScore;
        if (systemScore != expectedSystem) throw new IllegalArgumentException("systemScore must equal component sum");
        int expectedFinal = Math.max(0, Math.min(100, systemScore + manualAdjustment));
        if (finalScore != expectedFinal) throw new IllegalArgumentException("finalScore must equal clamped adjusted score");
        blockers = Set.copyOf(Objects.requireNonNull(blockers, "blockers is required"));
        Objects.requireNonNull(readinessStatus, "readinessStatus is required");
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
