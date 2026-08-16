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
        var first = canonicalizer.canonicalize("{\"z\":1,\"nested\":{\"b\":2,\"a\":1}}");
        var second = canonicalizer.canonicalize("{ \"nested\" : { \"a\": 1, \"b\": 2 }, \"z\": 1 }");
        assertThat(first.json()).isEqualTo("{\"nested\":{\"a\":1,\"b\":2},\"z\":1}");
        assertThat(first.sha256()).isEqualTo(second.sha256()).hasSize(64);
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
