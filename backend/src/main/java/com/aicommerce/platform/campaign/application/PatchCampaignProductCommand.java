package com.aicommerce.platform.campaign.application;

import com.aicommerce.platform.common.application.FieldPatch;
import java.math.BigDecimal;

public record PatchCampaignProductCommand(
    FieldPatch<String> role, FieldPatch<Integer> priority, FieldPatch<BigDecimal> budgetWeight) {}
