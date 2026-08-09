package com.aicommerce.platform.ai.infrastructure.provider;

import java.util.Map;

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
}
