package com.aicommerce.platform.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.aicommerce.platform.ai.infrastructure.provider.StubAssetBinaryStore;
import org.junit.jupiter.api.Test;

class ImagePreservationVerifierTest {

    private final ImagePreservationVerifier verifier = new ImagePreservationVerifier();

    @Test
    void acceptsExactAlphaProtectedPixelsAndReportsBoundedEvidence() {
        byte[] source = StubAssetBinaryStore.fixture();

        var evidence = verifier.verify(source, null, source);

        assertThat(evidence.status()).isEqualTo("PASSED");
        assertThat(evidence.width()).isEqualTo(4);
        assertThat(evidence.height()).isEqualTo(4);
        assertThat(evidence.protectedPixelsChecksum()).hasSize(64);
        assertThat(evidence.detailsJson()).isEqualTo("{\"changedPixelCount\":0,\"protectedPixelCount\":4}");
    }

    @Test
    void marksChangedProtectedPixelAsBlocked() {
        byte[] source = StubAssetBinaryStore.fixture();
        BufferedImage changed = decode(source);
        changed.setRGB(1, 1, 0xff000000);

        assertThat(verifier.verify(source, null, png(changed)).status()).isEqualTo("BLOCKED");
    }

    @Test
    void explicitMaskCanProtectOpaqueSourceRegion() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xff123456);
        source.setRGB(1, 0, 0xffabcdef);
        BufferedImage output = decode(png(source));
        output.setRGB(1, 0, 0xff000000);
        BufferedImage mask = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        mask.setRGB(0, 0, 0xffffffff);

        assertThat(verifier.verify(png(source), png(mask), png(output)).status()).isEqualTo("PASSED");
    }

    @Test
    void rejectsEmptyMaskDimensionMismatchMalformedAndOversizedInput() {
        byte[] source = StubAssetBinaryStore.fixture();
        BufferedImage emptyMask = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        BufferedImage wrongMask = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);

        assertCode(() -> verifier.verify(source, png(emptyMask), source), "AI_SOURCE_ASSET_INVALID");
        assertCode(() -> verifier.verify(source, png(wrongMask), source), "AI_SOURCE_ASSET_INVALID");
        assertCode(() -> verifier.verify(source, null, new byte[] {1, 2, 3}), "AI_OUTPUT_INVALID");
        assertCode(() -> verifier.verify(new byte[16_777_217], null, source), "AI_SOURCE_ASSET_INVALID");
    }

    @Test
    void sourceInspectionRequiresMetadataToMatchBytes() {
        byte[] source = StubAssetBinaryStore.fixture();
        assertThat(verifier.inspectSource(source, "image/png", source.length,
                StubAssetBinaryStore.sha256(source)).width()).isEqualTo(4);
        assertCode(() -> verifier.inspectSource(source, "image/jpeg", source.length,
                StubAssetBinaryStore.sha256(source)), "AI_SOURCE_ASSET_INVALID");
    }

    private void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(AiGenerationException.class)
                .extracting(value -> ((AiGenerationException) value).code()).isEqualTo(code);
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
