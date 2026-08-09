package com.aicommerce.platform.ai.infrastructure.provider;

import com.aicommerce.platform.ai.application.AiProviderException;
import com.aicommerce.platform.ai.application.ImageGenerationProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production | (!local & !test & !comfyui)")
public class DenyImageGenerationProvider implements ImageGenerationProvider {

    @Override
    public ImageSubmission submit(ImageRequest request) {
        throw new AiProviderException(
                "AI_PROVIDER_NOT_CONFIGURED",
                "A trusted image generation provider is not configured");
    }

    @Override
    public ImageResult await(ImageRequest request, ImageSubmission submission) {
        throw new AiProviderException(
                "AI_PROVIDER_NOT_CONFIGURED",
                "A trusted image generation provider is not configured");
    }
}
