package com.aicommerce.platform.delivery.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() < 0 || amount.scale() > 6 || amount.precision() - amount.scale() > 13) {
            throw new IllegalArgumentException("amount must fit NUMERIC(19,6) and be non-negative");
        }
        amount = amount.setScale(6, RoundingMode.UNNECESSARY);
        currency = Objects.requireNonNull(currency, "currency is required").strip().toUpperCase();
        Currency.getInstance(currency);
    }
}
