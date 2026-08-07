package com.aicommerce.platform.campaign.domain;

import com.aicommerce.platform.common.persistence.ArchivableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

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

  protected CampaignProduct() {}

  private CampaignProduct(UUID campaignProductUuid, UUID campaignUuid, UUID productUuid) {
    this.campaignProductUuid =
        Objects.requireNonNull(campaignProductUuid, "campaignProductUuid is required");
    this.campaignUuid = Objects.requireNonNull(campaignUuid, "campaignUuid is required");
    this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
  }

  public static CampaignProduct create(
      UUID campaignProductUuid, UUID campaignUuid, UUID productUuid) {
    return new CampaignProduct(campaignProductUuid, campaignUuid, productUuid);
  }

  public void update(String role, Integer priority, BigDecimal budgetWeight) {
    if (getLifecycleStatus() == com.aicommerce.platform.common.domain.LifecycleStatus.ARCHIVED)
      throw new IllegalStateException("Archived campaign product cannot be modified");
    if (priority != null && priority < 0)
      throw new IllegalArgumentException("priority must be non-negative");
    if (budgetWeight != null
        && (budgetWeight.signum() < 0
            || budgetWeight.compareTo(new BigDecimal("100.00")) > 0
            || budgetWeight.scale() > 2))
      throw new IllegalArgumentException(
          "budgetWeight must be between 0.00 and 100.00 with at most two decimal places");
    if (role == null || role.isBlank()) this.role = null;
    else {
      String normalized = role.trim();
      if (normalized.length() > 128)
        throw new IllegalArgumentException("role exceeds 128 characters");
      this.role = normalized;
    }
    this.priority = priority;
    this.budgetWeight = budgetWeight == null ? null : budgetWeight.setScale(2);
  }

  public UUID getCampaignProductUuid() {
    return campaignProductUuid;
  }

  public UUID getCampaignUuid() {
    return campaignUuid;
  }

  public UUID getProductUuid() {
    return productUuid;
  }

  public String getRole() {
    return role;
  }

  public Integer getPriority() {
    return priority;
  }

  public BigDecimal getBudgetWeight() {
    return budgetWeight;
  }
}
