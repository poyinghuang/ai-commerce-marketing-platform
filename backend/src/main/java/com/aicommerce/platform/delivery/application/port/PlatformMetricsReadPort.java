package com.aicommerce.platform.delivery.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.ProviderKey;

public interface PlatformMetricsReadPort {
    default ProviderKey providerKey() {
        return ProviderKey.FAKE;
    }

    MetricObservation readWindow(MetricReadCommand command);

    record MetricReadCommand(
            UUID platformAccountUuid,
            PlatformEntityType entityType,
            UUID entityUuid,
            String durableExternalId,
            Instant windowStart,
            Instant windowEnd,
            String timezone,
            int attributionClickDays,
            int attributionViewDays,
            String currency) {
        public MetricReadCommand {
            PlatformContractSupport.req(platformAccountUuid);
            PlatformContractSupport.req(entityType);
            PlatformContractSupport.req(entityUuid);
            PlatformContractSupport.req(durableExternalId);
            if (durableExternalId.isBlank()) throw PlatformContractSupport.invalid();
            PlatformContractSupport.req(windowStart);
            PlatformContractSupport.req(windowEnd);
            if (!windowEnd.isAfter(windowStart)) throw PlatformContractSupport.invalid();
            if (!"Asia/Taipei".equals(timezone)) throw PlatformContractSupport.invalid();
            if (attributionClickDays != 7 || attributionViewDays != 1) throw PlatformContractSupport.invalid();
            if (!"TWD".equals(currency)) throw PlatformContractSupport.invalid();
        }
    }

    record MetricObservation(
            Optional<Long> impressions,
            Optional<Long> reach,
            Optional<Long> clicks,
            Optional<Long> conversions,
            Optional<BigDecimal> spend,
            Optional<BigDecimal> revenue,
            FreshnessStatus freshnessStatus,
            Instant fetchedAt,
            Optional<String> safeProviderTraceId) {
        public MetricObservation {
            impressions = PlatformContractSupport.opt(impressions);
            reach = PlatformContractSupport.opt(reach);
            clicks = PlatformContractSupport.opt(clicks);
            conversions = PlatformContractSupport.opt(conversions);
            spend = PlatformContractSupport.opt(spend);
            revenue = PlatformContractSupport.opt(revenue);
            PlatformContractSupport.req(freshnessStatus);
            PlatformContractSupport.req(fetchedAt);
            safeProviderTraceId = PlatformContractSupport.opt(safeProviderTraceId);
            safeProviderTraceId.ifPresent(PlatformContractSupport::safe);
            impressions.ifPresent(MetricObservation::requireCount);
            reach.ifPresent(MetricObservation::requireCount);
            clicks.ifPresent(MetricObservation::requireCount);
            conversions.ifPresent(MetricObservation::requireCount);
            spend.ifPresent(MetricObservation::requireMoney);
            revenue.ifPresent(MetricObservation::requireMoney);
        }

        private static void requireCount(Long value) {
            if (value < 0) throw PlatformContractSupport.invalid();
        }

        private static void requireMoney(BigDecimal value) {
            if (value.signum() < 0 || value.scale() < 0 || value.scale() > 6) throw PlatformContractSupport.invalid();
        }
    }
}
