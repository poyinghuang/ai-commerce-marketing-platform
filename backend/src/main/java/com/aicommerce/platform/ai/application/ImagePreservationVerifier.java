package com.aicommerce.platform.ai.application;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

@Component
public class ImagePreservationVerifier {
    private static final int MAX_BYTES = 16_777_216;
    private static final long MAX_PIXELS = 16_777_216L;

    public ImageInfo inspectSource(byte[] bytes, String expectedMediaType, long expectedSize,
            String expectedChecksum) {
        BufferedImage image = decode(bytes, "source", "AI_SOURCE_ASSET_INVALID");
        String mediaType = mediaType(bytes, "AI_SOURCE_ASSET_INVALID");
        String checksum = sha256(bytes);
        if (!mediaType.equals(expectedMediaType) || bytes.length != expectedSize
                || !checksum.equals(expectedChecksum)) {
            throw new AiGenerationException("AI_SOURCE_ASSET_INVALID",
                    "Source Asset metadata does not match its binary");
        }
        return new ImageInfo(image.getWidth(), image.getHeight(), mediaType, checksum);
    }

    public Evidence verify(byte[] sourceBytes, byte[] maskBytes, byte[] outputBytes) {
        BufferedImage source = decode(sourceBytes, "source", "AI_SOURCE_ASSET_INVALID");
        BufferedImage output = decode(outputBytes, "output", "AI_OUTPUT_INVALID");
        if (source.getWidth() != output.getWidth() || source.getHeight() != output.getHeight()) {
            throw new AiGenerationException("AI_OUTPUT_INVALID", "Generated image dimensions do not match source");
        }
        BufferedImage mask = maskBytes == null ? null : decode(maskBytes, "mask", "AI_SOURCE_ASSET_INVALID");
        if (mask != null && (mask.getWidth() != source.getWidth() || mask.getHeight() != source.getHeight())) {
            throw new AiGenerationException("AI_SOURCE_ASSET_INVALID", "Mask dimensions do not match source");
        }
        MessageDigest protectedDigest = digest();
        int protectedCount = 0;
        int changedCount = 0;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int sourcePixel = source.getRGB(x, y);
                boolean protectedPixel = mask == null
                        ? ((sourcePixel >>> 24) & 0xff) > 0
                        : maskValue(mask.getRGB(x, y)) > 0;
                if (!protectedPixel) continue;
                protectedCount++;
                update(protectedDigest, sourcePixel);
                if (sourcePixel != output.getRGB(x, y)) changedCount++;
            }
        }
        if (protectedCount == 0) {
            throw new AiGenerationException(mask == null ? "AI_MASK_REQUIRED" : "AI_SOURCE_ASSET_INVALID",
                    "Protected Product region is empty");
        }
        String status = changedCount == 0 ? "PASSED" : "BLOCKED";
        String details = "{\"changedPixelCount\":" + changedCount
                + ",\"protectedPixelCount\":" + protectedCount + "}";
        return new Evidence(source.getWidth(), source.getHeight(), mediaType(outputBytes, "AI_OUTPUT_INVALID"),
                sha256(sourceBytes), maskBytes == null ? null : sha256(maskBytes), sha256(outputBytes),
                HexFormat.of().formatHex(protectedDigest.digest()), status, details);
    }

    private BufferedImage decode(byte[] bytes, String label, String errorCode) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new AiGenerationException(errorCode, label + " image size is invalid");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > 4096 || image.getHeight() > 4096
                    || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
                throw new AiGenerationException(errorCode, label + " image is invalid");
            }
            return image;
        } catch (AiGenerationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiGenerationException(errorCode, label + " image cannot be decoded", exception);
        }
    }

    private int maskValue(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha < 255) return alpha;
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        return Math.max(red, Math.max(green, blue));
    }

    private void update(MessageDigest digest, int argb) {
        digest.update((byte) ((argb >>> 24) & 0xff));
        digest.update((byte) ((argb >>> 16) & 0xff));
        digest.update((byte) ((argb >>> 8) & 0xff));
        digest.update((byte) (argb & 0xff));
    }

    private String mediaType(byte[] bytes, String errorCode) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47) return "image/png";
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8) return "image/jpeg";
        throw new AiGenerationException(errorCode, "Image media type is unsupported");
    }

    private String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Evidence(int width, int height, String mediaType, String sourceChecksum,
            String maskChecksum, String outputChecksum, String protectedPixelsChecksum,
            String status, String detailsJson) {
    }

    public record ImageInfo(int width, int height, String mediaType, String checksum) {
    }
}
