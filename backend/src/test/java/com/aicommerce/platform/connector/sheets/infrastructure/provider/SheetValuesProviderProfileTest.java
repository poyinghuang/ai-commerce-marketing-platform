package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import com.aicommerce.platform.connector.sheets.application.SheetValuesProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SheetValuesProviderProfileTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("profileCombinations")
    void selectsExactlyOneFailClosedProvider(String scenario, String[] profiles,
            Class<? extends SheetValuesProvider> expected) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.register(StubSheetValuesProvider.class, GoogleSheetValuesProvider.class);
            context.refresh();
            Map<String, SheetValuesProvider> providers = context.getBeansOfType(SheetValuesProvider.class);
            assertThat(providers).hasSize(1);
            assertThat(providers.values().iterator().next()).isExactlyInstanceOf(expected);
        }
    }

    private static Stream<Arguments> profileCombinations() {
        return Stream.of(
                Arguments.of("local", new String[] {"local"}, StubSheetValuesProvider.class),
                Arguments.of("test", new String[] {"test"}, StubSheetValuesProvider.class),
                Arguments.of("default", new String[0], GoogleSheetValuesProvider.class),
                Arguments.of("production", new String[] {"production"}, GoogleSheetValuesProvider.class),
                Arguments.of("production,local", new String[] {"production", "local"}, GoogleSheetValuesProvider.class),
                Arguments.of("production,test", new String[] {"production", "test"}, GoogleSheetValuesProvider.class));
    }
}
