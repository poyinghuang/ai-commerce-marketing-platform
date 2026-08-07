package com.aicommerce.platform.connector.sheets.domain;

public record SheetValidationError(String field, String code, String message) {

    public SheetValidationError {
        field = requireText(field, "field", 128);
        code = requireText(code, "code", 64);
        message = requireText(message, "message", 512);
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
