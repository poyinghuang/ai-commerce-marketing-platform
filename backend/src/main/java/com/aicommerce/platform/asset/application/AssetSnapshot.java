package com.aicommerce.platform.asset.application;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record AssetSnapshot(UUID creativePlanUuid, UUID campaignUuid, AssetType assetType, String purpose,
        String storageProvider, String providerFileId, String fileUrl, String mediaType,
        String originalFilename, Long sizeBytes, String checksumSha256,
        Map<String, Object> providerMetadata, LifecycleStatus lifecycleStatus, Instant archivedAt) {
    static AssetSnapshot from(Asset asset) {
        return new AssetSnapshot(asset.getCreativePlanUuid(), asset.getCampaignUuid(), asset.getAssetType(),
                asset.getPurpose(), asset.getStorageProvider(), asset.getProviderFileId(), asset.getFileUrl(),
                asset.getMediaType(), asset.getOriginalFilename(), asset.getSizeBytes(), asset.getChecksumSha256(),
                asset.getProviderMetadata(), asset.getLifecycleStatus(), asset.getArchivedAt());
    }
}
