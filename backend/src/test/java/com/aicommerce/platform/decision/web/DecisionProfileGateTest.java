package com.aicommerce.platform.decision.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aicommerce.platform.decision.application.DecisionService;

class DecisionProfileGateTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(TestDependencies.class, DecisionController.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean
        DecisionService decisionService() {
            return Mockito.mock(DecisionService.class);
        }
    }

    @Test
    void exactTestFakeEnabledGateCreatesController() {
        runner("test").withPropertyValues(
                "platform.adapter=fake", "platform.web.enabled=true", "platform.stage6.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DecisionController.class));
    }

    @Test
    void missingFlagWrongAdapterDefaultAndProductionFailClosed() {
        runner("test").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DecisionController.class));
        runner("test").withPropertyValues(
                "platform.adapter=meta", "platform.web.enabled=true", "platform.stage6.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DecisionController.class));
        runner("default").withPropertyValues(
                "platform.adapter=fake", "platform.web.enabled=true", "platform.stage6.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DecisionController.class));
        runner("production").withPropertyValues(
                "platform.adapter=fake", "platform.web.enabled=true", "platform.stage6.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DecisionController.class));
    }
}
