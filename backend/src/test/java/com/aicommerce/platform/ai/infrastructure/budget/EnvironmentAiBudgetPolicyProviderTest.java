package com.aicommerce.platform.ai.infrastructure.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicommerce.platform.ai.application.AiBudgetUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentAiBudgetPolicyProviderTest {

    @Test
    void validHumanControlledConfigurationProducesPolicy() {
        var environment = new MockEnvironment()
                .withProperty("AI_BUDGET_CURRENCY", "USD")
                .withProperty("AI_MAX_JOB_COST", "1.000000")
                .withProperty("AI_MAX_BATCH_COST", "5.000000")
                .withProperty("AI_MAX_DAILY_COST", "20.000000");
        var policy = new EnvironmentAiBudgetPolicyProvider(environment).currentPolicy();
        assertThat(policy.currency()).isEqualTo("USD");
        assertThat(policy.maximumDailyCost().toPlainString()).isEqualTo("20.000000");
    }

    @Test
    void missingMalformedOrInconsistentConfigurationFailsClosed() {
        assertThatThrownBy(() -> new EnvironmentAiBudgetPolicyProvider(new MockEnvironment()).currentPolicy())
                .isInstanceOf(AiBudgetUnavailableException.class);
        var inconsistent = new MockEnvironment()
                .withProperty("AI_BUDGET_CURRENCY", "USD")
                .withProperty("AI_MAX_JOB_COST", "10")
                .withProperty("AI_MAX_BATCH_COST", "5")
                .withProperty("AI_MAX_DAILY_COST", "20");
        assertThatThrownBy(() -> new EnvironmentAiBudgetPolicyProvider(inconsistent).currentPolicy())
                .isInstanceOf(AiBudgetUnavailableException.class);
    }
}
