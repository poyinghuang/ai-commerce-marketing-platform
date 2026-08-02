package com.aicommerce.platform.audit.domain;

import java.util.Objects;
import java.util.UUID;

public record AuditOperationContext(
        UUID operationUuid,
        String requestId,
        AuditActor actor,
        AuditSource source) {

    public AuditOperationContext {
        Objects.requireNonNull(operationUuid, "operationUuid is required");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(source, "source is required");
    }
}
