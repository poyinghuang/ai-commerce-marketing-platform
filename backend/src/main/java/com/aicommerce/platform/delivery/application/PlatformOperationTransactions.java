package com.aicommerce.platform.delivery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.audit.PlatformAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEventKind;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
import com.aicommerce.platform.delivery.application.audit.PlatformBudgetAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformBudgetAuditEventKind;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType;
import com.aicommerce.platform.delivery.application.audit.PlatformReservationKind;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditActor;
import com.aicommerce.platform.audit.domain.AuditActorType;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditSource;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.delivery.application.port.PlatformCommand;
import com.aicommerce.platform.delivery.application.port.*;
import com.aicommerce.platform.delivery.domain.PlatformOperation;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import com.aicommerce.platform.delivery.domain.PlatformAttemptKind;
import com.aicommerce.platform.delivery.domain.PlatformEvidenceResultKind;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import com.aicommerce.platform.delivery.infrastructure.persistence.PlatformOperationJpaRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlatformOperationTransactions {
    private final PlatformOperationJpaRepository operations;
    private final JdbcTemplate jdbc;
    private final PlatformAuditWriter platformAudit;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final PlatformAccountPolicyProvider accountPolicies;
    private final PlatformBudgetPolicyProvider budgetPolicies;
    private final Environment environment;
    private final Stage4CSupport stage4c;
    private final Stage4CCriticalSectionHook stage4cHook;

    public PlatformOperationTransactions(PlatformOperationJpaRepository operations, JdbcTemplate jdbc,
            PlatformAuditWriter platformAudit, Clock clock, ObjectMapper mapper,
            EntityManager entityManager, PlatformAccountPolicyProvider accountPolicies,
            PlatformBudgetPolicyProvider budgetPolicies, Environment environment, Stage4CSupport stage4c,
            Stage4CCriticalSectionHook stage4cHook) {
        this.operations = operations;
        this.jdbc = jdbc;
        this.platformAudit = platformAudit;
        this.clock = clock;
        this.mapper = mapper;
        this.entityManager = entityManager;
        this.accountPolicies=accountPolicies;
        this.budgetPolicies=budgetPolicies;
        this.environment=environment;
        this.stage4c=stage4c;
        this.stage4cHook=stage4cHook;
    }

    @Transactional
    public PlatformOperation createOrReplay(CreatePlatformOperationCommand command,
            PlatformOperationInputCanonicalizer.CanonicalInput input, String idempotencyKey,
            AuditOperationContext context) {
        if (context.actor().type().name().equals("SYSTEM")) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID,
                    Optional.of(command.operationUuid()));
        }
        validateAccount(command.platformAccountUuid(),command.operationUuid());
        validateBudgetPolicy(command,input.json());
        boolean entityCreated=ensureCreateEntity(command, input.json());
        int inserted = jdbc.update("""
                INSERT INTO platform_operations (
                  operation_uuid,platform_account_uuid,operation_type,entity_type,
                  platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid,
                  client_request_uuid,idempotency_key,request_payload,request_sha256,
                  requested_actor_type,requested_actor_id,request_id,status,attempt_count,max_attempts,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?, ?,?,?::jsonb,?, ?,?,?,'CREATED',0,?, CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                ON CONFLICT DO NOTHING
                """, command.operationUuid(), command.platformAccountUuid(), command.operationType().name(),
                command.entityType().name(), entity(command, "CAMPAIGN"), entity(command, "AD_SET"),
                entity(command, "AD"), command.clientRequestUuid(), idempotencyKey, input.json(), input.sha256(),
                context.actor().type().name(), context.actor().id(), context.requestId(), command.maxAttempts());

        PlatformOperation stored = operations
                .findByPlatformAccountUuidAndRequestedActorTypeAndRequestedActorIdAndClientRequestUuid(
                        command.platformAccountUuid(), context.actor().type().name(), context.actor().id(),
                        command.clientRequestUuid())
                .orElseThrow(() -> new PlatformOperationException(PlatformStableErrorCode.PLATFORM_IDEMPOTENCY_CONFLICT,
                        Optional.of(command.operationUuid())));
        if (inserted == 0) {
            if (!stored.getRequestSha256().equals(input.sha256())
                    || stored.getOperationType() != command.operationType()
                    || stored.getEntityType() != command.entityType()
                    || !stored.getEntityUuid().equals(command.entityUuid())
                    || stored.getMaxAttempts() != command.maxAttempts()) {
                throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_IDEMPOTENCY_CONFLICT,
                        Optional.of(stored.getOperationUuid()));
            }
            return stored;
        }
        if(entityCreated) platformAudit.write(entityCreatedEvent(stored),context);
        appendStage4BBudgetAudit(stored, context);
        append(context, AuditAction.CREATE, stored, List.of(
                change("status", null, "CREATED", AuditValueType.ENUM, 0),
                change("operationType", null, stored.getOperationType().name(), AuditValueType.ENUM, 1),
                change("entityType", null, stored.getEntityType().name(), AuditValueType.ENUM, 2),
                change("requestSha256", null, stored.getRequestSha256(), AuditValueType.STRING, 3)));
        return stored;
    }

    private void appendStage4BBudgetAudit(PlatformOperation operation, AuditOperationContext context) {
        if(jdbc.queryForObject("SELECT to_regclass('platform_operation_batches') IS NOT NULL",Boolean.class)!=Boolean.TRUE)return;
        List<Map<String,Object>> batches=jdbc.queryForList("SELECT operation_batch_uuid,business_date,reserved_amount,currency FROM platform_operation_batches WHERE operation_uuid=?",operation.getOperationUuid());
        if(batches.isEmpty())return;
        Map<String,Object> b=batches.getFirst();UUID batch=(UUID)b.get("operation_batch_uuid");
        List<Map<String,Object>> reservations=jdbc.queryForList("SELECT budget_reservation_uuid,account_budget_day_uuid,reservation_kind,previous_budget_amount,new_budget_amount,reserved_amount FROM platform_budget_reservations WHERE operation_uuid=?",operation.getOperationUuid());
        Optional<UUID> reservationUuid=reservations.isEmpty()?Optional.empty():Optional.of((UUID)reservations.getFirst().get("budget_reservation_uuid"));
        Optional<UUID> dayUuid=reservations.isEmpty()?Optional.empty():Optional.of((UUID)reservations.getFirst().get("account_budget_day_uuid"));
        Optional<PlatformReservationKind> kind=reservations.isEmpty()?Optional.empty():Optional.of(PlatformReservationKind.valueOf((String)reservations.getFirst().get("reservation_kind")));
        Optional<BigDecimal> previous=reservations.isEmpty()?Optional.empty():Optional.ofNullable((BigDecimal)reservations.getFirst().get("previous_budget_amount"));
        Optional<BigDecimal> next=reservations.isEmpty()?Optional.empty():Optional.of((BigDecimal)reservations.getFirst().get("new_budget_amount"));
        BigDecimal reserved=(BigDecimal)b.get("reserved_amount");Object rawDate=b.get("business_date");java.time.LocalDate date=rawDate instanceof java.sql.Date sqlDate?sqlDate.toLocalDate():(java.time.LocalDate)rawDate;
        platformAudit.write(new PlatformBudgetAuditEvent(PlatformAuditSubjectType.PLATFORM_OPERATION_BATCH,batch,AuditAction.CREATE,PlatformBudgetAuditEventKind.OPERATION_BATCH_CREATED,operation.getOperationUuid(),operation.getOperationType(),operation.getEntityType(),operation.getEntityUuid(),batch,reservationUuid,dayUuid,date,kind,"TWD",previous,next,reserved,Optional.empty(),Optional.empty()),context);
        if(!reservations.isEmpty()){
            Map<String,Object> r=reservations.getFirst();BigDecimal delta=(BigDecimal)r.get("reserved_amount");
            platformAudit.write(new PlatformBudgetAuditEvent(PlatformAuditSubjectType.PLATFORM_BUDGET_RESERVATION,reservationUuid.orElseThrow(),AuditAction.CREATE,PlatformBudgetAuditEventKind.BUDGET_RESERVATION_CREATED,operation.getOperationUuid(),operation.getOperationType(),operation.getEntityType(),operation.getEntityUuid(),batch,reservationUuid,dayUuid,date,kind,"TWD",previous,next,delta,Optional.empty(),Optional.empty()),context);
            if(delta.signum()>0){BigDecimal aggregate=jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days WHERE account_budget_day_uuid=?",BigDecimal.class,dayUuid.orElseThrow());platformAudit.write(new PlatformBudgetAuditEvent(PlatformAuditSubjectType.PLATFORM_ACCOUNT_BUDGET_DAY,dayUuid.orElseThrow(),AuditAction.UPDATE,PlatformBudgetAuditEventKind.ACCOUNT_DAY_RESERVED,operation.getOperationUuid(),operation.getOperationType(),operation.getEntityType(),operation.getEntityUuid(),batch,reservationUuid,dayUuid,date,kind,"TWD",Optional.empty(),Optional.empty(),delta,Optional.of(aggregate.subtract(delta)),Optional.of(aggregate)),context);}
        }
    }

    @Transactional
    public PlatformCommand claimSubmit(UUID operationUuid, long expectedVersion, Instant claimTime, AuditOperationContext context) {
        return claimExpected(operationUuid, expectedVersion, claimTime, context, PlatformOperationStatus.CREATED);
    }

    @Transactional
    public PlatformCommand claimRetry(UUID operationUuid, long expectedVersion, Instant claimTime, AuditOperationContext context) {
        return claimExpected(operationUuid, expectedVersion, claimTime, context, PlatformOperationStatus.FAILED_RETRYABLE);
    }

    /** Compatibility seam for focused transaction tests; production submit uses claimSubmit. */
    @Transactional
    public PlatformCommand claim(UUID operationUuid, long expectedVersion, Instant claimTime, AuditOperationContext context) {
        return claimSubmit(operationUuid, expectedVersion, claimTime, context);
    }

    private PlatformCommand claimExpected(UUID operationUuid, long expectedVersion, Instant claimTime,
            AuditOperationContext context, PlatformOperationStatus expectedEntryStatus) {
        PlatformOperation operation = lockOperation(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale(operationUuid);
        if(expectedEntryStatus==PlatformOperationStatus.FAILED_RETRYABLE&&operation.getAttemptCount()>=operation.getMaxAttempts())throw new PlatformOperationException(
                PlatformStableErrorCode.PLATFORM_MAX_ATTEMPTS_EXCEEDED,Optional.of(operationUuid));
        if(operation.getStatus()!=expectedEntryStatus)throw new PlatformOperationException(
                PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,Optional.of(operationUuid));
        if(operation.getStatus()==PlatformOperationStatus.FAILED_RETRYABLE&&(operation.getNextAttemptAt()==null||claimTime.isBefore(operation.getNextAttemptAt())))throw new PlatformOperationException(
                PlatformStableErrorCode.PLATFORM_RETRY_NOT_DUE,Optional.of(operationUuid));
        stage4c.validateClaim(operation);
        Optional<String> durableExternalId=validateMutationTarget(operation);
        validateAccount(operation.getPlatformAccountUuid(),operationUuid);
        PlatformOperationStatus before = operation.getStatus();
        int attempts = operation.getAttemptCount();
        try {
            operation.claim(Objects.requireNonNull(claimTime));
            operations.saveAndFlush(operation);
            entityManager.refresh(operation);
            Instant serverClaimedAt=operation.getClaimedAt();
            UUID attemptUuid=UUID.randomUUID(); jdbc.update("""
                    INSERT INTO platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,
                      attempt_number,status,started_at,version) VALUES (?,?,'SUBMIT',?,'STARTED',?,0)
                    """, attemptUuid, operationUuid, operation.getAttemptCount(), ts(serverClaimedAt));
            platformAudit.write(PlatformAuditEvent.attempt(operation,attemptUuid,PlatformAttemptKind.SUBMIT,
                    operation.getAttemptCount(),null,com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,
                    null,null,true),context);
        } catch (OptimisticLockException | OptimisticLockingFailureException exception) {
            throw stale(operationUuid);
        } catch (IllegalStateException exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,
                    Optional.of(operationUuid));
        }
        append(context, AuditAction.UPDATE, operation, List.of(
                change("status", before.name(), "SUBMITTING", AuditValueType.ENUM, 0),
                change("attemptCount", Integer.toString(attempts), Integer.toString(operation.getAttemptCount()),
                        AuditValueType.INTEGER, 1)));
        return new PlatformCommand(operation.getOperationUuid(), operation.getPlatformAccountUuid(),
                operation.getOperationType(), operation.getEntityType(), operation.getEntityUuid(),
                operation.getRequestPayload(), operation.getRequestSha256(), durableExternalId);
    }

    @Transactional
    public PlatformReconciliationQuery claimReconciliation(UUID operationUuid, long expectedVersion, Instant claimTime,
            AuditOperationContext context) {
        PlatformOperation operation = lockOperation(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale(operationUuid);
        if (operation.getStatus() != PlatformOperationStatus.UNKNOWN_OUTCOME) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,
                    Optional.of(operationUuid));
        }
        if (operation.getReconciliationCount() >= 3) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_MAX_RECONCILIATIONS_EXCEEDED,
                    Optional.of(operationUuid));
        }
        validateAccount(operation.getPlatformAccountUuid(),operationUuid);
        int updated = jdbc.update("""
                UPDATE platform_operations SET status='RECONCILING', reconciliation_count=reconciliation_count+1,
                  claimed_at=?, normalized_error_code=NULL, safe_provider_trace_id=NULL, outcome_evidence=NULL,
                  next_attempt_at=NULL, completed_at=NULL, updated_at=?, version=version+1
                WHERE operation_uuid=? AND version=? AND status='UNKNOWN_OUTCOME'
                """, ts(claimTime), ts(claimTime), operationUuid, expectedVersion);
        if (updated != 1) throw stale(operationUuid);
        Instant serverClaimedAt=jdbc.queryForObject("SELECT claimed_at FROM platform_operations WHERE operation_uuid=?",
                (resultSet,rowNumber)->resultSet.getTimestamp(1).toInstant(),operationUuid);
        int reconciliationNumber = operation.getReconciliationCount() + 1;
        UUID attemptUuid=UUID.randomUUID(); jdbc.update("""
                INSERT INTO platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,
                  attempt_number,status,started_at,version) VALUES (?,?,'RECONCILE',?,'STARTED',?,0)
                """, attemptUuid, operationUuid, reconciliationNumber, ts(serverClaimedAt));
        entityManager.clear();
        PlatformOperation claimed = require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(claimed,attemptUuid,PlatformAttemptKind.RECONCILE,
                reconciliationNumber,null,com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,
                null,null,true),context);
        append(context, AuditAction.UPDATE, claimed, List.of(
                change("status", "UNKNOWN_OUTCOME", "RECONCILING", AuditValueType.ENUM, 0),
                change("reconciliationCount", Integer.toString(operation.getReconciliationCount()),
                        Integer.toString(reconciliationNumber), AuditValueType.INTEGER, 1)));
        return new PlatformReconciliationQuery(identity(claimed), claimed.getOperationType(), claimed.getEntityType(),
                claimed.getEntityUuid(), claimed.getAttemptCount(), reconciliationNumber,
                Optional.ofNullable(entityExternalOrNull(claimed)));
    }

    @Transactional
    public PlatformOperation recordWriteOutcome(UUID operationUuid, PlatformWriteOutcome outcome,
            AuditOperationContext context) {
        PlatformOperation operation=lockOperation(operationUuid);
        stage4cHook.beforeFinalize();
        if(operation.getStatus()!=PlatformOperationStatus.SUBMITTING) throw new PlatformOperationException(
                PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,Optional.of(operationUuid));
        Instant now=Instant.now(clock); String status; String code=null; Integer retry=null;
        String trace; NormalizedPlatformEvidence evidence; String external=null;
        EntityMutation entityMutation=null;
        if(outcome instanceof WriteSucceeded x){
            trace=x.safeProviderTraceId().orElse(null);
            entityMutation=applyDirectEntity(operation,x,now).orElse(null);
            if(entityMutation==null){
                status="UNKNOWN_OUTCOME"; code="PLATFORM_RESPONSE_AMBIGUOUS";
                evidence=recoveryEvidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.UNKNOWN_OUTCOME);
            }else{status="SUCCEEDED";evidence=x.evidence();external=x.externalId().orElse(null);}
        }
        else if(outcome instanceof WriteRetryableFailure x){trace=x.safeProviderTraceId().orElse(null);if(operation.getAttemptCount()>=3){status="FAILED_TERMINAL";code="PLATFORM_MAX_ATTEMPTS_EXCEEDED";evidence=new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty());}else{status="FAILED_RETRYABLE";code=x.errorCode().name();retry=x.retryAfterSeconds();evidence=x.evidence();}}
        else if(outcome instanceof WriteTerminalFailure x){status="FAILED_TERMINAL";code=x.errorCode().name();trace=x.safeProviderTraceId().orElse(null);evidence=x.evidence();}
        else {WriteUnknownOutcome x=(WriteUnknownOutcome)outcome;status="UNKNOWN_OUTCOME";code=x.errorCode().name();trace=x.safeProviderTraceId().orElse(null);evidence=x.evidence();}
        String evidenceJson=json(evidence); String attemptStatus=status;
        UUID attemptUuid=jdbc.queryForObject("SELECT operation_attempt_uuid FROM platform_operation_attempts WHERE operation_uuid=? AND attempt_kind='SUBMIT' AND attempt_number=?",UUID.class,operationUuid,operation.getAttemptCount());
        int attemptUpdated=jdbc.update("UPDATE platform_operation_attempts SET status=?,safe_provider_trace_id=?,normalized_error_code=?,evidence=?::jsonb,completed_at=?,version=1 WHERE operation_uuid=? AND attempt_kind='SUBMIT' AND attempt_number=? AND status='STARTED' AND version=0",attemptStatus,trace,code,evidenceJson,ts(now),operationUuid,operation.getAttemptCount());
        if(attemptUpdated!=1)throw stale(operationUuid);
        Instant next=retry==null?null:now.plusSeconds(retry); Instant completed=status.equals("SUCCEEDED")||status.equals("FAILED_TERMINAL")?now:null;
        int opUpdated=jdbc.update("UPDATE platform_operations SET status=?,external_id=?,normalized_error_code=?,safe_provider_trace_id=?,outcome_evidence=?::jsonb,next_attempt_at=?,completed_at=?,updated_at=?,version=version+1 WHERE operation_uuid=? AND status='SUBMITTING' AND version=?",status,external,code,trace,evidenceJson,ts(next),ts(completed),ts(now),operationUuid,operation.getVersion());
        if(opUpdated!=1)throw stale(operationUuid);
        entityManager.clear(); PlatformOperation updated=require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(updated,attemptUuid,PlatformAttemptKind.SUBMIT,updated.getAttemptCount(),com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.valueOf(attemptStatus),code==null?null:PlatformStableErrorCode.valueOf(code),trace,false),context);
        append(context,AuditAction.UPDATE,updated,outcomeChanges(PlatformOperationStatus.SUBMITTING,updated));
        if(entityMutation!=null)platformAudit.write(entityResultEvent(updated,entityMutation),context);
        return updated;
    }

    private String json(Object value){try{
        if(value instanceof NormalizedPlatformEvidence evidence){
            var fields=new java.util.LinkedHashMap<String,Object>();
            fields.put("schemaVersion",evidence.schemaVersion()); fields.put("providerKey",evidence.providerKey().name());
            fields.put("attemptKind",evidence.attemptKind().name()); fields.put("resultKind",evidence.resultKind().name());
            evidence.externalIdFingerprint().ifPresent(v->fields.put("externalIdFingerprint",v));
            evidence.observedState().ifPresent(v->fields.put("observedState",v.name()));
            evidence.retryAfterSeconds().ifPresent(v->fields.put("retryAfterSeconds",v));
            return mapper.writeValueAsString(fields);
        }
        return mapper.writeValueAsString(value);
    }catch(Exception e){throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID,Optional.empty());}}

    @Transactional
    public PlatformOperation recordReconciliationOutcome(UUID operationUuid, PlatformReconciliationOutcome outcome,
            Instant completionTime, AuditOperationContext context) {
        PlatformOperation operation = lockOperation(operationUuid);
        stage4cHook.beforeFinalize();
        if (operation.getStatus() != PlatformOperationStatus.RECONCILING) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,
                    Optional.of(operationUuid));
        }
        String status; String attemptStatus; String code = null; String trace; String external = null;
        NormalizedPlatformEvidence evidence; EntityMutation entityMutation=null;
        if (outcome instanceof ReconciliationFound found) {
            trace = found.safeProviderTraceId().orElse(null);
            entityMutation=applyReconciledEntity(operation, found, completionTime).orElse(null);
            if (entityMutation==null) {
                status = "UNKNOWN_OUTCOME"; attemptStatus = "UNKNOWN_OUTCOME";
                code = "PLATFORM_RESPONSE_AMBIGUOUS";
                evidence = recoveryEvidence(PlatformAttemptKind.RECONCILE, PlatformEvidenceResultKind.UNKNOWN_OUTCOME);
            } else {
                status = "SUCCEEDED"; attemptStatus = "SUCCEEDED"; evidence = found.evidence();
                external = found.externalId().orElse(null);
            }
        } else if (outcome instanceof ReconciliationNotFound notFound) {
            status = "UNKNOWN_OUTCOME"; attemptStatus = "NOT_FOUND";
            code = "PLATFORM_RECONCILIATION_NOT_FOUND"; trace = notFound.safeProviderTraceId().orElse(null);
            evidence = notFound.evidence();
        } else if (outcome instanceof ReconciliationStillUnknown unknown) {
            status = "UNKNOWN_OUTCOME"; attemptStatus = "UNKNOWN_OUTCOME";
            code = "PLATFORM_RECONCILIATION_INCONCLUSIVE"; trace = unknown.safeProviderTraceId().orElse(null);
            evidence = unknown.evidence();
        } else {
            ReconciliationTerminalFailure failure = (ReconciliationTerminalFailure) outcome;
            status = "FAILED_TERMINAL"; attemptStatus = "FAILED_TERMINAL";
            code = failure.errorCode().name(); trace = failure.safeProviderTraceId().orElse(null);
            evidence = failure.evidence();
        }
        String evidenceJson = json(evidence);
        UUID attemptUuid=jdbc.queryForObject("SELECT operation_attempt_uuid FROM platform_operation_attempts WHERE operation_uuid=? AND attempt_kind='RECONCILE' AND attempt_number=?",UUID.class,operationUuid,operation.getReconciliationCount());
        int attemptUpdated = jdbc.update("""
                UPDATE platform_operation_attempts SET status=?,safe_provider_trace_id=?,normalized_error_code=?,
                  evidence=?::jsonb,completed_at=?,version=1
                WHERE operation_uuid=? AND attempt_kind='RECONCILE' AND attempt_number=? AND status='STARTED'
                """, attemptStatus, trace, code, evidenceJson, ts(completionTime), operationUuid,
                operation.getReconciliationCount());
        if (attemptUpdated != 1) throw stale(operationUuid);
        Instant completed = status.equals("SUCCEEDED") || status.equals("FAILED_TERMINAL") ? completionTime : null;
        int opUpdated = jdbc.update("""
                UPDATE platform_operations SET status=?,external_id=?,normalized_error_code=?,safe_provider_trace_id=?,
                  outcome_evidence=?::jsonb,next_attempt_at=NULL,completed_at=?,updated_at=?,version=version+1
                WHERE operation_uuid=? AND status='RECONCILING' AND version=?
                """, status, external, code, trace, evidenceJson, ts(completed), ts(completionTime), operationUuid,
                operation.getVersion());
        if (opUpdated != 1) throw stale(operationUuid);
        entityManager.clear();
        PlatformOperation updated = require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(updated,attemptUuid,PlatformAttemptKind.RECONCILE,updated.getReconciliationCount(),com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.valueOf(attemptStatus),code==null?null:PlatformStableErrorCode.valueOf(code),trace,false),context);
        append(context, AuditAction.UPDATE, updated, outcomeChanges(PlatformOperationStatus.RECONCILING, updated));
        if(entityMutation!=null)platformAudit.write(entityResultEvent(updated,entityMutation),context);
        return updated;
    }

    @Transactional
    public PlatformOperation recoverStaleClaim(UUID operationUuid, long expectedVersion, Instant recoveryTime,
            AuditOperationContext context) {
        PlatformOperation operation = lockOperation(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale(operationUuid);
        if ((operation.getStatus() != PlatformOperationStatus.SUBMITTING
                && operation.getStatus() != PlatformOperationStatus.RECONCILING)
                || operation.getClaimedAt() == null
                || operation.getClaimedAt().isAfter(recoveryTime.minus(Duration.ofMinutes(5)))) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_RECOVERY_NOT_DUE,
                    Optional.of(operationUuid));
        }
        PlatformAttemptKind kind = operation.getStatus() == PlatformOperationStatus.SUBMITTING
                ? PlatformAttemptKind.SUBMIT : PlatformAttemptKind.RECONCILE;
        int number = kind == PlatformAttemptKind.SUBMIT ? operation.getAttemptCount() : operation.getReconciliationCount();
        String code = kind == PlatformAttemptKind.SUBMIT ? "PLATFORM_RESPONSE_AMBIGUOUS"
                : "PLATFORM_RECONCILIATION_INCONCLUSIVE";
        PlatformEvidenceResultKind result = kind == PlatformAttemptKind.SUBMIT
                ? PlatformEvidenceResultKind.UNKNOWN_OUTCOME : PlatformEvidenceResultKind.STILL_UNKNOWN;
        String evidence = json(recoveryEvidence(kind, result));
        UUID attemptUuid=jdbc.queryForObject("SELECT operation_attempt_uuid FROM platform_operation_attempts WHERE operation_uuid=? AND attempt_kind=? AND attempt_number=?",UUID.class,operationUuid,kind.name(),number);
        int attemptUpdated = jdbc.update("""
                UPDATE platform_operation_attempts SET status='UNKNOWN_OUTCOME',normalized_error_code=?,
                  safe_provider_trace_id=NULL,evidence=?::jsonb,completed_at=?,version=1
                WHERE operation_uuid=? AND attempt_kind=? AND attempt_number=? AND status='STARTED' AND version=0
                """, code, evidence, ts(recoveryTime), operationUuid, kind.name(), number);
        if (attemptUpdated != 1) throw stale(operationUuid);
        int opUpdated = jdbc.update("""
                UPDATE platform_operations SET status='UNKNOWN_OUTCOME',external_id=NULL,normalized_error_code=?,
                  safe_provider_trace_id=NULL,outcome_evidence=?::jsonb,next_attempt_at=NULL,completed_at=NULL,
                  updated_at=?,version=version+1 WHERE operation_uuid=? AND version=? AND status=?
                """, code, evidence, ts(recoveryTime), operationUuid, expectedVersion, operation.getStatus().name());
        if (opUpdated != 1) throw stale(operationUuid);
        entityManager.clear();
        PlatformOperation updated = require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(updated,attemptUuid,kind,number,
                com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,
                com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.UNKNOWN_OUTCOME,
                PlatformStableErrorCode.valueOf(code),null,false),context);
        append(context, AuditAction.UPDATE, updated, outcomeChanges(operation.getStatus(), updated));
        return updated;
    }

    @Transactional
    public PlatformOperation recoverImmediateAmbiguity(UUID operationUuid, Optional<String> retainedTrace,
            AuditOperationContext context) {
        PlatformOperation operation = lockOperation(operationUuid);
        if (operation.getStatus() != PlatformOperationStatus.SUBMITTING
                && operation.getStatus() != PlatformOperationStatus.RECONCILING) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,
                    Optional.of(operationUuid));
        }
        Instant now = Instant.now(clock);
        PlatformAttemptKind kind = operation.getStatus() == PlatformOperationStatus.SUBMITTING
                ? PlatformAttemptKind.SUBMIT : PlatformAttemptKind.RECONCILE;
        int number = kind == PlatformAttemptKind.SUBMIT ? operation.getAttemptCount() : operation.getReconciliationCount();
        String code = "PLATFORM_RESPONSE_AMBIGUOUS";
        String evidence = json(recoveryEvidence(kind, PlatformEvidenceResultKind.UNKNOWN_OUTCOME));
        String trace = retainedTrace.filter(value -> !value.isBlank()).orElse(null);
        UUID attemptUuid = jdbc.queryForObject(
                "SELECT operation_attempt_uuid FROM platform_operation_attempts WHERE operation_uuid=? AND attempt_kind=? AND attempt_number=?",
                UUID.class, operationUuid, kind.name(), number);
        int attemptUpdated = jdbc.update("""
                UPDATE platform_operation_attempts SET status='UNKNOWN_OUTCOME',normalized_error_code=?,
                  safe_provider_trace_id=?,evidence=?::jsonb,completed_at=?,version=1
                WHERE operation_uuid=? AND attempt_kind=? AND attempt_number=? AND status='STARTED' AND version=0
                """, code, trace, evidence, ts(now), operationUuid, kind.name(), number);
        if (attemptUpdated != 1) throw stale(operationUuid);
        int opUpdated = jdbc.update("""
                UPDATE platform_operations SET status='UNKNOWN_OUTCOME',external_id=NULL,normalized_error_code=?,
                  safe_provider_trace_id=?,outcome_evidence=?::jsonb,next_attempt_at=NULL,completed_at=NULL,
                  updated_at=?,version=version+1 WHERE operation_uuid=? AND version=? AND status=?
                """, code, trace, evidence, ts(now), operationUuid, operation.getVersion(), operation.getStatus().name());
        if (opUpdated != 1) throw stale(operationUuid);
        entityManager.clear();
        PlatformOperation updated = require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(updated, attemptUuid, kind, number,
                com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,
                com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.UNKNOWN_OUTCOME,
                PlatformStableErrorCode.PLATFORM_RESPONSE_AMBIGUOUS, trace, false), context);
        append(context, AuditAction.UPDATE, updated, outcomeChanges(operation.getStatus(), updated));
        return updated;
    }

    @Transactional(readOnly = true)
    public PlatformOperation get(UUID operationUuid) { return require(operationUuid); }

    @Transactional(readOnly = true)
    public AuditOperationContext operationContext(UUID operationUuid) {
        PlatformOperation operation = require(operationUuid);
        AuditActorType actorType = AuditActorType.valueOf(operation.getRequestedActorType());
        AuditSource source = actorType == AuditActorType.LOCAL_ADMIN ? AuditSource.API : AuditSource.SYSTEM;
        return new AuditOperationContext(operationUuid, operation.getRequestId(),
                new AuditActor(actorType, operation.getRequestedActorId()), source);
    }

    private UUID entity(CreatePlatformOperationCommand command, String expected) {
        return command.entityType().name().equals(expected) ? command.entityUuid() : null;
    }

    @SuppressWarnings("unchecked")
    private void validateBudgetPolicy(CreatePlatformOperationCommand command,String requestJson){
        if(command.operationType()!=com.aicommerce.platform.delivery.domain.PlatformOperationType.CREATE_AD_SET&&command.operationType()!=com.aicommerce.platform.delivery.domain.PlatformOperationType.UPDATE_BUDGET)return;
        try{
            var payload=mapper.readValue(requestJson,java.util.Map.class);
            var type=com.aicommerce.platform.delivery.domain.PlatformBudgetType.valueOf(payload.get("budgetType").toString());
            var policy=Objects.requireNonNull(budgetPolicies.requirePolicy(command.platformAccountUuid(),type));
            BigDecimal amount=canonicalMoney(new BigDecimal(payload.get(command.operationType()==com.aicommerce.platform.delivery.domain.PlatformOperationType.CREATE_AD_SET?"budgetAmount":"newBudgetAmount").toString()));
            if(!policy.currency().equals(payload.get("currency"))||policy.budgetType()!=type||amount.signum()<=0||amount.compareTo(policy.maxEntityAmount())>0)throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_POLICY_REJECTED,Optional.of(command.operationUuid()));
        }catch(PlatformOperationException exception){throw exception;}catch(RuntimeException exception){throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_POLICY_REJECTED,Optional.of(command.operationUuid()));}
    }

    private void validateAccount(UUID accountUuid,UUID operationUuid){
        PlatformAccountPolicy policy=Objects.requireNonNull(accountPolicies.requirePolicy(accountUuid));
        if(policy.providerKey()!=ProviderKey.FAKE)throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_PROVIDER_UNSUPPORTED,Optional.of(operationUuid));
        if(!policy.active())throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_ACCOUNT_INACTIVE,Optional.of(operationUuid));
        if(!policy.currency().equals("TWD")||!policy.timezone().equals("Asia/Taipei"))throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_POLICY_REJECTED,Optional.of(operationUuid));
        boolean test=java.util.Arrays.asList(environment.getActiveProfiles()).contains("test");
        boolean local=java.util.Arrays.asList(environment.getActiveProfiles()).contains("local");
        if((test&&policy.environment()!=com.aicommerce.platform.delivery.domain.PlatformEnvironment.TEST)||(local&&policy.environment()!=com.aicommerce.platform.delivery.domain.PlatformEnvironment.LOCAL)||(!test&&!local))throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH,Optional.of(operationUuid));
    }

    @SuppressWarnings("unchecked")
    private Optional<String> validateMutationTarget(PlatformOperation operation) {
        if (operation.getOperationType().name().startsWith("CREATE_")) return Optional.empty();
        try {
            var payload = mapper.readValue(operation.getRequestPayload(), java.util.Map.class);
            String table=switch(operation.getEntityType()){case CAMPAIGN->"platform_campaigns";case AD_SET->"platform_ad_sets";case AD->"platform_ads";};
            String column=switch(operation.getEntityType()){case CAMPAIGN->"platform_campaign_uuid";case AD_SET->"platform_ad_set_uuid";case AD->"platform_ad_uuid";};
            var row=jdbc.queryForMap("SELECT desired_state,external_id,version,"+
                    (operation.getEntityType()==com.aicommerce.platform.delivery.domain.PlatformEntityType.AD_SET
                            ? "budget_type,budget_amount,currency" : "NULL::text AS budget_type,NULL::numeric AS budget_amount,NULL::text AS currency")+
                    " FROM "+table+" WHERE "+column+"=? AND platform_account_uuid=? FOR UPDATE",
                    operation.getEntityUuid(),operation.getPlatformAccountUuid());
            String external=(String)row.get("external_id");
            if(external==null)throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID,Optional.of(operation.getOperationUuid()));
            long expected=Long.parseLong(payload.get("expectedEntityVersion").toString());
            if(((Number)row.get("version")).longValue()!=expected)throw stale(operation.getOperationUuid());
            if(operation.getOperationType()==com.aicommerce.platform.delivery.domain.PlatformOperationType.PAUSE
                    ||operation.getOperationType()==com.aicommerce.platform.delivery.domain.PlatformOperationType.RESUME){
                String current=(String)row.get("desired_state");
                String required=operation.getOperationType()==com.aicommerce.platform.delivery.domain.PlatformOperationType.PAUSE?"ACTIVE":"PAUSED";
                String target=operation.getOperationType()==com.aicommerce.platform.delivery.domain.PlatformOperationType.PAUSE?"PAUSED":"ACTIVE";
                if(!target.equals(payload.get("targetDesiredState"))||!required.equals(current))throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,Optional.of(operation.getOperationUuid()));
            } else {
                if(!row.get("budget_type").equals(payload.get("budgetType"))||!row.get("currency").toString().equals(payload.get("currency")))throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_POLICY_REJECTED,Optional.of(operation.getOperationUuid()));
                BigDecimal current=canonicalMoney((BigDecimal)row.get("budget_amount"));
                BigDecimal previous=canonicalMoney(new BigDecimal(payload.get("previousBudgetAmount").toString()));
                BigDecimal next=canonicalMoney(new BigDecimal(payload.get("newBudgetAmount").toString()));
                if(current.compareTo(previous)!=0)throw stale(operation.getOperationUuid());
                if(current.compareTo(next)==0)throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,Optional.of(operation.getOperationUuid()));
            }
            return Optional.of(external);
        } catch (PlatformOperationException exception) {
            throw exception;
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID,Optional.of(operation.getOperationUuid()));
        } catch (RuntimeException exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID,Optional.of(operation.getOperationUuid()));
        }
    }

    private PlatformOperation lockOperation(UUID operationUuid) {
        entityManager.flush();
        if (jdbc.query("SELECT 1 FROM platform_operations WHERE operation_uuid=? FOR UPDATE",
                (rs, n) -> 1, operationUuid).size() != 1) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_OPERATION_NOT_FOUND,
                    Optional.of(operationUuid));
        }
        stage4cHook.afterOperationLock();
        entityManager.clear();
        return require(operationUuid);
    }

    private PlatformOperation require(UUID operationUuid) {
        return operations.findById(operationUuid)
                .orElseThrow(() -> new PlatformOperationException(PlatformStableErrorCode.PLATFORM_OPERATION_NOT_FOUND,
                        Optional.of(operationUuid)));
    }

    private PlatformCommandIdentity identity(PlatformOperation operation) {
        return new PlatformCommandIdentity(operation.getOperationUuid(), operation.getPlatformAccountUuid(),
                operation.getIdempotencyKey(), operation.getRequestSha256());
    }

    @SuppressWarnings("unchecked")
    private boolean ensureCreateEntity(CreatePlatformOperationCommand command, String requestJson) {
        if (!command.operationType().name().startsWith("CREATE_")) return false;
        try {
            var payload = mapper.readValue(requestJson, java.util.Map.class);
            int inserted=switch (command.operationType()) {
                case CREATE_CAMPAIGN -> jdbc.update("""
                    INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,
                      objective,desired_state,schedule_start,schedule_end,account_timezone,version)
                    VALUES (?,?,?,'OUTCOME_SALES','PAUSED',?::timestamptz,?::timestamptz,?,0) ON CONFLICT DO NOTHING
                    """, command.entityUuid(), UUID.fromString(payload.get("campaignUuid").toString()),
                        command.platformAccountUuid(), payload.get("scheduleStart"), payload.get("scheduleEnd"),
                        payload.get("accountTimezone"));
                case CREATE_AD_SET -> jdbc.update("""
                    INSERT INTO platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,
                      budget_type,budget_amount,currency,schedule_start,schedule_end,account_timezone,
                      optimization_goal,targeting_profile_key,placement_profile_key,desired_state,version)
                    VALUES (?,?,?,?,?,?,?::timestamptz,?::timestamptz,?,?,?,?,'PAUSED',0) ON CONFLICT DO NOTHING
                    """, command.entityUuid(), UUID.fromString(payload.get("platformCampaignUuid").toString()),
                        command.platformAccountUuid(), payload.get("budgetType"), payload.get("budgetAmount"),
                        payload.get("currency"), payload.get("scheduleStart"), payload.get("scheduleEnd"),
                        payload.get("accountTimezone"), payload.get("optimizationGoal"),
                        payload.get("targetingProfileKey"), payload.get("placementProfileKey"));
                case CREATE_AD -> jdbc.update("""
                    INSERT INTO platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,
                      asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,
                      creative_mapping_key,desired_state,version) VALUES (?,?,?,?,?,?,?,?,?,'PAUSED',0)
                    ON CONFLICT DO NOTHING
                    """, command.entityUuid(), UUID.fromString(payload.get("platformAdSetUuid").toString()),
                        command.platformAccountUuid(), UUID.fromString(payload.get("productUuid").toString()),
                        UUID.fromString(payload.get("assetUuid").toString()),
                        UUID.fromString(payload.get("generationOutputUuid").toString()),
                        UUID.fromString(payload.get("reviewDecisionUuid").toString()),
                        payload.get("approvedChecksumSha256"), payload.get("creativeMappingKey"));
                default -> 0;
            };
            return inserted==1;
        } catch (RuntimeException exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID,
                    Optional.of(command.operationUuid()));
        }
    }

    private PlatformAuditEvent entityCreatedEvent(PlatformOperation operation){
        var subject=switch(operation.getEntityType()){case CAMPAIGN->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_CAMPAIGN;case AD_SET->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_AD_SET;case AD->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_AD;};
        return new PlatformAuditEvent(subject,operation.getEntityUuid(),AuditAction.CREATE,PlatformAuditEventKind.ENTITY_CREATED,
                operation.getOperationUuid(),operation.getOperationType(),operation.getEntityType(),operation.getEntityUuid(),
                Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),
                Optional.empty(),Optional.of(com.aicommerce.platform.delivery.domain.PlatformDesiredState.PAUSED),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
    }

    private PlatformAuditEvent entityResultEvent(PlatformOperation operation,EntityMutation mutation){
        var subject=switch(operation.getEntityType()){case CAMPAIGN->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_CAMPAIGN;case AD_SET->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_AD_SET;case AD->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_AD;};
        return new PlatformAuditEvent(subject,operation.getEntityUuid(),AuditAction.UPDATE,PlatformAuditEventKind.ENTITY_RESULT_APPLIED,
                operation.getOperationUuid(),operation.getOperationType(),operation.getEntityType(),operation.getEntityUuid(),
                Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),
                mutation.previousDesiredState(),mutation.newDesiredState(),mutation.previousObservedState(),mutation.newObservedState(),
                mutation.previousBudgetAmount(),mutation.newBudgetAmount(),mutation.externalIdFingerprint(),Optional.empty(),Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private Optional<EntityMutation> applyDirectEntity(PlatformOperation operation, WriteSucceeded success, Instant now) {
        return applyEntity(operation,success.externalId(),success.observedState(),success.evidence().externalIdFingerprint(),now);
    }

    @SuppressWarnings("unchecked")
    private Optional<EntityMutation> applyEntity(PlatformOperation operation,Optional<String> returnedExternalId,
            Optional<com.aicommerce.platform.delivery.domain.PlatformObservedState> observedState,
            Optional<String> externalIdFingerprint,Instant now) {
        try {
            var payload = mapper.readValue(operation.getRequestPayload(), java.util.Map.class);
            EntityState before=entityState(operation);
            if (operation.getOperationType().name().startsWith("CREATE_")) {
                String external = returnedExternalId.orElseThrow();
                String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
                String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
                int updated=jdbc.update("UPDATE " + table + " SET external_id=?,observed_state=COALESCE(?,observed_state),updated_at=?,version=version+1 WHERE " + column + "=? AND external_id IS NULL",
                        external, observedState.map(Enum::name).orElse(null), ts(now),operation.getEntityUuid());
                if(updated!=1)return Optional.empty();
                return Optional.of(mutation(before,before.desiredState(),before.budgetAmount(),observedState,externalIdFingerprint));
            }
            long expected = Long.parseLong(payload.get("expectedEntityVersion").toString());
            String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
            String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
            if (operation.getOperationType() == com.aicommerce.platform.delivery.domain.PlatformOperationType.UPDATE_BUDGET) {
                BigDecimal newBudget=canonicalMoney(new BigDecimal(payload.get("newBudgetAmount").toString()));
                int updated=jdbc.update("UPDATE platform_ad_sets SET budget_amount=?,last_budget_operation_uuid=?,observed_state=COALESCE(?,observed_state),updated_at=?,version=version+1 WHERE platform_ad_set_uuid=? AND version=? AND budget_amount=?",
                        payload.get("newBudgetAmount"), operation.getOperationUuid(),
                        observedState.map(Enum::name).orElse(null), ts(now), operation.getEntityUuid(), expected,
                        payload.get("previousBudgetAmount"));
                if(updated!=1)return Optional.empty();
                return Optional.of(mutation(before,before.desiredState(),Optional.of(newBudget),observedState,Optional.empty()));
            }
            var desired=com.aicommerce.platform.delivery.domain.PlatformDesiredState.valueOf(payload.get("targetDesiredState").toString());
            int updated=jdbc.update("UPDATE " + table + " SET desired_state=?,observed_state=COALESCE(?,observed_state),updated_at=?,version=version+1 WHERE " + column + "=? AND version=?",
                    desired.name(), observedState.map(Enum::name).orElse(null), ts(now),operation.getEntityUuid(), expected);
            if(updated!=1)return Optional.empty();
            return Optional.of(mutation(before,Optional.of(desired),before.budgetAmount(),observedState,Optional.empty()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String entityExternalOrNull(PlatformOperation operation) {
        String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
        String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
        return jdbc.queryForObject("SELECT external_id FROM " + table + " WHERE " + column + "=?", String.class,
                operation.getEntityUuid());
    }

    private Optional<EntityMutation> applyReconciledEntity(PlatformOperation operation, ReconciliationFound found, Instant now) {
        return applyEntity(operation,found.externalId(),found.observedState(),found.evidence().externalIdFingerprint(),now);
    }

    private EntityState entityState(PlatformOperation operation){
        String table=switch(operation.getEntityType()){case CAMPAIGN->"platform_campaigns";case AD_SET->"platform_ad_sets";case AD->"platform_ads";};
        String column=switch(operation.getEntityType()){case CAMPAIGN->"platform_campaign_uuid";case AD_SET->"platform_ad_set_uuid";case AD->"platform_ad_uuid";};
        return jdbc.queryForObject("SELECT desired_state,observed_state,"+(operation.getEntityType()==com.aicommerce.platform.delivery.domain.PlatformEntityType.AD_SET?"budget_amount":"NULL::numeric AS budget_amount")+" FROM "+table+" WHERE "+column+"=?",(rs,row)->new EntityState(Optional.of(com.aicommerce.platform.delivery.domain.PlatformDesiredState.valueOf(rs.getString("desired_state"))),Optional.ofNullable(rs.getString("observed_state")).map(com.aicommerce.platform.delivery.domain.PlatformObservedState::valueOf),Optional.ofNullable(rs.getBigDecimal("budget_amount")).map(PlatformOperationTransactions::canonicalMoney)),operation.getEntityUuid());
    }

    private EntityMutation mutation(EntityState before,Optional<com.aicommerce.platform.delivery.domain.PlatformDesiredState> desired,
            Optional<BigDecimal> budget,Optional<com.aicommerce.platform.delivery.domain.PlatformObservedState> observed,
            Optional<String> fingerprint){
        Optional<com.aicommerce.platform.delivery.domain.PlatformDesiredState> previousDesired=before.desiredState().equals(desired)?Optional.empty():before.desiredState();
        Optional<com.aicommerce.platform.delivery.domain.PlatformDesiredState> newDesired=before.desiredState().equals(desired)?Optional.empty():desired;
        Optional<BigDecimal> previousBudget=before.budgetAmount().equals(budget)?Optional.empty():before.budgetAmount();
        Optional<BigDecimal> newBudget=before.budgetAmount().equals(budget)?Optional.empty():budget;
        Optional<com.aicommerce.platform.delivery.domain.PlatformObservedState> previousObserved=observed.isPresent()?before.observedState():Optional.empty();
        return new EntityMutation(previousDesired,newDesired,previousObserved,observed,previousBudget,newBudget,fingerprint);
    }

    private record EntityState(Optional<com.aicommerce.platform.delivery.domain.PlatformDesiredState> desiredState,
            Optional<com.aicommerce.platform.delivery.domain.PlatformObservedState> observedState,Optional<BigDecimal> budgetAmount){}
    private record EntityMutation(Optional<com.aicommerce.platform.delivery.domain.PlatformDesiredState> previousDesiredState,
            Optional<com.aicommerce.platform.delivery.domain.PlatformDesiredState> newDesiredState,
            Optional<com.aicommerce.platform.delivery.domain.PlatformObservedState> previousObservedState,
            Optional<com.aicommerce.platform.delivery.domain.PlatformObservedState> newObservedState,
            Optional<BigDecimal> previousBudgetAmount,Optional<BigDecimal> newBudgetAmount,
            Optional<String> externalIdFingerprint){}

    private static BigDecimal canonicalMoney(BigDecimal value){BigDecimal normalized=value.stripTrailingZeros();return normalized.scale()<0?normalized.setScale(0):normalized;}

    private NormalizedPlatformEvidence recoveryEvidence(PlatformAttemptKind kind,
            PlatformEvidenceResultKind result) {
        return new NormalizedPlatformEvidence(1, ProviderKey.FAKE, kind, result, Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private List<AuditChange> outcomeChanges(PlatformOperationStatus before, PlatformOperation after) {
        var changes = new java.util.ArrayList<AuditChange>();
        changes.add(change("status", before.name(), after.getStatus().name(), AuditValueType.ENUM, changes.size()));
        if (after.getExternalId() != null) changes.add(change("externalId", null, after.getExternalId(), AuditValueType.STRING, changes.size()));
        if (after.getNormalizedErrorCode() != null) changes.add(change("normalizedErrorCode", null,
                after.getNormalizedErrorCode(), AuditValueType.STRING, changes.size()));
        if (after.getSafeProviderTraceId() != null) changes.add(change("safeProviderTraceId", null,
                after.getSafeProviderTraceId(), AuditValueType.STRING, changes.size()));
        return List.copyOf(changes);
    }

    private void append(AuditOperationContext context, AuditAction action, PlatformOperation operation,
            List<AuditChange> changes) {
        PlatformOperationStatus previous=null;
        for(AuditChange change:changes)if(change.fieldName().equals("status")&&change.oldValue()!=null)previous=PlatformOperationStatus.valueOf(change.oldValue());
        platformAudit.write(PlatformAuditEvent.operation(operation,previous,
                action==AuditAction.CREATE?PlatformAuditEventKind.OPERATION_CREATED:PlatformAuditEventKind.OPERATION_TRANSITIONED,
                operation.getNormalizedErrorCode()==null?null:PlatformStableErrorCode.valueOf(operation.getNormalizedErrorCode()),
                operation.getSafeProviderTraceId()),context);
    }

    private AuditChange change(String field, String oldValue, String newValue, AuditValueType type, int order) {
        return new AuditChange(field, oldValue, newValue, type, order);
    }

    private PlatformOperationException stale(UUID operationUuid) {
        return new PlatformOperationException(PlatformStableErrorCode.PLATFORM_STALE_VERSION,Optional.of(operationUuid));
    }
}
