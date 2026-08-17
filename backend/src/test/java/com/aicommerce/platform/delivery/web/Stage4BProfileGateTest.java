package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aicommerce.platform.delivery.application.Stage4BService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class Stage4BProfileGateTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(TestDependencies.class,Stage4BController.class);
    }

    @Configuration(proxyBeanMethods=false)
    static class TestDependencies {
        @Bean Stage4BService stage4BService(){return mock(Stage4BService.class);}
    }

    @Test void exactTestFakeEnabledGateCreatesController() {
        runner("test").withPropertyValues("platform.adapter=fake","platform.web.enabled=true","platform.stage4b.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(Stage4BController.class));
    }

    @Test void missingFlagWrongAdapterDefaultAndProductionFailClosed() {
        runner("test").withPropertyValues("platform.adapter=fake","platform.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4BController.class));
        runner("test").withPropertyValues("platform.adapter=meta","platform.web.enabled=true","platform.stage4b.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4BController.class));
        runner("default").withPropertyValues("platform.adapter=fake","platform.web.enabled=true","platform.stage4b.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4BController.class));
        runner("production").withPropertyValues("platform.adapter=fake","platform.web.enabled=true","platform.stage4b.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(Stage4BController.class));
    }
}
