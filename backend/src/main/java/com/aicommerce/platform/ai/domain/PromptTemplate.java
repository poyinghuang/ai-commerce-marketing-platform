package com.aicommerce.platform.ai.domain;

import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_prompt_templates")
public class PromptTemplate extends MutableEntity {

    @Id
    @Column(name = "prompt_template_uuid", nullable = false, updatable = false)
    private UUID promptTemplateUuid;

    @Column(name = "template_key", nullable = false, updatable = false, length = 128)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type", nullable = false, updatable = false, length = 16)
    private GenerationType generationType;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus;

    protected PromptTemplate() {
    }

    private PromptTemplate(UUID id, String templateKey, GenerationType generationType, String displayName) {
        this.promptTemplateUuid = Objects.requireNonNull(id, "promptTemplateUuid is required");
        this.templateKey = AiDomainRules.required(templateKey, "templateKey", 128);
        if (!this.templateKey.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("templateKey has an invalid format");
        }
        this.generationType = Objects.requireNonNull(generationType, "generationType is required");
        this.displayName = AiDomainRules.required(displayName, "displayName", 256);
        this.lifecycleStatus = LifecycleStatus.ACTIVE;
    }

    public static PromptTemplate create(UUID id, String templateKey, GenerationType generationType,
            String displayName) {
        return new PromptTemplate(id, templateKey, generationType, displayName);
    }

    public boolean rename(String displayName) {
        String normalized = AiDomainRules.required(displayName, "displayName", 256);
        if (normalized.equals(this.displayName)) {
            return false;
        }
        if (lifecycleStatus == LifecycleStatus.ARCHIVED) {
            throw new IllegalStateException("Archived prompt template cannot be modified");
        }
        this.displayName = normalized;
        return true;
    }

    public boolean archive() {
        if (lifecycleStatus == LifecycleStatus.ARCHIVED) {
            return false;
        }
        lifecycleStatus = LifecycleStatus.ARCHIVED;
        return true;
    }

    public UUID getPromptTemplateUuid() { return promptTemplateUuid; }
    public String getTemplateKey() { return templateKey; }
    public GenerationType getGenerationType() { return generationType; }
    public String getDisplayName() { return displayName; }
    public LifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
}
