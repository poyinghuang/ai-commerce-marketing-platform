package com.aicommerce.platform.delivery.infrastructure.provider;

import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformBudgetPolicyProvider;
import com.aicommerce.platform.delivery.domain.Money;
import org.springframework.stereotype.Component;

@Component
public class FailClosedPlatformBudgetPolicyProvider implements PlatformBudgetPolicyProvider {
    @Override
    public BudgetDecision evaluate(UUID accountUuid, Money amount) {
        return new BudgetDecision(false, "PLATFORM_BUDGET_POLICY_NOT_ENABLED");
    }
}
