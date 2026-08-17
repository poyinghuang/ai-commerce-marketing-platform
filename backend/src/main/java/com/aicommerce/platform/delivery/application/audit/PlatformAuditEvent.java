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
  safeProviderTraceId.ifPresent(v->{if(!v.matches("[A-Za-z0-9._:-]{1,128}"))throw invalid();});
  previousBudgetAmount.ifPresent(PlatformAuditEvent::validateMoney);
  newBudgetAmount.ifPresent(PlatformAuditEvent::validateMoney);
  boolean create=eventKind==PlatformAuditEventKind.ENTITY_CREATED||eventKind==PlatformAuditEventKind.OPERATION_CREATED||eventKind==PlatformAuditEventKind.ATTEMPT_CREATED;
  if((create&&action!=AuditAction.CREATE)||(!create&&action!=AuditAction.UPDATE))throw invalid();
  boolean operationFields=previousOperationStatus.isPresent()||newOperationStatus.isPresent();
  boolean attemptFields=attemptKind.isPresent()||attemptNumber.isPresent()||previousAttemptStatus.isPresent()||newAttemptStatus.isPresent();
  boolean entityFields=previousDesiredState.isPresent()||newDesiredState.isPresent()||previousObservedState.isPresent()||newObservedState.isPresent()||previousBudgetAmount.isPresent()||newBudgetAmount.isPresent()||externalIdFingerprint.isPresent();
  boolean resultFields=normalizedErrorCode.isPresent()||safeProviderTraceId.isPresent();
  switch(subjectType){
   case PLATFORM_OPERATION->{
    if(eventKind!=PlatformAuditEventKind.OPERATION_CREATED&&eventKind!=PlatformAuditEventKind.OPERATION_TRANSITIONED)throw invalid();
    if(!operationFields||attemptFields||entityFields)throw invalid();
    if(eventKind==PlatformAuditEventKind.OPERATION_CREATED){if(previousOperationStatus.isPresent()||newOperationStatus.orElse(null)!=PlatformOperationStatus.CREATED||resultFields)throw invalid();}
    else if(previousOperationStatus.isEmpty()||newOperationStatus.isEmpty()||previousOperationStatus.equals(newOperationStatus))throw invalid();
   }
   case PLATFORM_OPERATION_ATTEMPT->{
    if(eventKind!=PlatformAuditEventKind.ATTEMPT_CREATED&&eventKind!=PlatformAuditEventKind.ATTEMPT_FINALIZED)throw invalid();
    if(operationFields||entityFields||attemptKind.isEmpty()||attemptNumber.isEmpty()||attemptNumber.get()<1||newAttemptStatus.isEmpty())throw invalid();
    if(eventKind==PlatformAuditEventKind.ATTEMPT_CREATED){if(previousAttemptStatus.isPresent()||newAttemptStatus.get()!=PlatformAttemptStatus.STARTED||resultFields)throw invalid();}
    else if(previousAttemptStatus.orElse(null)!=PlatformAttemptStatus.STARTED||newAttemptStatus.get()==PlatformAttemptStatus.STARTED)throw invalid();
   }
   default->{
    PlatformAuditSubjectType expected=switch(entityType){case CAMPAIGN->PlatformAuditSubjectType.PLATFORM_CAMPAIGN;case AD_SET->PlatformAuditSubjectType.PLATFORM_AD_SET;case AD->PlatformAuditSubjectType.PLATFORM_AD;};
    if(subjectType!=expected||!subjectUuid.equals(entityUuid)||operationFields||attemptFields||resultFields)throw invalid();
    if(eventKind==PlatformAuditEventKind.ENTITY_CREATED){
     if(previousDesiredState.isPresent()||newDesiredState.orElse(null)!=PlatformDesiredState.PAUSED||previousObservedState.isPresent()||newObservedState.isPresent()||previousBudgetAmount.isPresent()||newBudgetAmount.isPresent()||externalIdFingerprint.isPresent())throw invalid();
    }else if(eventKind==PlatformAuditEventKind.ENTITY_RESULT_APPLIED){
     boolean desiredChanged=pairChanged(previousDesiredState,newDesiredState);
     boolean budgetChanged=pairChanged(previousBudgetAmount,newBudgetAmount);
     boolean observationPresent=newObservedState.isPresent();
     boolean observationChanged=observationPresent&&!previousObservedState.equals(newObservedState);
     if(previousObservedState.isPresent()&&!observationPresent)throw invalid();
     if(!desiredChanged&&!budgetChanged&&!observationChanged&&externalIdFingerprint.isEmpty())throw invalid();
     if(previousDesiredState.isPresent()!=newDesiredState.isPresent()||previousBudgetAmount.isPresent()!=newBudgetAmount.isPresent())throw invalid();
    }else throw invalid();
   }
  }
 }
 public static PlatformAuditEvent operation(PlatformOperation o,PlatformOperationStatus before,PlatformAuditEventKind kind,PlatformStableErrorCode code,String trace){return new PlatformAuditEvent(PlatformAuditSubjectType.PLATFORM_OPERATION,o.getOperationUuid(),kind==PlatformAuditEventKind.OPERATION_CREATED?AuditAction.CREATE:AuditAction.UPDATE,kind,o.getOperationUuid(),o.getOperationType(),o.getEntityType(),o.getEntityUuid(),Optional.ofNullable(before),Optional.of(o.getStatus()),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.ofNullable(code),Optional.ofNullable(trace));}
 public static PlatformAuditEvent attempt(PlatformOperation o,UUID attemptUuid,PlatformAttemptKind kind,int number,PlatformAttemptStatus before,PlatformAttemptStatus after,PlatformStableErrorCode code,String trace,boolean created){return new PlatformAuditEvent(PlatformAuditSubjectType.PLATFORM_OPERATION_ATTEMPT,attemptUuid,created?AuditAction.CREATE:AuditAction.UPDATE,created?PlatformAuditEventKind.ATTEMPT_CREATED:PlatformAuditEventKind.ATTEMPT_FINALIZED,o.getOperationUuid(),o.getOperationType(),o.getEntityType(),o.getEntityUuid(),Optional.empty(),Optional.empty(),Optional.of(kind),Optional.of(number),Optional.ofNullable(before),Optional.of(after),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.ofNullable(code),Optional.ofNullable(trace));}
 private static boolean pairChanged(Optional<?> previous,Optional<?> next){return previous.isPresent()&&next.isPresent()&&!previous.equals(next);}
 private static void validateMoney(BigDecimal value){BigDecimal normalized=value.stripTrailingZeros();if(normalized.scale()<0)normalized=normalized.setScale(0);if(value.signum()<=0||value.scale()<0||value.scale()>6||!value.equals(normalized))throw invalid();}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");}
}
