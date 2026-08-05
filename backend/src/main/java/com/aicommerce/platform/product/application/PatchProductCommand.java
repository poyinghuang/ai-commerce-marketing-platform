package com.aicommerce.platform.product.application;

import java.math.BigDecimal;

public record PatchProductCommand(
        PatchField<String> sku,
        PatchField<String> productName,
        PatchField<String> brand,
        PatchField<String> category,
        PatchField<String> subcategory,
        PatchField<String> shortDescription,
        PatchField<BigDecimal> cost,
        PatchField<BigDecimal> salePrice,
        PatchField<String> currency,
        PatchField<Long> stock,
        PatchField<String> productUrl) {
}
