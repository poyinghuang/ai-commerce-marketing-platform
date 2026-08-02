package com.aicommerce.platform.audit.domain;

import java.util.Objects;

public record AuditChange(
        String fieldName,
        String oldValue,
        String newValue,
        AuditValueType valueType,
        int changeOrder) {

    public AuditChange {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName is required");
        }
        Objects.requireNonNull(valueType, "valueType is required");
        if (changeOrder < 0) {
            throw new IllegalArgumentException("changeOrder must be non-negative");
        }
        if (changeOrder > Short.MAX_VALUE) {
            throw new IllegalArgumentException("changeOrder exceeds the database smallint limit");
        }
    }
}
