package com.aicommerce.platform.creativeplan.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditValueType;
import org.springframework.stereotype.Component;

@Component
public class CreativePlanAuditChangeFactory {
    public List<AuditChange> forCreate(CreativePlanSnapshot after) { return differences(null, after); }
    public List<AuditChange> between(CreativePlanSnapshot before, CreativePlanSnapshot after) { return differences(before, after); }
    private List<AuditChange> differences(CreativePlanSnapshot before, CreativePlanSnapshot after) {
        List<AuditChange> result = new ArrayList<>();
        add(result, "plan_name", value(before, CreativePlanSnapshot::planName), after.planName(), AuditValueType.STRING);
        add(result, "primary_audience", value(before, CreativePlanSnapshot::primaryAudience), after.primaryAudience(), AuditValueType.STRING);
        add(result, "secondary_audience", value(before, CreativePlanSnapshot::secondaryAudience), after.secondaryAudience(), AuditValueType.STRING);
        add(result, "pain_point", value(before, CreativePlanSnapshot::painPoint), after.painPoint(), AuditValueType.STRING);
        add(result, "core_benefit", value(before, CreativePlanSnapshot::coreBenefit), after.coreBenefit(), AuditValueType.STRING);
        add(result, "creative_angle", value(before, CreativePlanSnapshot::creativeAngle), after.creativeAngle(), AuditValueType.STRING);
        add(result, "emotional_direction", value(before, CreativePlanSnapshot::emotionalDirection), after.emotionalDirection(), AuditValueType.STRING);
        add(result, "brand_tone", value(before, CreativePlanSnapshot::brandTone), after.brandTone(), AuditValueType.STRING);
        add(result, "visual_style", value(before, CreativePlanSnapshot::visualStyle), after.visualStyle(), AuditValueType.STRING);
        add(result, "main_color", value(before, CreativePlanSnapshot::mainColor), after.mainColor(), AuditValueType.STRING);
        add(result, "character_setting", value(before, CreativePlanSnapshot::characterSetting), after.characterSetting(), AuditValueType.STRING);
        add(result, "cta", value(before, CreativePlanSnapshot::cta), after.cta(), AuditValueType.STRING);
        add(result, "lifecycle_status", before == null ? null : before.lifecycleStatus().name(), after.lifecycleStatus().name(), AuditValueType.ENUM);
        add(result, "archived_at", before == null || before.archivedAt() == null ? null : before.archivedAt().toString(),
                after.archivedAt() == null ? null : after.archivedAt().toString(), AuditValueType.TIMESTAMP);
        return List.copyOf(result);
    }
    private String value(CreativePlanSnapshot snapshot, Function<CreativePlanSnapshot, String> getter) { return snapshot == null ? null : getter.apply(snapshot); }
    private void add(List<AuditChange> target, String field, String oldValue, String newValue, AuditValueType type) {
        if (!Objects.equals(oldValue, newValue)) target.add(new AuditChange(field, oldValue, newValue, type, target.size()));
    }
}
