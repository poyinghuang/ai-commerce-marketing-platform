package com.aicommerce.platform.quality.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.quality.domain.QualityScore;
import com.aicommerce.platform.quality.domain.QualityScoreBlocker;
import com.aicommerce.platform.quality.domain.ReadinessStatus;
import com.aicommerce.platform.quality.domain.WorkflowStatus;

public record QualityProjectionView(UUID productUuid, int productMasterScore,
        int productKnowledgeScore, int creativePlanScore, int assetMetadataScore,
        int campaignReadinessScore, int systemScore, Integer aiSuggestedScore,
        int manualAdjustment, String manualAdjustmentReason, String manualAdjustedBy,
        Instant manualAdjustedAt, int finalScore, List<BlockerView> blockers,
        ReadinessStatus readinessStatus, String statusReason, Instant calculatedAt, long version) {

    public QualityProjectionView { blockers = List.copyOf(blockers); }

    public static QualityProjectionView from(QualityScore score, List<QualityScoreBlocker> blockers,
            WorkflowStatus workflow) {
        return new QualityProjectionView(score.getProductUuid(), score.getProductMasterScore(),
                score.getProductKnowledgeScore(), score.getCreativePlanScore(), score.getAssetMetadataScore(),
                score.getCampaignReadinessScore(), score.getSystemScore(), score.getAiSuggestedScore(),
                score.getManualAdjustment(), score.getManualAdjustmentReason(), score.getManualAdjustedBy(),
                score.getManualAdjustedAt(), score.getFinalScore(), blockers.stream().map(BlockerView::from).toList(),
                workflow.getStatus(), workflow.getStatusReason(), score.getCalculatedAt(), score.getVersion());
    }

    public record BlockerView(String code, String field, String message) {
        static BlockerView from(QualityScoreBlocker value) {
            return new BlockerView(value.getBlockerCode().name(), value.getFieldPath(), value.getMessage());
        }
    }
}
