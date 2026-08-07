package com.aicommerce.platform.creativeplan.application;

import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreativePlanQueryService {
    private final CreativePlanJpaRepository plans; private final ProductJpaRepository products;
    public CreativePlanQueryService(CreativePlanJpaRepository plans, ProductJpaRepository products) { this.plans = plans; this.products = products; }
    @Transactional(readOnly = true)
    public CreativePlan get(UUID productUuid, UUID planUuid) {
        requireProduct(productUuid);
        return plans.findByCreativePlanUuidAndProductUuid(planUuid, productUuid).orElseThrow(CreativePlanNotFoundException::new);
    }
    @Transactional(readOnly = true)
    public Page<CreativePlan> list(UUID productUuid, LifecycleStatus status, Pageable pageable) {
        requireProduct(productUuid);
        return status == null ? plans.findByProductUuid(productUuid, pageable) : plans.findByProductUuidAndLifecycleStatus(productUuid, status, pageable);
    }
    private void requireProduct(UUID uuid) { if (!products.existsById(uuid)) throw new ProductNotFoundException(uuid); }
}
