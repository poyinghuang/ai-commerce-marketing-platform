package com.aicommerce.platform.quality.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignProductJpaRepository;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
import com.aicommerce.platform.product.application.ProductArchivedException;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.domain.DeterministicQualityRuleEngine;
import com.aicommerce.platform.quality.domain.QualityAssessment;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.AssetFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.CampaignFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.CreativePlanFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.ProductFacts;
import com.aicommerce.platform.quality.domain.QualityBlockerCode;
import com.aicommerce.platform.quality.domain.QualityScore;
import com.aicommerce.platform.quality.domain.QualityScoreBlocker;
import com.aicommerce.platform.quality.domain.ReadinessStatus;
import com.aicommerce.platform.quality.domain.WorkflowStatus;
import com.aicommerce.platform.quality.infrastructure.persistence.QualityScoreBlockerJpaRepository;
import com.aicommerce.platform.quality.infrastructure.persistence.QualityScoreJpaRepository;
import com.aicommerce.platform.quality.infrastructure.persistence.WorkflowStatusJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQualityRecalculationService {
    private final ProductJpaRepository products;
    private final ProductKnowledgeJpaRepository knowledge;
    private final CreativePlanJpaRepository creativePlans;
    private final AssetJpaRepository assets;
    private final CampaignProductJpaRepository campaignProducts;
    private final QualityScoreJpaRepository scores;
    private final QualityScoreBlockerJpaRepository blockers;
    private final WorkflowStatusJpaRepository workflows;
    private final AuditWriter audit;
    private final QualityAuditChangeFactory changes;
    private final DeterministicQualityRuleEngine rules = new DeterministicQualityRuleEngine();
    private final Clock clock;

    public ProductQualityRecalculationService(ProductJpaRepository products,
            ProductKnowledgeJpaRepository knowledge, CreativePlanJpaRepository creativePlans,
            AssetJpaRepository assets, CampaignProductJpaRepository campaignProducts,
            QualityScoreJpaRepository scores, QualityScoreBlockerJpaRepository blockers,
            WorkflowStatusJpaRepository workflows, AuditWriter audit,
            QualityAuditChangeFactory changes, Clock clock) {
        this.products = products; this.knowledge = knowledge; this.creativePlans = creativePlans;
        this.assets = assets; this.campaignProducts = campaignProducts; this.scores = scores;
        this.blockers = blockers; this.workflows = workflows; this.audit = audit;
        this.changes = changes; this.clock = clock;
    }

    @Transactional
    public QualityProjectionView recalculate(UUID productUuid, AuditOperationContext context) {
        return recalculateLocked(requireProduct(productUuid), context, null, null);
    }

    @Transactional
    public QualityProjectionView adjust(UUID productUuid, long expectedVersion,
            ManualAdjustmentPatch patch, AuditOperationContext context) {
        Product product = requireProduct(productUuid);
        if (product.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED) throw new ProductArchivedException();
        QualityScore score = scores.findForUpdate(productUuid).orElseThrow(QualityNotFoundException::new);
        if (score.getVersion() != expectedVersion) throw new QualityPreconditionFailedException();
        int adjustment = patch.manualAdjustment().resolve(score.getManualAdjustment());
        String reason = patch.reason().resolve(score.getManualAdjustmentReason());
        QualityScoreSnapshot before = QualityScoreSnapshot.from(score);
        try {
            if (!score.recordManualAdjustment(adjustment, adjustment == 0 ? null : reason,
                    context.actor().id(), Instant.now(clock))) {
                return view(score, productUuid);
            }
        } catch (IllegalArgumentException exception) {
            throw validation(exception);
        }
        return recalculateLocked(product, context, score, before);
    }

    private QualityProjectionView recalculateLocked(Product product, AuditOperationContext context,
            QualityScore alreadyLocked, QualityScoreSnapshot suppliedBefore) {
        Instant now = Instant.now(clock);
        QualityScore score = alreadyLocked == null ? scores.findForUpdate(product.getProductUuid()).orElse(null) : alreadyLocked;
        boolean createdScore = score == null;
        QualityScoreSnapshot beforeScore = suppliedBefore != null ? suppliedBefore
                : score == null ? null : QualityScoreSnapshot.from(score);
        List<QualityScoreBlocker> currentBlockers = score == null ? List.of()
                : blockers.findByQualityScoreUuidOrderByBlockerCode(score.getQualityScoreUuid());
        Set<QualityBlockerCode> beforeBlockers = codes(currentBlockers);
        int adjustment = score == null ? 0 : score.getManualAdjustment();
        QualityAssessment assessment = rules.assess(input(product, adjustment));

        WorkflowStatus workflow = workflows.findByProductUuid(product.getProductUuid()).orElse(null);
        boolean createdWorkflow = workflow == null;
        WorkflowSnapshot beforeWorkflow = workflow == null ? null : WorkflowSnapshot.from(workflow);
        String reason = statusReason(assessment);
        boolean workflowChanged;
        if (workflow == null) {
            workflow = WorkflowStatus.create(UUID.randomUUID(), product.getProductUuid(),
                    assessment.readinessStatus(), reason, now);
            workflowChanged = true;
        } else {
            workflowChanged = workflow.apply(assessment.readinessStatus(), reason, now);
        }
        boolean blockersChanged = !beforeBlockers.equals(assessment.blockers());
        boolean scoreChanged;
        if (score == null) {
            score = QualityScore.create(UUID.randomUUID(), product.getProductUuid(), assessment, now);
            scoreChanged = true;
        } else {
            scoreChanged = score.applyAssessment(assessment, blockersChanged || workflowChanged || suppliedBefore != null, now);
        }
        if (!scoreChanged && !blockersChanged && !workflowChanged) return view(score, currentBlockers, workflow);

        score = scores.saveAndFlush(score);
        workflow = workflows.saveAndFlush(workflow);
        if (blockersChanged) {
            blockers.deleteByQualityScoreUuid(score.getQualityScoreUuid());
            UUID scoreUuid = score.getQualityScoreUuid();
            currentBlockers = assessment.blockers().stream().sorted()
                    .map(code -> QualityScoreBlocker.create(UUID.randomUUID(), scoreUuid, code, now))
                    .toList();
            blockers.saveAll(currentBlockers);
        }
        appendScoreAudit(score, beforeScore, beforeBlockers, assessment.blockers(), context,
                createdScore ? AuditAction.CREATE : AuditAction.UPDATE);
        appendWorkflowAudit(workflow, beforeWorkflow, context,
                createdWorkflow ? AuditAction.CREATE : AuditAction.UPDATE);
        return view(score, currentBlockers, workflow);
    }

    private QualityAssessmentInput input(Product product, int adjustment) {
        Set<KnowledgeType> types = knowledge.findForAggregate(product.getProductUuid(), false).stream()
                .map(value -> value.getKnowledgeType()).collect(java.util.stream.Collectors.toSet());
        List<CreativePlanFacts> plans = creativePlans.findForAggregate(product.getProductUuid(), false).stream()
                .map(this::plan).toList();
        List<AssetFacts> assetFacts = assets.findForAggregate(product.getProductUuid(), false).stream()
                .map(this::asset).toList();
        List<CampaignFacts> campaigns = campaignProducts.findCampaignsForAggregate(product.getProductUuid(), false).stream()
                .map(row -> campaign((CampaignPlan) row[0])).toList();
        return new QualityAssessmentInput(new ProductFacts(
                product.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED,
                product.getProductName(), product.getBrand(), product.getCategory(), product.getShortDescription(),
                product.getCost(), product.getSalePrice(), product.getCurrency(), product.getStock(), product.getProductUrl()),
                types, plans, assetFacts, campaigns, adjustment);
    }

    private CreativePlanFacts plan(CreativePlan value) {
        return new CreativePlanFacts(value.getPrimaryAudience(), value.getPainPoint(), value.getCoreBenefit(),
                value.getCreativeAngle(), value.getBrandTone(), value.getVisualStyle(), value.getCta());
    }
    private AssetFacts asset(Asset value) {
        return new AssetFacts(value.getAssetType() == AssetType.IMAGE, value.getFileUrl(), value.getStorageProvider(),
                value.getProviderFileId(), value.getMediaType(), value.getOriginalFilename());
    }
    private CampaignFacts campaign(CampaignPlan value) {
        return new CampaignFacts(value.getObjective(), value.getLandingPage(), value.getBudgetDaily(),
                value.getBudgetTotal(), value.getCurrency());
    }
    private Product requireProduct(UUID uuid) {
        return products.findForQualityMutation(uuid).orElseThrow(() -> new ProductNotFoundException(uuid));
    }
    private Set<QualityBlockerCode> codes(List<QualityScoreBlocker> values) {
        if (values.isEmpty()) return Set.of();
        EnumSet<QualityBlockerCode> result = EnumSet.noneOf(QualityBlockerCode.class);
        values.forEach(value -> result.add(value.getBlockerCode()));
        return Set.copyOf(result);
    }
    private String statusReason(QualityAssessment value) {
        if (value.blockers().contains(QualityBlockerCode.PRODUCT_ARCHIVED)) return "Product is archived";
        if (!value.blockers().isEmpty()) return "Blocked: " + value.blockers().stream().sorted()
                .map(Enum::name).collect(java.util.stream.Collectors.joining(","));
        if (value.readinessStatus() == ReadinessStatus.DRAFT) return "Final score is below 70";
        if (value.readinessStatus() == ReadinessStatus.NEEDS_REVIEW) return "Final score is below 90";
        return "All readiness requirements are met";
    }
    private QualityProjectionView view(QualityScore score, UUID productUuid) {
        WorkflowStatus workflow = workflows.findByProductUuid(productUuid).orElseThrow(QualityNotFoundException::new);
        return view(score, blockers.findByQualityScoreUuidOrderByBlockerCode(score.getQualityScoreUuid()), workflow);
    }
    private QualityProjectionView view(QualityScore score, List<QualityScoreBlocker> values, WorkflowStatus workflow) {
        return QualityProjectionView.from(score, values, workflow);
    }
    private void appendScoreAudit(QualityScore score, QualityScoreSnapshot before,
            Set<QualityBlockerCode> beforeBlockers, Set<QualityBlockerCode> afterBlockers,
            AuditOperationContext context, AuditAction action) {
        var actual = changes.score(before, beforeBlockers, score, afterBlockers);
        if (!actual.isEmpty()) audit.append(new AuditEvent(UUID.randomUUID(), context, action, "QUALITY_SCORE",
                score.getQualityScoreUuid(), score.getProductUuid(), Instant.now(clock), actual));
    }
    private void appendWorkflowAudit(WorkflowStatus workflow, WorkflowSnapshot before,
            AuditOperationContext context, AuditAction action) {
        var actual = changes.workflow(before, workflow);
        if (!actual.isEmpty()) audit.append(new AuditEvent(UUID.randomUUID(), context, action, "WORKFLOW_STATUS",
                workflow.getWorkflowStatusUuid(), workflow.getProductUuid(), Instant.now(clock), actual));
    }
    private QualityValidationException validation(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Quality validation failed" : exception.getMessage();
        String field = message.startsWith("manualAdjustment") ? "manualAdjustment" : "reason";
        return new QualityValidationException(field, message);
    }
}
