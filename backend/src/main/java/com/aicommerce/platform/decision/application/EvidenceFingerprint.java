package com.aicommerce.platform.decision.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class EvidenceFingerprint {
    public static final String GOLDEN_JSON = "{\"campaignUuid\":\"00000000-0000-4000-8000-0000000000c1\","
            + "\"metricSourceFingerprint\":\"0f322b3764cc00ff1d548932116f6ce9379944a70c6af198c935ef0003624b73\","
            + "\"recommendationType\":\"INCREASE_BUDGET\",\"ruleSetKey\":\"RULE_SET_V1\","
            + "\"windowEnd\":\"2026-08-22T16:00:00Z\",\"windowStart\":\"2026-08-21T16:00:00Z\"}";
    public static final String GOLDEN_SHA256 = "c6d95966c5b6f0d94f55e75e5ddb3fb5ebc4ea2843449cae09375e073053e33f";

    private EvidenceFingerprint() {}

    public static String json(UUID campaignUuid, String metricSourceFingerprint, String recommendationType,
            Instant windowStart, Instant windowEnd) {
        return "{\"campaignUuid\":\"" + campaignUuid.toString().toLowerCase(Locale.ROOT) + "\","
                + "\"metricSourceFingerprint\":\"" + metricSourceFingerprint + "\","
                + "\"recommendationType\":\"" + recommendationType + "\","
                + "\"ruleSetKey\":\"RULE_SET_V1\","
                + "\"windowEnd\":\"" + instant(windowEnd) + "\","
                + "\"windowStart\":\"" + instant(windowStart) + "\"}";
    }

    public static String sha256(String compactJson) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(compactJson.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String hash(UUID campaignUuid, String metricSourceFingerprint, String recommendationType,
            Instant windowStart, Instant windowEnd) {
        return sha256(json(campaignUuid, metricSourceFingerprint, recommendationType, windowStart, windowEnd));
    }

    static String instant(Instant value) {
        Instant truncated = value.truncatedTo(ChronoUnit.SECONDS);
        String text = truncated.toString();
        if (!text.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")) {
            throw new IllegalArgumentException("instant");
        }
        return text;
    }
}
