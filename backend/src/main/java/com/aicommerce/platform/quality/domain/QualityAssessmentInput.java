package com.aicommerce.platform.quality.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.aicommerce.platform.knowledge.domain.KnowledgeType;

public record QualityAssessmentInput(
        ProductFacts product,
        Set<KnowledgeType> activeKnowledgeTypes,
        List<CreativePlanFacts> activeCreativePlans,
        List<AssetFacts> activeAssets,
        List<CampaignFacts> activeCampaigns,
        int manualAdjustment) {

    public QualityAssessmentInput {
        Objects.requireNonNull(product, "product is required");
        activeKnowledgeTypes = Set.copyOf(Objects.requireNonNull(activeKnowledgeTypes, "activeKnowledgeTypes is required"));
        activeCreativePlans = List.copyOf(Objects.requireNonNull(activeCreativePlans, "activeCreativePlans is required"));
        activeAssets = List.copyOf(Objects.requireNonNull(activeAssets, "activeAssets is required"));
        activeCampaigns = List.copyOf(Objects.requireNonNull(activeCampaigns, "activeCampaigns is required"));
        if (manualAdjustment < -20 || manualAdjustment > 20) {
            throw new IllegalArgumentException("manualAdjustment must be between -20 and 20");
        }
    }

    public record ProductFacts(boolean archived, String productName, String brand, String category,
            String shortDescription, BigDecimal cost, BigDecimal salePrice, String currency,
            Long stock, String productUrl) {}

    public record CreativePlanFacts(String primaryAudience, String painPoint, String coreBenefit,
            String creativeAngle, String brandTone, String visualStyle, String cta) {}

    public record AssetFacts(boolean image, String fileUrl, String storageProvider,
            String providerFileId, String mediaType, String originalFilename) {}

    public record CampaignFacts(String objective, String landingPage, BigDecimal budgetDaily,
            BigDecimal budgetTotal, String currency) {}
}
