package com.aicommerce.platform.ai.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationBatch;
import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.PromptTemplate;
import com.aicommerce.platform.ai.domain.PromptTemplateVersion;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationBatchJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationJobJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.PromptTemplateJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.PromptTemplateVersionJpaRepository;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiGenerationFoundationService {

    private static final int MAXIMUM_JOBS_PER_BATCH = 20;

    private final ProductJpaRepository productRepository;
    private final CreativePlanJpaRepository creativePlanRepository;
    private final PromptTemplateJpaRepository templateRepository;
    private final PromptTemplateVersionJpaRepository versionRepository;
    private final GenerationBatchJpaRepository batchRepository;
    private final GenerationJobJpaRepository jobRepository;
    private final AiBudgetLedgerService budgetLedgerService;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiGenerationFoundationService(ProductJpaRepository productRepository,
            CreativePlanJpaRepository creativePlanRepository, PromptTemplateJpaRepository templateRepository,
            PromptTemplateVersionJpaRepository versionRepository, GenerationBatchJpaRepository batchRepository,
            GenerationJobJpaRepository jobRepository, AiBudgetLedgerService budgetLedgerService,
            AuditWriter auditWriter, ObjectMapper objectMapper, Clock clock) {
        this.productRepository = productRepository;
        this.creativePlanRepository = creativePlanRepository;
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.batchRepository = batchRepository;
        this.jobRepository = jobRepository;
        this.budgetLedgerService = budgetLedgerService;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public GenerationFoundationResult create(CreateGenerationFoundationCommand command,
            AuditOperationContext context) {
        if (command.jobs() == null || command.jobs().isEmpty()
                || command.jobs().size() > MAXIMUM_JOBS_PER_BATCH) {
            throw new AiFoundationValidationException("jobs must contain between 1 and 20 requests");
        }
        Product product = productRepository.findForAssetMutation(command.productUuid())
                .orElseThrow(() -> new AiFoundationValidationException("Product does not exist"));
        if (product.getLifecycleStatus() != ProductLifecycleStatus.ACTIVE) {
            throw new AiFoundationValidationException("Archived Product cannot generate AI content");
        }
        CreativePlan plan = null;
        if (command.creativePlanUuid() != null) {
            plan = creativePlanRepository.findForAssetMutation(command.creativePlanUuid(), command.productUuid())
                    .orElseThrow(() -> new AiFoundationValidationException(
                            "Creative Plan does not belong to the Product"));
            if (plan.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
                throw new AiFoundationValidationException("Archived Creative Plan cannot generate AI content");
            }
        }

        List<JobDraft> drafts = command.jobs().stream().map(this::validateJob).toList();
        BigDecimal estimatedTotal = drafts.stream().map(draft -> draft.request().estimatedCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reservedTotal = drafts.stream().map(draft -> draft.request().worstCaseCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        UUID batchUuid = UUID.randomUUID();
        GenerationBatch batch;
        try {
            batch = GenerationBatch.create(batchUuid, product.getProductUuid(),
                    plan == null ? null : plan.getCreativePlanUuid(), command.currency(), estimatedTotal,
                    reservedTotal, drafts.size(), context.actor().id());
        } catch (IllegalArgumentException exception) {
            throw new AiFoundationValidationException(exception.getMessage(), exception);
        }
        batch = batchRepository.saveAndFlush(batch);

        List<GenerationJob> jobs = new ArrayList<>();
        for (JobDraft draft : drafts) {
            var request = draft.request();
            try {
                jobs.add(GenerationJob.create(UUID.randomUUID(), batchUuid, product.getProductUuid(),
                        plan == null ? null : plan.getCreativePlanUuid(), request.promptTemplateVersionUuid(),
                        request.generationType(), request.providerKey(), request.modelKey(),
                        request.renderedPrompt(), request.negativePrompt(), canonicalSnapshot(request.inputSnapshot()),
                        request.estimatedCost(), request.worstCaseCost(), command.currency()));
            } catch (IllegalArgumentException exception) {
                throw new AiFoundationValidationException(exception.getMessage(), exception);
            }
        }
        jobs = jobRepository.saveAllAndFlush(jobs);
        appendCreateAudits(batch, jobs, context);

        try {
            budgetLedgerService.reserve(jobs.stream()
                    .map(job -> new BudgetReservation(job.getGenerationJobUuid(), job.getReservedCost()))
                    .toList(), command.currency(), product.getProductUuid(), context);
            return new GenerationFoundationResult(batch, List.copyOf(jobs), true, null);
        } catch (AiBudgetExceededException exception) {
            batch.rejectBudget();
            Instant rejectedAt = Instant.now(clock);
            jobs.forEach(job -> job.rejectBudget(rejectedAt));
            batchRepository.saveAndFlush(batch);
            jobRepository.saveAllAndFlush(jobs);
            auditWriter.append(event(context, AuditAction.UPDATE, "AI_GENERATION_BATCH", batchUuid,
                    product.getProductUuid(), List.of(
                            change("status", "CREATED", "BUDGET_REJECTED", AuditValueType.ENUM, 0),
                            change("rejectedJobCount", "0", Integer.toString(jobs.size()),
                                    AuditValueType.INTEGER, 1))));
            for (GenerationJob job : jobs) {
                auditWriter.append(event(context, AuditAction.UPDATE, "AI_GENERATION_JOB",
                        job.getGenerationJobUuid(), product.getProductUuid(), List.of(
                                change("status", "CREATED", "BUDGET_REJECTED", AuditValueType.ENUM, 0),
                                change("failureCode", null, "AI_BUDGET_EXCEEDED", AuditValueType.STRING, 1))));
            }
            return new GenerationFoundationResult(batch, List.copyOf(jobs), false, "AI_BUDGET_EXCEEDED");
        }
    }

    private JobDraft validateJob(GenerationJobFoundationRequest request) {
        PromptTemplateVersion version = versionRepository.findById(request.promptTemplateVersionUuid())
                .orElseThrow(() -> new AiFoundationValidationException("Prompt template version does not exist"));
        PromptTemplate template = templateRepository.findById(version.getPromptTemplateUuid())
                .orElseThrow(() -> new AiFoundationValidationException("Prompt template does not exist"));
        if (template.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new AiFoundationValidationException("Archived prompt template cannot generate content");
        }
        if (template.getGenerationType() != request.generationType()) {
            throw new AiFoundationValidationException("Generation type does not match prompt template");
        }
        if (request.estimatedCost() == null || request.worstCaseCost() == null
                || request.estimatedCost().signum() < 0 || request.worstCaseCost().signum() <= 0
                || request.estimatedCost().compareTo(request.worstCaseCost()) > 0) {
            throw new AiFoundationValidationException("Job cost bounds are invalid");
        }
        return new JobDraft(request);
    }

    private String canonicalSnapshot(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode value = objectMapper.readTree(raw);
            if (value == null || !value.isObject()) {
                throw new AiFoundationValidationException("inputSnapshot must be a JSON object");
            }
            String canonical = objectMapper.writeValueAsString(value);
            if (canonical.length() > 32768) {
                throw new AiFoundationValidationException("inputSnapshot exceeds 32768 characters");
            }
            return canonical;
        } catch (AiFoundationValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiFoundationValidationException("inputSnapshot must be valid JSON", exception);
        }
    }

    private void appendCreateAudits(GenerationBatch batch, List<GenerationJob> jobs,
            AuditOperationContext context) {
        auditWriter.append(event(context, AuditAction.CREATE, "AI_GENERATION_BATCH",
                batch.getGenerationBatchUuid(), batch.getProductUuid(), List.of(
                        change("productUuid", null, batch.getProductUuid().toString(), AuditValueType.UUID, 0),
                        change("requestedJobCount", null, Integer.toString(batch.getRequestedJobCount()),
                                AuditValueType.INTEGER, 1),
                        change("reservedCost", null, batch.getReservedCost().toPlainString(),
                                AuditValueType.DECIMAL, 2))));
        for (GenerationJob job : jobs) {
            auditWriter.append(event(context, AuditAction.CREATE, "AI_GENERATION_JOB",
                    job.getGenerationJobUuid(), job.getProductUuid(), List.of(
                            change("generationBatchUuid", null, batch.getGenerationBatchUuid().toString(),
                                    AuditValueType.UUID, 0),
                            change("generationType", null, job.getGenerationType().name(),
                                    AuditValueType.ENUM, 1),
                            change("reservedCost", null, job.getReservedCost().toPlainString(),
                                    AuditValueType.DECIMAL, 2))));
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

    private record JobDraft(GenerationJobFoundationRequest request) {
    }
}
