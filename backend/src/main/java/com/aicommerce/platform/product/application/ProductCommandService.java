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
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.application.ProductQualityRecalculationService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCommandService {

    private static final String ENTITY_TYPE = "PRODUCT";

    private final ProductJpaRepository productRepository;
    private final ProductIdGenerator productIdGenerator;
    private final AuditOperationContextFactory contextFactory;
    private final AuditWriter auditWriter;
    private final ProductAuditChangeFactory changeFactory;
    private final ProductQualityRecalculationService quality;
    private final Clock clock;

    public ProductCommandService(
            ProductJpaRepository productRepository,
            ProductIdGenerator productIdGenerator,
            AuditOperationContextFactory contextFactory,
            AuditWriter auditWriter,
            ProductAuditChangeFactory changeFactory,
            ProductQualityRecalculationService quality,
            Clock clock) {
        this.productRepository = productRepository;
        this.productIdGenerator = productIdGenerator;
        this.contextFactory = contextFactory;
        this.auditWriter = auditWriter;
        this.changeFactory = changeFactory;
        this.quality = quality;
        this.clock = clock;
    }

    @Transactional
    public Product create(CreateProductCommand command, String requestId) {
        return create(command, currentContext(requestId));
    }

    @Transactional
    public Product create(CreateProductCommand command, AuditOperationContext context) {
        Product product;
        try {
            product = Product.create(
                    UUID.randomUUID(),
                    productIdGenerator.nextId(),
                    command.sku(),
                    command.productName(),
                    command.brand(),
                    command.category(),
                    command.subcategory(),
                    command.shortDescription(),
                    command.cost(),
                    command.salePrice(),
                    command.currency(),
                    command.stock(),
                    command.productUrl());
        } catch (IllegalArgumentException exception) {
            throw validation(exception);
        }
        // UUID-assigned entities are merged by Spring Data; use the returned managed instance so the
        // create response includes auditing timestamps populated during persistence.
        product = productRepository.saveAndFlush(product);
        appendAudit(product, context, AuditAction.CREATE, changeFactory.forCreate(ProductSnapshot.from(product)));
        quality.recalculate(product.getProductUuid(), context);
        return product;
    }

    @Transactional
    public Product patch(UUID productUuid, long expectedVersion, PatchProductCommand command, String requestId) {
        return patch(productUuid, expectedVersion, command, currentContext(requestId));
    }

    @Transactional
    public Product patch(UUID productUuid, long expectedVersion, PatchProductCommand command,
            AuditOperationContext context) {
        Product product = find(productUuid);
        checkVersion(product, expectedVersion);
        if (product.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED) {
            throw new ProductArchivedException();
        }
        ProductSnapshot before = ProductSnapshot.from(product);
        try {
            product.updateMasterData(
                    command.sku().resolve(product.getSku()),
                    command.productName().resolve(product.getProductName()),
                    command.brand().resolve(product.getBrand()),
                    command.category().resolve(product.getCategory()),
                    command.subcategory().resolve(product.getSubcategory()),
                    command.shortDescription().resolve(product.getShortDescription()),
                    command.cost().resolve(product.getCost()),
                    command.salePrice().resolve(product.getSalePrice()),
                    command.currency().resolve(product.getCurrency()),
                    command.stock().resolve(product.getStock()),
                    command.productUrl().resolve(product.getProductUrl()));
        } catch (IllegalArgumentException exception) {
            throw validation(exception);
        }
        List<AuditChange> changes = changeFactory.between(before, ProductSnapshot.from(product));
        if (changes.isEmpty()) {
            return product;
        }
        flush(product);
        appendAudit(product, context, AuditAction.UPDATE, changes);
        quality.recalculate(product.getProductUuid(), context);
        return product;
    }

    @Transactional
    public Product archive(UUID productUuid, long expectedVersion, String requestId) {
        AuditOperationContext context = currentContext(requestId);
        Product product = find(productUuid);
        checkVersion(product, expectedVersion);
        ProductSnapshot before = ProductSnapshot.from(product);
        if (!product.archive(Instant.now(clock))) {
            return product;
        }
        List<AuditChange> changes = changeFactory.between(before, ProductSnapshot.from(product));
        flush(product);
        appendAudit(product, context, AuditAction.ARCHIVE, changes);
        quality.recalculate(product.getProductUuid(), context);
        return product;
    }

    @Transactional
    public Product restore(UUID productUuid, long expectedVersion, String requestId) {
        AuditOperationContext context = currentContext(requestId);
        Product product = find(productUuid);
        checkVersion(product, expectedVersion);
        ProductSnapshot before = ProductSnapshot.from(product);
        if (!product.restore()) {
            return product;
        }
        List<AuditChange> changes = changeFactory.between(before, ProductSnapshot.from(product));
        flush(product);
        appendAudit(product, context, AuditAction.RESTORE, changes);
        quality.recalculate(product.getProductUuid(), context);
        return product;
    }

    private Product find(UUID productUuid) {
        return productRepository.findById(productUuid).orElseThrow(() -> new ProductNotFoundException(productUuid));
    }

    private void checkVersion(Product product, long expectedVersion) {
        if (product.getVersion() != expectedVersion) {
            throw new ProductPreconditionFailedException();
        }
    }

    private void flush(Product product) {
        try {
            productRepository.saveAndFlush(product);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ProductPreconditionFailedException();
        }
    }

    private AuditOperationContext currentContext(String requestId) {
        try {
            return contextFactory.forCurrentActor(requestId);
        } catch (IllegalStateException exception) {
            throw new AuditActorUnavailableException(exception);
        }
    }

    private void appendAudit(
            Product product,
            AuditOperationContext context,
            AuditAction action,
            List<AuditChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        auditWriter.append(new AuditEvent(
                UUID.randomUUID(),
                context,
                action,
                ENTITY_TYPE,
                product.getProductUuid(),
                product.getProductUuid(),
                Instant.now(clock),
                changes));
    }

    private ProductValidationException validation(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Product validation failed" : exception.getMessage();
        String field = message.contains(" ") ? message.substring(0, message.indexOf(' ')) : "product";
        return new ProductValidationException(field, message);
    }
}
