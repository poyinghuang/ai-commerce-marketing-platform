package com.aicommerce.platform.connector.sheets.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class SheetImportApplicationPortIsolationTest {

    @Test
    void applicationAndDomainPackagesUseThePortAndOmitGoogleSdk() throws Exception {
        assertSourceTree(applicationSource(), true);
        assertSourceTree(domainSource(), false);
        assertClassTree(compiledApplication());
        assertClassTree(compiledDomain());
    }

    private static void assertSourceTree(Path root, boolean requirePort) throws IOException {
        AtomicBoolean mentionsPort = new AtomicBoolean();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                assertThat(text)
                        .as(path.toString())
                        .doesNotContain("GoogleSheetValuesProvider")
                        .doesNotContain("StubSheetValuesProvider")
                        .doesNotContain("com.google.auth")
                        .doesNotContain("com.google.api");
                if (text.contains("SheetValuesProvider")) {
                    mentionsPort.set(true);
                }
            });
        }
        if (requirePort) {
            assertThat(mentionsPort.get()).isTrue();
        }
    }

    private static void assertClassTree(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                String bytes;
                try {
                    bytes = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                assertThat(bytes)
                        .as(path.toString())
                        .doesNotContain("GoogleSheetValuesProvider")
                        .doesNotContain("com/google/auth")
                        .doesNotContain("com/google/api");
            });
        }
    }

    private static Path applicationSource() {
        return sourceDirectory("connector/sheets/application");
    }

    private static Path domainSource() {
        return sourceDirectory("connector/sheets/domain");
    }

    private static Path sourceDirectory(String relative) {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/java/com/aicommerce/platform/" + relative);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = cwd.resolve("backend/src/main/java/com/aicommerce/platform/" + relative);
        assertThat(nested).isDirectory();
        return nested;
    }

    private static Path compiledApplication() throws Exception {
        return compiledPackage(SheetValuesProvider.class, "com/aicommerce/platform/connector/sheets/application");
    }

    private static Path compiledDomain() throws Exception {
        return compiledPackage(
                com.aicommerce.platform.connector.sheets.domain.SheetImportJob.class,
                "com/aicommerce/platform/connector/sheets/domain");
    }

    private static Path compiledPackage(Class<?> type, String packagePath) throws Exception {
        URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path root = Path.of(location).resolve(packagePath);
        assertThat(root).isDirectory();
        return root;
    }
}
