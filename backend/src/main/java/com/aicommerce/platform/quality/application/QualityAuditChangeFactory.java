package com.aicommerce.platform.quality.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.quality.domain.QualityBlockerCode;
import com.aicommerce.platform.quality.domain.QualityScore;
import com.aicommerce.platform.quality.domain.WorkflowStatus;
import org.springframework.stereotype.Component;

@Component
public class QualityAuditChangeFactory {
    public List<AuditChange> score(QualityScoreSnapshot before, Set<QualityBlockerCode> beforeBlockers,
            QualityScore after, Set<QualityBlockerCode> afterBlockers) {
        List<AuditChange> result = new ArrayList<>();
        add(result, "product_master_score", before == null ? null : Integer.toString(before.productMasterScore()),
                Integer.toString(after.getProductMasterScore()), AuditValueType.INTEGER);
        add(result, "product_knowledge_score", before == null ? null : Integer.toString(before.productKnowledgeScore()),
                Integer.toString(after.getProductKnowledgeScore()), AuditValueType.INTEGER);
        add(result, "creative_plan_score", before == null ? null : Integer.toString(before.creativePlanScore()),
                Integer.toString(after.getCreativePlanScore()), AuditValueType.INTEGER);
        add(result, "asset_metadata_score", before == null ? null : Integer.toString(before.assetMetadataScore()),
                Integer.toString(after.getAssetMetadataScore()), AuditValueType.INTEGER);
        add(result, "campaign_readiness_score", before == null ? null : Integer.toString(before.campaignReadinessScore()),
                Integer.toString(after.getCampaignReadinessScore()), AuditValueType.INTEGER);
        add(result, "system_score", before == null ? null : Integer.toString(before.systemScore()),
                Integer.toString(after.getSystemScore()), AuditValueType.INTEGER);
        add(result, "manual_adjustment", before == null ? null : Integer.toString(before.manualAdjustment()),
                Integer.toString(after.getManualAdjustment()), AuditValueType.INTEGER);
        add(result, "manual_adjustment_reason", before == null ? null : before.manualAdjustmentReason(),
                after.getManualAdjustmentReason(), AuditValueType.STRING);
        add(result, "manual_adjusted_by", before == null ? null : before.manualAdjustedBy(),
                after.getManualAdjustedBy(), AuditValueType.STRING);
        add(result, "manual_adjusted_at", before == null ? null : string(before.manualAdjustedAt()),
                string(after.getManualAdjustedAt()), AuditValueType.TIMESTAMP);
        add(result, "final_score", before == null ? null : Integer.toString(before.finalScore()),
                Integer.toString(after.getFinalScore()), AuditValueType.INTEGER);
        add(result, "blockers", blockers(beforeBlockers), blockers(afterBlockers), AuditValueType.STRING);
        return List.copyOf(result);
    }

    public List<AuditChange> workflow(WorkflowSnapshot before, WorkflowStatus after) {
        List<AuditChange> result = new ArrayList<>();
        add(result, "status", before == null ? null : before.status().name(), after.getStatus().name(), AuditValueType.ENUM);
        add(result, "status_reason", before == null ? null : before.reason(), after.getStatusReason(), AuditValueType.STRING);
        return List.copyOf(result);
    }

    private String string(Object value) { return value == null ? null : value.toString(); }
    private String blockers(Set<QualityBlockerCode> values) {
        return values == null ? null : values.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
    private void add(List<AuditChange> result, String field, String oldValue, String newValue, AuditValueType type) {
        if (!Objects.equals(oldValue, newValue)) result.add(new AuditChange(field, oldValue, newValue, type, result.size()));
    }
}
