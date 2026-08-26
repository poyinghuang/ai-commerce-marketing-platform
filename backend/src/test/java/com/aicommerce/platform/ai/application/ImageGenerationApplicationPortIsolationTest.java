package com.aicommerce.platform.ai.application;

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

class ImageGenerationApplicationPortIsolationTest {

    @Test
    void applicationPackageUsesImageGenerationProviderAndOmitsConcreteProviders() throws Exception {
        Path source = applicationSource();
        AtomicBoolean mentionsPort = new AtomicBoolean();
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                assertThat(text)
                        .as(path.toString())
                        .doesNotContain("FakeSecondaryImageGenerationProvider")
                        .doesNotContain("ComfyUiImageGenerationProvider");
                if (text.contains("ImageGenerationProvider")) {
                    mentionsPort.set(true);
                }
            });
        }
        assertThat(mentionsPort.get()).isTrue();

        Path classes = compiledApplication();
        try (Stream<Path> files = Files.walk(classes)) {
            files.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                String bytes;
                try {
                    bytes = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                assertThat(bytes)
                        .as(path.toString())
                        .doesNotContain("FakeSecondaryImageGenerationProvider")
                        .doesNotContain("ComfyUiImageGenerationProvider")
                        .doesNotContain("com/aicommerce/platform/ai/infrastructure/provider/FakeSecondary")
                        .doesNotContain("com/aicommerce/platform/ai/infrastructure/provider/ComfyUi");
            });
        }
    }

    private static Path applicationSource() {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/java/com/aicommerce/platform/ai/application");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = cwd.resolve("backend/src/main/java/com/aicommerce/platform/ai/application");
        assertThat(nested).isDirectory();
        return nested;
    }

    private static Path compiledApplication() throws Exception {
        URI location = ImageGenerationService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path root = Path.of(location);
        Path application = root.resolve("com/aicommerce/platform/ai/application");
        assertThat(application).isDirectory();
        return application;
    }
}
