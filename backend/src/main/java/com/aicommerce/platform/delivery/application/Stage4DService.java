package com.aicommerce.platform.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.application.port.PlatformMetricsReadPort;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("(local | test) & !production")
public class Stage4DService {
    private final Stage4DTransactions tx;
    private final PlatformDeliveryReadPort deliveryPort;
    private final PlatformMetricsReadPort metricsPort;
    private final Clock clock;

    public Stage4DService(Stage4DTransactions tx, ObjectProvider<PlatformDeliveryReadPort> deliveryPort,
            ObjectProvider<PlatformMetricsReadPort> metricsPort, Clock clock) {
        this.tx = tx;
        this.deliveryPort = deliveryPort.getIfAvailable();
        this.metricsPort = metricsPort.getIfAvailable();
        this.clock = clock;
    }

    public Stage4DViews.DeliveryView delivery(PlatformEntityType type, UUID entityUuid) {
        return tx.delivery(type, entityUuid);
    }

    public Stage4DViews.DeliveryPreview previewDelivery(PlatformEntityType type, UUID entityUuid) {
        return tx.previewDelivery(type, entityUuid);
    }

    public Stage4DViews.DeliveryView syncDelivery(PlatformEntityType type, UUID entityUuid, String requestId) {
        Stage4DTransactions.Eligibility eligibility = tx.lockDelivery(type, entityUuid);
        PlatformDeliveryReadPort.DeliveryObservation observation = readDelivery(tx.deliveryCommand(eligibility));
        return tx.persistDelivery(eligibility, observation, requestId);
    }

    public Stage4DViews.MetricsView metrics(PlatformEntityType type, UUID entityUuid, Optional<Instant> asOf) {
        return tx.metrics(type, entityUuid, asOf);
    }

    public Stage4DViews.MetricsPreview previewMetrics(PlatformEntityType type, UUID entityUuid) {
        return tx.previewMetrics(type, entityUuid);
    }

    public Stage4DViews.MetricsView refreshMetrics(PlatformEntityType type, UUID entityUuid) {
        Stage4DTransactions.Eligibility eligibility = tx.lockMetrics(type, entityUuid);
        PlatformMetricsReadPort.MetricObservation observation = readMetrics(tx.metricsCommand(eligibility));
        return tx.persistMetrics(eligibility, observation, Instant.now(clock));
    }

    private PlatformDeliveryReadPort.DeliveryObservation readDelivery(PlatformDeliveryReadPort.DeliveryReadCommand command) {
        if (deliveryPort == null) throw unavailable();
        try {
            return deliveryPort.readObservedState(command);
        } catch (IllegalStateException exception) {
            if ("adapter invoked inside transaction".equals(exception.getMessage())) throw exception;
            throw unavailable();
        } catch (RuntimeException exception) {
            throw contract();
        }
    }

    private PlatformMetricsReadPort.MetricObservation readMetrics(PlatformMetricsReadPort.MetricReadCommand command) {
        if (metricsPort == null) throw unavailable();
        try {
            return metricsPort.readWindow(command);
        } catch (IllegalStateException exception) {
            if ("adapter invoked inside transaction".equals(exception.getMessage())) throw exception;
            throw unavailable();
        } catch (RuntimeException exception) {
            throw contract();
        }
    }

    private static Stage4BException unavailable() {
        return new Stage4BException("PLATFORM_ADAPTER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static Stage4BException contract() {
        return new Stage4BException("PLATFORM_CONTRACT_INVALID", HttpStatus.BAD_REQUEST);
    }
}
