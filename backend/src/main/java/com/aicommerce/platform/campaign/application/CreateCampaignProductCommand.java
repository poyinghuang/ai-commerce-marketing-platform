package com.aicommerce.platform.campaign.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCampaignProductCommand(
    UUID productUuid, String role, Integer priority, BigDecimal budgetWeight) {}
