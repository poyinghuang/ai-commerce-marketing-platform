package com.aicommerce.platform.delivery.application.port;
import java.util.UUID; import com.aicommerce.platform.delivery.domain.PlatformEntityType; import com.aicommerce.platform.delivery.domain.PlatformOperationType;
public record PlatformCommand(UUID operationUuid,UUID accountUuid,PlatformOperationType operationType,PlatformEntityType entityType,UUID entityUuid,String normalizedRequestJson,String requestSha256) {}
