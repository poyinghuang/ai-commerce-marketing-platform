package com.aicommerce.platform.ai.application;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationBatch;
import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.ai.domain.PromptTemplate;
import com.aicommerce.platform.ai.domain.PromptTemplateVersion;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationBatchJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationJobJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationOutputJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.PromptTemplateJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.PromptTemplateVersionJpaRepository;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tools.jackson.databind.ObjectMapper;

@Service
public class TextGenerationService {

    private static final Map<String, ModelSelection> MODEL_PROFILES = Map.of(
            "STANDARD", new ModelSelection("stub", "stub-text"),
            "LOW_COST", new ModelSelection("stub", "stub-text-low"),
            "PARTIAL_FAILURE_FIXTURE", new ModelSelection("stub", "stub-text-partial"),
            "COST_INVARIANT_FIXTURE", new ModelSelection("stub", "stub-text-cost-invariant"),
            "OVER_JOB_BUDGET_FIXTURE", new ModelSelection("stub", "stub-text-over-job"));

    private static final List<String> FORBIDDEN_METADATA_KEYS = List.of(
            "credential", "secret", "token", "url", "apikey", "password", "authorization",
            "cookie", "header", "payload", "requestbody", "responsebody");

    private final ProductJpaRepository products;
    private final CreativePlanJpaRepository plans;
    private final ProductKnowledgeJpaRepository knowledge;
    private final PromptTemplateJpaRepository templates;
    private final PromptTemplateVersionJpaRepository versions;
    private final GenerationBatchJpaRepository batches;
    private final GenerationJobJpaRepository jobs;
    private final GenerationOutputJpaRepository outputs;
    private final TextPromptRenderer renderer;
    private final AiGenerationFoundationService foundation;
    private final TextGenerationExecutionTransactions execution;
    private final TextGenerationProvider provider;
    private final AuditOperationContextFactory contexts;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public TextGenerationService(ProductJpaRepository products, CreativePlanJpaRepository plans,
            ProductKnowledgeJpaRepository knowledge, PromptTemplateJpaRepository templates,
            PromptTemplateVersionJpaRepository versions, GenerationBatchJpaRepository batches,
            GenerationJobJpaRepository jobs, GenerationOutputJpaRepository outputs,
            TextPromptRenderer renderer, AiGenerationFoundationService foundation,
            TextGenerationExecutionTransactions execution, TextGenerationProvider provider,
            AuditOperationContextFactory contexts, ObjectMapper objectMapper, Environment environment) {
        this.products = products;
        this.plans = plans;
        this.knowledge = knowledge;
        this.templates = templates;
        this.versions = versions;
        this.batches = batches;
        this.jobs = jobs;
        this.outputs = outputs;
        this.renderer = renderer;
        this.foundation = foundation;
        this.execution = execution;
        this.provider = provider;
        this.contexts = contexts;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Transactional
    public GenerationFoundationResult createBatch(CreateTextGenerationBatchCommand command, String requestId) {
        int count = command.variationCount() == 0 ? 3 : command.variationCount();
        if (count < 1 || count > 3) {
            throw new AiGenerationException("AI_PROMPT_INPUT_INVALID", "variationCount must be between 1 and 3");
        }
        ModelSelection model = MODEL_PROFILES.get(normalize(command.modelProfile()));
        if (model == null) {
            throw new AiGenerationException("AI_PROMPT_INPUT_INVALID", "modelProfile is not allowlisted");
        }
        Product product = products.findById(command.productUuid())
                .orElseThrow(() -> new AiGenerationException("PRODUCT_NOT_FOUND", "Product not found"));
        if (product.getLifecycleStatus() != ProductLifecycleStatus.ACTIVE) {
            throw new AiGenerationException("PRODUCT_ARCHIVED", "Archived Product cannot generate content");
        }
        if (command.creativePlanUuid() == null) {
            throw new AiGenerationException("AI_PROMPT_INPUT_INVALID", "creativePlanUuid is required");
        }
        CreativePlan plan = plans.findByCreativePlanUuidAndProductUuid(command.creativePlanUuid(), product.getProductUuid())
                .orElseThrow(() -> new AiGenerationException("AI_PROMPT_INPUT_INVALID", "Creative Plan does not belong to Product"));
        if (plan.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new AiGenerationException("AI_GENERATION_STATE_CONFLICT", "Archived Creative Plan cannot generate content");
        }
        PromptTemplate template = templates.findByTemplateKey(command.templateKey())
                .orElseThrow(() -> new AiGenerationException("AI_PROMPT_TEMPLATE_NOT_FOUND", "Prompt template not found"));
        if (template.getGenerationType() != GenerationType.TEXT || template.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new AiGenerationException("AI_PROMPT_TEMPLATE_NOT_FOUND", "Active text prompt template not found");
        }
        PromptTemplateVersion version = versions.findByPromptTemplateUuidOrderByVersionNumberDesc(
                template.getPromptTemplateUuid()).stream().findFirst()
                .orElseThrow(() -> new AiGenerationException("AI_PROMPT_TEMPLATE_NOT_FOUND", "Prompt template has no version"));
        List<com.aicommerce.platform.knowledge.domain.ProductKnowledge> activeKnowledge =
                knowledge.findForAggregate(product.getProductUuid(), false);
        List<GenerationJobFoundationRequest> requests = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> {
                    var rendered = renderer.render(version, product, activeKnowledge, plan, index);
                    return new GenerationJobFoundationRequest(version.getPromptTemplateVersionUuid(),
                            GenerationType.TEXT, model.providerKey(), model.modelKey(), rendered.prompt(),
                            version.getNegativePrompt(), rendered.snapshot());
                }).toList();
        try {
            return foundation.create(new CreateGenerationFoundationCommand(product.getProductUuid(),
                    plan.getCreativePlanUuid(), requests), contexts.forCurrentActor(requestId));
        } catch (AiGenerationException exception) {
            throw exception;
        } catch (AiProviderException exception) {
            throw new AiGenerationException(exception.code(), exception.getMessage(), exception);
        } catch (AiBudgetExceededException exception) {
            throw new AiGenerationException(exception.code(), exception.getMessage(), exception);
        } catch (AiFoundationValidationException exception) {
            throw new AiGenerationException("AI_PROMPT_INPUT_INVALID", exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new AiGenerationException("AUDIT_ACTOR_UNAVAILABLE", "A trusted audit actor is unavailable", exception);
        }
    }

    public GenerationOutput execute(UUID jobUuid, long expectedVersion, String requestId) {
        AuditOperationContext context;
        try {
            context = contexts.forCurrentActor(requestId);
        } catch (IllegalStateException exception) {
            throw new AiGenerationException("AUDIT_ACTOR_UNAVAILABLE", "A trusted audit actor is unavailable", exception);
        }
        TextGenerationExecutionTransactions.PreparedJob prepared = execution.prepare(
                jobUuid, expectedVersion, GenerationType.TEXT, context);
        try {
            TextGenerationProvider.TextResult result = provider.generate(new TextGenerationProvider.TextRequest(
                    prepared.jobUuid(), prepared.prompt(), prepared.modelKey(), 16000, Duration.ofSeconds(30)));
            ValidatedResult validated = validate(result);
            return execution.complete(prepared, result, validated.safetyJson(), validated.metadataJson(), context);
        } catch (AiProviderException exception) {
            execution.fail(prepared, exception.code(), exception.getMessage(), context);
            throw new AiGenerationException(exception.code(), "Text generation provider failed", exception);
        } catch (AiGenerationException exception) {
            execution.fail(prepared, exception.code(), exception.getMessage(), context);
            throw exception;
        } catch (RuntimeException exception) {
            execution.fail(prepared, "AI_PROVIDER_UNAVAILABLE", "Text generation provider unavailable", context);
            throw new AiGenerationException("AI_PROVIDER_UNAVAILABLE", "Text generation provider unavailable", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<GenerationBatch> listBatches(UUID productUuid) {
        if (!products.existsById(productUuid)) throw new AiGenerationException("PRODUCT_NOT_FOUND", "Product not found");
        return batches.findByProductUuidOrderByCreatedAtDesc(productUuid);
    }

    @Transactional(readOnly = true)
    public GenerationBatch getBatch(UUID batchUuid) {
        return batches.findById(batchUuid)
                .orElseThrow(() -> new AiGenerationException("AI_GENERATION_BATCH_NOT_FOUND", "Generation batch not found"));
    }

    @Transactional(readOnly = true)
    public List<GenerationJob> getBatchJobs(UUID batchUuid) {
        getBatch(batchUuid);
        return jobs.findByGenerationBatchUuidOrderByCreatedAt(batchUuid);
    }

    @Transactional(readOnly = true)
    public GenerationJob getJob(UUID jobUuid) {
        return jobs.findById(jobUuid)
                .orElseThrow(() -> new AiGenerationException("AI_GENERATION_JOB_NOT_FOUND", "Generation job not found"));
    }

    @Transactional(readOnly = true)
    public GenerationOutput getOutput(UUID outputUuid) {
        return outputs.findById(outputUuid)
                .orElseThrow(() -> new AiGenerationException("AI_OUTPUT_NOT_FOUND", "Generation output not found"));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<GenerationOutput> getJobOutput(UUID jobUuid) {
        return outputs.findByGenerationJobUuid(jobUuid);
    }

    @Transactional(readOnly = true)
    public List<String> textTemplateKeys() {
        return templates.findAll().stream()
                .filter(template -> template.getGenerationType() == GenerationType.TEXT)
                .filter(template -> template.getLifecycleStatus() == LifecycleStatus.ACTIVE)
                .map(PromptTemplate::getTemplateKey)
                .sorted()
                .toList();
    }

    public List<String> availableModelProfiles() {
        if (!environment.acceptsProfiles(Profiles.of("(local | test) & !production"))) return List.of();
        return List.of("STANDARD", "LOW_COST", "PARTIAL_FAILURE_FIXTURE", "COST_INVARIANT_FIXTURE",
                "OVER_JOB_BUDGET_FIXTURE");
    }

    private ValidatedResult validate(TextGenerationProvider.TextResult result) {
        if (result == null || result.text() == null || result.text().isBlank() || result.text().length() > 16000
                || result.modelLabel() == null || result.modelLabel().isBlank() || result.modelLabel().length() > 128
                || result.inputUnits() < 0 || result.outputUnits() < 0 || result.actualCost() == null
                || result.actualCost().signum() < 0) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider returned an invalid text result");
        }
        List<String> findings = result.safetyFindings() == null ? List.of() : result.safetyFindings();
        if (findings.size() > 50 || findings.stream().anyMatch(value -> value == null || value.length() > 256)) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider safety findings are invalid");
        }
        Map<String, String> metadata = result.metadata() == null ? Map.of() : result.metadata();
        if (metadata.size() > 32) throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider metadata is too large");
        for (var entry : metadata.entrySet()) {
            String normalized = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            if (entry.getKey().length() > 64 || entry.getValue() == null || entry.getValue().length() > 256
                    || FORBIDDEN_METADATA_KEYS.stream().anyMatch(normalized::contains)
                    || entry.getValue().matches("(?i).*https?://.*")) {
                throw new AiGenerationException("AI_DATA_POLICY_VIOLATION", "Provider metadata violates data policy");
            }
        }
        String safetyJson = objectMapper.writeValueAsString(findings);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        if (safetyJson.length() > 8192 || metadataJson.length() > 8192) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider metadata exceeds persistence limits");
        }
        return new ValidatedResult(safetyJson, metadataJson);
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private record ModelSelection(String providerKey, String modelKey) {
    }

    private record ValidatedResult(String safetyJson, String metadataJson) {
    }
}
