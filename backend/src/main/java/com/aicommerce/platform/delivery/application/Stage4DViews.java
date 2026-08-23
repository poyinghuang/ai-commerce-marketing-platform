package com.aicommerce.platform.delivery.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

public final class Stage4DViews {
    private Stage4DViews() {}

    public enum Warning {
        DETERMINISTIC_FAKE_ONLY,
        NO_REAL_PROVIDER_OR_SPEND,
        NULL_METRICS_MEAN_UNKNOWN
    }

    static final List<Warning> WARNINGS = List.of(
            Warning.DETERMINISTIC_FAKE_ONLY,
            Warning.NO_REAL_PROVIDER_OR_SPEND,
            Warning.NULL_METRICS_MEAN_UNKNOWN);

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonPropertyOrder({
            "entityType", "entityUuid", "desiredState", "observedState", "syncEligible", "warnings", "confirmable"})
    public record DeliveryPreview(
            PlatformEntityType entityType,
            UUID entityUuid,
            PlatformDesiredState desiredState,
            Optional<PlatformObservedState> observedState,
            boolean syncEligible,
            List<Warning> warnings,
            boolean confirmable) {
        public DeliveryPreview {
            Objects.requireNonNull(entityType);
            Objects.requireNonNull(entityUuid);
            Objects.requireNonNull(desiredState);
            Objects.requireNonNull(observedState);
            warnings = List.copyOf(warnings);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonPropertyOrder({
            "entityType", "entityUuid", "desiredState", "observedState", "externalIdFingerprint", "updatedAt", "version"})
    public record DeliveryView(
            PlatformEntityType entityType,
            UUID entityUuid,
            PlatformDesiredState desiredState,
            Optional<PlatformObservedState> observedState,
            Optional<String> externalIdFingerprint,
            Instant updatedAt,
            long version) {
        public DeliveryView {
            Objects.requireNonNull(entityType);
            Objects.requireNonNull(entityUuid);
            Objects.requireNonNull(desiredState);
            Objects.requireNonNull(observedState);
            Objects.requireNonNull(externalIdFingerprint);
            Objects.requireNonNull(updatedAt);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonPropertyOrder({
            "entityType", "entityUuid", "windowStart", "windowEnd", "timezone", "attributionClickDays",
            "attributionViewDays", "currency", "refreshEligible", "warnings", "confirmable"})
    public record MetricsPreview(
            PlatformEntityType entityType,
            UUID entityUuid,
            Instant windowStart,
            Instant windowEnd,
            String timezone,
            int attributionClickDays,
            int attributionViewDays,
            String currency,
            boolean refreshEligible,
            List<Warning> warnings,
            boolean confirmable) {
        public MetricsPreview {
            Objects.requireNonNull(entityType);
            Objects.requireNonNull(entityUuid);
            Objects.requireNonNull(windowStart);
            Objects.requireNonNull(windowEnd);
            Objects.requireNonNull(timezone);
            Objects.requireNonNull(currency);
            warnings = List.copyOf(warnings);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonPropertyOrder({
            "entityType", "entityUuid", "windowStart", "windowEnd", "timezone", "attributionClickDays",
            "attributionViewDays", "currency", "present", "freshnessStatus", "revisionNumber", "fetchedAt",
            "impressions", "reach", "clicks", "conversions", "spend", "revenue", "ctr", "cpc", "cpm", "cpa", "cvr",
            "roas", "warnings"})
    public record MetricsView(
            PlatformEntityType entityType,
            UUID entityUuid,
            Instant windowStart,
            Instant windowEnd,
            String timezone,
            int attributionClickDays,
            int attributionViewDays,
            String currency,
            boolean present,
            FreshnessStatus freshnessStatus,
            Optional<Integer> revisionNumber,
            Optional<Instant> fetchedAt,
            Optional<Long> impressions,
            Optional<Long> reach,
            Optional<Long> clicks,
            Optional<Long> conversions,
            Optional<String> spend,
            Optional<String> revenue,
            Optional<String> ctr,
            Optional<String> cpc,
            Optional<String> cpm,
            Optional<String> cpa,
            Optional<String> cvr,
            Optional<String> roas,
            List<Warning> warnings) {
        public MetricsView {
            Objects.requireNonNull(entityType);
            Objects.requireNonNull(entityUuid);
            Objects.requireNonNull(windowStart);
            Objects.requireNonNull(windowEnd);
            Objects.requireNonNull(timezone);
            Objects.requireNonNull(currency);
            Objects.requireNonNull(freshnessStatus);
            Objects.requireNonNull(revisionNumber);
            Objects.requireNonNull(fetchedAt);
            Objects.requireNonNull(impressions);
            Objects.requireNonNull(reach);
            Objects.requireNonNull(clicks);
            Objects.requireNonNull(conversions);
            Objects.requireNonNull(spend);
            Objects.requireNonNull(revenue);
            Objects.requireNonNull(ctr);
            Objects.requireNonNull(cpc);
            Objects.requireNonNull(cpm);
            Objects.requireNonNull(cpa);
            Objects.requireNonNull(cvr);
            Objects.requireNonNull(roas);
            warnings = List.copyOf(warnings);
        }
    }
}
