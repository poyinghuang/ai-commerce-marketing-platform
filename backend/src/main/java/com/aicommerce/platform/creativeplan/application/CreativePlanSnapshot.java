package com.aicommerce.platform.creativeplan.application;

import java.time.Instant;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;

record CreativePlanSnapshot(
        String planName, String primaryAudience, String secondaryAudience, String painPoint,
        String coreBenefit, String creativeAngle, String emotionalDirection, String brandTone,
        String visualStyle, String mainColor, String characterSetting, String cta,
        LifecycleStatus lifecycleStatus, Instant archivedAt) {
    static CreativePlanSnapshot from(CreativePlan plan) {
        return new CreativePlanSnapshot(plan.getPlanName(), plan.getPrimaryAudience(), plan.getSecondaryAudience(),
                plan.getPainPoint(), plan.getCoreBenefit(), plan.getCreativeAngle(), plan.getEmotionalDirection(),
                plan.getBrandTone(), plan.getVisualStyle(), plan.getMainColor(), plan.getCharacterSetting(),
                plan.getCta(), plan.getLifecycleStatus(), plan.getArchivedAt());
    }
}
