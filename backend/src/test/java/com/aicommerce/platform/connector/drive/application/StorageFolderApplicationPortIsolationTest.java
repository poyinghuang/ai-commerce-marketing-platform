package com.aicommerce.platform.connector.drive.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.aicommerce.platform.asset.application.AssetCommandService;
import org.junit.jupiter.api.Test;

class StorageFolderApplicationPortIsolationTest {
    private static final List<String> FORBIDDEN = List.of(
            "FakeObjectStorageProvider",
            "GoogleDriveStorageProvider",
            "com.google.auth",
            "com.google.api",
            "com/aicommerce/platform/connector/drive/infrastructure/provider/FakeObject",
            "com/aicommerce/platform/connector/drive/infrastructure/provider/GoogleDrive",
            "com/google/auth",
            "com/google/api");

    @Test
    void assetPackageAndFolderServiceDoNotImportFakeObjectOrGoogleSdk() throws Exception {
        assertSourceTree(assetSource());
        assertSourceFile(folderServiceSource());
        assertClassTree(compiledAsset());
        assertClassFile(compiledFolderService());
    }

    private static void assertSourceTree(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                for (String forbidden : FORBIDDEN) {
                    assertThat(text).as(path + " must not mention " + forbidden).doesNotContain(forbidden);
                }
            });
        }
    }

    private static void assertSourceFile(Path path) throws IOException {
        String text = Files.readString(path);
        for (String forbidden : FORBIDDEN) {
            assertThat(text).as(path + " must not mention " + forbidden).doesNotContain(forbidden);
        }
        assertThat(text).contains("StorageProvider");
    }

    private static void assertClassTree(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".class")).forEach(StorageFolderApplicationPortIsolationTest::assertClassBytes);
        }
    }

    private static void assertClassFile(Path path) {
        assertClassBytes(path);
    }

    private static void assertClassBytes(Path path) {
        String bytes;
        try {
            bytes = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        for (String forbidden : FORBIDDEN) {
            assertThat(bytes).as(path + " must not depend on " + forbidden).doesNotContain(forbidden);
        }
    }

    private static Path assetSource() {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/java/com/aicommerce/platform/asset");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = cwd.resolve("backend/src/main/java/com/aicommerce/platform/asset");
        assertThat(nested).isDirectory();
        return nested;
    }

    private static Path folderServiceSource() {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/java/com/aicommerce/platform/connector/drive/application/ProductStorageFolderService.java");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path nested = cwd.resolve("backend/src/main/java/com/aicommerce/platform/connector/drive/application/ProductStorageFolderService.java");
        assertThat(nested).isRegularFile();
        return nested;
    }

    private static Path compiledAsset() throws Exception {
        URI location = AssetCommandService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path asset = Path.of(location).resolve("com/aicommerce/platform/asset");
        assertThat(asset).isDirectory();
        return asset;
    }

    private static Path compiledFolderService() throws Exception {
        URI location = ProductStorageFolderService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path service = Path.of(location).resolve("com/aicommerce/platform/connector/drive/application/ProductStorageFolderService.class");
        assertThat(service).isRegularFile();
        return service;
    }
}
