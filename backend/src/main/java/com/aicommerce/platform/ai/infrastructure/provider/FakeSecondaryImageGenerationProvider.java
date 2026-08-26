package com.aicommerce.platform.ai.infrastructure.provider;

import java.util.Map;
import java.math.BigDecimal;
import java.util.List;

import com.aicommerce.platform.ai.application.ImageGenerationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
@ConditionalOnProperty(name = "platform.image.provider", havingValue = "fake-secondary")
public class FakeSecondaryImageGenerationProvider implements ImageGenerationProvider {

    public static final String PROVIDER_KEY = "FAKE_SECONDARY_IMAGE";
    public static final String MODEL_KEY = "deterministic-fake-secondary";

    @Override
    public String jobProviderKey() {
        return PROVIDER_KEY;
    }

    @Override
    public String jobModelKey() {
        return MODEL_KEY;
    }

    @Override
    public ImageSubmission submit(ImageRequest request) {
        return new ImageSubmission(
                "fake-secondary-image-" + request.generationJobUuid(),
                MODEL_KEY,
                Map.of("fixture", "stage-07a"));
    }

    @Override
    public ImageResult await(ImageRequest request, ImageSubmission submission) {
        byte[] result = StubAssetBinaryStore.CHANGED_PIXEL_SOURCE_HANDLE.equals(request.sourceHandle())
                ? StubAssetBinaryStore.changedPixelFixture()
                : request.sourceBytes().clone();
        return new ImageResult(result, "image/png", submission.modelLabel(),
                BigDecimal.ZERO, List.of(), submission.metadata());
    }
}
