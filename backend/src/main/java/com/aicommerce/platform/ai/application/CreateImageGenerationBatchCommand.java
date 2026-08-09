package com.aicommerce.platform.ai.application;

import java.util.UUID;

public record CreateImageGenerationBatchCommand(UUID productUuid, UUID creativePlanUuid,
        String templateKey, String workflowKey, String modelProfile,
        UUID sourceAssetUuid, UUID maskAssetUuid) {
}
