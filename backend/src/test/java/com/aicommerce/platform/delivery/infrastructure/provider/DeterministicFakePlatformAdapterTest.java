package com.aicommerce.platform.delivery.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformCommand;
import com.aicommerce.platform.delivery.domain.OperationOutcome;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.domain.ReconciliationResult;
import org.junit.jupiter.api.Test;

class DeterministicFakePlatformAdapterTest {
    private final DeterministicFakePlatformAdapter adapter = new DeterministicFakePlatformAdapter(Clock.systemUTC());

    @Test
    void returnsStableSuccessForReplay() {
        PlatformCommand command = command();
        assertThat(adapter.submit(command)).isEqualTo(adapter.submit(command))
                .isInstanceOf(OperationOutcome.Success.class);
    }

    @Test
    void exposesBoundedRetryableValidationAndPermissionFailures() {
        assertScenario(DeterministicFakePlatformAdapter.Scenario.RETRYABLE_FAILURE,
                OperationOutcome.RetryableFailure.class);
        assertScenario(DeterministicFakePlatformAdapter.Scenario.TERMINAL_VALIDATION_FAILURE,
                OperationOutcome.TerminalFailure.class);
        assertScenario(DeterministicFakePlatformAdapter.Scenario.TERMINAL_PERMISSION_FAILURE,
                OperationOutcome.TerminalFailure.class);
    }

    @Test
    void supportsAllAmbiguousReconciliationResults() {
        assertReconciliation(DeterministicFakePlatformAdapter.Scenario.TIMEOUT_RECONCILE_SUCCESS,
                ReconciliationResult.FoundSuccess.class);
        assertReconciliation(DeterministicFakePlatformAdapter.Scenario.TIMEOUT_RECONCILE_FAILURE,
                ReconciliationResult.FoundFailure.class);
        assertReconciliation(DeterministicFakePlatformAdapter.Scenario.TIMEOUT_RECONCILE_UNRESOLVED,
                ReconciliationResult.Unresolved.class);
    }

    @Test
    void preservesEntityTypeAcrossProviderNeutralReads() {
        UUID account = UUID.randomUUID();
        assertThat(adapter.readCampaign(account, UUID.randomUUID()).entityType())
                .isEqualTo(PlatformEntityType.CAMPAIGN);
        assertThat(adapter.readAdSet(account, UUID.randomUUID()).entityType())
                .isEqualTo(PlatformEntityType.AD_SET);
        assertThat(adapter.readAd(account, UUID.randomUUID()).entityType())
                .isEqualTo(PlatformEntityType.AD);
    }

    private void assertScenario(DeterministicFakePlatformAdapter.Scenario scenario,
            Class<? extends OperationOutcome> expected) {
        PlatformCommand command = command();
        adapter.setScenario(command.operationUuid(), scenario);
        assertThat(adapter.submit(command)).isInstanceOf(expected);
    }

    private void assertReconciliation(DeterministicFakePlatformAdapter.Scenario scenario,
            Class<? extends ReconciliationResult> expected) {
        PlatformCommand command = command();
        adapter.setScenario(command.operationUuid(), scenario);
        assertThat(adapter.submit(command)).isInstanceOf(OperationOutcome.Unknown.class);
        assertThat(adapter.reconcile(command.operationUuid())).isInstanceOf(expected);
    }

    private PlatformCommand command() {
        return new PlatformCommand(UUID.randomUUID(), UUID.randomUUID(), PlatformOperationType.CREATE_CAMPAIGN,
                PlatformEntityType.CAMPAIGN, UUID.randomUUID(), "{}", "a".repeat(64));
    }
}
