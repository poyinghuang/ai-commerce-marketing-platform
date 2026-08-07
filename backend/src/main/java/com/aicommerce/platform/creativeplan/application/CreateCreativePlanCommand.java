package com.aicommerce.platform.creativeplan.application;

public record CreateCreativePlanCommand(
        String planName, String primaryAudience, String secondaryAudience, String painPoint,
        String coreBenefit, String creativeAngle, String emotionalDirection, String brandTone,
        String visualStyle, String mainColor, String characterSetting, String cta) { }
