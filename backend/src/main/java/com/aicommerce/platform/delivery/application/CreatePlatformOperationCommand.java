package com.aicommerce.platform.delivery.application;

import java.util.UUID;

import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;

public record CreatePlatformOperationCommand(
        UUID operationUuid,
        UUID platformAccountUuid,
        PlatformOperationType operationType,
        PlatformEntityType entityType,
        UUID entityUuid,
        UUID clientRequestUuid,
        String normalizedRequestJson,
        int maxAttempts) {
    public CreatePlatformOperationCommand {
        if (operationUuid == null || platformAccountUuid == null || operationType == null || entityType == null
                || entityUuid == null || clientRequestUuid == null || normalizedRequestJson == null
                || normalizedRequestJson.isBlank()) {
            throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
        }
        if (maxAttempts != 3) throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
    }
}
