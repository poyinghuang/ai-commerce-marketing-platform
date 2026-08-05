package com.aicommerce.platform.product.application;

import java.math.BigDecimal;

public record CreateProductCommand(
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
        String productUrl) {
}
