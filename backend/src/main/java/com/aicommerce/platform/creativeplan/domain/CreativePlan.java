package com.aicommerce.platform.creativeplan.domain;

import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.persistence.ArchivableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "creative_plans")
public class CreativePlan extends ArchivableEntity {

    @Id
    @Column(name = "creative_plan_uuid", nullable = false, updatable = false)
    private UUID creativePlanUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Column(name = "plan_name", nullable = false, length = 256)
    private String planName;
    @Column(name = "primary_audience", length = 2000)
    private String primaryAudience;
    @Column(name = "secondary_audience", length = 2000)
    private String secondaryAudience;
    @Column(name = "pain_point", length = 4000)
    private String painPoint;
    @Column(name = "core_benefit", length = 4000)
    private String coreBenefit;
    @Column(name = "creative_angle", length = 4000)
    private String creativeAngle;
    @Column(name = "emotional_direction", length = 1000)
    private String emotionalDirection;
    @Column(name = "brand_tone", length = 1000)
    private String brandTone;
    @Column(name = "visual_style", length = 2000)
    private String visualStyle;
    @Column(name = "main_color", length = 128)
    private String mainColor;
    @Column(name = "character_setting", length = 2000)
    private String characterSetting;
    @Column(name = "cta", length = 1000)
    private String cta;

    protected CreativePlan() {
    }

    private CreativePlan(UUID creativePlanUuid, UUID productUuid, String planName) {
        this.creativePlanUuid = Objects.requireNonNull(creativePlanUuid, "creativePlanUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.planName = required(planName, "planName", 256);
    }

    public static CreativePlan create(UUID creativePlanUuid, UUID productUuid, String planName) {
        return new CreativePlan(creativePlanUuid, productUuid, planName);
    }

    public void update(
            String planName,
            String primaryAudience,
            String secondaryAudience,
            String painPoint,
            String coreBenefit,
            String creativeAngle,
            String emotionalDirection,
            String brandTone,
            String visualStyle,
            String mainColor,
            String characterSetting,
            String cta) {
        if (getLifecycleStatus() == LifecycleStatus.ARCHIVED) {
            throw new IllegalStateException("Archived creative plan cannot be modified");
        }
        this.planName = required(planName, "planName", 256);
        this.primaryAudience = optional(primaryAudience, "primaryAudience", 2000);
        this.secondaryAudience = optional(secondaryAudience, "secondaryAudience", 2000);
        this.painPoint = optional(painPoint, "painPoint", 4000);
        this.coreBenefit = optional(coreBenefit, "coreBenefit", 4000);
        this.creativeAngle = optional(creativeAngle, "creativeAngle", 4000);
        this.emotionalDirection = optional(emotionalDirection, "emotionalDirection", 1000);
        this.brandTone = optional(brandTone, "brandTone", 1000);
        this.visualStyle = optional(visualStyle, "visualStyle", 2000);
        this.mainColor = optional(mainColor, "mainColor", 128);
        this.characterSetting = optional(characterSetting, "characterSetting", 2000);
        this.cta = optional(cta, "cta", 1000);
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value, field, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    public UUID getCreativePlanUuid() { return creativePlanUuid; }
    public UUID getProductUuid() { return productUuid; }
    public String getPlanName() { return planName; }
    public String getPrimaryAudience() { return primaryAudience; }
    public String getSecondaryAudience() { return secondaryAudience; }
    public String getPainPoint() { return painPoint; }
    public String getCoreBenefit() { return coreBenefit; }
    public String getCreativeAngle() { return creativeAngle; }
    public String getEmotionalDirection() { return emotionalDirection; }
    public String getBrandTone() { return brandTone; }
    public String getVisualStyle() { return visualStyle; }
    public String getMainColor() { return mainColor; }
    public String getCharacterSetting() { return characterSetting; }
    public String getCta() { return cta; }
}
