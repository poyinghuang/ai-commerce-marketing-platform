package com.aicommerce.platform.ai.application;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.*;
import com.aicommerce.platform.ai.infrastructure.persistence.*;
import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImageGenerationService {
    public static final String WORKFLOW_KEY = "background-composite-v1";
    public static final String WORKFLOW_VERSION = "1";
    private final ProductJpaRepository products;
    private final CreativePlanJpaRepository plans;
    private final AssetJpaRepository assets;
    private final PromptTemplateJpaRepository templates;
    private final PromptTemplateVersionJpaRepository versions;
    private final AiGenerationFoundationService foundation;
    private final TextGenerationExecutionTransactions execution;
    private final ImageGenerationExecutionTransactions imageExecution;
    private final ImageGenerationProvider provider;
    private final AssetBinaryStore binaryStore;
    private final ImagePreservationVerifier verifier;
    private final ImagePromptRenderer renderer;
    private final AuditOperationContextFactory contexts;
    private final ObjectMapper mapper;
    private final Environment environment;

    public ImageGenerationService(ProductJpaRepository products, CreativePlanJpaRepository plans,
            AssetJpaRepository assets, PromptTemplateJpaRepository templates,
            PromptTemplateVersionJpaRepository versions, AiGenerationFoundationService foundation,
            TextGenerationExecutionTransactions execution, ImageGenerationExecutionTransactions imageExecution,
            ImageGenerationProvider provider, AssetBinaryStore binaryStore, ImagePreservationVerifier verifier,
            ImagePromptRenderer renderer, AuditOperationContextFactory contexts, ObjectMapper mapper,
            Environment environment) {
        this.products = products; this.plans = plans; this.assets = assets; this.templates = templates;
        this.versions = versions; this.foundation = foundation; this.execution = execution;
        this.imageExecution = imageExecution; this.provider = provider; this.binaryStore = binaryStore;
        this.verifier = verifier; this.renderer = renderer; this.contexts = contexts; this.mapper = mapper;
        this.environment = environment;
    }

    @Transactional
    public GenerationFoundationResult createBatch(CreateImageGenerationBatchCommand command, String requestId) {
        if (!WORKFLOW_KEY.equals(command.workflowKey()) || !"STANDARD_IMAGE".equals(command.modelProfile())) {
            throw new AiGenerationException("AI_PROMPT_INPUT_INVALID", "Image workflow or model profile is not allowlisted");
        }
        Product product = products.findById(command.productUuid())
                .orElseThrow(() -> new AiGenerationException("PRODUCT_NOT_FOUND", "Product not found"));
        if (product.getLifecycleStatus() != ProductLifecycleStatus.ACTIVE) throw conflict("PRODUCT_ARCHIVED");
        CreativePlan plan = plans.findByCreativePlanUuidAndProductUuid(command.creativePlanUuid(), command.productUuid())
                .orElseThrow(() -> new AiGenerationException("AI_PROMPT_INPUT_INVALID", "Creative Plan does not belong to Product"));
        if (plan.getLifecycleStatus() != LifecycleStatus.ACTIVE) throw conflict("AI_GENERATION_STATE_CONFLICT");
        if (command.sourceAssetUuid() == null) {
            throw new AiGenerationException("AI_SOURCE_ASSET_REQUIRED", "Source Asset is required");
        }
        Asset source = requireSource(command.productUuid(), command.sourceAssetUuid());
        if (command.maskAssetUuid() != null) requireSource(command.productUuid(), command.maskAssetUuid());
        PromptTemplate template = templates.findByTemplateKey(command.templateKey())
                .orElseThrow(() -> new AiGenerationException("AI_PROMPT_TEMPLATE_NOT_FOUND", "Prompt template not found"));
        if (template.getGenerationType() != GenerationType.IMAGE || template.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new AiGenerationException("AI_PROMPT_TEMPLATE_NOT_FOUND", "Active image prompt template not found");
        }
        PromptTemplateVersion version = versions.findByPromptTemplateUuidOrderByVersionNumberDesc(
                template.getPromptTemplateUuid()).stream().findFirst()
                .orElseThrow(() -> new AiGenerationException("AI_PROMPT_TEMPLATE_NOT_FOUND", "Prompt template version not found"));
        var rendered = renderer.render(version, product, plan, source.getAssetUuid(), command.maskAssetUuid(), WORKFLOW_KEY);
        try {
            return foundation.create(new CreateGenerationFoundationCommand(product.getProductUuid(), plan.getCreativePlanUuid(),
                    List.of(new GenerationJobFoundationRequest(version.getPromptTemplateVersionUuid(), GenerationType.IMAGE,
                            "stub", "stub-image", rendered.prompt(), version.getNegativePrompt(), rendered.snapshot()))),
                    contexts.forCurrentActor(requestId));
        } catch (AiGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiGenerationException("AI_PROMPT_INPUT_INVALID", exception.getMessage(), exception);
        }
    }

    public GenerationOutput execute(UUID jobUuid, long expectedVersion, String requestId) {
        com.aicommerce.platform.audit.domain.AuditOperationContext context;
        try {
            context = contexts.forCurrentActor(requestId);
        } catch (IllegalStateException exception) {
            throw new AiGenerationException("AUDIT_ACTOR_UNAVAILABLE", "A trusted audit actor is unavailable", exception);
        }
        var prepared = execution.prepareImage(jobUuid, expectedVersion, context);
        PendingCompletion pending;
        try {
            JsonNode snapshot = mapper.readTree(prepared.inputSnapshot());
            UUID sourceUuid = UUID.fromString(snapshot.get("sourceAssetUuid").asText());
            UUID maskUuid = snapshot.has("maskAssetUuid") ? UUID.fromString(snapshot.get("maskAssetUuid").asText()) : null;
            String workflowKey = snapshot.get("workflowKey").asText();
            Asset source = requireSource(prepared.productUuid(), sourceUuid);
            Asset mask = maskUuid == null ? null : requireSource(prepared.productUuid(), maskUuid);
            var sourceBinary = binaryStore.read(reference(source));
            var maskBinary = mask == null ? null : binaryStore.read(reference(mask));
            var sourceInfo = verifier.inspectSource(sourceBinary.bytes(), source.getMediaType(),
                    source.getSizeBytes(), source.getChecksumSha256());
            if (mask != null) {
                verifier.inspectSource(maskBinary.bytes(), mask.getMediaType(), mask.getSizeBytes(),
                        mask.getChecksumSha256());
            }
            var request = new ImageGenerationProvider.ImageRequest(prepared.jobUuid(), workflowKey, WORKFLOW_VERSION,
                    Map.of("prompt", prepared.prompt()), sourceBinary.handle(),
                    maskBinary == null ? null : maskBinary.handle(), sourceBinary.bytes(),
                    maskBinary == null ? null : maskBinary.bytes(), sourceInfo.width(), sourceInfo.height(),
                    "png", Duration.ofSeconds(30));
            var submission = provider.submit(request);
            var result = validate(provider.await(request, submission));
            var evidence = verifier.verify(sourceBinary.bytes(), maskBinary == null ? null : maskBinary.bytes(), result.bytes());
            var stored = binaryStore.writeGenerated(prepared.jobUuid(), prepared.productUuid(), result.bytes(), evidence.mediaType());
            if (!stored.mediaType().equals(evidence.mediaType()) || stored.sizeBytes() != result.bytes().length
                    || !stored.checksumSha256().equals(evidence.outputChecksum())) {
                throw new AiGenerationException("AI_OUTPUT_INVALID", "Stored generated binary evidence is inconsistent");
            }
            var serialized = serializeMetadata(result);
            pending = new PendingCompletion(sourceUuid, maskUuid, workflowKey, result, stored, evidence, serialized);
        } catch (AiProviderException exception) {
            execution.fail(prepared, exception.code(), exception.getMessage(), context);
            throw new AiGenerationException(exception.code(), "Image generation provider failed", exception);
        } catch (AiGenerationException exception) {
            execution.fail(prepared, exception.code(), exception.getMessage(), context);
            throw exception;
        } catch (RuntimeException exception) {
            execution.fail(prepared, "AI_PROVIDER_UNAVAILABLE", "Image generation failed", context);
            throw new AiGenerationException("AI_PROVIDER_UNAVAILABLE", "Image generation failed", exception);
        }
        // Completion is intentionally outside the provider-failure block. If the database transaction
        // rolls back after the idempotent binary write, the Job stays RUNNING and the same Job UUID can resume.
        return imageExecution.complete(prepared, pending.sourceUuid(), pending.maskUuid(), pending.workflowKey(),
                WORKFLOW_VERSION, pending.result(), pending.stored(), pending.evidence(),
                pending.serialized().safetyJson(), pending.serialized().metadataJson(), context);
    }

    public List<String> imageTemplateKeys() {
        if (!isAvailable()) return List.of();
        return templates.findAll().stream().filter(t -> t.getGenerationType() == GenerationType.IMAGE)
                .filter(t -> t.getLifecycleStatus() == LifecycleStatus.ACTIVE).map(PromptTemplate::getTemplateKey).sorted().toList();
    }

    public List<String> availableModelProfiles() {
        return isAvailable() ? List.of("STANDARD_IMAGE") : List.of();
    }

    public List<String> availableWorkflowKeys() {
        return isAvailable() ? List.of(WORKFLOW_KEY) : List.of();
    }

    private boolean isAvailable() {
        return environment.acceptsProfiles(Profiles.of("(local | test | comfyui) & !production"));
    }

    private ImageGenerationProvider.ImageResult validate(ImageGenerationProvider.ImageResult result) {
        if (result == null || result.bytes() == null || result.bytes().length == 0
                || result.bytes().length > 16_777_216 || result.modelLabel() == null
                || result.modelLabel().isBlank() || result.modelLabel().length() > 128
                || result.actualCost() == null || result.actualCost().signum() < 0) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider returned an invalid image result");
        }
        return result;
    }

    private SerializedMetadata serializeMetadata(ImageGenerationProvider.ImageResult result) {
        List<String> findings = result.safetyFindings() == null ? List.of() : result.safetyFindings();
        if (findings.size() > 50 || findings.stream().anyMatch(value -> value == null || value.length() > 256)) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider safety findings are invalid");
        }
        Map<String, String> metadata = result.metadata() == null ? Map.of() : result.metadata();
        if (metadata.size() > 32) throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider metadata is too large");
        List<String> forbidden = List.of("credential", "secret", "token", "url", "apikey", "password",
                "authorization", "cookie", "header", "payload", "requestbody", "responsebody");
        for (var entry : metadata.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            if (key.length() > 64 || entry.getValue() == null || entry.getValue().length() > 256
                    || forbidden.stream().anyMatch(normalized::contains)
                    || entry.getValue().matches("(?i).*https?://.*")) {
                throw new AiGenerationException("AI_DATA_POLICY_VIOLATION", "Provider metadata violates data policy");
            }
        }
        String safetyJson = mapper.writeValueAsString(findings);
        String metadataJson = mapper.writeValueAsString(metadata);
        if (safetyJson.length() > 8192 || metadataJson.length() > 8192) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Provider metadata exceeds persistence limits");
        }
        return new SerializedMetadata(safetyJson, metadataJson);
    }

    private Asset requireSource(UUID productUuid, UUID assetUuid) {
        Asset asset = assets.findByAssetUuidAndProductUuid(assetUuid, productUuid)
                .orElseThrow(() -> new AiGenerationException("AI_SOURCE_ASSET_INVALID", "Source Asset not found"));
        if (asset.getLifecycleStatus() != LifecycleStatus.ACTIVE || asset.getAssetType() != AssetType.IMAGE
                || asset.getProviderFileId() == null || asset.getMediaType() == null
                || asset.getSizeBytes() == null || asset.getChecksumSha256() == null) {
            throw new AiGenerationException("AI_SOURCE_ASSET_INVALID", "Source Asset is not eligible");
        }
        return asset;
    }

    private AssetBinaryStore.SourceReference reference(Asset asset) {
        return new AssetBinaryStore.SourceReference(asset.getAssetUuid(), asset.getProductUuid(),
                asset.getProviderFileId(), asset.getMediaType(), asset.getSizeBytes(), asset.getChecksumSha256());
    }

    private AiGenerationException conflict(String code) {
        return new AiGenerationException(code, "Resource is not active");
    }

    private record SerializedMetadata(String safetyJson, String metadataJson) {
    }

    private record PendingCompletion(UUID sourceUuid, UUID maskUuid, String workflowKey,
            ImageGenerationProvider.ImageResult result, AssetBinaryStore.StoredBinary stored,
            ImagePreservationVerifier.Evidence evidence, SerializedMetadata serialized) {
    }
}
