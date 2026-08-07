package com.aicommerce.platform.campaign.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCampaignCommand(
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
    String landingPage) {}
