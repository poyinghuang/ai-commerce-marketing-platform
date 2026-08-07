package com.aicommerce.platform.campaign.application;

import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import java.math.BigDecimal;
import java.time.*;

record CampaignSnapshot(
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
    Instant archivedAt) {
  static CampaignSnapshot from(CampaignPlan p) {
    return new CampaignSnapshot(
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
        p.getArchivedAt());
  }
}
