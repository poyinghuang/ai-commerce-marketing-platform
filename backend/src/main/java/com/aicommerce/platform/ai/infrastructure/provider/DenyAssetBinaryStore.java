package com.aicommerce.platform.ai.infrastructure.provider;

import java.util.UUID;

import com.aicommerce.platform.ai.application.AiGenerationException;
import com.aicommerce.platform.ai.application.AssetBinaryStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production | (!local & !test)")
public class DenyAssetBinaryStore implements AssetBinaryStore {
    @Override
    public BinaryObject read(SourceReference reference) {
        throw unavailable();
    }

    @Override
    public StoredBinary writeGenerated(UUID generationJobUuid, UUID productUuid, byte[] bytes, String mediaType) {
        throw unavailable();
    }

    private AiGenerationException unavailable() {
        return new AiGenerationException("AI_PROVIDER_NOT_CONFIGURED", "A trusted binary store is not configured");
    }
}
