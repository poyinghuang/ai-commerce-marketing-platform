package com.aicommerce.platform.delivery.infrastructure.provider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.application.port.PlatformMetricsReadPort;
import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Profile("(local | test) & !production")
@ConditionalOnProperty(name = "platform.adapter", havingValue = "fake")
public class DeterministicFakePlatformReadAdapter implements PlatformDeliveryReadPort, PlatformMetricsReadPort {
    public static final long SUCCESS_IMPRESSIONS = 10_000L;
    public static final long SUCCESS_REACH = 8_000L;
    public static final long SUCCESS_CLICKS = 100L;
    public static final long SUCCESS_CONVERSIONS = 4L;
    public static final BigDecimal SUCCESS_SPEND = new BigDecimal("25.000000");
    public static final BigDecimal SUCCESS_REVENUE = new BigDecimal("100.000000");
    public static final BigDecimal CORRECTED_SPEND = new BigDecimal("26.000000");

    private volatile Scenario scenario;
    private final AtomicInteger invocations = new AtomicInteger();
    private volatile boolean transactionObserved;
    private final Clock clock;

    public DeterministicFakePlatformReadAdapter(Clock clock) {
        this(Scenario.SUCCESS, clock);
    }

    @Autowired
    public DeterministicFakePlatformReadAdapter(
            @Value("${platform.fake.read-scenario:SUCCESS}") String scenario, Clock clock) {
        this(Scenario.valueOf(scenario), clock);
    }

    public DeterministicFakePlatformReadAdapter(Scenario scenario, Clock clock) {
        this.scenario = Objects.requireNonNull(scenario);
        this.clock = Objects.requireNonNull(clock);
    }

    public int invocationCount() {
        return invocations.get();
    }

    public boolean transactionObserved() {
        return transactionObserved;
    }

    public void reset() {
        scenario = Scenario.SUCCESS;
        invocations.set(0);
        transactionObserved = false;
    }

    public void useScenario(Scenario fixture) {
        this.scenario = Objects.requireNonNull(fixture);
    }

    @Override
    public DeliveryObservation readObservedState(DeliveryReadCommand command) {
        Objects.requireNonNull(command);
        begin();
        return switch (scenario) {
            case MALFORMED -> new DeliveryObservation(null, Optional.empty());
            case THROW -> throw new IllegalStateException("fake-read-throw");
            case UNAVAILABLE -> new DeliveryObservation(PlatformObservedState.UNKNOWN, Optional.empty());
            default -> new DeliveryObservation(fromDesired(command.currentDesiredState()), Optional.empty());
        };
    }

    @Override
    public MetricObservation readWindow(MetricReadCommand command) {
        Objects.requireNonNull(command);
        begin();
        Instant fetchedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return switch (scenario) {
            case MALFORMED -> new MetricObservation(
                    Optional.of(-1L), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), FreshnessStatus.FRESH, fetchedAt, Optional.empty());
            case THROW -> throw new IllegalStateException("fake-read-throw");
            case UNAVAILABLE -> empty(fetchedAt, FreshnessStatus.UNAVAILABLE);
            case DELAYED -> success(fetchedAt, FreshnessStatus.DELAYED, SUCCESS_SPEND);
            case PARTIAL_NULL -> new MetricObservation(
                    Optional.of(SUCCESS_IMPRESSIONS), Optional.empty(), Optional.of(SUCCESS_CLICKS), Optional.empty(),
                    Optional.empty(), Optional.empty(), FreshnessStatus.FRESH, fetchedAt, Optional.empty());
            case CORRECTED -> success(fetchedAt, FreshnessStatus.FRESH, CORRECTED_SPEND);
            default -> success(fetchedAt, FreshnessStatus.FRESH, SUCCESS_SPEND);
        };
    }

    private MetricObservation success(Instant fetchedAt, FreshnessStatus freshness, BigDecimal spend) {
        return new MetricObservation(
                Optional.of(SUCCESS_IMPRESSIONS),
                Optional.of(SUCCESS_REACH),
                Optional.of(SUCCESS_CLICKS),
                Optional.of(SUCCESS_CONVERSIONS),
                Optional.of(spend),
                Optional.of(SUCCESS_REVENUE),
                freshness,
                fetchedAt,
                Optional.empty());
    }

    private MetricObservation empty(Instant fetchedAt, FreshnessStatus freshness) {
        return new MetricObservation(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), freshness, fetchedAt, Optional.empty());
    }

    private static PlatformObservedState fromDesired(PlatformDesiredState desired) {
        return switch (desired) {
            case PAUSED -> PlatformObservedState.PAUSED;
            case ACTIVE -> PlatformObservedState.ACTIVE;
            default -> PlatformObservedState.UNKNOWN;
        };
    }

    private void begin() {
        invocations.incrementAndGet();
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        transactionObserved |= active;
        if (active) throw new IllegalStateException("adapter invoked inside transaction");
    }

    public enum Scenario {
        SUCCESS, DELAYED, UNAVAILABLE, PARTIAL_NULL, CORRECTED, MALFORMED, THROW
    }
}
