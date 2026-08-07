package com.aicommerce.platform.connector.sheets.application;

import java.util.List;
import java.util.Set;

public final class ProductSheetMapping {

    public static final int MAX_DATA_ROWS = 1_000;
    public static final int COLUMN_COUNT = 13;
    public static final List<String> HEADERS = List.of(
            "product_uuid", "product_id", "sku", "product_name", "brand", "category", "subcategory",
            "short_description", "cost", "sale_price", "currency", "stock", "product_url");
    public static final Set<String> REQUIRED_HEADERS = Set.of("product_uuid", "product_id", "product_name");

    private ProductSheetMapping() {
    }

    public static boolean isKnownHeader(String header) {
        return HEADERS.contains(header);
    }
}
