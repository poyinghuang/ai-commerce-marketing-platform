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
        if (maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("maxAttempts must be 1..10");
    }
}
