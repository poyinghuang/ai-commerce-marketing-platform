package com.aicommerce.platform.creativeplan.application;

import com.aicommerce.platform.common.application.FieldPatch;

public record PatchCreativePlanCommand(
        FieldPatch<String> planName, FieldPatch<String> primaryAudience,
        FieldPatch<String> secondaryAudience, FieldPatch<String> painPoint,
        FieldPatch<String> coreBenefit, FieldPatch<String> creativeAngle,
        FieldPatch<String> emotionalDirection, FieldPatch<String> brandTone,
        FieldPatch<String> visualStyle, FieldPatch<String> mainColor,
        FieldPatch<String> characterSetting, FieldPatch<String> cta) { }
