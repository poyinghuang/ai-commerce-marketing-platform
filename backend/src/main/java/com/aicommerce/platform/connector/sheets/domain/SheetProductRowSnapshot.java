package com.aicommerce.platform.connector.sheets.domain;

public record SheetProductRowSnapshot(
        String productUuid,
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
        String productUrl) {
}
