package com.aicommerce.platform.asset.web;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AssetResponse(UUID assetUuid, UUID productUuid, UUID creativePlanUuid, UUID campaignUuid,
        AssetType assetType, String purpose, String storageProvider, String providerFileId,
        String fileUrl, String mediaType, String originalFilename, Long sizeBytes,
        String checksumSha256, Map<String,Object> providerMetadata, LifecycleStatus lifecycleStatus,
        Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
    static AssetResponse from(Asset a) {
        return new AssetResponse(a.getAssetUuid(),a.getProductUuid(),a.getCreativePlanUuid(),a.getCampaignUuid(),
                a.getAssetType(),a.getPurpose(),a.getStorageProvider(),a.getProviderFileId(),a.getFileUrl(),
                a.getMediaType(),a.getOriginalFilename(),a.getSizeBytes(),a.getChecksumSha256(),a.getProviderMetadata(),
                a.getLifecycleStatus(),a.getArchivedAt(),a.getCreatedAt(),a.getUpdatedAt(),a.getVersion());
    }
}
