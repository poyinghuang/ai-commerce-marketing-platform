package com.aicommerce.platform.audit.domain;

import java.util.Objects;

public record AuditActor(AuditActorType type, String id) {

    public AuditActor {
        Objects.requireNonNull(type, "actor type is required");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("actor id is required");
        }
    }

    public static AuditActor localAdmin() {
        return new AuditActor(AuditActorType.LOCAL_ADMIN, "local-admin");
    }

    public static AuditActor system(String id) {
        return new AuditActor(AuditActorType.SYSTEM, id);
    }
}
