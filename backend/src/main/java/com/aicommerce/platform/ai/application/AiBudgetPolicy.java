package com.aicommerce.platform.ai.application;

import java.math.BigDecimal;
import java.util.Currency;

public record AiBudgetPolicy(
        String currency,
        BigDecimal maximumJobCost,
        BigDecimal maximumBatchCost,
        BigDecimal maximumDailyCost) {

    public AiBudgetPolicy {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("AI budget currency must be three uppercase letters");
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI budget currency must be a valid ISO currency code", exception);
        }
        requirePositive(maximumJobCost, "maximumJobCost");
        requirePositive(maximumBatchCost, "maximumBatchCost");
        requirePositive(maximumDailyCost, "maximumDailyCost");
        if (maximumJobCost.compareTo(maximumBatchCost) > 0
                || maximumBatchCost.compareTo(maximumDailyCost) > 0) {
            throw new IllegalArgumentException("AI budget limits must satisfy job <= batch <= daily");
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.scale() > 6
                || value.precision() - value.scale() > 13) {
            throw new IllegalArgumentException(field + " must be a positive numeric(19,6) value");
        }
    }
}
