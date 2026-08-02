package com.aicommerce.platform.audit.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID auditUuid,
        AuditOperationContext context,
        AuditAction action,
        String entityType,
        UUID entityUuid,
        UUID productUuid,
        Instant occurredAt,
        List<AuditChange> changes) {

    public AuditEvent {
        Objects.requireNonNull(auditUuid, "auditUuid is required");
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(action, "action is required");
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        Objects.requireNonNull(entityUuid, "entityUuid is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
