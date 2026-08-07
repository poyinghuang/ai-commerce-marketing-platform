package com.aicommerce.platform.campaign.web;

import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record CampaignResponse(
    UUID campaignUuid,
    String campaignName,
    String activityType,
    LocalDate startDate,
    LocalDate endDate,
    String objective,
    String platform,
    BigDecimal budgetDaily,
    BigDecimal budgetTotal,
    String currency,
    String promotion,
    String landingPage,
    LifecycleStatus lifecycleStatus,
    Instant archivedAt,
    Instant createdAt,
    Instant updatedAt,
    long version,
    CampaignProductResponse association) {
  public static CampaignResponse from(CampaignPlan p) {
    return from(p, null);
  }

  public static CampaignResponse from(CampaignPlan p, CampaignProductResponse association) {
    return new CampaignResponse(
        p.getCampaignUuid(),
        p.getCampaignName(),
        p.getActivityType(),
        p.getStartDate(),
        p.getEndDate(),
        p.getObjective(),
        p.getPlatform(),
        p.getBudgetDaily(),
        p.getBudgetTotal(),
        p.getCurrency(),
        p.getPromotion(),
        p.getLandingPage(),
        p.getLifecycleStatus(),
        p.getArchivedAt(),
        p.getCreatedAt(),
        p.getUpdatedAt(),
        p.getVersion(),
        association);
  }
}
