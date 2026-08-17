package com.aicommerce.platform.delivery.application.audit;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.delivery.domain.*;

public record PlatformAuditEvent(PlatformAuditSubjectType subjectType, UUID subjectUuid, AuditAction action,
 PlatformAuditEventKind eventKind, UUID operationUuid, PlatformOperationType operationType,
 PlatformEntityType entityType, UUID entityUuid, Optional<PlatformOperationStatus> previousOperationStatus,
 Optional<PlatformOperationStatus> newOperationStatus, Optional<PlatformAttemptKind> attemptKind,
 Optional<Integer> attemptNumber, Optional<PlatformAttemptStatus> previousAttemptStatus,
 Optional<PlatformAttemptStatus> newAttemptStatus, Optional<PlatformDesiredState> previousDesiredState,
 Optional<PlatformDesiredState> newDesiredState, Optional<PlatformObservedState> previousObservedState,
 Optional<PlatformObservedState> newObservedState, Optional<BigDecimal> previousBudgetAmount,
 Optional<BigDecimal> newBudgetAmount, Optional<String> externalIdFingerprint,
 Optional<PlatformStableErrorCode> normalizedErrorCode, Optional<String> safeProviderTraceId) {
 public PlatformAuditEvent {
  Objects.requireNonNull(subjectType);Objects.requireNonNull(subjectUuid);Objects.requireNonNull(action);Objects.requireNonNull(eventKind);Objects.requireNonNull(operationUuid);Objects.requireNonNull(operationType);Objects.requireNonNull(entityType);Objects.requireNonNull(entityUuid);
  Objects.requireNonNull(previousOperationStatus);Objects.requireNonNull(newOperationStatus);Objects.requireNonNull(attemptKind);Objects.requireNonNull(attemptNumber);Objects.requireNonNull(previousAttemptStatus);Objects.requireNonNull(newAttemptStatus);Objects.requireNonNull(previousDesiredState);Objects.requireNonNull(newDesiredState);Objects.requireNonNull(previousObservedState);Objects.requireNonNull(newObservedState);Objects.requireNonNull(previousBudgetAmount);Objects.requireNonNull(newBudgetAmount);Objects.requireNonNull(externalIdFingerprint);Objects.requireNonNull(normalizedErrorCode);Objects.requireNonNull(safeProviderTraceId);
  externalIdFingerprint.ifPresent(v->{if(!v.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");});
  boolean create=eventKind==PlatformAuditEventKind.ENTITY_CREATED||eventKind==PlatformAuditEventKind.OPERATION_CREATED||eventKind==PlatformAuditEventKind.ATTEMPT_CREATED;
  if((create&&action!=AuditAction.CREATE)||(!create&&action!=AuditAction.UPDATE))throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
  if(subjectType==PlatformAuditSubjectType.PLATFORM_OPERATION && (newOperationStatus.isEmpty()||attemptKind.isPresent()))throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
  if(subjectType==PlatformAuditSubjectType.PLATFORM_OPERATION_ATTEMPT && (attemptKind.isEmpty()||attemptNumber.isEmpty()||newAttemptStatus.isEmpty()))throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
 }
 public static PlatformAuditEvent operation(PlatformOperation o,PlatformOperationStatus before,PlatformAuditEventKind kind,PlatformStableErrorCode code,String trace){return new PlatformAuditEvent(PlatformAuditSubjectType.PLATFORM_OPERATION,o.getOperationUuid(),kind==PlatformAuditEventKind.OPERATION_CREATED?AuditAction.CREATE:AuditAction.UPDATE,kind,o.getOperationUuid(),o.getOperationType(),o.getEntityType(),o.getEntityUuid(),Optional.ofNullable(before),Optional.of(o.getStatus()),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.ofNullable(code),Optional.ofNullable(trace));}
 public static PlatformAuditEvent attempt(PlatformOperation o,UUID attemptUuid,PlatformAttemptKind kind,int number,PlatformAttemptStatus before,PlatformAttemptStatus after,PlatformStableErrorCode code,String trace,boolean created){return new PlatformAuditEvent(PlatformAuditSubjectType.PLATFORM_OPERATION_ATTEMPT,attemptUuid,created?AuditAction.CREATE:AuditAction.UPDATE,created?PlatformAuditEventKind.ATTEMPT_CREATED:PlatformAuditEventKind.ATTEMPT_FINALIZED,o.getOperationUuid(),o.getOperationType(),o.getEntityType(),o.getEntityUuid(),Optional.empty(),Optional.empty(),Optional.of(kind),Optional.of(number),Optional.ofNullable(before),Optional.of(after),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.ofNullable(code),Optional.ofNullable(trace));}
}
