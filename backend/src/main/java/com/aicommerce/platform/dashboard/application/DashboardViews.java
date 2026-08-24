package com.aicommerce.platform.dashboard.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class DashboardViews {
    private static final Pattern HREF = Pattern.compile("^/[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]{0,511}$");

    private DashboardViews() {}

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

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record DashboardView(
            Instant generatedAt,
            DashboardSection<TodoItem> todos,
            DashboardSection<ProductReadinessItem> products,
            DashboardSection<PendingReviewItem> reviews,
            DashboardSection<CampaignPlanItem> campaigns,
            DashboardSection<PlatformCampaignItem> platformCampaigns,
            DashboardSection<AnomalyItem> anomalies,
            KpiOverview kpis) {
        public DashboardView {
            Objects.requireNonNull(generatedAt);
            Objects.requireNonNull(todos);
            Objects.requireNonNull(products);
            Objects.requireNonNull(reviews);
            Objects.requireNonNull(campaigns);
            Objects.requireNonNull(platformCampaigns);
            Objects.requireNonNull(anomalies);
            Objects.requireNonNull(kpis);
        }
    }

    public record DashboardSection<T>(boolean available, List<T> items, boolean truncated, long totalElements) {
        public DashboardSection {
            items = List.copyOf(Objects.requireNonNull(items));
            if (!available) {
                items = List.of();
                truncated = false;
                totalElements = 0;
            }
        }
    }

    public record DashboardPageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public DashboardPageView {
            content = List.copyOf(Objects.requireNonNull(content));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record TodoItem(
            String kind,
            UUID subjectUuid,
            Optional<UUID> productUuid,
            String href,
            String title,
            String summary,
            Instant occurredAt) {
        public TodoItem {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(subjectUuid);
            productUuid = Optional.ofNullable(productUuid).orElse(Optional.empty());
            href = path(href);
            Objects.requireNonNull(title);
            Objects.requireNonNull(summary);
            occurredAt = instant(occurredAt);
        }
    }

    public record ProductReadinessItem(
            UUID productUuid,
            String productName,
            String lifecycleStatus,
            String readinessStatus,
            int finalScore,
            int blockerCount,
            String href) {
        public ProductReadinessItem {
            Objects.requireNonNull(productUuid);
            Objects.requireNonNull(productName);
            Objects.requireNonNull(lifecycleStatus);
            Objects.requireNonNull(readinessStatus);
            href = path(href);
        }
    }

    public record PendingReviewItem(
            UUID generationOutputUuid,
            UUID productUuid,
            String generationType,
            String reviewStatus,
            long version,
            int blockerCount,
            boolean approvalBlocked,
            String href) {
        public PendingReviewItem {
            Objects.requireNonNull(generationOutputUuid);
            Objects.requireNonNull(productUuid);
            Objects.requireNonNull(generationType);
            Objects.requireNonNull(reviewStatus);
            href = path(href);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record CampaignPlanItem(
            UUID campaignUuid,
            String campaignName,
            String lifecycleStatus,
            Optional<String> startDate,
            Optional<String> endDate,
            Optional<String> platform,
            String href) {
        public CampaignPlanItem {
            Objects.requireNonNull(campaignUuid);
            Objects.requireNonNull(campaignName);
            Objects.requireNonNull(lifecycleStatus);
            startDate = Optional.ofNullable(startDate).orElse(Optional.empty());
            endDate = Optional.ofNullable(endDate).orElse(Optional.empty());
            platform = Optional.ofNullable(platform).orElse(Optional.empty());
            href = path(href);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record PlatformCampaignItem(
            UUID platformCampaignUuid,
            UUID campaignUuid,
            String campaignName,
            String desiredState,
            Optional<String> observedState,
            String href) {
        public PlatformCampaignItem {
            Objects.requireNonNull(platformCampaignUuid);
            Objects.requireNonNull(campaignUuid);
            Objects.requireNonNull(campaignName);
            Objects.requireNonNull(desiredState);
            observedState = Optional.ofNullable(observedState).orElse(Optional.empty());
            href = path(href);
        }
    }

    public record AnomalyItem(
            String kind,
            UUID subjectUuid,
            String href,
            String title,
            String summary,
            Instant occurredAt) {
        public AnomalyItem {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(subjectUuid);
            href = path(href);
            Objects.requireNonNull(title);
            Objects.requireNonNull(summary);
            occurredAt = instant(occurredAt);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record KpiOverview(
            boolean available,
            Optional<Instant> windowStart,
            Optional<Instant> windowEnd,
            Optional<String> timezone,
            Optional<String> currency,
            Optional<Integer> attributionClickDays,
            Optional<Integer> attributionViewDays,
            Optional<Integer> eligibleCampaignCount,
            Optional<Integer> presentCampaignCount,
            Optional<Boolean> incomplete,
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
        public KpiOverview {
            windowStart = Optional.ofNullable(windowStart).orElse(Optional.empty());
            windowEnd = Optional.ofNullable(windowEnd).orElse(Optional.empty());
            timezone = Optional.ofNullable(timezone).orElse(Optional.empty());
            currency = Optional.ofNullable(currency).orElse(Optional.empty());
            attributionClickDays = Optional.ofNullable(attributionClickDays).orElse(Optional.empty());
            attributionViewDays = Optional.ofNullable(attributionViewDays).orElse(Optional.empty());
            eligibleCampaignCount = Optional.ofNullable(eligibleCampaignCount).orElse(Optional.empty());
            presentCampaignCount = Optional.ofNullable(presentCampaignCount).orElse(Optional.empty());
            incomplete = Optional.ofNullable(incomplete).orElse(Optional.empty());
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

        public static KpiOverview unavailable() {
            return new KpiOverview(false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty());
        }
    }
}
