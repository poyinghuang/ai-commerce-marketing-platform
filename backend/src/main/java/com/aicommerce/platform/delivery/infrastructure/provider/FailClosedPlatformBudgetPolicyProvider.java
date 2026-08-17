package com.aicommerce.platform.delivery.infrastructure.provider;

import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformBudgetPolicyProvider;
import com.aicommerce.platform.delivery.application.port.PlatformBudgetPolicy;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class FailClosedPlatformBudgetPolicyProvider implements PlatformBudgetPolicyProvider {
    @Override
    public PlatformBudgetPolicy requirePolicy(UUID accountUuid, PlatformBudgetType budgetType) {
        return new PlatformBudgetPolicy("TWD", budgetType,
                budgetType == PlatformBudgetType.DAILY ? new BigDecimal("100.000000") : new BigDecimal("300.000000"));
    }
}
