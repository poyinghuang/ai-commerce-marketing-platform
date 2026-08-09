package com.aicommerce.platform.connector.sheets.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProductSheetMapping {

    public static final int MAX_DATA_ROWS = 1_000;
    public static final int COLUMN_COUNT = 13;
    public static final List<String> HEADERS = List.of(
            "product_uuid", "product_id", "sku", "product_name", "brand", "category", "subcategory",
            "short_description", "cost", "sale_price", "currency", "stock", "product_url");
    public static final Set<String> REQUIRED_HEADERS = Set.of("product_uuid", "product_id", "product_name");
    public static final int REQUIRED_HEADER_MASK = 11;
    public static final int ALL_HEADER_MASK = 8191;
    private static final Map<String, Integer> HEADER_BITS = bits();

    private ProductSheetMapping() {
    }

    public static boolean isKnownHeader(String header) {
        return HEADERS.contains(header);
    }

    public static int presenceMask(Collection<String> headers) {
        if (headers == null) {
            throw new IllegalArgumentException("headers are required");
        }
        int mask = 0;
        Set<String> seen = new java.util.HashSet<>();
        for (String header : headers) {
            Integer bit = HEADER_BITS.get(header);
            if (bit == null) {
                throw new IllegalArgumentException("unknown header: " + header);
            }
            if (!seen.add(header)) {
                throw new IllegalArgumentException("duplicate header: " + header);
            }
            mask |= bit;
        }
        requireValidMask(mask);
        return mask;
    }

    public static boolean isPresent(int mask, String header) {
        requireValidMask(mask);
        Integer bit = HEADER_BITS.get(header);
        if (bit == null) {
            throw new IllegalArgumentException("unknown header: " + header);
        }
        return (mask & bit) != 0;
    }

    public static void requireValidMask(int mask) {
        if (mask < 0 || mask > ALL_HEADER_MASK || (mask & REQUIRED_HEADER_MASK) != REQUIRED_HEADER_MASK) {
            throw new IllegalArgumentException("header presence mask is invalid");
        }
    }

    private static Map<String, Integer> bits() {
        Map<String, Integer> bits = new LinkedHashMap<>();
        for (int index = 0; index < HEADERS.size(); index++) {
            bits.put(HEADERS.get(index), 1 << index);
        }
        return Map.copyOf(bits);
    }
}
