package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.aicommerce.platform.delivery.application.Stage4DService;
import java.time.Clock;

class Stage4DProfileGateTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(TestDependencies.class, Stage4DController.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean Stage4DService stage4DService() { return Mockito.mock(Stage4DService.class); }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @Test void exactTestFakeEnabledGateCreatesController() {
        runner("test").withPropertyValues(
                "platform.adapter=fake", "platform.web.enabled=true",
                "platform.stage4b.enabled=true", "platform.stage4d.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(Stage4DController.class));
    }

    @Test void missingFlagWrongAdapterDefaultAndProductionFailClosed() {
        runner("test").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                "platform.stage4b.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4DController.class));
        runner("test").withPropertyValues("platform.adapter=meta", "platform.web.enabled=true",
                "platform.stage4b.enabled=true", "platform.stage4d.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4DController.class));
        runner("default").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                "platform.stage4b.enabled=true", "platform.stage4d.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4DController.class));
        runner("production").withPropertyValues("platform.adapter=fake", "platform.web.enabled=true",
                "platform.stage4b.enabled=true", "platform.stage4d.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4DController.class));
    }
}
