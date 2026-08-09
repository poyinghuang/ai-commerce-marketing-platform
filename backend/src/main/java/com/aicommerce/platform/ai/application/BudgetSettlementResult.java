package com.aicommerce.platform.ai.application;

import java.math.BigDecimal;

public record BudgetSettlementResult(
        BigDecimal reservedCost,
        BigDecimal actualCost,
        BigDecimal releasedCost,
        boolean invariantViolation) {
}
