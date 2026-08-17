package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PlatformOperationInputCanonicalizerTest {
    private final PlatformOperationInputCanonicalizer canonicalizer =
            new PlatformOperationInputCanonicalizer(new ObjectMapper());

    @Test
    void producesStableCanonicalJsonAndDigest() {
        var first = canonicalizer.canonicalize("{\"targetDesiredState\":\"PAUSED\",\"expectedEntityVersion\":1.000000,\"entityUuid\":\"00000000-0000-0000-0000-000000000001\",\"entityType\":\"AD\",\"operationType\":\"PAUSE\",\"schemaVersion\":1}");
        var second = canonicalizer.canonicalize("{\"schemaVersion\":1,\"operationType\":\"PAUSE\",\"entityType\":\"AD\",\"entityUuid\":\"00000000-0000-0000-0000-000000000001\",\"expectedEntityVersion\":1,\"targetDesiredState\":\"PAUSED\"}");
        assertThat(first.json()).isEqualTo("{\"entityType\":\"AD\",\"entityUuid\":\"00000000-0000-0000-0000-000000000001\",\"expectedEntityVersion\":1,\"operationType\":\"PAUSE\",\"schemaVersion\":1,\"targetDesiredState\":\"PAUSED\"}");
        assertThat(first.sha256()).isEqualTo(second.sha256()).hasSize(64);
    }

    @Test
    void rejectsUnknownKeysArraysNullAndBooleanValues() {
        String prefix = "{\"schemaVersion\":1,\"operationType\":\"PAUSE\",\"entityType\":\"AD\",\"entityUuid\":\"00000000-0000-0000-0000-000000000001\",\"expectedEntityVersion\":1,\"targetDesiredState\":\"PAUSED\",";
        assertThatThrownBy(() -> canonicalizer.canonicalize(prefix + "\"unknown\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalize(prefix + "\"unknown\":[]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("arrays");
        assertThatThrownBy(() -> canonicalizer.canonicalize(prefix + "\"unknown\":null}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("omitted");
        assertThatThrownBy(() -> canonicalizer.canonicalize(prefix + "\"unknown\":true}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("booleans");
    }

    @Test
    void rejectsSecretLikeFieldsAtAnyDepth() {
        assertThatThrownBy(() -> canonicalizer.canonicalize("{\"nested\":{\"accessToken\":\"x\"}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensitive field");
    }

    @Test
    void rejectsNonObjectInput() {
        assertThatThrownBy(() -> canonicalizer.canonicalize("[1,2]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
    }
}
