package com.aicommerce.platform.ai.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record BudgetReservation(UUID generationJobUuid, BigDecimal worstCaseCost) {
    public BudgetReservation {
        Objects.requireNonNull(generationJobUuid, "generationJobUuid is required");
        if (worstCaseCost == null || worstCaseCost.signum() <= 0) {
            throw new IllegalArgumentException("worstCaseCost must be positive");
        }
    }
}
