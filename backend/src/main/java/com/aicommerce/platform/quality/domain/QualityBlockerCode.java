package com.aicommerce.platform.quality.domain;

public enum QualityBlockerCode {
    PRODUCT_ARCHIVED("lifecycleStatus", "Archived Product cannot be ready"),
    PRODUCT_NAME_MISSING("productName", "Product name is required for readiness"),
    SALE_PRICE_MISSING("salePrice", "Sale price is required for readiness"),
    CURRENCY_MISSING("currency", "Currency is required for readiness"),
    KNOWLEDGE_MISSING("knowledge", "At least one active Product Knowledge entry is required"),
    CREATIVE_PLAN_MISSING("creativePlans", "At least one active Creative Plan is required"),
    IMAGE_ASSET_MISSING("assets", "At least one active image Asset is required");

    private final String fieldPath;
    private final String message;

    QualityBlockerCode(String fieldPath, String message) {
        this.fieldPath = fieldPath;
        this.message = message;
    }

    public String fieldPath() { return fieldPath; }
    public String message() { return message; }
}
