package com.aicommerce.platform.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.audit.infrastructure.actor.DenyMutationAuditActorProvider;
import com.aicommerce.platform.audit.infrastructure.actor.LocalAdminAuditActorProvider;
import org.junit.jupiter.api.Test;

class AuditValueSanitizerTest {

    private final AuditValueSanitizer sanitizer = new AuditValueSanitizer();

    @Test
    void redactsSensitiveValuesBeforeApplyingLengthLimit() {
        AuditChange sanitized = sanitizer.sanitize(new AuditChange(
                "authorizationToken",
                "old-" + "x".repeat(5_000),
                "new-" + "y".repeat(5_000),
                AuditValueType.STRING,
                0));

        assertThat(sanitized.oldValue()).isEqualTo("[REDACTED]");
        assertThat(sanitized.newValue()).isEqualTo("[REDACTED]");
    }

    @Test
    void truncatesByUnicodeCodePointAndIncludesMarkerWithinLimit() {
        String value = "😀".repeat(4_100);

        String sanitized = sanitizer.sanitizeValue("description", value);

        assertThat(sanitized).endsWith("[TRUNCATED]");
        assertThat(sanitized.codePointCount(0, sanitized.length())).isEqualTo(4_096);
    }

    @Test
    void actorProvidersNeverAcceptAnActorFromTheCaller() {
        assertThat(new LocalAdminAuditActorProvider().currentActor().id()).isEqualTo("local-admin");
        assertThatThrownBy(() -> new DenyMutationAuditActorProvider().currentActor())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trusted production AuditActorProvider");
    }

    @Test
    void changeOrderMustFitTheDatabaseSmallint() {
        assertThatThrownBy(() -> new AuditChange(
                "sku", null, "new", AuditValueType.STRING, Short.MAX_VALUE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smallint");
    }
}
