package com.aicommerce.platform.ai.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.PromptTemplate;
import com.aicommerce.platform.ai.domain.PromptTemplateVersion;
import com.aicommerce.platform.ai.infrastructure.persistence.PromptTemplateJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.PromptTemplateVersionJpaRepository;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiPromptTemplateService {

    private final PromptTemplateJpaRepository templateRepository;
    private final PromptTemplateVersionJpaRepository versionRepository;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiPromptTemplateService(PromptTemplateJpaRepository templateRepository,
            PromptTemplateVersionJpaRepository versionRepository, AuditWriter auditWriter,
            ObjectMapper objectMapper, Clock clock) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public PromptTemplate createTemplate(CreatePromptTemplateCommand command, AuditOperationContext context) {
        try {
            PromptTemplate template = PromptTemplate.create(UUID.randomUUID(), command.templateKey(),
                    command.generationType(), command.displayName());
            template = templateRepository.saveAndFlush(template);
            auditWriter.append(event(context, AuditAction.CREATE, "AI_PROMPT_TEMPLATE",
                    template.getPromptTemplateUuid(), null, List.of(
                            change("templateKey", null, template.getTemplateKey(), AuditValueType.STRING, 0),
                            change("generationType", null, template.getGenerationType().name(), AuditValueType.ENUM, 1),
                            change("displayName", null, template.getDisplayName(), AuditValueType.STRING, 2))));
            return template;
        } catch (IllegalArgumentException exception) {
            throw new AiFoundationValidationException(exception.getMessage(), exception);
        }
    }

    @Transactional
    public PromptTemplateVersion appendVersion(UUID templateUuid, AppendPromptTemplateVersionCommand command,
            AuditOperationContext context) {
        PromptTemplate template = templateRepository.findByIdForUpdate(templateUuid)
                .orElseThrow(() -> new AiFoundationValidationException("Prompt template does not exist"));
        if (template.getLifecycleStatus().name().equals("ARCHIVED")) {
            throw new AiFoundationValidationException("Archived prompt template cannot receive a version");
        }
        String canonicalSchema = canonicalSchema(command.inputSchema());
        int nextVersion = versionRepository.findByPromptTemplateUuidOrderByVersionNumberDesc(templateUuid)
                .stream().findFirst().map(version -> version.getVersionNumber() + 1).orElse(1);
        String contentHash = sha256(command.templateText() + "\n" +
                (command.negativePrompt() == null ? "" : command.negativePrompt()) + "\n" + canonicalSchema);
        try {
            PromptTemplateVersion version = PromptTemplateVersion.create(UUID.randomUUID(), templateUuid,
                    nextVersion, command.templateText(), command.negativePrompt(), canonicalSchema,
                    contentHash, context.actor().id());
            version = versionRepository.saveAndFlush(version);
            auditWriter.append(event(context, AuditAction.CREATE, "AI_PROMPT_TEMPLATE_VERSION",
                    version.getPromptTemplateVersionUuid(), null, List.of(
                            change("promptTemplateUuid", null, templateUuid.toString(), AuditValueType.UUID, 0),
                            change("versionNumber", null, Integer.toString(nextVersion), AuditValueType.INTEGER, 1),
                            change("contentSha256", null, contentHash, AuditValueType.STRING, 2))));
            return version;
        } catch (IllegalArgumentException exception) {
            throw new AiFoundationValidationException(exception.getMessage(), exception);
        }
    }

    @Transactional
    public boolean archiveTemplate(UUID templateUuid, AuditOperationContext context) {
        PromptTemplate template = templateRepository.findByIdForUpdate(templateUuid)
                .orElseThrow(() -> new AiFoundationValidationException("Prompt template does not exist"));
        if (!template.archive()) return false;
        templateRepository.saveAndFlush(template);
        auditWriter.append(event(context, AuditAction.ARCHIVE, "AI_PROMPT_TEMPLATE", templateUuid, null,
                List.of(change("lifecycleStatus", "ACTIVE", "ARCHIVED", AuditValueType.ENUM, 0))));
        return true;
    }

    private String canonicalSchema(String raw) {
        try {
            JsonNode schema = objectMapper.readTree(raw);
            if (schema == null || !schema.isObject()) {
                throw new AiFoundationValidationException("inputSchema must be a JSON object");
            }
            AiInputDataPolicy.validateSchema(schema);
            String canonical = objectMapper.writeValueAsString(schema);
            if (canonical.length() > 16384) {
                throw new AiFoundationValidationException("inputSchema exceeds 16384 characters");
            }
            return canonical;
        } catch (AiFoundationValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiFoundationValidationException("inputSchema must be valid JSON", exception);
        }
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AuditEvent event(AuditOperationContext context, AuditAction action, String entityType,
            UUID entityUuid, UUID productUuid, List<AuditChange> changes) {
        return new AuditEvent(UUID.randomUUID(), context, action, entityType, entityUuid, productUuid,
                Instant.now(clock), changes);
    }

    private AuditChange change(String field, String oldValue, String newValue, AuditValueType type, int order) {
        return new AuditChange(field, oldValue, newValue, type, order);
    }
}
