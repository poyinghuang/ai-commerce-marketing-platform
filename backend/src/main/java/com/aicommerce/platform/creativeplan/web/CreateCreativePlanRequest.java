package com.aicommerce.platform.creativeplan.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCreativePlanRequest(
        @NotBlank @Size(max=256) String planName,
        @Size(max=2000) String primaryAudience, @Size(max=2000) String secondaryAudience,
        @Size(max=4000) String painPoint, @Size(max=4000) String coreBenefit,
        @Size(max=4000) String creativeAngle, @Size(max=1000) String emotionalDirection,
        @Size(max=1000) String brandTone, @Size(max=2000) String visualStyle,
        @Size(max=128) String mainColor, @Size(max=2000) String characterSetting,
        @Size(max=1000) String cta) { }
