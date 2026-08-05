package com.aicommerce.platform.product.application;

import com.aicommerce.platform.product.domain.ProductLifecycleStatus;

public record ProductSearchCriteria(
        ProductLifecycleStatus status,
        String category,
        String keyword,
        String sku,
        String productId) {
}
