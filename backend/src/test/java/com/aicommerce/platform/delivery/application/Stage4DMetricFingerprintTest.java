package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import org.junit.jupiter.api.Test;

class Stage4DMetricFingerprintTest {
    @Test
    void compactLexicographicJsonAndHashAreStableForSuccessCampaignConstants() {
        UUID entity = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
        Instant start = Instant.parse("2026-08-21T16:00:00Z");
        Instant end = Instant.parse("2026-08-22T16:00:00Z");
        String json = Stage4DMetricFingerprint.json(
                PlatformEntityType.CAMPAIGN, entity, start, end,
                Optional.of(10_000L), Optional.of(8_000L), Optional.of(100L), Optional.of(4L),
                Optional.of(new BigDecimal("25.000000")), Optional.of(new BigDecimal("100.000000")),
                FreshnessStatus.FRESH);
        assertThat(json).isEqualTo("{\"attributionClickDays\":7,\"attributionViewDays\":1,\"clicks\":100,\"conversions\":4,\"currency\":\"TWD\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\"00000000-0000-4000-8000-0000000000c1\",\"freshnessStatus\":\"FRESH\",\"impressions\":10000,\"reach\":8000,\"revenue\":\"100.000000\",\"spend\":\"25.000000\",\"timezone\":\"Asia/Taipei\",\"windowEnd\":\"2026-08-22T16:00:00Z\",\"windowStart\":\"2026-08-21T16:00:00Z\"}");
        assertThat(json).doesNotContain(" ").doesNotContain("\n");
        assertThat(Stage4DMetricFingerprint.sha256(json))
                .isEqualTo("0f322b3764cc00ff1d548932116f6ce9379944a70c6af198c935ef0003624b73");
        assertThat(Stage4DMetricFingerprint.hash(
                PlatformEntityType.CAMPAIGN, entity, start, end,
                Optional.of(10_000L), Optional.of(8_000L), Optional.of(100L), Optional.of(4L),
                Optional.of(new BigDecimal("25.000000")), Optional.of(new BigDecimal("100.000000")),
                FreshnessStatus.FRESH)).isEqualTo(Stage4DMetricFingerprint.sha256(json));
    }
}
