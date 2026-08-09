package com.aicommerce.platform.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationBatch;
import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.GenerationJobStatus;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationBatchJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationJobJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationOutputJpaRepository;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TextGenerationExecutionTransactions {

    private final GenerationJobJpaRepository jobs;
    private final GenerationBatchJpaRepository batches;
    private final GenerationOutputJpaRepository outputs;
    private final AiBudgetLedgerService budget;
    private final AuditWriter audit;
    private final Clock clock;

    public TextGenerationExecutionTransactions(GenerationJobJpaRepository jobs,
            GenerationBatchJpaRepository batches, GenerationOutputJpaRepository outputs,
            AiBudgetLedgerService budget, AuditWriter audit, Clock clock) {
        this.jobs = jobs;
        this.batches = batches;
        this.outputs = outputs;
        this.budget = budget;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PreparedJob prepare(UUID jobUuid, long expectedVersion, GenerationType expectedType,
            AuditOperationContext context) {
        GenerationJob job = jobs.findByIdForUpdate(jobUuid)
                .orElseThrow(() -> new AiGenerationException("AI_GENERATION_JOB_NOT_FOUND", "Generation job not found"));
        if (job.getVersion() != expectedVersion) {
            throw new AiGenerationException("AI_GENERATION_PRECONDITION_FAILED", "Generation job version is stale");
        }
        if (job.getGenerationType() != expectedType) {
            throw new AiGenerationException("AI_GENERATION_STATE_CONFLICT", "Generation job type is invalid");
        }
        if (jobs.findByGenerationBatchUuidOrderByCreatedAt(job.getGenerationBatchUuid()).stream()
                .anyMatch(candidate -> "AI_COST_INVARIANT_VIOLATION".equals(candidate.getFailureCode()))) {
            throw new AiGenerationException("AI_COST_INVARIANT_VIOLATION", "Batch execution is blocked by a cost invariant violation");
        }
        Instant now = Instant.now(clock);
        try {
            job.submit(now);
            job.start(now);
        } catch (IllegalStateException exception) {
            throw new AiGenerationException("AI_GENERATION_STATE_CONFLICT", exception.getMessage());
        }
        jobs.saveAndFlush(job);
        append(context, AuditAction.UPDATE, "AI_GENERATION_JOB", job.getGenerationJobUuid(), job.getProductUuid(),
                List.of(change("status", "CREATED", "RUNNING", AuditValueType.ENUM, 0),
                        change("attemptCount", "0", Integer.toString(job.getAttemptCount()), AuditValueType.INTEGER, 1)));
        return new PreparedJob(job.getGenerationJobUuid(), job.getGenerationBatchUuid(), job.getProductUuid(),
                job.getRenderedPrompt(), job.getModelKey(), job.getCurrency(), job.getInputSnapshot(),
                job.getCreativePlanUuid());
    }

    @Transactional
    public PreparedJob prepareImage(UUID jobUuid, long expectedVersion, AuditOperationContext context) {
        GenerationJob job = jobs.findByIdForUpdate(jobUuid)
                .orElseThrow(() -> new AiGenerationException("AI_GENERATION_JOB_NOT_FOUND", "Generation job not found"));
        if (job.getVersion() != expectedVersion) {
            throw new AiGenerationException("AI_GENERATION_PRECONDITION_FAILED", "Generation job version is stale");
        }
        if (job.getGenerationType() != GenerationType.IMAGE) {
            throw new AiGenerationException("AI_GENERATION_STATE_CONFLICT", "Generation job type is invalid");
        }
        if (jobs.findByGenerationBatchUuidOrderByCreatedAt(job.getGenerationBatchUuid()).stream()
                .anyMatch(candidate -> "AI_COST_INVARIANT_VIOLATION".equals(candidate.getFailureCode()))) {
            throw new AiGenerationException("AI_COST_INVARIANT_VIOLATION",
                    "Batch execution is blocked by a cost invariant violation");
        }
        if (job.getStatus() == GenerationJobStatus.CREATED) {
            Instant now = Instant.now(clock);
            job.submit(now);
            job.start(now);
            jobs.saveAndFlush(job);
            append(context, AuditAction.UPDATE, "AI_GENERATION_JOB", job.getGenerationJobUuid(), job.getProductUuid(),
                    List.of(change("status", "CREATED", "RUNNING", AuditValueType.ENUM, 0),
                            change("attemptCount", "0", Integer.toString(job.getAttemptCount()), AuditValueType.INTEGER, 1)));
        } else if (job.getStatus() != GenerationJobStatus.RUNNING) {
            throw new AiGenerationException("AI_GENERATION_STATE_CONFLICT", "Image generation job cannot be resumed");
        }
        return prepared(job);
    }

    private PreparedJob prepared(GenerationJob job) {
        return new PreparedJob(job.getGenerationJobUuid(), job.getGenerationBatchUuid(), job.getProductUuid(),
                job.getRenderedPrompt(), job.getModelKey(), job.getCurrency(), job.getInputSnapshot(),
                job.getCreativePlanUuid());
    }

    @Transactional
    public GenerationOutput complete(PreparedJob prepared, TextGenerationProvider.TextResult result,
            String safetyJson, String metadataJson, AuditOperationContext context) {
        GenerationJob job = jobs.findByIdForUpdate(prepared.jobUuid())
                .orElseThrow(() -> new AiGenerationException("AI_GENERATION_JOB_NOT_FOUND", "Generation job not found"));
        if (job.getStatus() != GenerationJobStatus.RUNNING) {
            throw new AiGenerationException("AI_GENERATION_STATE_CONFLICT", "Generation job is not running");
        }
        BudgetSettlementResult settlement = budget.settle(job.getGenerationJobUuid(), result.actualCost(),
                job.getProductUuid(), context);
        GenerationOutput output;
        try {
            output = GenerationOutput.createText(UUID.randomUUID(), job.getGenerationJobUuid(),
                    job.getGenerationBatchUuid(), job.getProductUuid(), result.text(), result.modelLabel(),
                    result.inputUnits(), result.outputUnits(), result.actualCost(), job.getCurrency(),
                    safetyJson, metadataJson);
        } catch (IllegalArgumentException exception) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", exception.getMessage(), exception);
        }
        outputs.save(output);
        job.succeed(Instant.now(clock));
        if (settlement.invariantViolation()) job.flagCostInvariantViolation();
        jobs.save(job);
        refreshBatch(job.getGenerationBatchUuid());
        outputs.flush();
        jobs.flush();
        append(context, AuditAction.CREATE, "AI_GENERATION_OUTPUT", output.getGenerationOutputUuid(),
                job.getProductUuid(), List.of(
                        change("generationJobUuid", null, job.getGenerationJobUuid().toString(), AuditValueType.UUID, 0),
                        change("reviewStatus", null, "PENDING_REVIEW", AuditValueType.ENUM, 1),
                        change("actualCost", null, result.actualCost().toPlainString(), AuditValueType.DECIMAL, 2)));
        append(context, AuditAction.UPDATE, "AI_GENERATION_JOB", job.getGenerationJobUuid(), job.getProductUuid(),
                List.of(change("status", "RUNNING", "SUCCEEDED", AuditValueType.ENUM, 0)));
        return output;
    }

    @Transactional
    public void fail(PreparedJob prepared, String code, String message, AuditOperationContext context) {
        GenerationJob job = jobs.findByIdForUpdate(prepared.jobUuid())
                .orElseThrow(() -> new AiGenerationException("AI_GENERATION_JOB_NOT_FOUND", "Generation job not found"));
        if (job.getStatus() != GenerationJobStatus.RUNNING) return;
        budget.release(job.getGenerationJobUuid(), job.getProductUuid(), context);
        job.fail(Instant.now(clock), code, bounded(message, 1000));
        jobs.save(job);
        refreshBatch(job.getGenerationBatchUuid());
        jobs.flush();
        append(context, AuditAction.UPDATE, "AI_GENERATION_JOB", job.getGenerationJobUuid(), job.getProductUuid(),
                List.of(change("status", "RUNNING", "FAILED", AuditValueType.ENUM, 0),
                        change("failureCode", null, code, AuditValueType.STRING, 1)));
    }

    private void refreshBatch(UUID batchUuid) {
        GenerationBatch batch = batches.findById(batchUuid)
                .orElseThrow(() -> new IllegalStateException("Generation batch not found"));
        List<GenerationJob> siblings = jobs.findByGenerationBatchUuidOrderByCreatedAt(batchUuid);
        int succeeded = (int) siblings.stream().filter(j -> j.getStatus() == GenerationJobStatus.SUCCEEDED).count();
        int failed = (int) siblings.stream().filter(j -> j.getStatus() == GenerationJobStatus.FAILED).count();
        int rejected = (int) siblings.stream().filter(j -> j.getStatus() == GenerationJobStatus.BUDGET_REJECTED).count();
        batch.refreshProgress(succeeded, failed, rejected);
        batches.save(batch);
    }

    private void append(AuditOperationContext context, AuditAction action, String entityType,
            UUID entityUuid, UUID productUuid, List<AuditChange> changes) {
        audit.append(new AuditEvent(UUID.randomUUID(), context, action, entityType, entityUuid,
                productUuid, Instant.now(clock), changes));
    }

    private AuditChange change(String field, String oldValue, String newValue, AuditValueType type, int order) {
        return new AuditChange(field, oldValue, newValue, type, order);
    }

    private String bounded(String value, int max) {
        String normalized = value == null || value.isBlank() ? "Provider execution failed" : value.strip();
        return normalized.length() <= max ? normalized : normalized.substring(0, max - 11) + "[TRUNCATED]";
    }

    public record PreparedJob(UUID jobUuid, UUID batchUuid, UUID productUuid,
            String prompt, String modelKey, String currency, String inputSnapshot, UUID creativePlanUuid) {
    }
}
