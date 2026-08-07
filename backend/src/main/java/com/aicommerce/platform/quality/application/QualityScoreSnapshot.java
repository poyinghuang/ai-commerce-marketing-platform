package com.aicommerce.platform.quality.application;

import java.time.Instant;

import com.aicommerce.platform.quality.domain.QualityScore;

record QualityScoreSnapshot(int productMasterScore, int productKnowledgeScore, int creativePlanScore,
        int assetMetadataScore, int campaignReadinessScore, int systemScore, int manualAdjustment,
        String manualAdjustmentReason, String manualAdjustedBy, Instant manualAdjustedAt, int finalScore) {
    static QualityScoreSnapshot from(QualityScore value) {
        return new QualityScoreSnapshot(value.getProductMasterScore(), value.getProductKnowledgeScore(),
                value.getCreativePlanScore(), value.getAssetMetadataScore(), value.getCampaignReadinessScore(),
                value.getSystemScore(), value.getManualAdjustment(), value.getManualAdjustmentReason(),
                value.getManualAdjustedBy(), value.getManualAdjustedAt(), value.getFinalScore());
    }
}
