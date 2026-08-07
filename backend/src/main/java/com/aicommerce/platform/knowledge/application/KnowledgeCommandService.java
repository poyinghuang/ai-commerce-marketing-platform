package com.aicommerce.platform.knowledge.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
import com.aicommerce.platform.product.application.AuditActorUnavailableException;
import com.aicommerce.platform.product.application.ProductArchivedException;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeCommandService {
    private static final String ENTITY_TYPE = "PRODUCT_KNOWLEDGE";
    private final ProductKnowledgeJpaRepository repository;
    private final ProductJpaRepository productRepository;
    private final AuditOperationContextFactory contextFactory;
    private final AuditWriter auditWriter;
    private final KnowledgeAuditChangeFactory changes;
    private final Clock clock;
    public KnowledgeCommandService(ProductKnowledgeJpaRepository repository, ProductJpaRepository productRepository,
            AuditOperationContextFactory contextFactory, AuditWriter auditWriter,
            KnowledgeAuditChangeFactory changes, Clock clock) {
        this.repository = repository; this.productRepository = productRepository; this.contextFactory = contextFactory;
        this.auditWriter = auditWriter; this.changes = changes; this.clock = clock;
    }
    @Transactional
    public ProductKnowledge create(UUID productUuid, CreateKnowledgeCommand command, String requestId) {
        requireMutableProduct(productUuid); AuditOperationContext context = context(requestId);
        ProductKnowledge value;
        try { value = ProductKnowledge.create(UUID.randomUUID(), productUuid, command.knowledgeType(), command.title(), command.content(), command.source()); }
        catch (IllegalArgumentException exception) { throw validation(exception); }
        value = repository.saveAndFlush(value);
        append(value, context, AuditAction.CREATE, changes.create(KnowledgeSnapshot.from(value)));
        return value;
    }
    @Transactional
    public ProductKnowledge patch(UUID productUuid, UUID knowledgeUuid, long expectedVersion, PatchKnowledgeCommand command, String requestId) {
        var product = requireProduct(productUuid); ProductKnowledge value = find(productUuid, knowledgeUuid);
        requireMutable(product); AuditOperationContext context = context(requestId); checkVersion(value, expectedVersion);
        if (value.getLifecycleStatus() == LifecycleStatus.ARCHIVED) throw new KnowledgeArchivedException();
        KnowledgeSnapshot before = KnowledgeSnapshot.from(value);
        try { value.update(command.knowledgeType().resolve(value.getKnowledgeType()), command.title().resolve(value.getTitle()),
                command.content().resolve(value.getContent()), command.source().resolve(value.getSource())); }
        catch (IllegalArgumentException exception) { throw validation(exception); }
        List<AuditChange> actual = changes.between(before, KnowledgeSnapshot.from(value));
        if (actual.isEmpty()) return value;
        flush(value); append(value, context, AuditAction.UPDATE, actual); return value;
    }
    @Transactional
    public ProductKnowledge archive(UUID productUuid, UUID knowledgeUuid, long expectedVersion, String requestId) {
        var product = requireProduct(productUuid); ProductKnowledge value = find(productUuid, knowledgeUuid);
        requireMutable(product); AuditOperationContext context = context(requestId); checkVersion(value, expectedVersion);
        KnowledgeSnapshot before = KnowledgeSnapshot.from(value);
        if (!value.archive(Instant.now(clock))) return value;
        List<AuditChange> actual = changes.between(before, KnowledgeSnapshot.from(value)); flush(value);
        append(value, context, AuditAction.ARCHIVE, actual); return value;
    }
    @Transactional
    public ProductKnowledge restore(UUID productUuid, UUID knowledgeUuid, long expectedVersion, String requestId) {
        var product = requireProduct(productUuid); ProductKnowledge value = find(productUuid, knowledgeUuid);
        requireMutable(product); AuditOperationContext context = context(requestId); checkVersion(value, expectedVersion);
        KnowledgeSnapshot before = KnowledgeSnapshot.from(value);
        if (!value.restore()) return value;
        List<AuditChange> actual = changes.between(before, KnowledgeSnapshot.from(value)); flush(value);
        append(value, context, AuditAction.RESTORE, actual); return value;
    }
    private void requireMutableProduct(UUID uuid) { requireMutable(requireProduct(uuid)); }
    private com.aicommerce.platform.product.domain.Product requireProduct(UUID uuid) { return productRepository.findForKnowledgeMutation(uuid).orElseThrow(() -> new ProductNotFoundException(uuid)); }
    private void requireMutable(com.aicommerce.platform.product.domain.Product product) { if (product.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED) throw new ProductArchivedException(); }
    private ProductKnowledge find(UUID p, UUID k) { return repository.findOwned(p, k).orElseThrow(KnowledgeNotFoundException::new); }
    private void checkVersion(ProductKnowledge value, long expected) { if (value.getVersion() != expected) throw new KnowledgePreconditionFailedException(); }
    private void flush(ProductKnowledge value) { try { repository.saveAndFlush(value); } catch (ObjectOptimisticLockingFailureException e) { throw new KnowledgePreconditionFailedException(); } }
    private AuditOperationContext context(String requestId) { try { return contextFactory.forCurrentActor(requestId); } catch (IllegalStateException e) { throw new AuditActorUnavailableException(e); } }
    private void append(ProductKnowledge value, AuditOperationContext context, AuditAction action, List<AuditChange> actual) { if (!actual.isEmpty()) auditWriter.append(new AuditEvent(UUID.randomUUID(), context, action, ENTITY_TYPE, value.getKnowledgeUuid(), value.getProductUuid(), Instant.now(clock), actual)); }
    private KnowledgeValidationException validation(IllegalArgumentException e) { String m = e.getMessage() == null ? "Knowledge validation failed" : e.getMessage(); return new KnowledgeValidationException(m.contains(" ") ? m.substring(0, m.indexOf(' ')) : "knowledge", m); }
}
