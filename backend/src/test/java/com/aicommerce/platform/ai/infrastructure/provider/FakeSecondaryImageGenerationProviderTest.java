package com.aicommerce.platform.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.ai.application.ImageGenerationProvider.ImageRequest;
import org.junit.jupiter.api.Test;

class FakeSecondaryImageGenerationProviderTest {

    private final FakeSecondaryImageGenerationProvider provider = new FakeSecondaryImageGenerationProvider();

    @Test
    void clonesSourcePixelsUnlessTheChangedPixelFixtureHandleIsUsed() {
        byte[] source = StubAssetBinaryStore.fixture();

        assertThat(result(StubAssetBinaryStore.SOURCE_HANDLE, source)).isEqualTo(source);
        assertThat(result(StubAssetBinaryStore.CHANGED_PIXEL_SOURCE_HANDLE, source))
                .isNotEqualTo(source)
                .isEqualTo(StubAssetBinaryStore.changedPixelFixture());
    }

    @Test
    void persistsTheNamedSecondaryProviderAndModelKeys() {
        assertThat(provider.jobProviderKey()).isEqualTo("FAKE_SECONDARY_IMAGE");
        assertThat(provider.jobModelKey()).isEqualTo("deterministic-fake-secondary");
        ImageRequest request = request(StubAssetBinaryStore.SOURCE_HANDLE, StubAssetBinaryStore.fixture());
        var submission = provider.submit(request);
        assertThat(submission.providerJobId()).isEqualTo("fake-secondary-image-" + request.generationJobUuid());
        assertThat(submission.modelLabel()).isEqualTo("deterministic-fake-secondary");
        assertThat(provider.await(request, submission).actualCost()).isEqualByComparingTo("0");
    }

    private byte[] result(String sourceHandle, byte[] source) {
        ImageRequest request = request(sourceHandle, source);
        var submission = provider.submit(request);
        return provider.await(request, submission).bytes();
    }

    private ImageRequest request(String sourceHandle, byte[] source) {
        return new ImageRequest(UUID.randomUUID(), "background-composite-v1", "1",
                Map.of("prompt", "fixture"), sourceHandle, null, source, null, 4, 4, "png",
                Duration.ofSeconds(1));
    }
}
