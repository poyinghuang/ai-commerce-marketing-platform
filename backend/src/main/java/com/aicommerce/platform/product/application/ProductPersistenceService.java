package com.aicommerce.platform.product.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductPersistenceService {

    private final ProductJpaRepository productRepository;
    private final ProductIdGenerator productIdGenerator;
    private final AuditOperationContextFactory contextFactory;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public ProductPersistenceService(
            ProductJpaRepository productRepository,
            ProductIdGenerator productIdGenerator,
            AuditOperationContextFactory contextFactory,
            AuditWriter auditWriter,
            Clock clock) {
        this.productRepository = productRepository;
        this.productIdGenerator = productIdGenerator;
        this.contextFactory = contextFactory;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional
    public Product createAsCurrentActor(String sku, String serverResolvedRequestId) {
        return create(sku, contextFactory.forCurrentActor(serverResolvedRequestId));
    }

    @Transactional
    public Product createAsSystem(String sku, String systemActorId) {
        return create(sku, contextFactory.forSystem(systemActorId));
    }

    @Transactional
    public Product archiveAsCurrentActor(UUID productUuid, String serverResolvedRequestId) {
        AuditOperationContext context = contextFactory.forCurrentActor(serverResolvedRequestId);
        Product product = productRepository.findById(productUuid)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (product.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED) {
            return product;
        }
        Instant archivedAt = Instant.now(clock);
        product.archive(archivedAt);
        auditWriter.append(new AuditEvent(
                UUID.randomUUID(),
                context,
                AuditAction.ARCHIVE,
                "PRODUCT",
                productUuid,
                productUuid,
                Instant.now(clock),
                List.of(
                        new AuditChange("lifecycle_status", "ACTIVE", "ARCHIVED", AuditValueType.ENUM, 0),
                        new AuditChange("archived_at", null, archivedAt.toString(), AuditValueType.TIMESTAMP, 1))));
        return product;
    }

    private Product create(String sku, AuditOperationContext context) {
        Product product = Product.create(UUID.randomUUID(), productIdGenerator.nextId(), sku);
        productRepository.save(product);
        auditWriter.append(new AuditEvent(
                UUID.randomUUID(),
                context,
                AuditAction.CREATE,
                "PRODUCT",
                product.getProductUuid(),
                product.getProductUuid(),
                Instant.now(clock),
                List.of(
                        new AuditChange("product_id", null, product.getProductId(), AuditValueType.STRING, 0),
                        new AuditChange("sku", null, product.getSku(), AuditValueType.STRING, 1),
                        new AuditChange("lifecycle_status", null, "ACTIVE", AuditValueType.ENUM, 2))));
        return product;
    }
}
