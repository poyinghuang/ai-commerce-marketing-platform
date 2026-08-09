package com.aicommerce.platform.ai.infrastructure.provider;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import com.aicommerce.platform.ai.application.AiGenerationException;
import com.aicommerce.platform.ai.application.AssetBinaryStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
public class StubAssetBinaryStore implements AssetBinaryStore {

    public static final String SOURCE_HANDLE = "stub-alpha-source-v1";
    private final Map<UUID, StoredBinary> generated = new ConcurrentHashMap<>();

    @Override
    public BinaryObject read(SourceReference reference) {
        if (!SOURCE_HANDLE.equals(reference.providerFileId())) {
            throw new AiGenerationException("AI_SOURCE_ASSET_INVALID", "Stub source handle is invalid");
        }
        byte[] bytes = fixture();
        String checksum = sha256(bytes);
        if (!"image/png".equals(reference.mediaType()) || reference.sizeBytes() != bytes.length
                || !checksum.equals(reference.checksumSha256())) {
            throw new AiGenerationException("AI_SOURCE_ASSET_INVALID", "Source Asset metadata does not match binary");
        }
        return new BinaryObject(reference.providerFileId(), bytes, "image/png", checksum);
    }

    @Override
    public StoredBinary writeGenerated(UUID jobUuid, UUID productUuid, byte[] bytes, String mediaType) {
        if (bytes == null || bytes.length == 0 || bytes.length > 16_777_216) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Generated binary size is invalid");
        }
        StoredBinary candidate = new StoredBinary("LOCAL_STUB", "generated-" + jobUuid,
                bytes.clone(), mediaType, bytes.length, sha256(bytes));
        StoredBinary existing = generated.putIfAbsent(jobUuid, candidate);
        if (existing != null && !existing.checksumSha256().equals(candidate.checksumSha256())) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Generated binary idempotency conflict");
        }
        return existing == null ? candidate : existing;
    }

    public static byte[] fixture() {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setRGB(x, y, 0x00000000);
            image.setRGB(1, 1, new Color(220, 20, 60, 255).getRGB());
            image.setRGB(2, 1, new Color(220, 20, 60, 255).getRGB());
            image.setRGB(1, 2, new Color(220, 20, 60, 255).getRGB());
            image.setRGB(2, 2, new Color(220, 20, 60, 255).getRGB());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create deterministic image fixture", exception);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
