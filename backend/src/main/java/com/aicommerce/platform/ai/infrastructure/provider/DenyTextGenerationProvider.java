package com.aicommerce.platform.ai.infrastructure.provider;

import com.aicommerce.platform.ai.application.AiProviderException;
import com.aicommerce.platform.ai.application.TextGenerationProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production | (!local & !test)")
public class DenyTextGenerationProvider implements TextGenerationProvider {

    @Override
    public TextResult generate(TextRequest request) {
        throw new AiProviderException(
                "AI_PROVIDER_NOT_CONFIGURED",
                "A trusted text generation provider is not configured");
    }
}
