package com.aicommerce.platform.delivery.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;

public final class Stage4DMetricFingerprint {
    private Stage4DMetricFingerprint() {}

    public static String json(
            PlatformEntityType entityType,
            UUID entityUuid,
            Instant windowStart,
            Instant windowEnd,
            Optional<Long> impressions,
            Optional<Long> reach,
            Optional<Long> clicks,
            Optional<Long> conversions,
            Optional<BigDecimal> spend,
            Optional<BigDecimal> revenue,
            FreshnessStatus freshnessStatus) {
        return "{\"attributionClickDays\":7,"
                + "\"attributionViewDays\":1,"
                + "\"clicks\":" + count(clicks) + ","
                + "\"conversions\":" + count(conversions) + ","
                + "\"currency\":\"TWD\","
                + "\"entityType\":\"" + entityType.name() + "\","
                + "\"entityUuid\":\"" + entityUuid.toString().toLowerCase(Locale.ROOT) + "\","
                + "\"freshnessStatus\":\"" + freshnessStatus.name() + "\","
                + "\"impressions\":" + count(impressions) + ","
                + "\"reach\":" + count(reach) + ","
                + "\"revenue\":" + money(revenue) + ","
                + "\"spend\":" + money(spend) + ","
                + "\"timezone\":\"Asia/Taipei\","
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

    public static String hash(
            PlatformEntityType entityType,
            UUID entityUuid,
            Instant windowStart,
            Instant windowEnd,
            Optional<Long> impressions,
            Optional<Long> reach,
            Optional<Long> clicks,
            Optional<Long> conversions,
            Optional<BigDecimal> spend,
            Optional<BigDecimal> revenue,
            FreshnessStatus freshnessStatus) {
        return sha256(json(entityType, entityUuid, windowStart, windowEnd, impressions, reach, clicks, conversions,
                spend, revenue, freshnessStatus));
    }

    static String instant(Instant value) {
        Instant truncated = value.truncatedTo(ChronoUnit.SECONDS);
        String text = truncated.toString();
        if (!text.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")) {
            throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
        }
        return text;
    }

    private static String count(Optional<Long> value) {
        return value.map(Object::toString).orElse("null");
    }

    private static String money(Optional<BigDecimal> value) {
        return value.map(amount -> "\"" + amount.setScale(6).toPlainString() + "\"").orElse("null");
    }
}
