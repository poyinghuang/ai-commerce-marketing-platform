package com.aicommerce.platform.delivery.infrastructure.provider;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aicommerce.platform.delivery.application.port.PlatformAdPort;
import com.aicommerce.platform.delivery.application.port.PlatformAdSetPort;
import com.aicommerce.platform.delivery.application.port.PlatformCampaignPort;
import com.aicommerce.platform.delivery.application.port.PlatformCommand;
import com.aicommerce.platform.delivery.application.port.PlatformDeliveryRecord;
import com.aicommerce.platform.delivery.domain.OperationOutcome;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.ReconciliationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Profile("(local | test) & !production")
public class DeterministicFakePlatformAdapter implements PlatformCampaignPort, PlatformAdSetPort, PlatformAdPort {
    private final Map<UUID, Scenario> scenarios = new ConcurrentHashMap<>();
    private final Map<UUID, OperationOutcome> submitted = new ConcurrentHashMap<>();
    private final Clock clock;

    public DeterministicFakePlatformAdapter(Clock clock) { this.clock = clock; }

    public void setScenario(UUID operationUuid, Scenario scenario) {
        scenarios.put(operationUuid, scenario);
        submitted.remove(operationUuid);
    }

    public void clearScenarios() {
        scenarios.clear();
        submitted.clear();
    }

    @Override
    public OperationOutcome submit(PlatformCommand command) {
        requireNoTransaction();
        return submitted.computeIfAbsent(command.operationUuid(), ignored -> outcome(command));
    }

    @Override
    public ReconciliationResult reconcile(UUID operationUuid) {
        requireNoTransaction();
        String externalId = externalId(operationUuid);
        String trace = trace(operationUuid);
        return switch (scenarios.getOrDefault(operationUuid, Scenario.SUCCESS)) {
            case TIMEOUT_RECONCILE_SUCCESS -> new ReconciliationResult.FoundSuccess(externalId, trace);
            case TIMEOUT_RECONCILE_FAILURE -> new ReconciliationResult.FoundFailure("FAKE_RECONCILED_FAILURE", trace);
            case TIMEOUT_RECONCILE_UNRESOLVED -> new ReconciliationResult.Unresolved(trace);
            default -> new ReconciliationResult.Unresolved(trace);
        };
    }

    @Override
    public PlatformDeliveryRecord readCampaign(UUID accountUuid, UUID entityUuid) {
        return new PlatformDeliveryRecord(accountUuid, PlatformEntityType.CAMPAIGN, entityUuid, "PAUSED",
                Instant.now(clock));
    }

    @Override
    public PlatformDeliveryRecord readAdSet(UUID accountUuid, UUID entityUuid) {
        return new PlatformDeliveryRecord(accountUuid, PlatformEntityType.AD_SET, entityUuid, "PAUSED",
                Instant.now(clock));
    }

    @Override
    public PlatformDeliveryRecord readAd(UUID accountUuid, UUID entityUuid) {
        return new PlatformDeliveryRecord(accountUuid, PlatformEntityType.AD, entityUuid, "PAUSED",
                Instant.now(clock));
    }

    private OperationOutcome outcome(PlatformCommand command) {
        String trace = trace(command.operationUuid());
        return switch (scenarios.getOrDefault(command.operationUuid(), Scenario.SUCCESS)) {
            case SUCCESS -> new OperationOutcome.Success(externalId(command.operationUuid()), trace);
            case RETRYABLE_FAILURE -> new OperationOutcome.RetryableFailure("FAKE_RATE_LIMITED", trace);
            case TERMINAL_VALIDATION_FAILURE -> new OperationOutcome.TerminalFailure("FAKE_VALIDATION_REJECTED", trace);
            case TERMINAL_PERMISSION_FAILURE -> new OperationOutcome.TerminalFailure("FAKE_PERMISSION_DENIED", trace);
            case TIMEOUT_RECONCILE_SUCCESS, TIMEOUT_RECONCILE_FAILURE, TIMEOUT_RECONCILE_UNRESOLVED ->
                    new OperationOutcome.Unknown(trace);
        };
    }

    private String externalId(UUID operationUuid) { return "fake-" + operationUuid; }
    private String trace(UUID operationUuid) { return "fake-trace-" + operationUuid; }

    private void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Fake platform adapter must be invoked outside a database transaction");
        }
    }

    public enum Scenario {
        SUCCESS,
        RETRYABLE_FAILURE,
        TERMINAL_VALIDATION_FAILURE,
        TERMINAL_PERMISSION_FAILURE,
        TIMEOUT_RECONCILE_SUCCESS,
        TIMEOUT_RECONCILE_FAILURE,
        TIMEOUT_RECONCILE_UNRESOLVED
    }
}
