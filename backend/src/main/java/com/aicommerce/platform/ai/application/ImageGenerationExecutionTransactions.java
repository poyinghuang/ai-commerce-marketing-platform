package com.aicommerce.platform.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationBatch;
import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.GenerationJobStatus;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationBatchJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationJobJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationOutputJpaRepository;
import com.aicommerce.platform.asset.application.AssetCommandService;
import com.aicommerce.platform.asset.application.CreateAssetCommand;
import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageGenerationExecutionTransactions {
    private final GenerationJobJpaRepository jobs;
    private final GenerationBatchJpaRepository batches;
    private final GenerationOutputJpaRepository outputs;
    private final AiBudgetLedgerService budget;
    private final AssetCommandService assets;
    private final AuditWriter audit;
    private final Clock clock;

    public ImageGenerationExecutionTransactions(GenerationJobJpaRepository jobs,
            GenerationBatchJpaRepository batches, GenerationOutputJpaRepository outputs,
            AiBudgetLedgerService budget, AssetCommandService assets, AuditWriter audit, Clock clock) {
        this.jobs = jobs; this.batches = batches; this.outputs = outputs; this.budget = budget;
        this.assets = assets; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public GenerationOutput complete(TextGenerationExecutionTransactions.PreparedJob prepared,
            UUID sourceAssetUuid, UUID maskAssetUuid, String workflowKey, String workflowVersion,
            ImageGenerationProvider.ImageResult result, AssetBinaryStore.StoredBinary stored,
            ImagePreservationVerifier.Evidence evidence, String safetyJson, String metadataJson,
            AuditOperationContext context) {
        GenerationJob job = jobs.findByIdForUpdate(prepared.jobUuid())
                .orElseThrow(() -> new AiGenerationException("AI_GENERATION_JOB_NOT_FOUND", "Generation job not found"));
        if (job.getStatus() != GenerationJobStatus.RUNNING) {
            throw new AiGenerationException("AI_GENERATION_STATE_CONFLICT", "Generation job is not running");
        }
        var settlement = budget.settle(job.getGenerationJobUuid(), result.actualCost(), job.getProductUuid(), context);
        Asset generated = assets.createGenerated(job.getProductUuid(), new CreateAssetCommand(
                job.getCreativePlanUuid(), null, AssetType.IMAGE, "AI_BACKGROUND_COMPOSITE",
                stored.provider(), stored.providerFileId(), null, stored.mediaType(),
                "ai-background-" + job.getGenerationJobUuid() + extension(stored.mediaType()),
                stored.sizeBytes(), stored.checksumSha256(), Map.of(
                        "generationJobUuid", job.getGenerationJobUuid().toString(),
                        "preservationStatus", evidence.status())), context);
        GenerationOutput output = GenerationOutput.createImage(UUID.randomUUID(), job.getGenerationJobUuid(),
                job.getGenerationBatchUuid(), job.getProductUuid(), sourceAssetUuid, maskAssetUuid,
                generated.getAssetUuid(), workflowKey, workflowVersion, evidence.width(), evidence.height(),
                evidence.mediaType(), stored.sizeBytes(), evidence.sourceChecksum(), evidence.maskChecksum(),
                evidence.outputChecksum(), evidence.protectedPixelsChecksum(), evidence.status(),
                evidence.detailsJson(), result.modelLabel(), result.actualCost(), job.getCurrency(),
                safetyJson, metadataJson);
        outputs.saveAndFlush(output);
        job.succeed(Instant.now(clock));
        if (settlement.invariantViolation()) job.flagCostInvariantViolation();
        jobs.saveAndFlush(job);
        refreshBatch(job.getGenerationBatchUuid());
        audit.append(new AuditEvent(UUID.randomUUID(), context, AuditAction.CREATE, "AI_GENERATION_OUTPUT",
                output.getGenerationOutputUuid(), job.getProductUuid(), Instant.now(clock), List.of(
                        new AuditChange("generationJobUuid", null, job.getGenerationJobUuid().toString(), AuditValueType.UUID, 0),
                        new AuditChange("generatedAssetUuid", null, generated.getAssetUuid().toString(), AuditValueType.UUID, 1),
                        new AuditChange("preservationStatus", null, evidence.status(), AuditValueType.ENUM, 2))));
        audit.append(new AuditEvent(UUID.randomUUID(), context, AuditAction.UPDATE, "AI_GENERATION_JOB",
                job.getGenerationJobUuid(), job.getProductUuid(), Instant.now(clock), List.of(
                        new AuditChange("status", "RUNNING", "SUCCEEDED", AuditValueType.ENUM, 0))));
        return output;
    }

    private void refreshBatch(UUID batchUuid) {
        GenerationBatch batch = batches.findById(batchUuid).orElseThrow();
        List<GenerationJob> siblings = jobs.findByGenerationBatchUuidOrderByCreatedAt(batchUuid);
        int succeeded = (int) siblings.stream().filter(j -> j.getStatus() == GenerationJobStatus.SUCCEEDED).count();
        int failed = (int) siblings.stream().filter(j -> j.getStatus() == GenerationJobStatus.FAILED).count();
        int rejected = (int) siblings.stream().filter(j -> j.getStatus() == GenerationJobStatus.BUDGET_REJECTED).count();
        batch.refreshProgress(succeeded, failed, rejected);
        batches.save(batch);
    }

    private String extension(String mediaType) {
        return "image/png".equals(mediaType) ? ".png" : ".jpg";
    }
}
