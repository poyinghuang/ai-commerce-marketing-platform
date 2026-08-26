package com.aicommerce.platform.decision.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.aicommerce.platform.decision.application.DecisionViews.RecommendationType;

public final class RuleSetV1 {
    public static final String KEY = "RULE_SET_V1";
    public static final String RISK = "Approval records the operator decision only. It does not change desired state, Ad Set budget, creatives, or metrics, and it does not call a platform adapter.";
    static final BigDecimal ROAS_INCREASE = new BigDecimal("3.000000");
    static final BigDecimal ROAS_DECREASE = new BigDecimal("1.000000");
    static final BigDecimal CPA_DECREASE = new BigDecimal("50.000000");
    static final BigDecimal CTR_SWAP = new BigDecimal("0.005000");
    static final BigDecimal CTR_FATIGUE = new BigDecimal("0.008000");
    static final long IMPRESSIONS_FATIGUE = 20_000L;

    private RuleSetV1() {}

    public static List<Emission> evaluate(Metrics metrics, String desiredState) {
        List<Emission> emitted = new ArrayList<>();
        if (present(metrics.roas()).filter(value -> value.compareTo(ROAS_INCREASE) >= 0).isPresent()) {
            emitted.add(new Emission(RecommendationType.INCREASE_BUDGET,
                    "Campaign-grain ROAS is at or above 3.000000 on the canonical previous Taipei day."));
        }
        boolean decrease = present(metrics.roas()).filter(value -> value.compareTo(ROAS_DECREASE) <= 0).isPresent()
                || present(metrics.cpa()).filter(value -> value.compareTo(CPA_DECREASE) >= 0).isPresent();
        if (decrease) {
            emitted.add(new Emission(RecommendationType.DECREASE_BUDGET,
                    "Campaign-grain ROAS is at or below 1.000000, or CPA is at or above 50.000000, on the canonical previous Taipei day."));
        }
        if ("ACTIVE".equals(desiredState)
                && metrics.conversions().filter(value -> value == 0).isPresent()
                && present(metrics.spend()).filter(value -> value.compareTo(BigDecimal.ZERO) > 0).isPresent()) {
            emitted.add(new Emission(RecommendationType.PAUSE,
                    "The campaign is ACTIVE, recorded conversions are zero, and spend is present on the canonical previous Taipei day."));
        }
        if (present(metrics.ctr()).filter(value -> value.compareTo(CTR_SWAP) < 0).isPresent()) {
            emitted.add(new Emission(RecommendationType.SWAP_CREATIVE,
                    "Campaign-grain CTR is below 0.005000 on the canonical previous Taipei day."));
            emitted.add(new Emission(RecommendationType.REGENERATE_CREATIVE,
                    "Campaign-grain CTR is below 0.005000; consider generating a new approved IMAGE through the existing Creative Factory."));
        }
        if (metrics.impressions().filter(value -> value >= IMPRESSIONS_FATIGUE).isPresent()
                && present(metrics.ctr()).filter(value -> value.compareTo(CTR_FATIGUE) < 0).isPresent()) {
            emitted.add(new Emission(RecommendationType.CREATIVE_FATIGUE,
                    "Campaign-grain impressions are at least 20000 and CTR is below 0.008000 on the canonical previous Taipei day."));
        }
        return List.copyOf(emitted);
    }

    private static Optional<BigDecimal> present(Optional<BigDecimal> value) {
        return value;
    }

    public record Emission(RecommendationType type, String reasonSummary) {}

    public record Metrics(
            Optional<Long> impressions,
            Optional<Long> reach,
            Optional<Long> clicks,
            Optional<Long> conversions,
            Optional<BigDecimal> spend,
            Optional<BigDecimal> revenue,
            Optional<BigDecimal> ctr,
            Optional<BigDecimal> cpc,
            Optional<BigDecimal> cpm,
            Optional<BigDecimal> cpa,
            Optional<BigDecimal> cvr,
            Optional<BigDecimal> roas) {
        public Metrics {
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
}
