package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aicommerce.platform.delivery.application.Stage4CService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class Stage7C2AdProfileGateTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(TestDependencies.class, Stage7C2AdController.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean Stage4CService stage4CService() { return mock(Stage4CService.class); }
    }

    @Test void exactTestFakeGoogleWebAndStage4cGateCreatesController() {
        runner("test").withPropertyValues(
                        "platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage4c.enabled=true",
                        "platform.stage7.google.web.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(Stage7C2AdController.class));
    }

    @Test void missingStage4cOrGoogleWebFailClosed() {
        runner("test").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage7.google.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage7C2AdController.class));
        runner("test").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage4c.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage7C2AdController.class));
        runner("production").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage4c.enabled=true",
                        "platform.stage7.google.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage7C2AdController.class));
    }
}
