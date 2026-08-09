package com.aicommerce.platform.ai.application;

import java.util.UUID;

public interface AssetBinaryStore {

    BinaryObject read(SourceReference reference);

    StoredBinary writeGenerated(UUID generationJobUuid, UUID productUuid, byte[] bytes, String mediaType);

    record SourceReference(UUID assetUuid, UUID productUuid, String providerFileId,
            String mediaType, long sizeBytes, String checksumSha256) {
    }

    record BinaryObject(String handle, byte[] bytes, String mediaType, String checksumSha256) {
    }

    record StoredBinary(String provider, String providerFileId, byte[] bytes,
            String mediaType, long sizeBytes, String checksumSha256) {
    }
}
