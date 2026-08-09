package com.aicommerce.platform.ai.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class AiDomainRules {

    private AiDomainRules() {
    }

    static String required(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        return normalized;
    }

    static String optional(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, field, maximumLength);
    }

    static BigDecimal money(BigDecimal value, String field, boolean positive) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (positive ? value.signum() <= 0 : value.signum() < 0) {
            throw new IllegalArgumentException(field + (positive ? " must be positive" : " must be non-negative"));
        }
        if (value.scale() > 6 || value.precision() - value.scale() > 13) {
            throw new IllegalArgumentException(field + " exceeds numeric(19,6)");
        }
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    static String currency(String value) {
        String normalized = required(value, "currency", 3);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be three uppercase letters");
        }
        return normalized;
    }
}
