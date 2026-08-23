package com.aicommerce.platform.delivery.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.application.port.PlatformMetricsReadPort;
import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import org.junit.jupiter.api.Test;

class DeterministicFakePlatformReadAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-22T16:00:00Z"), ZoneOffset.UTC);

    @Test
    void everyFixtureMatchesTheClosedDeliveryAndMetricsContract() {
        var command = delivery(PlatformDesiredState.PAUSED);
        var metrics = metrics();
        assertThat(adapter(DeterministicFakePlatformReadAdapter.Scenario.SUCCESS).readObservedState(command).observedState())
                .isEqualTo(PlatformObservedState.PAUSED);
        assertThat(adapter(DeterministicFakePlatformReadAdapter.Scenario.SUCCESS).readObservedState(delivery(PlatformDesiredState.ACTIVE)).observedState())
                .isEqualTo(PlatformObservedState.ACTIVE);
        assertThat(adapter(DeterministicFakePlatformReadAdapter.Scenario.SUCCESS).readObservedState(delivery(PlatformDesiredState.DRAFT)).observedState())
                .isEqualTo(PlatformObservedState.UNKNOWN);
        for (var scenario : java.util.List.of(
                DeterministicFakePlatformReadAdapter.Scenario.SUCCESS,
                DeterministicFakePlatformReadAdapter.Scenario.DELAYED,
                DeterministicFakePlatformReadAdapter.Scenario.PARTIAL_NULL,
                DeterministicFakePlatformReadAdapter.Scenario.CORRECTED)) {
            var observation = adapter(scenario).readObservedState(command).observedState();
            assertThat(observation).isNotIn(PlatformObservedState.PENDING, PlatformObservedState.COMPLETED,
                    PlatformObservedState.REJECTED, PlatformObservedState.ERROR, PlatformObservedState.DELETED);
        }
        assertThat(adapter(DeterministicFakePlatformReadAdapter.Scenario.UNAVAILABLE).readObservedState(command).observedState())
                .isEqualTo(PlatformObservedState.UNKNOWN);
        assertThatThrownBy(() -> adapter(DeterministicFakePlatformReadAdapter.Scenario.MALFORMED).readObservedState(command))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> adapter(DeterministicFakePlatformReadAdapter.Scenario.THROW).readObservedState(command))
                .isInstanceOf(IllegalStateException.class);

        var success = adapter(DeterministicFakePlatformReadAdapter.Scenario.SUCCESS).readWindow(metrics);
        assertThat(success.impressions()).contains(10_000L);
        assertThat(success.reach()).contains(8_000L);
        assertThat(success.clicks()).contains(100L);
        assertThat(success.conversions()).contains(4L);
        assertThat(success.spend()).contains(new BigDecimal("25.000000"));
        assertThat(success.revenue()).contains(new BigDecimal("100.000000"));
        assertThat(success.freshnessStatus()).isEqualTo(FreshnessStatus.FRESH);
        assertThat(adapter(DeterministicFakePlatformReadAdapter.Scenario.DELAYED).readWindow(metrics).freshnessStatus())
                .isEqualTo(FreshnessStatus.DELAYED);
        assertThat(adapter(DeterministicFakePlatformReadAdapter.Scenario.CORRECTED).readWindow(metrics).spend())
                .contains(new BigDecimal("26.000000"));
        var partial = adapter(DeterministicFakePlatformReadAdapter.Scenario.PARTIAL_NULL).readWindow(metrics);
        assertThat(partial.impressions()).contains(10_000L);
        assertThat(partial.clicks()).contains(100L);
        assertThat(partial.spend()).isEmpty();
        assertThat(adapter(DeterministicFakePlatformReadAdapter.Scenario.UNAVAILABLE).readWindow(metrics).freshnessStatus())
                .isEqualTo(FreshnessStatus.UNAVAILABLE);
        assertThatThrownBy(() -> adapter(DeterministicFakePlatformReadAdapter.Scenario.MALFORMED).readWindow(metrics))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> adapter(DeterministicFakePlatformReadAdapter.Scenario.THROW).readWindow(metrics))
                .isInstanceOf(IllegalStateException.class);
        var counted = adapter(DeterministicFakePlatformReadAdapter.Scenario.SUCCESS);
        counted.readWindow(metrics);
        assertThat(counted.invocationCount()).isEqualTo(1);
        assertThat(counted.transactionObserved()).isFalse();
    }

    private static DeterministicFakePlatformReadAdapter adapter(DeterministicFakePlatformReadAdapter.Scenario scenario) {
        return new DeterministicFakePlatformReadAdapter(scenario, CLOCK);
    }

    private static PlatformDeliveryReadPort.DeliveryReadCommand delivery(PlatformDesiredState desired) {
        return new PlatformDeliveryReadPort.DeliveryReadCommand(
                UUID.fromString("00000000-0000-4000-8000-00000000005b"),
                PlatformEntityType.CAMPAIGN,
                UUID.fromString("00000000-0000-4000-8000-0000000000c1"),
                "fake-campaign-1",
                desired);
    }

    private static PlatformMetricsReadPort.MetricReadCommand metrics() {
        return new PlatformMetricsReadPort.MetricReadCommand(
                UUID.fromString("00000000-0000-4000-8000-00000000005b"),
                PlatformEntityType.CAMPAIGN,
                UUID.fromString("00000000-0000-4000-8000-0000000000c1"),
                "fake-campaign-1",
                Instant.parse("2026-08-21T16:00:00Z"),
                Instant.parse("2026-08-22T16:00:00Z"),
                "Asia/Taipei",
                7,
                1,
                "TWD");
    }
}
