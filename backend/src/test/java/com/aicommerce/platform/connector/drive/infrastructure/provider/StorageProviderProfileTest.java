package com.aicommerce.platform.connector.drive.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicommerce.platform.connector.drive.application.StorageProvider;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class StorageProviderProfileTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void selectsExactlyOneProvider(String scenario, String[] profiles, Class<? extends StorageProvider> expected) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.register(StubStorageProvider.class, FakeObjectStorageProvider.class, GoogleDriveStorageProvider.class);
            context.refresh();
            Map<String, StorageProvider> beans = context.getBeansOfType(StorageProvider.class);
            assertThat(beans).hasSize(1);
            assertThat(beans.values().iterator().next()).isExactlyInstanceOf(expected);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("storageFlags")
    void selectsExactlyOneStorageProviderForTheNamedFlag(
            String scenario, String[] profiles, String storageProvider, Class<?> expected) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            if (storageProvider != null) {
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("storage-flag", Map.of("platform.storage.provider", storageProvider)));
            }
            context.register(StubStorageProvider.class, FakeObjectStorageProvider.class, GoogleDriveStorageProvider.class);
            context.refresh();
            Map<String, StorageProvider> beans = context.getBeansOfType(StorageProvider.class);
            assertThat(beans).hasSize(1);
            assertThat(beans.values().iterator().next()).isExactlyInstanceOf(expected);
        }
    }

    @Test
    void localWrongStorageProviderValueLoadsNoStubOrFakeObjectBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("storage-flag", Map.of("platform.storage.provider", "s3")));
            context.register(StubStorageProvider.class, FakeObjectStorageProvider.class, GoogleDriveStorageProvider.class);
            context.refresh();
            assertThat(context.getBeansOfType(StorageProvider.class)).isEmpty();
            assertThat(context.getBeansOfType(FakeObjectStorageProvider.class)).isEmpty();
        }
    }

    static Stream<Arguments> profiles() {
        return Stream.of(
                Arguments.of("local", new String[] {"local"}, StubStorageProvider.class),
                Arguments.of("test", new String[] {"test"}, StubStorageProvider.class),
                Arguments.of("default", new String[0], GoogleDriveStorageProvider.class),
                Arguments.of("production", new String[] {"production"}, GoogleDriveStorageProvider.class),
                Arguments.of("production,local", new String[] {"production", "local"}, GoogleDriveStorageProvider.class),
                Arguments.of("production,test", new String[] {"production", "test"}, GoogleDriveStorageProvider.class));
    }

    static Stream<Arguments> storageFlags() {
        return Stream.of(
                Arguments.of("local-default-stub", new String[] {"local"}, null, StubStorageProvider.class),
                Arguments.of("local-stub", new String[] {"local"}, "stub", StubStorageProvider.class),
                Arguments.of("test-fake-object", new String[] {"test"}, "fake-object", FakeObjectStorageProvider.class),
                Arguments.of("local-fake-object", new String[] {"local"}, "fake-object", FakeObjectStorageProvider.class),
                Arguments.of("production-ignores-fake-object", new String[] {"production"}, "fake-object",
                        GoogleDriveStorageProvider.class),
                Arguments.of("production-local-ignores-fake-object", new String[] {"production", "local"}, "fake-object",
                        GoogleDriveStorageProvider.class));
    }
}
