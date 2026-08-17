package com.aicommerce.platform.delivery.application.audit;

import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
import com.aicommerce.platform.audit.domain.AuditAction;import com.aicommerce.platform.delivery.domain.*;
public record PlatformBudgetAuditEvent(PlatformAuditSubjectType subjectType,UUID subjectUuid,AuditAction action,
 PlatformBudgetAuditEventKind eventKind,UUID operationUuid,PlatformOperationType operationType,PlatformEntityType entityType,
 UUID entityUuid,UUID operationBatchUuid,Optional<UUID> budgetReservationUuid,Optional<UUID> accountBudgetDayUuid,
 LocalDate businessDate,Optional<PlatformReservationKind> reservationKind,String currency,
 Optional<BigDecimal> previousBudgetAmount,Optional<BigDecimal> newBudgetAmount,BigDecimal reservedAmount,
 Optional<BigDecimal> previousAggregateAmount,Optional<BigDecimal> newAggregateAmount){
 public PlatformBudgetAuditEvent{Objects.requireNonNull(subjectType);Objects.requireNonNull(subjectUuid);Objects.requireNonNull(action);Objects.requireNonNull(eventKind);Objects.requireNonNull(operationUuid);Objects.requireNonNull(operationType);Objects.requireNonNull(entityType);Objects.requireNonNull(entityUuid);Objects.requireNonNull(operationBatchUuid);Objects.requireNonNull(budgetReservationUuid);Objects.requireNonNull(accountBudgetDayUuid);Objects.requireNonNull(businessDate);Objects.requireNonNull(reservationKind);Objects.requireNonNull(previousBudgetAmount);Objects.requireNonNull(newBudgetAmount);Objects.requireNonNull(reservedAmount);Objects.requireNonNull(previousAggregateAmount);Objects.requireNonNull(newAggregateAmount);if(!Set.of(PlatformAuditSubjectType.PLATFORM_OPERATION_BATCH,PlatformAuditSubjectType.PLATFORM_BUDGET_RESERVATION,PlatformAuditSubjectType.PLATFORM_ACCOUNT_BUDGET_DAY).contains(subjectType)||!"TWD".equals(currency)||reservedAmount.signum()<0||reservedAmount.scale()>6)throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");}
}
