package com.aicommerce.platform.asset.application;

import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.common.application.FieldPatch;
import java.util.Map;

public record PatchAssetCommand(FieldPatch<AssetType> assetType, FieldPatch<String> purpose,
        FieldPatch<String> storageProvider, FieldPatch<String> providerFileId,
        FieldPatch<String> fileUrl, FieldPatch<String> mediaType,
        FieldPatch<String> originalFilename, FieldPatch<Long> sizeBytes,
        FieldPatch<String> checksumSha256, FieldPatch<Map<String, Object>> providerMetadata) {}
