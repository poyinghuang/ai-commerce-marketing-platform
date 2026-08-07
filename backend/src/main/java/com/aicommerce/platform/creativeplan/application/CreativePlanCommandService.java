package com.aicommerce.platform.creativeplan.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.application.ProductQualityRecalculationService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreativePlanCommandService {
    private static final String ENTITY_TYPE = "CREATIVE_PLAN";
    private final CreativePlanJpaRepository plans;
    private final ProductJpaRepository products;
    private final AuditOperationContextFactory contexts;
    private final AuditWriter auditWriter;
    private final CreativePlanAuditChangeFactory changes;
    private final ProductQualityRecalculationService quality;
    private final Clock clock;

    public CreativePlanCommandService(CreativePlanJpaRepository plans, ProductJpaRepository products,
            AuditOperationContextFactory contexts, AuditWriter auditWriter,
            CreativePlanAuditChangeFactory changes, ProductQualityRecalculationService quality, Clock clock) {
        this.plans = plans; this.products = products; this.contexts = contexts;
        this.auditWriter = auditWriter; this.changes = changes; this.quality = quality; this.clock = clock;
    }

    @Transactional
    public CreativePlan create(UUID productUuid, CreateCreativePlanCommand command, String requestId) {
        AuditOperationContext context = context(requestId);
        requireActiveProduct(productUuid);
        CreativePlan plan;
        try {
            plan = CreativePlan.create(UUID.randomUUID(), productUuid, command.planName());
            plan.update(command.planName(), command.primaryAudience(), command.secondaryAudience(), command.painPoint(),
                    command.coreBenefit(), command.creativeAngle(), command.emotionalDirection(), command.brandTone(),
                    command.visualStyle(), command.mainColor(), command.characterSetting(), command.cta());
        } catch (IllegalArgumentException exception) { throw validation(exception); }
        plan = plans.saveAndFlush(plan);
        append(plan, context, AuditAction.CREATE, changes.forCreate(CreativePlanSnapshot.from(plan)));
        quality.recalculate(productUuid, context);
        return plan;
    }

    @Transactional
    public CreativePlan patch(UUID productUuid, UUID planUuid, long expectedVersion,
            PatchCreativePlanCommand command, String requestId) {
        AuditOperationContext context = context(requestId);
        requireActiveProduct(productUuid);
        CreativePlan plan = find(productUuid, planUuid);
        checkVersion(plan, expectedVersion);
        if (plan.getLifecycleStatus() == com.aicommerce.platform.common.domain.LifecycleStatus.ARCHIVED) {
            throw new CreativePlanArchivedException();
        }
        CreativePlanSnapshot before = CreativePlanSnapshot.from(plan);
        try {
            plan.update(command.planName().resolve(plan.getPlanName()),
                    command.primaryAudience().resolve(plan.getPrimaryAudience()),
                    command.secondaryAudience().resolve(plan.getSecondaryAudience()),
                    command.painPoint().resolve(plan.getPainPoint()), command.coreBenefit().resolve(plan.getCoreBenefit()),
                    command.creativeAngle().resolve(plan.getCreativeAngle()),
                    command.emotionalDirection().resolve(plan.getEmotionalDirection()),
                    command.brandTone().resolve(plan.getBrandTone()), command.visualStyle().resolve(plan.getVisualStyle()),
                    command.mainColor().resolve(plan.getMainColor()),
                    command.characterSetting().resolve(plan.getCharacterSetting()), command.cta().resolve(plan.getCta()));
        } catch (IllegalArgumentException exception) { throw validation(exception); }
        List<AuditChange> actual = changes.between(before, CreativePlanSnapshot.from(plan));
        if (actual.isEmpty()) return plan;
        flush(plan); append(plan, context, AuditAction.UPDATE, actual); quality.recalculate(productUuid, context); return plan;
    }

    @Transactional
    public CreativePlan archive(UUID productUuid, UUID planUuid, long expectedVersion, String requestId) {
        AuditOperationContext context = context(requestId); requireActiveProduct(productUuid);
        CreativePlan plan = find(productUuid, planUuid); checkVersion(plan, expectedVersion);
        CreativePlanSnapshot before = CreativePlanSnapshot.from(plan);
        if (!plan.archive(Instant.now(clock))) return plan;
        List<AuditChange> actual = changes.between(before, CreativePlanSnapshot.from(plan));
        flush(plan); append(plan, context, AuditAction.ARCHIVE, actual); quality.recalculate(productUuid, context); return plan;
    }

    @Transactional
    public CreativePlan restore(UUID productUuid, UUID planUuid, long expectedVersion, String requestId) {
        AuditOperationContext context = context(requestId); requireActiveProduct(productUuid);
        CreativePlan plan = find(productUuid, planUuid); checkVersion(plan, expectedVersion);
        CreativePlanSnapshot before = CreativePlanSnapshot.from(plan);
        if (!plan.restore()) return plan;
        List<AuditChange> actual = changes.between(before, CreativePlanSnapshot.from(plan));
        flush(plan); append(plan, context, AuditAction.RESTORE, actual); quality.recalculate(productUuid, context); return plan;
    }

    private Product requireActiveProduct(UUID uuid) {
        Product product = products.findById(uuid).orElseThrow(() -> new ProductNotFoundException(uuid));
        if (product.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED) throw new ProductArchivedException();
        return product;
    }
    private CreativePlan find(UUID productUuid, UUID planUuid) {
        return plans.findByCreativePlanUuidAndProductUuid(planUuid, productUuid).orElseThrow(CreativePlanNotFoundException::new);
    }
    private void checkVersion(CreativePlan plan, long expected) { if (plan.getVersion() != expected) throw new CreativePlanPreconditionFailedException(); }
    private void flush(CreativePlan plan) {
        try { plans.saveAndFlush(plan); } catch (ObjectOptimisticLockingFailureException exception) { throw new CreativePlanPreconditionFailedException(); }
    }
    private AuditOperationContext context(String requestId) {
        try { return contexts.forCurrentActor(requestId); } catch (IllegalStateException exception) { throw new AuditActorUnavailableException(exception); }
    }
    private void append(CreativePlan plan, AuditOperationContext context, AuditAction action, List<AuditChange> actual) {
        if (!actual.isEmpty()) auditWriter.append(new AuditEvent(UUID.randomUUID(), context, action, ENTITY_TYPE,
                plan.getCreativePlanUuid(), plan.getProductUuid(), Instant.now(clock), actual));
    }
    private CreativePlanValidationException validation(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Creative plan validation failed" : exception.getMessage();
        String field = message.contains(" ") ? message.substring(0, message.indexOf(' ')) : "creativePlan";
        return new CreativePlanValidationException(field, message);
    }
}
