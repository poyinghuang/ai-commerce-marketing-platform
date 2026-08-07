package com.aicommerce.platform.creativeplan.web;

import java.time.Instant;
import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;

public record CreativePlanResponse(
        UUID creativePlanUuid, UUID productUuid, String planName, String primaryAudience,
        String secondaryAudience, String painPoint, String coreBenefit, String creativeAngle,
        String emotionalDirection, String brandTone, String visualStyle, String mainColor,
        String characterSetting, String cta, LifecycleStatus lifecycleStatus, Instant archivedAt,
        Instant createdAt, Instant updatedAt, long version) {
    public static CreativePlanResponse from(CreativePlan p) {
        return new CreativePlanResponse(p.getCreativePlanUuid(), p.getProductUuid(), p.getPlanName(),
                p.getPrimaryAudience(), p.getSecondaryAudience(), p.getPainPoint(), p.getCoreBenefit(),
                p.getCreativeAngle(), p.getEmotionalDirection(), p.getBrandTone(), p.getVisualStyle(),
                p.getMainColor(), p.getCharacterSetting(), p.getCta(), p.getLifecycleStatus(),
                p.getArchivedAt(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }
}
