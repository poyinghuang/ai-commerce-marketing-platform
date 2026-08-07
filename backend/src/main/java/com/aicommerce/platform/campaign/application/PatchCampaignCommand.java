package com.aicommerce.platform.campaign.application;

import com.aicommerce.platform.common.application.FieldPatch;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PatchCampaignCommand(
    FieldPatch<String> campaignName,
    FieldPatch<String> activityType,
    FieldPatch<LocalDate> startDate,
    FieldPatch<LocalDate> endDate,
    FieldPatch<String> objective,
    FieldPatch<String> platform,
    FieldPatch<BigDecimal> budgetDaily,
    FieldPatch<BigDecimal> budgetTotal,
    FieldPatch<String> currency,
    FieldPatch<String> promotion,
    FieldPatch<String> landingPage) {}
