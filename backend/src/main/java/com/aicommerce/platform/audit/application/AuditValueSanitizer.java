package com.aicommerce.platform.audit.application;

import java.util.List;
import java.util.Locale;

import com.aicommerce.platform.audit.domain.AuditChange;
import org.springframework.stereotype.Component;

@Component
public class AuditValueSanitizer {

    static final int MAX_VALUE_LENGTH = 4096;
    static final String REDACTED = "[REDACTED]";
    static final String TRUNCATED = "[TRUNCATED]";
    private static final List<String> SENSITIVE_FIELD_MARKERS = List.of(
            "authorization", "cookie", "credential", "password", "secret", "token");

    public AuditChange sanitize(AuditChange change) {
        return new AuditChange(
                change.fieldName(),
                sanitizeValue(change.fieldName(), change.oldValue()),
                sanitizeValue(change.fieldName(), change.newValue()),
                change.valueType(),
                change.changeOrder());
    }

    String sanitizeValue(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        if (isSensitive(fieldName)) {
            return REDACTED;
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= MAX_VALUE_LENGTH) {
            return value;
        }
        int prefixCodePoints = MAX_VALUE_LENGTH - TRUNCATED.length();
        int prefixEnd = value.offsetByCodePoints(0, prefixCodePoints);
        return value.substring(0, prefixEnd) + TRUNCATED;
    }

    private boolean isSensitive(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_MARKERS.stream().anyMatch(normalized::contains);
    }
}
