package com.aicommerce.platform.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.GenerationJobStatus;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import com.aicommerce.platform.ai.domain.GenerationOutputReviewStatus;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.ai.domain.ReviewDecision;
import com.aicommerce.platform.ai.domain.ReviewDecisionType;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationJobJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationOutputJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.ReviewDecisionJpaRepository;
import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditActorType;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReviewDecisionService {

    private final GenerationOutputJpaRepository outputs;
    private final GenerationJobJpaRepository jobs;
    private final ReviewDecisionJpaRepository decisions;
    private final ProductJpaRepository products;
    private final AssetJpaRepository assets;
    private final AuditOperationContextFactory contexts;
    private final AuditWriter audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ReviewDecisionService(GenerationOutputJpaRepository outputs, GenerationJobJpaRepository jobs,
            ReviewDecisionJpaRepository decisions, ProductJpaRepository products, AssetJpaRepository assets,
            AuditOperationContextFactory contexts, AuditWriter audit, ObjectMapper mapper, Clock clock) {
        this.outputs = outputs;
        this.jobs = jobs;
        this.decisions = decisions;
        this.products = products;
        this.assets = assets;
        this.contexts = contexts;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewDetails details(GenerationOutput output) {
        return new ReviewDetails(output, decisions.findByGenerationOutputUuid(output.getGenerationOutputUuid()).orElse(null),
                approvalBlockers(output));
    }

    @Transactional
    public ReviewDetails approve(UUID outputUuid, long expectedVersion, String requestId) {
        return decide(outputUuid, expectedVersion, ReviewDecisionType.APPROVED, null, requestId);
    }

    @Transactional
    public ReviewDetails reject(UUID outputUuid, long expectedVersion, String reason, String requestId) {
        return decide(outputUuid, expectedVersion, ReviewDecisionType.REJECTED, reason, requestId);
    }

    private ReviewDetails decide(UUID outputUuid, long expectedVersion, ReviewDecisionType decision,
            String reason, String requestId) {
        AuditOperationContext context;
        try {
            context = contexts.forCurrentActor(requestId);
        } catch (RuntimeException exception) {
            throw new AiGenerationException("AUDIT_ACTOR_UNAVAILABLE", "A trusted review actor is unavailable", exception);
        }
        if (context.actor().type() == AuditActorType.SYSTEM) {
            throw new AiGenerationException("AUDIT_ACTOR_UNAVAILABLE", "A trusted human review actor is required");
        }
        GenerationOutput output = outputs.findByIdForUpdate(outputUuid)
                .orElseThrow(() -> new AiGenerationException("AI_OUTPUT_NOT_FOUND", "Generation output not found"));
        if (output.getVersion() != expectedVersion) {
            throw new AiGenerationException("AI_GENERATION_PRECONDITION_FAILED", "Generation output version is stale");
        }
        if (output.getReviewStatus() != GenerationOutputReviewStatus.PENDING_REVIEW) {
            throw new AiGenerationException("AI_OUTPUT_ALREADY_DECIDED", "Generation output is already decided");
        }
        List<String> blockers = approvalBlockers(output);
        if (decision == ReviewDecisionType.APPROVED && !blockers.isEmpty()) {
            throw new AiGenerationException("AI_REVIEW_BLOCKED", "Approval is blocked: " + String.join(", ", blockers));
        }
        Instant now = Instant.now(clock);
        ReviewDecision review;
        try {
            review = ReviewDecision.create(UUID.randomUUID(), outputUuid, decision, reason,
                    context.actor(), context.requestId(), output.getVersion(), now);
        } catch (IllegalArgumentException exception) {
            String code = decision == ReviewDecisionType.REJECTED
                    ? "AI_REVIEW_REASON_REQUIRED" : "AI_PROMPT_INPUT_INVALID";
            throw new AiGenerationException(code, exception.getMessage(), exception);
        }
        if (decision == ReviewDecisionType.APPROVED) output.approve(); else output.reject();
        decisions.save(review);
        outputs.save(output);
        List<AuditChange> decisionChanges = new ArrayList<>(List.of(
                change("generationOutputUuid", null, outputUuid.toString(), AuditValueType.UUID, 0),
                change("decision", null, decision.name(), AuditValueType.ENUM, 1),
                change("reviewerType", null, context.actor().type().name(), AuditValueType.ENUM, 2),
                change("reviewerId", null, context.actor().id(), AuditValueType.STRING, 3)));
        if (review.getReason() != null) {
            decisionChanges.add(change("reason", null, review.getReason(), AuditValueType.STRING, 4));
        }
        append(context, AuditAction.CREATE, "AI_REVIEW_DECISION", review.getReviewDecisionUuid(), output.getProductUuid(),
                decisionChanges);
        append(context, AuditAction.UPDATE, "AI_GENERATION_OUTPUT", outputUuid, output.getProductUuid(),
                List.of(change("reviewStatus", "PENDING_REVIEW", decision.name(), AuditValueType.ENUM, 0)));
        outputs.flush();
        decisions.flush();
        return new ReviewDetails(output, review, blockers);
    }

    private List<String> approvalBlockers(GenerationOutput output) {
        List<String> blockers = new ArrayList<>();
        JsonNode safety = mapper.readTree(output.getSafetyFindings());
        if (!safety.isArray() || !safety.isEmpty()) blockers.add("SAFETY_FINDINGS");
        GenerationJob job = jobs.findById(output.getGenerationJobUuid()).orElse(null);
        if (job == null || job.getStatus() != GenerationJobStatus.SUCCEEDED) blockers.add("JOB_NOT_SUCCEEDED");
        if (job != null && job.getFailureCode() != null) blockers.add(job.getFailureCode());
        if (products.findById(output.getProductUuid())
                .map(product -> product.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED).orElse(true)) {
            blockers.add("PRODUCT_ARCHIVED");
        }
        if (output.getGenerationType() == GenerationType.IMAGE) {
            if (!"PASSED".equals(output.getPreservationStatus())
                    || output.getProtectedPixelsSha256() == null || output.getOutputChecksumSha256() == null) {
                blockers.add("AI_PRODUCT_PIXELS_CHANGED");
            }
            assetBlocker(output.getSourceAssetUuid(), output.getProductUuid(), "SOURCE_ASSET_ARCHIVED", blockers);
            assetBlocker(output.getMaskAssetUuid(), output.getProductUuid(), "MASK_ASSET_ARCHIVED", blockers);
            assetBlocker(output.getGeneratedAssetUuid(), output.getProductUuid(), "GENERATED_ASSET_ARCHIVED", blockers);
        }
        return List.copyOf(blockers);
    }

    private void assetBlocker(UUID assetUuid, UUID productUuid, String code, List<String> blockers) {
        if (assetUuid == null) return;
        Asset asset = assets.findByAssetUuidAndProductUuid(assetUuid, productUuid).orElse(null);
        if (asset == null || asset.getLifecycleStatus() != LifecycleStatus.ACTIVE) blockers.add(code);
    }

    private void append(AuditOperationContext context, AuditAction action, String entityType,
            UUID entityUuid, UUID productUuid, List<AuditChange> changes) {
        audit.append(new AuditEvent(UUID.randomUUID(), context, action, entityType, entityUuid,
                productUuid, Instant.now(clock), changes));
    }

    private AuditChange change(String field, String oldValue, String newValue, AuditValueType type, int order) {
        return new AuditChange(field, oldValue, newValue, type, order);
    }

    public record ReviewDetails(GenerationOutput output, ReviewDecision decision, List<String> blockers) {
    }
}
