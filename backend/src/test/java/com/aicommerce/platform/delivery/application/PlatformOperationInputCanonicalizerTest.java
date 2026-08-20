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
        assertThat(canonicalizer.canonicalizePersisted(first.json()).sha256()).isEqualTo(first.sha256());
    }

    @Test
    void newCreateAdRequiresApprovedMappingAndCanonicalParentVersion() {
        String ad = "00000000-0000-4000-8000-0000000000ad";
        String valid = "{\"schemaVersion\":1,\"operationType\":\"CREATE_AD\",\"entityType\":\"AD\",\"entityUuid\":\"" + ad
                + "\",\"platformAdUuid\":\"" + ad
                + "\",\"platformAdSetUuid\":\"00000000-0000-4000-8000-0000000000a1\",\"expectedParentVersion\":1"
                + ",\"productUuid\":\"00000000-0000-4000-8000-0000000000a2\",\"assetUuid\":\"00000000-0000-4000-8000-0000000000a3\""
                + ",\"generationOutputUuid\":\"00000000-0000-4000-8000-0000000000a4\",\"reviewDecisionUuid\":\"00000000-0000-4000-8000-0000000000a5\""
                + ",\"approvedChecksumSha256\":\"" + "a".repeat(64) + "\",\"creativeMappingKey\":\"APPROVED_IMAGE_ASSET_V1\",\"desiredState\":\"PAUSED\"}";
        var canonical = canonicalizer.canonicalizeNewCreateAd(valid);
        assertThat(canonicalizer.canonicalizePersisted(valid).sha256()).isEqualTo(canonical.sha256());
        assertThatThrownBy(() -> canonicalizer.canonicalizeNewCreateAd(valid.replace("APPROVED_IMAGE_ASSET_V1", "DEFAULT_IMAGE_V1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalizeNewCreateAd(valid.replace("\"expectedParentVersion\":1", "\"expectedParentVersion\":1e0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalizeNewCreateAd(valid.replace("\"expectedParentVersion\":1", "\"expectedParentVersion\":01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalizeNewCreateAd(valid.replace("\"expectedParentVersion\":1", "\"expectedParentVersion\":1.0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalizeNewCreateAd(valid.replace(",\"expectedParentVersion\":1", "")))
                .isInstanceOf(IllegalArgumentException.class);
        String legacy = valid.replace(",\"expectedParentVersion\":1", "");
        assertThat(canonicalizer.canonicalizePersisted(legacy).json()).contains("CREATE_AD");
        assertThat(canonicalizer.canonicalizePersisted(legacy).json()).doesNotContain("expectedParentVersion");
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
