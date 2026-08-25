package com.aicommerce.platform.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EvidenceFingerprintTest {
    @Test
    void goldenSuccessCampaignJsonHashesToLockedSha256() {
        assertThat(EvidenceFingerprint.GOLDEN_JSON).isEqualTo(
                "{\"campaignUuid\":\"00000000-0000-4000-8000-0000000000c1\","
                        + "\"metricSourceFingerprint\":\"0f322b3764cc00ff1d548932116f6ce9379944a70c6af198c935ef0003624b73\","
                        + "\"recommendationType\":\"INCREASE_BUDGET\",\"ruleSetKey\":\"RULE_SET_V1\","
                        + "\"windowEnd\":\"2026-08-22T16:00:00Z\",\"windowStart\":\"2026-08-21T16:00:00Z\"}");
        assertThat(EvidenceFingerprint.sha256(EvidenceFingerprint.GOLDEN_JSON))
                .isEqualTo(EvidenceFingerprint.GOLDEN_SHA256)
                .isEqualTo("c6d95966c5b6f0d94f55e75e5ddb3fb5ebc4ea2843449cae09375e073053e33f");
        assertThat(EvidenceFingerprint.hash(
                UUID.fromString("00000000-0000-4000-8000-0000000000c1"),
                "0f322b3764cc00ff1d548932116f6ce9379944a70c6af198c935ef0003624b73",
                "INCREASE_BUDGET",
                Instant.parse("2026-08-21T16:00:00Z"),
                Instant.parse("2026-08-22T16:00:00Z")))
                .isEqualTo(EvidenceFingerprint.GOLDEN_SHA256);
    }
}
