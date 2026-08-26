package com.aicommerce.platform.decision.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class DecisionViews {
    private static final Pattern HREF = Pattern.compile("^/[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]{0,511}$");

    private DecisionViews() {}

    public static Instant instant(Instant value) {
        return Objects.requireNonNull(value).truncatedTo(ChronoUnit.SECONDS);
    }

    public static String path(String value) {
        Objects.requireNonNull(value);
        if (value.length() > 512 || !HREF.matcher(value).matches() || value.startsWith("//")
                || value.toLowerCase().startsWith("/http")) {
            throw new IllegalArgumentException("href");
        }
        return value;
    }

    public enum RecommendationType {
        INCREASE_BUDGET,
        DECREASE_BUDGET,
        PAUSE,
        SWAP_CREATIVE,
        REGENERATE_CREATIVE,
        AUDIENCE_FATIGUE,
        CREATIVE_FATIGUE
    }

    public enum RecommendationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    public enum Stage06Warning {
        DETERMINISTIC_FAKE_ONLY,
        NO_REAL_PROVIDER_OR_SPEND,
        NULL_METRICS_MEAN_UNKNOWN,
        APPROVAL_DOES_NOT_EXECUTE
    }

    public static final List<Stage06Warning> WARNINGS = List.of(
            Stage06Warning.DETERMINISTIC_FAKE_ONLY,
            Stage06Warning.NO_REAL_PROVIDER_OR_SPEND,
            Stage06Warning.NULL_METRICS_MEAN_UNKNOWN,
            Stage06Warning.APPROVAL_DOES_NOT_EXECUTE);

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record GenerateView(
            Instant generatedAt,
            Instant windowStart,
            Instant windowEnd,
            String timezone,
            String currency,
            long consideredCampaignCount,
            long createdCount,
            long updatedCount,
            long replayedCount,
            long skippedIncompleteCount,
            List<RecommendationView> items,
            boolean truncated,
            List<Stage06Warning> warnings) {
        public GenerateView {
            generatedAt = instant(generatedAt);
            windowStart = instant(windowStart);
            windowEnd = instant(windowEnd);
            timezone = Objects.requireNonNull(timezone);
            currency = Objects.requireNonNull(currency);
            items = List.copyOf(Objects.requireNonNull(items));
            warnings = List.copyOf(Objects.requireNonNull(warnings));
        }
    }

    public record DecisionPageView(
            List<RecommendationView> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public DecisionPageView {
            content = List.copyOf(Objects.requireNonNull(content));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record RecommendationView(
            UUID recommendationUuid,
            UUID platformCampaignUuid,
            UUID campaignUuid,
            String campaignName,
            RecommendationType recommendationType,
            RecommendationStatus status,
            Instant windowStart,
            Instant windowEnd,
            String timezone,
            String currency,
            int attributionClickDays,
            int attributionViewDays,
            String desiredState,
            String reasonSummary,
            String riskSummary,
            RecommendationEvidence evidence,
            String href,
            Optional<UUID> productUuid,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<Stage06Warning> warnings) {
        public RecommendationView {
            Objects.requireNonNull(recommendationUuid);
            Objects.requireNonNull(platformCampaignUuid);
            Objects.requireNonNull(campaignUuid);
            Objects.requireNonNull(campaignName);
            Objects.requireNonNull(recommendationType);
            Objects.requireNonNull(status);
            windowStart = instant(windowStart);
            windowEnd = instant(windowEnd);
            timezone = Objects.requireNonNull(timezone);
            currency = Objects.requireNonNull(currency);
            desiredState = Objects.requireNonNull(desiredState);
            reasonSummary = Objects.requireNonNull(reasonSummary);
            riskSummary = Objects.requireNonNull(riskSummary);
            Objects.requireNonNull(evidence);
            href = path(href);
            productUuid = Optional.ofNullable(productUuid).orElse(Optional.empty());
            createdAt = instant(createdAt);
            updatedAt = instant(updatedAt);
            warnings = List.copyOf(Objects.requireNonNull(warnings));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record RecommendationEvidence(
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
            Optional<String> roas) {
        public RecommendationEvidence {
            impressions = Optional.ofNullable(impressions).orElse(Optional.empty());
            reach = Optional.ofNullable(reach).orElse(Optional.empty());
            clicks = Optional.ofNullable(clicks).orElse(Optional.empty());
            conversions = Optional.ofNullable(conversions).orElse(Optional.empty());
            spend = Optional.ofNullable(spend).orElse(Optional.empty());
            revenue = Optional.ofNullable(revenue).orElse(Optional.empty());
            ctr = Optional.ofNullable(ctr).orElse(Optional.empty());
            cpc = Optional.ofNullable(cpc).orElse(Optional.empty());
            cpm = Optional.ofNullable(cpm).orElse(Optional.empty());
            cpa = Optional.ofNullable(cpa).orElse(Optional.empty());
            cvr = Optional.ofNullable(cvr).orElse(Optional.empty());
            roas = Optional.ofNullable(roas).orElse(Optional.empty());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record RecommendationDetailView(
            UUID recommendationUuid,
            UUID platformCampaignUuid,
            UUID campaignUuid,
            String campaignName,
            RecommendationType recommendationType,
            RecommendationStatus status,
            Instant windowStart,
            Instant windowEnd,
            String timezone,
            String currency,
            int attributionClickDays,
            int attributionViewDays,
            String desiredState,
            String reasonSummary,
            String riskSummary,
            RecommendationEvidence evidence,
            String href,
            Optional<UUID> productUuid,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<Stage06Warning> warnings,
            Optional<RecommendationDecisionView> decision) {
        public RecommendationDetailView {
            Objects.requireNonNull(recommendationUuid);
            Objects.requireNonNull(platformCampaignUuid);
            Objects.requireNonNull(campaignUuid);
            Objects.requireNonNull(campaignName);
            Objects.requireNonNull(recommendationType);
            Objects.requireNonNull(status);
            windowStart = instant(windowStart);
            windowEnd = instant(windowEnd);
            timezone = Objects.requireNonNull(timezone);
            currency = Objects.requireNonNull(currency);
            desiredState = Objects.requireNonNull(desiredState);
            reasonSummary = Objects.requireNonNull(reasonSummary);
            riskSummary = Objects.requireNonNull(riskSummary);
            Objects.requireNonNull(evidence);
            href = path(href);
            productUuid = Optional.ofNullable(productUuid).orElse(Optional.empty());
            createdAt = instant(createdAt);
            updatedAt = instant(updatedAt);
            warnings = List.copyOf(Objects.requireNonNull(warnings));
            decision = Optional.ofNullable(decision).orElse(Optional.empty());
        }

        public static RecommendationDetailView from(RecommendationView view,
                Optional<RecommendationDecisionView> decision) {
            return new RecommendationDetailView(
                    view.recommendationUuid(), view.platformCampaignUuid(), view.campaignUuid(), view.campaignName(),
                    view.recommendationType(), view.status(), view.windowStart(), view.windowEnd(), view.timezone(),
                    view.currency(), view.attributionClickDays(), view.attributionViewDays(), view.desiredState(),
                    view.reasonSummary(), view.riskSummary(), view.evidence(), view.href(), view.productUuid(),
                    view.version(), view.createdAt(), view.updatedAt(), view.warnings(), decision);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record RecommendationDecisionView(
            UUID recommendationDecisionUuid,
            RecommendationStatus decision,
            Optional<String> reason,
            Instant decidedAt) {
        public RecommendationDecisionView {
            Objects.requireNonNull(recommendationDecisionUuid);
            Objects.requireNonNull(decision);
            reason = Optional.ofNullable(reason).orElse(Optional.empty());
            decidedAt = instant(decidedAt);
        }
    }
}
