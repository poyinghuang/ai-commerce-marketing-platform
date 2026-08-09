package com.aicommerce.platform.ai.application;

import java.math.BigDecimal;

public record AiCostCeiling(BigDecimal estimatedCost, BigDecimal worstCaseCost) {

    public AiCostCeiling {
        if (estimatedCost == null || worstCaseCost == null
                || estimatedCost.signum() < 0 || worstCaseCost.signum() <= 0
                || estimatedCost.compareTo(worstCaseCost) > 0
                || estimatedCost.scale() > 6 || worstCaseCost.scale() > 6
                || estimatedCost.precision() - estimatedCost.scale() > 13
                || worstCaseCost.precision() - worstCaseCost.scale() > 13) {
            throw new IllegalArgumentException("AI cost ceiling must be a valid numeric(19,6) bound");
        }
        estimatedCost = estimatedCost.setScale(6);
        worstCaseCost = worstCaseCost.setScale(6);
    }
}
