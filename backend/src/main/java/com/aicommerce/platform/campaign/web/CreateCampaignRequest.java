package com.aicommerce.platform.campaign.web;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCampaignRequest(
    @NotBlank @Size(max = 256) String campaignName,
    @Size(max = 64) String activityType,
    LocalDate startDate,
    LocalDate endDate,
    @Size(max = 2000) String objective,
    @Size(max = 64) String platform,
    @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal budgetDaily,
    @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal budgetTotal,
    @Pattern(regexp = "[A-Z]{3}") String currency,
    @Size(max = 2000) String promotion,
    @Size(max = 2048) String landingPage) {}
