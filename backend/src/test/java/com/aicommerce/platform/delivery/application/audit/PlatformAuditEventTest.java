package com.aicommerce.platform.delivery.application.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import org.junit.jupiter.api.Test;

class PlatformAuditEventTest {
    private final UUID operationUuid = UUID.randomUUID();
    private final UUID entityUuid = UUID.randomUUID();

    @Test
    void acceptsExactBudgetMutationAndRejectsUnrelatedOrNoOpFields() {
        event(Optional.empty(), Optional.empty(), Optional.of(PlatformObservedState.PAUSED),
                Optional.of(PlatformObservedState.PAUSED), Optional.of(new BigDecimal("20")),
                Optional.of(new BigDecimal("25.5")), Optional.empty());

        assertThatThrownBy(() -> event(Optional.empty(), Optional.empty(),
                Optional.of(PlatformObservedState.PAUSED), Optional.of(PlatformObservedState.PAUSED),
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PLATFORM_CONTRACT_INVALID");
        assertThatThrownBy(() -> event(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new BigDecimal("20.000000")), Optional.of(new BigDecimal("25")), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PLATFORM_CONTRACT_INVALID");
    }

    @Test
    void rejectsEntitySubjectAndCorrelationMismatch() {
        assertThatThrownBy(() -> new PlatformAuditEvent(PlatformAuditSubjectType.PLATFORM_CAMPAIGN, entityUuid,
                AuditAction.UPDATE, PlatformAuditEventKind.ENTITY_RESULT_APPLIED, operationUuid,
                PlatformOperationType.UPDATE_BUDGET, PlatformEntityType.AD_SET, entityUuid,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new BigDecimal("20")), Optional.of(new BigDecimal("25")), Optional.empty(),
                Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PLATFORM_CONTRACT_INVALID");
    }

    private PlatformAuditEvent event(Optional<PlatformDesiredState> oldDesired,
            Optional<PlatformDesiredState> newDesired, Optional<PlatformObservedState> oldObserved,
            Optional<PlatformObservedState> newObserved, Optional<BigDecimal> oldBudget,
            Optional<BigDecimal> newBudget, Optional<String> fingerprint) {
        return new PlatformAuditEvent(PlatformAuditSubjectType.PLATFORM_AD_SET, entityUuid, AuditAction.UPDATE,
                PlatformAuditEventKind.ENTITY_RESULT_APPLIED, operationUuid, PlatformOperationType.UPDATE_BUDGET,
                PlatformEntityType.AD_SET, entityUuid, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), oldDesired, newDesired, oldObserved,
                newObserved, oldBudget, newBudget, fingerprint, Optional.empty(), Optional.empty());
    }
}
