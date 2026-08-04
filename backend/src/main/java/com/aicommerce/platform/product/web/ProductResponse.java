package com.aicommerce.platform.product.web;

import java.time.Instant;
import java.util.UUID;

import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;

public record ProductResponse(
        UUID productUuid,
        String productId,
        String sku,
        String productName,
        String brand,
        String category,
        String subcategory,
        String shortDescription,
        String cost,
        String salePrice,
        String currency,
        String stock,
        String productUrl,
        ProductLifecycleStatus lifecycleStatus,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getProductUuid(),
                product.getProductId(),
                product.getSku(),
                product.getProductName(),
                product.getBrand(),
                product.getCategory(),
                product.getSubcategory(),
                product.getShortDescription(),
                product.getCost() == null ? null : product.getCost().toPlainString(),
                product.getSalePrice() == null ? null : product.getSalePrice().toPlainString(),
                product.getCurrency(),
                product.getStock() == null ? null : product.getStock().toString(),
                product.getProductUrl(),
                product.getLifecycleStatus(),
                product.getArchivedAt(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion());
    }
}
