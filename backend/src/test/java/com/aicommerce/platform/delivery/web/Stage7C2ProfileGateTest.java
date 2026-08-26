package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aicommerce.platform.delivery.application.Stage4BService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class Stage7C2ProfileGateTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(TestDependencies.class, Stage7C2Controller.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean Stage4BService stage4BService() { return mock(Stage4BService.class); }
    }

    @Test void exactTestFakeGoogleWebGateCreatesController() {
        runner("test").withPropertyValues(
                        "platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage7.google.web.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(Stage7C2Controller.class));
    }

    @Test void missingFlagWrongAdapterDefaultAndProductionFailClosed() {
        runner("test").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage7C2Controller.class));
        runner("test").withPropertyValues("platform.adapter=meta", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage7.google.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage7C2Controller.class));
        runner("default").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage7.google.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage7C2Controller.class));
        runner("production").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                        "platform.stage4b.enabled=true", "platform.stage7.google.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage7C2Controller.class));
    }
}
