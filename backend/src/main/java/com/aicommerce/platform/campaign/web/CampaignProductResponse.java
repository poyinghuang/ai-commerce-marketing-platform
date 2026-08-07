package com.aicommerce.platform.campaign.web;

import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CampaignProductResponse(
    UUID campaignProductUuid,
    UUID campaignUuid,
    UUID productUuid,
    String role,
    Integer priority,
    BigDecimal budgetWeight,
    LifecycleStatus lifecycleStatus,
    Instant archivedAt,
    Instant createdAt,
    Instant updatedAt,
    long version) {
  public static CampaignProductResponse from(CampaignProduct p) {
    return new CampaignProductResponse(
        p.getCampaignProductUuid(),
        p.getCampaignUuid(),
        p.getProductUuid(),
        p.getRole(),
        p.getPriority(),
        p.getBudgetWeight(),
        p.getLifecycleStatus(),
        p.getArchivedAt(),
        p.getCreatedAt(),
        p.getUpdatedAt(),
        p.getVersion());
  }
}
