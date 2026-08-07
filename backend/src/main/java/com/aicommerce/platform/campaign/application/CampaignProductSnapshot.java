package com.aicommerce.platform.campaign.application;

import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record CampaignProductSnapshot(
    UUID campaignUuid,
    UUID productUuid,
    String role,
    Integer priority,
    BigDecimal budgetWeight,
    LifecycleStatus lifecycleStatus,
    Instant archivedAt) {
  static CampaignProductSnapshot from(CampaignProduct p) {
    return new CampaignProductSnapshot(
        p.getCampaignUuid(),
        p.getProductUuid(),
        p.getRole(),
        p.getPriority(),
        p.getBudgetWeight(),
        p.getLifecycleStatus(),
        p.getArchivedAt());
  }
}
