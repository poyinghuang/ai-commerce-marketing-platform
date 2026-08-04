package com.aicommerce.platform.product.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductEtagTest {

    @Test
    void formatsAndParsesWeakVersionEtag() {
        assertThat(ProductEtag.fromVersion(42)).isEqualTo("W/\"42\"");
        assertThat(ProductEtag.requireVersion("W/\"42\"")).isEqualTo(42L);
    }

    @Test
    void rejectsMissingMalformedMultipleAndWildcardTags() {
        assertThatThrownBy(() -> ProductEtag.requireVersion(null))
                .isInstanceOf(PreconditionRequiredException.class);
        assertThatThrownBy(() -> ProductEtag.requireVersion("\"1\""))
                .isInstanceOf(InvalidIfMatchException.class);
        assertThatThrownBy(() -> ProductEtag.requireVersion("W/\"1\", W/\"2\""))
                .isInstanceOf(InvalidIfMatchException.class);
        assertThatThrownBy(() -> ProductEtag.requireVersion("*"))
                .isInstanceOf(InvalidIfMatchException.class);
        for (String malformed : new String[] {
                "W/\"01\"", "W/\"-1\"", " W/\"1\"", "W/\"1\" ", "W/\"9223372036854775808\""
        }) {
            assertThatThrownBy(() -> ProductEtag.requireVersion(malformed))
                    .isInstanceOf(InvalidIfMatchException.class);
        }
    }
}
