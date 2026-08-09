package com.aicommerce.platform.ai.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "ai_prompt_template_versions")
@EntityListeners(AuditingEntityListener.class)
public class PromptTemplateVersion {

    @Id
    @Column(name = "prompt_template_version_uuid", nullable = false, updatable = false)
    private UUID promptTemplateVersionUuid;
    @Column(name = "prompt_template_uuid", nullable = false, updatable = false)
    private UUID promptTemplateUuid;
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;
    @Column(name = "template_text", nullable = false, updatable = false, length = 16000)
    private String templateText;
    @Column(name = "negative_prompt", updatable = false, length = 8000)
    private String negativePrompt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String inputSchema;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", nullable = false, updatable = false, columnDefinition = "char(64)")
    private String contentSha256;
    @Column(name = "created_by", nullable = false, updatable = false, length = 128)
    private String createdBy;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PromptTemplateVersion() {
    }

    private PromptTemplateVersion(UUID id, UUID templateId, int versionNumber, String templateText,
            String negativePrompt, String inputSchema, String contentSha256, String createdBy) {
        this.promptTemplateVersionUuid = Objects.requireNonNull(id, "promptTemplateVersionUuid is required");
        this.promptTemplateUuid = Objects.requireNonNull(templateId, "promptTemplateUuid is required");
        if (versionNumber <= 0) throw new IllegalArgumentException("versionNumber must be positive");
        this.versionNumber = versionNumber;
        this.templateText = AiDomainRules.required(templateText, "templateText", 16000);
        this.negativePrompt = AiDomainRules.optional(negativePrompt, "negativePrompt", 8000);
        this.inputSchema = AiDomainRules.required(inputSchema, "inputSchema", 16384);
        this.contentSha256 = AiDomainRules.required(contentSha256, "contentSha256", 64);
        if (!this.contentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentSha256 must be lowercase SHA-256");
        }
        this.createdBy = AiDomainRules.required(createdBy, "createdBy", 128);
    }

    public static PromptTemplateVersion create(UUID id, UUID templateId, int versionNumber,
            String templateText, String negativePrompt, String inputSchema, String contentSha256,
            String createdBy) {
        return new PromptTemplateVersion(id, templateId, versionNumber, templateText, negativePrompt,
                inputSchema, contentSha256, createdBy);
    }

    public UUID getPromptTemplateVersionUuid() { return promptTemplateVersionUuid; }
    public UUID getPromptTemplateUuid() { return promptTemplateUuid; }
    public int getVersionNumber() { return versionNumber; }
    public String getTemplateText() { return templateText; }
    public String getNegativePrompt() { return negativePrompt; }
    public String getInputSchema() { return inputSchema; }
    public String getContentSha256() { return contentSha256; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
