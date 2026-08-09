package com.aicommerce.platform.ai.infrastructure.budget;

import java.math.BigDecimal;

import com.aicommerce.platform.ai.application.AiBudgetPolicy;
import com.aicommerce.platform.ai.application.AiBudgetPolicyProvider;
import com.aicommerce.platform.ai.application.AiBudgetUnavailableException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentAiBudgetPolicyProvider implements AiBudgetPolicyProvider {

    private final Environment environment;

    public EnvironmentAiBudgetPolicyProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public AiBudgetPolicy currentPolicy() {
        try {
            return new AiBudgetPolicy(
                    required("AI_BUDGET_CURRENCY"),
                    decimal("AI_MAX_JOB_COST"),
                    decimal("AI_MAX_BATCH_COST"),
                    decimal("AI_MAX_DAILY_COST"));
        } catch (IllegalArgumentException exception) {
            throw new AiBudgetUnavailableException("AI generation is disabled because budget configuration is invalid",
                    exception);
        }
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private BigDecimal decimal(String key) {
        return new BigDecimal(required(key));
    }
}
