package com.aicommerce.platform.ai.application;

import java.util.UUID;

public record CreateTextGenerationBatchCommand(
        UUID productUuid,
        UUID creativePlanUuid,
        String templateKey,
        String modelProfile,
        int variationCount) {
}
