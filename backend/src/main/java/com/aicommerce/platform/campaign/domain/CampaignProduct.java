package com.aicommerce.platform.campaign.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.ArchivableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "campaign_products")
public class CampaignProduct extends ArchivableEntity {

    @Id
    @Column(name = "campaign_product_uuid", nullable = false, updatable = false)
    private UUID campaignProductUuid;
    @Column(name = "campaign_uuid", nullable = false, updatable = false)
    private UUID campaignUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Column(name = "role", length = 128)
    private String role;
    @Column(name = "priority")
    private Integer priority;
    @Column(name = "budget_weight", precision = 5, scale = 2)
    private BigDecimal budgetWeight;

    protected CampaignProduct() {
    }

    private CampaignProduct(UUID campaignProductUuid, UUID campaignUuid, UUID productUuid) {
        this.campaignProductUuid = Objects.requireNonNull(campaignProductUuid, "campaignProductUuid is required");
        this.campaignUuid = Objects.requireNonNull(campaignUuid, "campaignUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
    }

    public static CampaignProduct create(UUID campaignProductUuid, UUID campaignUuid, UUID productUuid) {
        return new CampaignProduct(campaignProductUuid, campaignUuid, productUuid);
    }

    public UUID getCampaignProductUuid() { return campaignProductUuid; }
    public UUID getCampaignUuid() { return campaignUuid; }
    public UUID getProductUuid() { return productUuid; }
    public String getRole() { return role; }
    public Integer getPriority() { return priority; }
    public BigDecimal getBudgetWeight() { return budgetWeight; }
}
