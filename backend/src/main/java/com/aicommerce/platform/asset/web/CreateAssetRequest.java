package com.aicommerce.platform.asset.web;

import com.aicommerce.platform.asset.domain.AssetType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record CreateAssetRequest(UUID creativePlanUuid, UUID campaignUuid,
        @NotNull(message="must not be null") AssetType assetType, String purpose,
        String storageProvider, String providerFileId, String fileUrl, String mediaType,
        String originalFilename, Long sizeBytes, String checksumSha256,
        JsonNode providerMetadata) {}
