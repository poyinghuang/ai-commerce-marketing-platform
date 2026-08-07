package com.aicommerce.platform.aggregate.application;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.quality.application.QualityProjectionView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductAggregateView(
        ProductView product,
        List<KnowledgeView> knowledge,
        List<CreativePlanView> creativePlans,
        List<CampaignView> campaigns,
        List<AssetView> assets,
        QualityProjectionView quality) {

    public ProductAggregateView {
        knowledge = List.copyOf(knowledge);
        creativePlans = List.copyOf(creativePlans);
        campaigns = List.copyOf(campaigns);
        assets = List.copyOf(assets);
    }

    public record ProductView(UUID productUuid, String productId, String sku, String productName,
            String brand, String category, String subcategory, String shortDescription,
            String cost, String salePrice, String currency, String stock, String productUrl,
            ProductLifecycleStatus lifecycleStatus, Instant archivedAt, Instant createdAt,
            Instant updatedAt, long version) {
        static ProductView from(Product value) {
            return new ProductView(value.getProductUuid(), value.getProductId(), value.getSku(),
                    value.getProductName(), value.getBrand(), value.getCategory(), value.getSubcategory(),
                    value.getShortDescription(), decimal(value.getCost()), decimal(value.getSalePrice()),
                    value.getCurrency(), value.getStock() == null ? null : value.getStock().toString(),
                    value.getProductUrl(), value.getLifecycleStatus(), value.getArchivedAt(),
                    value.getCreatedAt(), value.getUpdatedAt(), value.getVersion());
        }
    }

    public record KnowledgeView(UUID knowledgeUuid, UUID productUuid, KnowledgeType knowledgeType,
            String title, String content, String source, LifecycleStatus lifecycleStatus,
            Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
        static KnowledgeView from(ProductKnowledge value) {
            return new KnowledgeView(value.getKnowledgeUuid(), value.getProductUuid(), value.getKnowledgeType(),
                    value.getTitle(), value.getContent(), value.getSource(), value.getLifecycleStatus(),
                    value.getArchivedAt(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion());
        }
    }

    public record CreativePlanView(UUID creativePlanUuid, UUID productUuid, String planName,
            String primaryAudience, String secondaryAudience, String painPoint, String coreBenefit,
            String creativeAngle, String emotionalDirection, String brandTone, String visualStyle,
            String mainColor, String characterSetting, String cta, LifecycleStatus lifecycleStatus,
            Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
        static CreativePlanView from(CreativePlan value) {
            return new CreativePlanView(value.getCreativePlanUuid(), value.getProductUuid(), value.getPlanName(),
                    value.getPrimaryAudience(), value.getSecondaryAudience(), value.getPainPoint(),
                    value.getCoreBenefit(), value.getCreativeAngle(), value.getEmotionalDirection(),
                    value.getBrandTone(), value.getVisualStyle(), value.getMainColor(), value.getCharacterSetting(),
                    value.getCta(), value.getLifecycleStatus(), value.getArchivedAt(), value.getCreatedAt(),
                    value.getUpdatedAt(), value.getVersion());
        }
    }

    public record CampaignView(UUID campaignUuid, String campaignName, String activityType,
            LocalDate startDate, LocalDate endDate, String objective, String platform,
            BigDecimal budgetDaily, BigDecimal budgetTotal, String currency, String promotion,
            String landingPage, LifecycleStatus lifecycleStatus, Instant archivedAt,
            Instant createdAt, Instant updatedAt, long version, CampaignProductView association) {
        static CampaignView from(CampaignPlan campaign, CampaignProduct association) {
            return new CampaignView(campaign.getCampaignUuid(), campaign.getCampaignName(),
                    campaign.getActivityType(), campaign.getStartDate(), campaign.getEndDate(),
                    campaign.getObjective(), campaign.getPlatform(), campaign.getBudgetDaily(),
                    campaign.getBudgetTotal(), campaign.getCurrency(), campaign.getPromotion(),
                    campaign.getLandingPage(), campaign.getLifecycleStatus(), campaign.getArchivedAt(),
                    campaign.getCreatedAt(), campaign.getUpdatedAt(), campaign.getVersion(),
                    CampaignProductView.from(association));
        }
    }

    public record CampaignProductView(UUID campaignProductUuid, UUID campaignUuid, UUID productUuid,
            String role, Integer priority, BigDecimal budgetWeight, LifecycleStatus lifecycleStatus,
            Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
        static CampaignProductView from(CampaignProduct value) {
            return new CampaignProductView(value.getCampaignProductUuid(), value.getCampaignUuid(),
                    value.getProductUuid(), value.getRole(), value.getPriority(), value.getBudgetWeight(),
                    value.getLifecycleStatus(), value.getArchivedAt(), value.getCreatedAt(),
                    value.getUpdatedAt(), value.getVersion());
        }
    }

    public record AssetView(UUID assetUuid, UUID productUuid, UUID creativePlanUuid, UUID campaignUuid,
            AssetType assetType, String purpose, String storageProvider, String providerFileId,
            String fileUrl, String mediaType, String originalFilename, Long sizeBytes,
            String checksumSha256, Map<String, Object> providerMetadata, LifecycleStatus lifecycleStatus,
            Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
        public AssetView {
            providerMetadata = immutableJsonObject(providerMetadata);
        }

        static AssetView from(Asset value) {
            return new AssetView(value.getAssetUuid(), value.getProductUuid(), value.getCreativePlanUuid(),
                    value.getCampaignUuid(), value.getAssetType(), value.getPurpose(), value.getStorageProvider(),
                    value.getProviderFileId(), value.getFileUrl(), value.getMediaType(),
                    value.getOriginalFilename(), value.getSizeBytes(), value.getChecksumSha256(),
                    value.getProviderMetadata(), value.getLifecycleStatus(), value.getArchivedAt(),
                    value.getCreatedAt(), value.getUpdatedAt(), value.getVersion());
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static Map<String, Object> immutableJsonObject(Map<String, Object> value) {
        if (value == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, item) -> copy.put(key, immutableJsonValue(item)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), immutableJsonValue(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(immutableJsonValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
