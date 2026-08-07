package com.aicommerce.platform.aggregate.application;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignProductJpaRepository;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.application.ProductQualityQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductAggregateQueryService {
    private final ProductJpaRepository products;
    private final ProductKnowledgeJpaRepository knowledge;
    private final CreativePlanJpaRepository creativePlans;
    private final CampaignProductJpaRepository campaignProducts;
    private final AssetJpaRepository assets;
    private final ProductQualityQueryService quality;

    public ProductAggregateQueryService(ProductJpaRepository products,
            ProductKnowledgeJpaRepository knowledge, CreativePlanJpaRepository creativePlans,
            CampaignProductJpaRepository campaignProducts, AssetJpaRepository assets,
            ProductQualityQueryService quality) {
        this.products = products;
        this.knowledge = knowledge;
        this.creativePlans = creativePlans;
        this.campaignProducts = campaignProducts;
        this.assets = assets;
        this.quality = quality;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductAggregateView get(UUID productUuid, boolean includeArchived) {
        Product product = products.findById(productUuid)
                .orElseThrow(() -> new ProductNotFoundException(productUuid));
        List<ProductAggregateView.KnowledgeView> knowledgeViews = knowledge
                .findForAggregate(productUuid, includeArchived).stream()
                .map(ProductAggregateView.KnowledgeView::from).toList();
        List<ProductAggregateView.CreativePlanView> creativePlanViews = creativePlans
                .findForAggregate(productUuid, includeArchived).stream()
                .map(ProductAggregateView.CreativePlanView::from).toList();
        List<ProductAggregateView.CampaignView> campaignViews = campaignProducts
                .findCampaignsForAggregate(productUuid, includeArchived).stream()
                .map(row -> ProductAggregateView.CampaignView.from(
                        (CampaignPlan) row[0], (CampaignProduct) row[1]))
                .toList();
        List<ProductAggregateView.AssetView> assetViews = assets
                .findForAggregate(productUuid, includeArchived).stream()
                .map(ProductAggregateView.AssetView::from).toList();
        return new ProductAggregateView(ProductAggregateView.ProductView.from(product), knowledgeViews,
                creativePlanViews, campaignViews, assetViews, quality.get(productUuid));
    }
}
