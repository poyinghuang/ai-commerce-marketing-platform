package com.aicommerce.platform.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.ai.application.ImageGenerationProvider.ImageRequest;
import org.junit.jupiter.api.Test;

class StubImageGenerationProviderTest {

    private final StubImageGenerationProvider provider = new StubImageGenerationProvider();

    @Test
    void returnsSourcePixelsForNormalFixtureAndChangesProtectedPixelOnlyForExplicitFixture() {
        byte[] source = StubAssetBinaryStore.fixture();

        assertThat(result(StubAssetBinaryStore.SOURCE_HANDLE, source)).isEqualTo(source);
        assertThat(result(StubAssetBinaryStore.CHANGED_PIXEL_SOURCE_HANDLE, source))
                .isNotEqualTo(source)
                .isEqualTo(StubAssetBinaryStore.changedPixelFixture());
    }

    private byte[] result(String sourceHandle, byte[] source) {
        ImageRequest request = new ImageRequest(UUID.randomUUID(), "background-composite-v1", "1",
                Map.of("prompt", "fixture"), sourceHandle, null, source, null, 4, 4, "png",
                Duration.ofSeconds(1));
        var submission = provider.submit(request);
        return provider.await(request, submission).bytes();
    }
}
