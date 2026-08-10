package com.aicommerce.platform.ai.infrastructure.provider;

import java.util.Map;
import java.math.BigDecimal;
import java.util.List;

import com.aicommerce.platform.ai.application.ImageGenerationProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
public class StubImageGenerationProvider implements ImageGenerationProvider {

    @Override
    public ImageSubmission submit(ImageRequest request) {
        return new ImageSubmission(
                "stub-image-" + request.generationJobUuid(),
                "deterministic-local-stub",
                Map.of("fixture", "stage-03"));
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
