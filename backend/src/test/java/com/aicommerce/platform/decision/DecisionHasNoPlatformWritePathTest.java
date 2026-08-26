package com.aicommerce.platform.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.aicommerce.platform.decision.application.DecisionService;
import org.junit.jupiter.api.Test;

class DecisionHasNoPlatformWritePathTest {
    static final List<String> FORBIDDEN_SIMPLE = List.of(
            "PlatformCampaignPort",
            "PlatformAdSetPort",
            "PlatformAdPort",
            "PlatformDeliveryReadPort",
            "PlatformMetricsReadPort");
    private static final List<String> FORBIDDEN_BINARY = List.of(
            "com/aicommerce/platform/delivery/application/port/PlatformCampaignPort",
            "com/aicommerce/platform/delivery/application/port/PlatformAdSetPort",
            "com/aicommerce/platform/delivery/application/port/PlatformAdPort",
            "com/aicommerce/platform/delivery/application/port/PlatformDeliveryReadPort",
            "com/aicommerce/platform/delivery/application/port/PlatformMetricsReadPort");

    @Test
    void decisionPackageHasNoPlatformWriteOrRefreshPorts() throws Exception {
        Path source = decisionSource();
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                for (String name : FORBIDDEN_SIMPLE) {
                    assertThat(text).as(path + " must not mention " + name).doesNotContain(name);
                }
            });
        }
        Path classes = compiledDecision();
        try (Stream<Path> files = Files.walk(classes)) {
            files.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                String bytes;
                try {
                    bytes = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                for (String name : FORBIDDEN_BINARY) {
                    assertThat(bytes).as(path + " must not depend on " + name).doesNotContain(name);
                }
                for (String name : FORBIDDEN_SIMPLE) {
                    assertThat(bytes).as(path + " must not mention " + name).doesNotContain(name);
                }
            });
        }
    }

    private static Path decisionSource() {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/java/com/aicommerce/platform/decision");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = cwd.resolve("backend/src/main/java/com/aicommerce/platform/decision");
        assertThat(nested).isDirectory();
        return nested;
    }

    private static Path compiledDecision() throws Exception {
        URI location = DecisionService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path root = Path.of(location);
        Path decision = root.resolve("com/aicommerce/platform/decision");
        assertThat(decision).isDirectory();
        return decision;
    }
}
