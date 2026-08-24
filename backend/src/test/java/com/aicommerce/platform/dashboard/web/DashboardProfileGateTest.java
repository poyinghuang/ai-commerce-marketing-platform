package com.aicommerce.platform.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aicommerce.platform.dashboard.application.DashboardService;

class DashboardProfileGateTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(TestDependencies.class, DashboardController.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean
        DashboardService dashboardService() {
            return Mockito.mock(DashboardService.class);
        }
    }

    @Test
    void exactTestFakeEnabledGateCreatesController() {
        runner("test").withPropertyValues(
                "platform.adapter=fake", "platform.web.enabled=true", "platform.stage5.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DashboardController.class));
    }

    @Test
    void missingFlagWrongAdapterDefaultAndProductionFailClosed() {
        runner("test").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DashboardController.class));
        runner("test").withPropertyValues(
                "platform.adapter=meta", "platform.web.enabled=true", "platform.stage5.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DashboardController.class));
        runner("default").withPropertyValues(
                "platform.adapter=fake", "platform.web.enabled=true", "platform.stage5.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DashboardController.class));
        runner("production").withPropertyValues(
                "platform.adapter=fake", "platform.web.enabled=true", "platform.stage5.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DashboardController.class));
    }
}
