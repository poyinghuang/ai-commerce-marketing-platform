package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import com.aicommerce.platform.connector.sheets.application.SheetValuesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("sheetFlags")
    void selectsExactlyOneSheetProviderForTheNamedFlag(
            String scenario, String[] profiles, String sheetsProvider, Class<?> expected) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            if (sheetsProvider != null) {
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("sheets-flag", Map.of("platform.sheets.provider", sheetsProvider)));
            }
            context.register(StubSheetValuesProvider.class, GoogleSheetValuesProvider.class);
            context.refresh();
            Map<String, SheetValuesProvider> providers = context.getBeansOfType(SheetValuesProvider.class);
            assertThat(providers).hasSize(1);
            assertThat(providers.values().iterator().next()).isExactlyInstanceOf(expected);
        }
    }

    @Test
    void localWrongSheetsProviderValueLoadsNoStubOrGoogleBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("sheets-flag", Map.of("platform.sheets.provider", "excel")));
            context.register(StubSheetValuesProvider.class, GoogleSheetValuesProvider.class);
            context.refresh();
            assertThat(context.getBeansOfType(SheetValuesProvider.class)).isEmpty();
            assertThat(context.getBeansOfType(GoogleSheetValuesProvider.class)).isEmpty();
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

    private static Stream<Arguments> sheetFlags() {
        return Stream.of(
                Arguments.of("local-default-stub", new String[] {"local"}, null, StubSheetValuesProvider.class),
                Arguments.of("local-stub", new String[] {"local"}, "stub", StubSheetValuesProvider.class),
                Arguments.of("local-google", new String[] {"local"}, "google", GoogleSheetValuesProvider.class),
                Arguments.of("test-google", new String[] {"test"}, "google", GoogleSheetValuesProvider.class),
                Arguments.of("production-ignores-stub-flag", new String[] {"production"}, "stub",
                        GoogleSheetValuesProvider.class),
                Arguments.of("production-local-stays-google", new String[] {"production", "local"}, "google",
                        GoogleSheetValuesProvider.class));
    }
}
