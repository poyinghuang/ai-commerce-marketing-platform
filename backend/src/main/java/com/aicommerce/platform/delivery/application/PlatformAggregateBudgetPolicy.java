package com.aicommerce.platform.delivery.application;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Objects;

public record PlatformAggregateBudgetPolicy(String currency, ZoneId businessZone,
        BigDecimal maxOperationBatchAmount, BigDecimal maxAccountBusinessDayAmount) {
    public PlatformAggregateBudgetPolicy {
        Objects.requireNonNull(businessZone);
        Objects.requireNonNull(maxOperationBatchAmount);
        Objects.requireNonNull(maxAccountBusinessDayAmount);
        if (!"TWD".equals(currency) || !ZoneId.of("Asia/Taipei").equals(businessZone)
                || maxOperationBatchAmount.compareTo(new BigDecimal("300.000000")) != 0
                || maxAccountBusinessDayAmount.compareTo(new BigDecimal("1000.000000")) != 0) {
            throw new IllegalArgumentException("PLATFORM_POLICY_REJECTED");
        }
    }

    public static PlatformAggregateBudgetPolicy stage4b() {
        return new PlatformAggregateBudgetPolicy("TWD", ZoneId.of("Asia/Taipei"),
                new BigDecimal("300.000000"), new BigDecimal("1000.000000"));
    }
}
