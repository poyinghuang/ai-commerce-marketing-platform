package com.aicommerce.platform.campaign.web;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateCampaignProductRequest(
    @NotNull UUID productUuid,
    @Size(max = 128) String role,
    @PositiveOrZero Integer priority,
    @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal budgetWeight) {}
