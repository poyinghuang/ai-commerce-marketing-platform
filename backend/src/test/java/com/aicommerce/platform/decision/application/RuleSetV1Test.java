package com.aicommerce.platform.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import com.aicommerce.platform.decision.application.DecisionViews.RecommendationType;
import org.junit.jupiter.api.Test;

class RuleSetV1Test {
    @Test
    void successConstantsEmitIncreaseBudgetOnly() {
        var types = RuleSetV1.evaluate(metrics(
                Optional.of(10_000L), Optional.of(100L), Optional.of(4L),
                Optional.of(new BigDecimal("25.000000")), Optional.of(new BigDecimal("100.000000"))), "PAUSED")
                .stream().map(RuleSetV1.Emission::type).toList();
        assertThat(types).containsExactly(RecommendationType.INCREASE_BUDGET);
    }

    @Test
    void constructedRoasAndCpaEmitIncreaseAndDecreaseTogether() {
        var types = RuleSetV1.evaluate(metrics(
                Optional.of(10_000L), Optional.of(100L), Optional.of(2L),
                Optional.of(new BigDecimal("100.000000")), Optional.of(new BigDecimal("400.000000"))), "PAUSED")
                .stream().map(RuleSetV1.Emission::type).toList();
        assertThat(types).containsExactly(RecommendationType.INCREASE_BUDGET, RecommendationType.DECREASE_BUDGET);
    }

    @Test
    void nullSpendOmitsMoneyRulesAndDoesNotZeroFill() {
        var emitted = RuleSetV1.evaluate(metrics(
                Optional.of(10_000L), Optional.of(100L), Optional.of(4L),
                Optional.empty(), Optional.of(new BigDecimal("100.000000"))), "ACTIVE");
        assertThat(emitted).isEmpty();
    }

    @Test
    void nullClicksOmitsCtrRules() {
        var types = RuleSetV1.evaluate(new RuleSetV1.Metrics(
                Optional.of(10_000L), Optional.empty(), Optional.empty(), Optional.of(4L),
                Optional.of(new BigDecimal("25.000000")), Optional.of(new BigDecimal("100.000000")),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new BigDecimal("6.250000")), Optional.empty(), Optional.of(new BigDecimal("4.000000"))),
                "PAUSED").stream().map(RuleSetV1.Emission::type).toList();
        assertThat(types).containsExactly(RecommendationType.INCREASE_BUDGET)
                .doesNotContain(RecommendationType.SWAP_CREATIVE, RecommendationType.REGENERATE_CREATIVE,
                        RecommendationType.CREATIVE_FATIGUE, RecommendationType.AUDIENCE_FATIGUE);
    }

    @Test
    void pauseRequiresActiveZeroConversionsAndPositiveSpend() {
        var active = RuleSetV1.evaluate(metrics(
                Optional.of(10_000L), Optional.of(100L), Optional.of(0L),
                Optional.of(new BigDecimal("25.000000")), Optional.empty()), "ACTIVE")
                .stream().map(RuleSetV1.Emission::type).toList();
        assertThat(active).contains(RecommendationType.PAUSE);
        var paused = RuleSetV1.evaluate(metrics(
                Optional.of(10_000L), Optional.of(100L), Optional.of(0L),
                Optional.of(new BigDecimal("25.000000")), Optional.empty()), "PAUSED")
                .stream().map(RuleSetV1.Emission::type).toList();
        assertThat(paused).doesNotContain(RecommendationType.PAUSE);
    }

    @Test
    void audienceFatigueIsNeverEmitted() {
        var highImpressions = RuleSetV1.evaluate(metrics(
                Optional.of(50_000L), Optional.of(100L), Optional.of(4L),
                Optional.of(new BigDecimal("25.000000")), Optional.of(new BigDecimal("100.000000"))), "ACTIVE");
        assertThat(highImpressions.stream().map(RuleSetV1.Emission::type))
                .doesNotContain(RecommendationType.AUDIENCE_FATIGUE);
    }

    private static RuleSetV1.Metrics metrics(Optional<Long> impressions, Optional<Long> clicks,
            Optional<Long> conversions, Optional<BigDecimal> spend, Optional<BigDecimal> revenue) {
        Optional<BigDecimal> ctr = impressions.filter(value -> value != 0).flatMap(imp ->
                clicks.map(value -> BigDecimal.valueOf(value).divide(BigDecimal.valueOf(imp), 6, java.math.RoundingMode.HALF_UP)));
        Optional<BigDecimal> cpa = conversions.filter(value -> value != 0).flatMap(conv ->
                spend.map(value -> value.divide(BigDecimal.valueOf(conv), 6, java.math.RoundingMode.HALF_UP)));
        Optional<BigDecimal> roas = spend.filter(value -> value.signum() != 0).flatMap(sp ->
                revenue.map(value -> value.divide(sp, 6, java.math.RoundingMode.HALF_UP)));
        return new RuleSetV1.Metrics(impressions, Optional.empty(), clicks, conversions, spend, revenue,
                ctr, Optional.empty(), Optional.empty(), cpa, Optional.empty(), roas);
    }
}
