package com.aicommerce.platform.delivery.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.application.port.PlatformMetricsReadPort;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class PlatformReadAdapterProfileTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("selections")
    void selectsAtMostOnePrimaryReadAdapter(
            String scenario, String[] profiles, Map<String, Object> properties, Class<?> expected) {
        try (AnnotationConfigApplicationContext context = context(profiles, properties)) {
            if (expected == null) {
                assertThat(context.getBeansOfType(PlatformDeliveryReadPort.class)).isEmpty();
                assertThat(context.getBeansOfType(PlatformMetricsReadPort.class)).isEmpty();
                assertThat(context.getBeansOfType(DeterministicFakePlatformReadAdapter.class)).isEmpty();
                assertThat(context.getBeansOfType(LiveMetaInsightsReadAdapter.class)).isEmpty();
                return;
            }
            assertThat(context.getBeansOfType(PlatformDeliveryReadPort.class)).hasSize(1);
            assertThat(context.getBeansOfType(PlatformMetricsReadPort.class)).hasSize(1);
            assertThat(context.getBean(PlatformDeliveryReadPort.class)).isExactlyInstanceOf(expected);
            assertThat(context.getBean(PlatformMetricsReadPort.class)).isExactlyInstanceOf(expected);
            if (expected == LiveMetaInsightsReadAdapter.class) {
                assertThat(context.getBeansOfType(DeterministicFakePlatformReadAdapter.class)).isEmpty();
            } else {
                assertThat(context.getBeansOfType(LiveMetaInsightsReadAdapter.class)).isEmpty();
            }
        }
    }

    @Test
    void liveBeanLoadsWithBlankTokenAndFailsClosedOnUse() {
        try (AnnotationConfigApplicationContext context = context(new String[] {"test"}, Map.of(
                "platform.adapter", "fake",
                "platform.stage8.insights.live", "true",
                "META_TEST_ACCESS_TOKEN", ""))) {
            assertThat(context.getBeansOfType(LiveMetaInsightsReadAdapter.class)).hasSize(1);
            assertThat(context.getBeansOfType(DeterministicFakePlatformReadAdapter.class)).isEmpty();
            LiveMetaInsightsReadAdapter adapter = context.getBean(LiveMetaInsightsReadAdapter.class);
            assertThatThrownBy(() -> adapter.readObservedState(new PlatformDeliveryReadPort.DeliveryReadCommand(
                            UUID.fromString("00000000-0000-4000-8000-00000000008d"),
                            PlatformEntityType.CAMPAIGN,
                            UUID.fromString("00000000-0000-4000-8000-00000000018d"),
                            "camp_1",
                            PlatformDesiredState.PAUSED)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Meta test access token is not configured");
        }
    }

    static Stream<Arguments> selections() {
        return Stream.of(
                Arguments.of("test-default-fake", new String[] {"test"},
                        Map.of("platform.adapter", "fake"), DeterministicFakePlatformReadAdapter.class),
                Arguments.of("local-default-fake", new String[] {"local"},
                        Map.of("platform.adapter", "fake"), DeterministicFakePlatformReadAdapter.class),
                Arguments.of("test-live-false", new String[] {"test"},
                        Map.of("platform.adapter", "fake", "platform.stage8.insights.live", "false"),
                        DeterministicFakePlatformReadAdapter.class),
                Arguments.of("test-live-true", new String[] {"test"},
                        Map.of("platform.adapter", "fake", "platform.stage8.insights.live", "true",
                                "META_TEST_ACCESS_TOKEN", "test-token"),
                        LiveMetaInsightsReadAdapter.class),
                Arguments.of("local-live-true", new String[] {"local"},
                        Map.of("platform.adapter", "fake", "platform.stage8.insights.live", "true",
                                "META_TEST_ACCESS_TOKEN", "test-token"),
                        LiveMetaInsightsReadAdapter.class),
                Arguments.of("production-ignores-live", new String[] {"production"},
                        Map.of("platform.adapter", "fake", "platform.stage8.insights.live", "true",
                                "META_TEST_ACCESS_TOKEN", "test-token"),
                        null),
                Arguments.of("production-local-ignores-live", new String[] {"production", "local"},
                        Map.of("platform.adapter", "fake", "platform.stage8.insights.live", "true",
                                "META_TEST_ACCESS_TOKEN", "test-token"),
                        null),
                Arguments.of("default-profile", new String[] {"default"},
                        Map.of("platform.adapter", "fake", "platform.stage8.insights.live", "true"),
                        null));
    }

    private static AnnotationConfigApplicationContext context(String[] profiles, Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles);
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("stage8c-flags", new LinkedHashMap<>(properties)));
        }
        context.register(ClockConfig.class, DeterministicFakePlatformReadAdapter.class,
                LiveMetaInsightsReadAdapter.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
