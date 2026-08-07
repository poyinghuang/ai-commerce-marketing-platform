package com.aicommerce.platform.quality.application;

import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.product.application.AuditActorUnavailableException;
import org.springframework.stereotype.Service;

@Service
public class ProductQualityCommandService {
    private final ProductQualityRecalculationService recalculation;
    private final AuditOperationContextFactory contexts;
    public ProductQualityCommandService(ProductQualityRecalculationService recalculation,
            AuditOperationContextFactory contexts) {
        this.recalculation = recalculation; this.contexts = contexts;
    }
    public QualityProjectionView adjust(UUID productUuid, long version,
            ManualAdjustmentPatch patch, String requestId) {
        try {
            return recalculation.adjust(productUuid, version, patch, contexts.forCurrentActor(requestId));
        } catch (IllegalStateException exception) {
            throw new AuditActorUnavailableException(exception);
        }
    }
}
