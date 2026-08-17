package com.aicommerce.platform.delivery.application.audit;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.delivery.domain.*;
import org.junit.jupiter.api.Test;

class PlatformBudgetAuditEventTest {
    private final UUID operation=UUID.randomUUID(),entity=UUID.randomUUID(),batch=UUID.randomUUID(),reservation=UUID.randomUUID(),day=UUID.randomUUID();

    @Test void acceptsTheExactFiveCommandBudgetEventShapes() {
        assertThatCode(()->event(PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,PlatformBudgetAuditEventKind.OPERATION_BATCH_CREATED,PlatformAuditSubjectType.PLATFORM_OPERATION_BATCH,batch,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),BigDecimal.ZERO,Optional.empty(),Optional.empty())).doesNotThrowAnyException();
        assertThatCode(()->event(PlatformOperationType.RESUME,PlatformEntityType.CAMPAIGN,PlatformBudgetAuditEventKind.OPERATION_BATCH_CREATED,PlatformAuditSubjectType.PLATFORM_OPERATION_BATCH,batch,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),BigDecimal.ZERO,Optional.empty(),Optional.empty())).doesNotThrowAnyException();
        assertBudgetTriplet(PlatformOperationType.CREATE_AD_SET,PlatformReservationKind.INITIAL,Optional.empty(),Optional.of(new BigDecimal("20")),new BigDecimal("20"));
        assertBudgetTriplet(PlatformOperationType.UPDATE_BUDGET,PlatformReservationKind.INCREASE,Optional.of(new BigDecimal("20")),Optional.of(new BigDecimal("30")),new BigDecimal("10"));
        assertBudgetPair(PlatformOperationType.UPDATE_BUDGET,PlatformReservationKind.DECREASE_NO_RELEASE,Optional.of(new BigDecimal("30")),Optional.of(new BigDecimal("15")),BigDecimal.ZERO);
    }

    @Test void rejectsMismatchedKindsSubjectsActionsAmountsCurrencyAndAggregateArithmetic() {
        assertThatThrownBy(()->event(PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.AD_SET,PlatformBudgetAuditEventKind.OPERATION_BATCH_CREATED,PlatformAuditSubjectType.PLATFORM_OPERATION_BATCH,batch,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),BigDecimal.ZERO,Optional.empty(),Optional.empty())).isInstanceOf(IllegalArgumentException.class).hasMessage("PLATFORM_CONTRACT_INVALID");
        assertThatThrownBy(()->new PlatformBudgetAuditEvent(PlatformAuditSubjectType.PLATFORM_ACCOUNT_BUDGET_DAY,day,AuditAction.UPDATE,PlatformBudgetAuditEventKind.ACCOUNT_DAY_RESERVED,operation,PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,entity,batch,Optional.of(reservation),Optional.of(day),LocalDate.of(2026,1,1),Optional.of(PlatformReservationKind.INCREASE),"TWD",Optional.empty(),Optional.empty(),new BigDecimal("10"),Optional.of(new BigDecimal("20")),Optional.of(new BigDecimal("29")))).isInstanceOf(IllegalArgumentException.class).hasMessage("PLATFORM_CONTRACT_INVALID");
        assertThatThrownBy(()->new PlatformBudgetAuditEvent(PlatformAuditSubjectType.PLATFORM_OPERATION_BATCH,batch,AuditAction.UPDATE,PlatformBudgetAuditEventKind.OPERATION_BATCH_CREATED,operation,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,entity,batch,Optional.empty(),Optional.empty(),LocalDate.of(2026,1,1),Optional.empty(),"USD",Optional.empty(),Optional.empty(),BigDecimal.ZERO,Optional.empty(),Optional.empty())).isInstanceOf(IllegalArgumentException.class).hasMessage("PLATFORM_CONTRACT_INVALID");
    }

    private void assertBudgetTriplet(PlatformOperationType type,PlatformReservationKind kind,Optional<BigDecimal> previous,Optional<BigDecimal> next,BigDecimal delta){assertBudgetPair(type,kind,previous,next,delta);assertThatCode(()->new PlatformBudgetAuditEvent(PlatformAuditSubjectType.PLATFORM_ACCOUNT_BUDGET_DAY,day,AuditAction.UPDATE,PlatformBudgetAuditEventKind.ACCOUNT_DAY_RESERVED,operation,type,PlatformEntityType.AD_SET,entity,batch,Optional.of(reservation),Optional.of(day),LocalDate.of(2026,1,1),Optional.of(kind),"TWD",Optional.empty(),Optional.empty(),delta,Optional.of(new BigDecimal("40")),Optional.of(new BigDecimal("40").add(delta)))).doesNotThrowAnyException();}
    private void assertBudgetPair(PlatformOperationType type,PlatformReservationKind kind,Optional<BigDecimal> previous,Optional<BigDecimal> next,BigDecimal delta){assertThatCode(()->event(type,PlatformEntityType.AD_SET,PlatformBudgetAuditEventKind.OPERATION_BATCH_CREATED,PlatformAuditSubjectType.PLATFORM_OPERATION_BATCH,batch,Optional.of(reservation),Optional.of(day),Optional.of(kind),previous,next,delta,Optional.empty(),Optional.empty())).doesNotThrowAnyException();assertThatCode(()->event(type,PlatformEntityType.AD_SET,PlatformBudgetAuditEventKind.BUDGET_RESERVATION_CREATED,PlatformAuditSubjectType.PLATFORM_BUDGET_RESERVATION,reservation,Optional.of(reservation),Optional.of(day),Optional.of(kind),previous,next,delta,Optional.empty(),Optional.empty())).doesNotThrowAnyException();}
    private PlatformBudgetAuditEvent event(PlatformOperationType type,PlatformEntityType entityType,PlatformBudgetAuditEventKind kind,PlatformAuditSubjectType subject,UUID subjectUuid,Optional<UUID> reservationUuid,Optional<UUID> dayUuid,Optional<PlatformReservationKind> reservationKind,Optional<BigDecimal> previous,Optional<BigDecimal> next,BigDecimal delta,Optional<BigDecimal> previousAggregate,Optional<BigDecimal> newAggregate){return new PlatformBudgetAuditEvent(subject,subjectUuid,kind==PlatformBudgetAuditEventKind.ACCOUNT_DAY_RESERVED?AuditAction.UPDATE:AuditAction.CREATE,kind,operation,type,entityType,entity,batch,reservationUuid,dayUuid,LocalDate.of(2026,1,1),reservationKind,"TWD",previous,next,delta,previousAggregate,newAggregate);}
}
