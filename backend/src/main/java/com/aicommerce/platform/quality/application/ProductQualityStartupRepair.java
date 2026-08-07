package com.aicommerce.platform.quality.application;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
@ConditionalOnProperty(name = "app.quality.startup-repair-enabled", havingValue = "true")
public class ProductQualityStartupRepair implements ApplicationRunner {
    private final ProductJpaRepository products;
    private final ProductQualityRecalculationService recalculation;
    private final AuditOperationContextFactory contexts;
    public ProductQualityStartupRepair(ProductJpaRepository products,
            ProductQualityRecalculationService recalculation, AuditOperationContextFactory contexts) {
        this.products = products; this.recalculation = recalculation; this.contexts = contexts;
    }
    @Override
    public void run(ApplicationArguments args) {
        products.findAllProductUuids().forEach(productUuid ->
                recalculation.recalculate(productUuid, contexts.forSystem("quality-startup-repair")));
    }
}
