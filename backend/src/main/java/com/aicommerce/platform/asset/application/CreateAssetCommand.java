package com.aicommerce.platform.asset.application;

import com.aicommerce.platform.asset.domain.AssetType;
import java.util.Map;
import java.util.UUID;

public record CreateAssetCommand(UUID creativePlanUuid, UUID campaignUuid, AssetType assetType,
        String purpose, String storageProvider, String providerFileId, String fileUrl,
        String mediaType, String originalFilename, Long sizeBytes, String checksumSha256,
        Map<String, Object> providerMetadata) {}
