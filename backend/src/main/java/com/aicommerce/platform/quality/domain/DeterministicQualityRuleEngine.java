package com.aicommerce.platform.quality.domain;

import java.net.URI;
import java.util.EnumSet;

import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.AssetFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.CampaignFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.CreativePlanFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.ProductFacts;

public final class DeterministicQualityRuleEngine {

    public QualityAssessment assess(QualityAssessmentInput input) {
        int productMaster = productMaster(input.product());
        int knowledge = knowledge(input);
        int creativePlan = input.activeCreativePlans().stream().mapToInt(this::creativePlan).max().orElse(0);
        int assets = assets(input);
        int campaigns = campaigns(input);
        int systemScore = productMaster + knowledge + creativePlan + assets + campaigns;
        int finalScore = Math.max(0, Math.min(100, systemScore + input.manualAdjustment()));
        var blockers = blockers(input);
        ReadinessStatus status = readinessStatus(finalScore, input.product().archived(), blockers);
        return new QualityAssessment(productMaster, knowledge, creativePlan, assets, campaigns,
                systemScore, input.manualAdjustment(), finalScore, blockers, status);
    }

    ReadinessStatus readinessStatus(int finalScore, boolean archived, java.util.Set<QualityBlockerCode> blockers) {
        if (finalScore < 0 || finalScore > 100) throw new IllegalArgumentException("finalScore must be between 0 and 100");
        if (archived || finalScore < 70) return ReadinessStatus.DRAFT;
        if (finalScore < 90 || !blockers.isEmpty()) return ReadinessStatus.NEEDS_REVIEW;
        return ReadinessStatus.READY;
    }

    private int productMaster(ProductFacts product) {
        int score = 0;
        if (hasText(product.productName())) score += 8;
        if (hasText(product.brand())) score += 4;
        if (hasText(product.category())) score += 4;
        if (hasText(product.shortDescription())) score += 5;
        if (product.salePrice() != null && hasText(product.currency())) score += 5;
        if (product.cost() != null && hasText(product.currency())) score += 3;
        if (product.stock() != null) score += 3;
        if (isSafeHttpUrl(product.productUrl())) score += 3;
        return score;
    }

    private int knowledge(QualityAssessmentInput input) {
        var types = input.activeKnowledgeTypes();
        int score = types.isEmpty() ? 0 : 5;
        if (types.contains(KnowledgeType.FEATURE)) score += 5;
        if (types.contains(KnowledgeType.BENEFIT)) score += 5;
        if (types.contains(KnowledgeType.AUDIENCE)) score += 5;
        if (types.stream().anyMatch(type -> type == KnowledgeType.PAIN_POINT
                || type == KnowledgeType.FAQ || type == KnowledgeType.PROOF)) score += 5;
        return score;
    }

    private int creativePlan(CreativePlanFacts plan) {
        int score = 5;
        if (hasText(plan.primaryAudience())) score += 5;
        if (hasText(plan.painPoint()) && hasText(plan.coreBenefit())) score += 5;
        if (hasText(plan.creativeAngle())) score += 5;
        if (hasText(plan.brandTone()) && hasText(plan.visualStyle()) && hasText(plan.cta())) score += 5;
        return score;
    }

    private int assets(QualityAssessmentInput input) {
        if (input.activeAssets().isEmpty()) return 0;
        int score = 2;
        if (input.activeAssets().stream().anyMatch(AssetFacts::image)) score += 3;
        if (input.activeAssets().stream().anyMatch(asset -> hasText(asset.fileUrl())
                || hasText(asset.storageProvider()) && hasText(asset.providerFileId()))) score += 3;
        if (input.activeAssets().stream().anyMatch(asset -> hasText(asset.mediaType())
                && hasText(asset.originalFilename()))) score += 2;
        return score;
    }

    private int campaigns(QualityAssessmentInput input) {
        if (input.activeCampaigns().isEmpty()) return 0;
        int score = 2;
        if (input.activeCampaigns().stream().anyMatch(campaign -> hasText(campaign.objective()))) score++;
        if (input.activeCampaigns().stream().anyMatch(campaign -> isSafeHttpUrl(campaign.landingPage()))) score++;
        if (input.activeCampaigns().stream().anyMatch(this::hasCurrencyBackedBudget)) score++;
        return score;
    }

    private boolean hasCurrencyBackedBudget(CampaignFacts campaign) {
        return hasText(campaign.currency()) && (campaign.budgetDaily() != null || campaign.budgetTotal() != null);
    }

    private EnumSet<QualityBlockerCode> blockers(QualityAssessmentInput input) {
        var blockers = EnumSet.noneOf(QualityBlockerCode.class);
        ProductFacts product = input.product();
        if (product.archived()) blockers.add(QualityBlockerCode.PRODUCT_ARCHIVED);
        if (!hasText(product.productName())) blockers.add(QualityBlockerCode.PRODUCT_NAME_MISSING);
        if (product.salePrice() == null) blockers.add(QualityBlockerCode.SALE_PRICE_MISSING);
        if (!hasText(product.currency())) blockers.add(QualityBlockerCode.CURRENCY_MISSING);
        if (input.activeKnowledgeTypes().isEmpty()) blockers.add(QualityBlockerCode.KNOWLEDGE_MISSING);
        if (input.activeCreativePlans().isEmpty()) blockers.add(QualityBlockerCode.CREATIVE_PLAN_MISSING);
        if (input.activeAssets().stream().noneMatch(AssetFacts::image)) blockers.add(QualityBlockerCode.IMAGE_ASSET_MISSING);
        return blockers;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isSafeHttpUrl(String value) {
        if (!hasText(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
