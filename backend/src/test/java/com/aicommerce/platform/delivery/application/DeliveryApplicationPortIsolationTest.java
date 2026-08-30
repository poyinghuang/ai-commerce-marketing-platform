package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.aicommerce.platform.delivery.domain.PlatformOperation;
import org.junit.jupiter.api.Test;

class DeliveryApplicationPortIsolationTest {
    private static final List<String> FORBIDDEN = List.of(
            "DeterministicFakeGooglePlatformAdapter",
            "LiveMetaInsightsReadAdapter",
            "com.google.ads",
            "google.ads.googleads",
            "com.facebook.ads",
            "com/aicommerce/platform/delivery/infrastructure/provider/DeterministicFakeGoogle",
            "com/aicommerce/platform/delivery/infrastructure/provider/LiveMetaInsightsReadAdapter",
            "com/google/ads",
            "google/ads/googleads",
            "com/facebook/ads");

    @Test
    void domainAndApplicationDoNotImportTheGoogleFakeAdapterOrGoogleAdsSdk() throws Exception {
        assertSourceTree(source("delivery/domain"));
        assertSourceTree(source("delivery/application"));
        assertClassTree(compiled("com/aicommerce/platform/delivery/domain", PlatformOperation.class));
        assertClassTree(compiled("com/aicommerce/platform/delivery/application", PlatformOperationService.class));
    }

    private static void assertSourceTree(Path root) throws IOException {
        assertThat(root).isDirectory();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                for (String forbidden : FORBIDDEN) {
                    if (forbidden.contains("/")) {
                        continue;
                    }
                    assertThat(text).as(path.toString()).doesNotContain(forbidden);
                }
            });
        }
    }

    private static void assertClassTree(Path root) throws IOException {
        assertThat(root).isDirectory();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                String bytes;
                try {
                    bytes = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                for (String forbidden : FORBIDDEN) {
                    assertThat(bytes).as(path.toString()).doesNotContain(forbidden);
                }
            });
        }
    }

    private static Path source(String relative) {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/java/com/aicommerce/platform/" + relative);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = cwd.resolve("backend/src/main/java/com/aicommerce/platform/" + relative);
        assertThat(nested).isDirectory();
        return nested;
    }

    private static Path compiled(String relative, Class<?> type) throws Exception {
        URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path root = Path.of(location).resolve(relative);
        assertThat(root).isDirectory();
        return root;
    }
}
