package com.aicommerce.platform.product.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;

record ProductSnapshot(
        String productId,
        String sku,
        String productName,
        String brand,
        String category,
        String subcategory,
        String shortDescription,
        BigDecimal cost,
        BigDecimal salePrice,
        String currency,
        Long stock,
        String productUrl,
        ProductLifecycleStatus lifecycleStatus,
        Instant archivedAt) {

    static ProductSnapshot from(Product product) {
        return new ProductSnapshot(
                product.getProductId(),
                product.getSku(),
                product.getProductName(),
                product.getBrand(),
                product.getCategory(),
                product.getSubcategory(),
                product.getShortDescription(),
                product.getCost(),
                product.getSalePrice(),
                product.getCurrency(),
                product.getStock(),
                product.getProductUrl(),
                product.getLifecycleStatus(),
                product.getArchivedAt());
    }
}
