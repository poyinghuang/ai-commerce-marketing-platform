package com.aicommerce.platform.delivery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.delivery.application.port.PlatformCommand;
import com.aicommerce.platform.delivery.application.port.*;
import com.aicommerce.platform.delivery.domain.OperationOutcome;
import com.aicommerce.platform.delivery.domain.PlatformOperation;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.ReconciliationResult;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import com.aicommerce.platform.delivery.domain.PlatformAttemptKind;
import com.aicommerce.platform.delivery.domain.PlatformEvidenceResultKind;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import com.aicommerce.platform.delivery.infrastructure.persistence.PlatformOperationJpaRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlatformOperationTransactions {
    private final PlatformOperationJpaRepository operations;
    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final AuditOperationContextFactory auditContexts;
    private final Clock clock;
    private final ObjectMapper mapper;

    public PlatformOperationTransactions(PlatformOperationJpaRepository operations, JdbcTemplate jdbc,
            AuditWriter audit, AuditOperationContextFactory auditContexts, Clock clock, ObjectMapper mapper) {
        this.operations = operations;
        this.jdbc = jdbc;
        this.audit = audit;
        this.auditContexts = auditContexts;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Transactional
    public PlatformOperation createOrReplay(CreatePlatformOperationCommand command,
            PlatformOperationInputCanonicalizer.CanonicalInput input, String idempotencyKey,
            AuditOperationContext context) {
        if (context.actor().type().name().equals("SYSTEM")) {
            throw new PlatformOperationConflictException("PLATFORM_TRUSTED_ACTOR_REQUIRED",
                    "A trusted human actor is required to create a platform operation");
        }
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
                .orElseThrow(() -> new PlatformOperationConflictException("PLATFORM_IDEMPOTENCY_CONFLICT",
                        "Idempotency identity is already in use"));
        if (inserted == 0) {
            if (!stored.getRequestSha256().equals(input.sha256())
                    || stored.getOperationType() != command.operationType()
                    || stored.getEntityType() != command.entityType()
                    || !stored.getEntityUuid().equals(command.entityUuid())
                    || stored.getMaxAttempts() != command.maxAttempts()) {
                throw new PlatformOperationConflictException("PLATFORM_IDEMPOTENCY_PAYLOAD_MISMATCH",
                        "The request identity was previously used with different immutable input");
            }
            return stored;
        }
        append(context, AuditAction.CREATE, stored, List.of(
                change("status", null, "CREATED", AuditValueType.ENUM, 0),
                change("operationType", null, stored.getOperationType().name(), AuditValueType.ENUM, 1),
                change("entityType", null, stored.getEntityType().name(), AuditValueType.ENUM, 2),
                change("requestSha256", null, stored.getRequestSha256(), AuditValueType.STRING, 3)));
        return stored;
    }

    @Transactional
    public PlatformCommand claim(UUID operationUuid, long expectedVersion, AuditOperationContext context) {
        PlatformOperation operation = require(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale();
        PlatformOperationStatus before = operation.getStatus();
        int attempts = operation.getAttemptCount();
        try {
            operation.claim(Instant.now(clock));
            operations.saveAndFlush(operation);
            jdbc.update("""
                    INSERT INTO platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,
                      attempt_number,status,started_at,version) VALUES (?,?,'SUBMIT',?,'STARTED',?,0)
                    """, UUID.randomUUID(), operationUuid, operation.getAttemptCount(), Instant.now(clock));
        } catch (OptimisticLockException | OptimisticLockingFailureException exception) {
            throw stale();
        } catch (IllegalStateException exception) {
            throw new PlatformOperationConflictException("PLATFORM_OPERATION_NOT_CLAIMABLE", exception.getMessage());
        }
        append(context, AuditAction.UPDATE, operation, List.of(
                change("status", before.name(), "SUBMITTING", AuditValueType.ENUM, 0),
                change("attemptCount", Integer.toString(attempts), Integer.toString(operation.getAttemptCount()),
                        AuditValueType.INTEGER, 1)));
        return new PlatformCommand(operation.getOperationUuid(), operation.getPlatformAccountUuid(),
                operation.getOperationType(), operation.getEntityType(), operation.getEntityUuid(),
                operation.getRequestPayload(), operation.getRequestSha256());
    }

    @Transactional
    public PlatformOperation recordWriteOutcome(UUID operationUuid, PlatformWriteOutcome outcome,
            AuditOperationContext context) {
        PlatformOperation operation=require(operationUuid);
        if(operation.getStatus()!=PlatformOperationStatus.SUBMITTING) throw new PlatformOperationException(
                PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,Optional.of(operationUuid));
        Instant now=Instant.now(clock); String status; String code=null; Integer retry=null;
        String trace; NormalizedPlatformEvidence evidence; String external=null;
        if(outcome instanceof WriteSucceeded x){status="SUCCEEDED";trace=x.safeProviderTraceId().orElse(null);evidence=x.evidence();external=x.externalId().orElse(null);}
        else if(outcome instanceof WriteRetryableFailure x){trace=x.safeProviderTraceId().orElse(null);if(operation.getAttemptCount()>=3){status="FAILED_TERMINAL";code="PLATFORM_MAX_ATTEMPTS_EXCEEDED";evidence=new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty());}else{status="FAILED_RETRYABLE";code=x.errorCode().name();retry=x.retryAfterSeconds();evidence=x.evidence();}}
        else if(outcome instanceof WriteTerminalFailure x){status="FAILED_TERMINAL";code=x.errorCode().name();trace=x.safeProviderTraceId().orElse(null);evidence=x.evidence();}
        else {WriteUnknownOutcome x=(WriteUnknownOutcome)outcome;status="UNKNOWN_OUTCOME";code=x.errorCode().name();trace=x.safeProviderTraceId().orElse(null);evidence=x.evidence();}
        String evidenceJson=json(evidence); String attemptStatus=status;
        jdbc.update("UPDATE platform_operation_attempts SET status=?,safe_provider_trace_id=?,normalized_error_code=?,evidence=?::jsonb,completed_at=?,version=1 WHERE operation_uuid=? AND attempt_kind='SUBMIT' AND attempt_number=? AND status='STARTED'",attemptStatus,trace,code,evidenceJson,now,operationUuid,operation.getAttemptCount());
        Instant next=retry==null?null:now.plusSeconds(retry); Instant completed=status.equals("SUCCEEDED")||status.equals("FAILED_TERMINAL")?now:null;
        jdbc.update("UPDATE platform_operations SET status=?,external_id=?,normalized_error_code=?,safe_provider_trace_id=?,outcome_evidence=?::jsonb,next_attempt_at=?,completed_at=?,updated_at=?,version=version+1 WHERE operation_uuid=? AND status='SUBMITTING'",status,external,code,trace,evidenceJson,next,completed,now,operationUuid);
        if(status.equals("SUCCEEDED")&&external!=null){String table=switch(operation.getEntityType()){case CAMPAIGN->"platform_campaigns";case AD_SET->"platform_ad_sets";case AD->"platform_ads";};String id=switch(operation.getEntityType()){case CAMPAIGN->"platform_campaign_uuid";case AD_SET->"platform_ad_set_uuid";case AD->"platform_ad_uuid";};jdbc.update("UPDATE "+table+" SET external_id=?,updated_at=?,version=version+1 WHERE "+id+"=? AND external_id IS NULL",external,now,operation.getEntityUuid());}
        PlatformOperation updated=require(operationUuid); append(context,AuditAction.UPDATE,updated,outcomeChanges(PlatformOperationStatus.SUBMITTING,updated)); return updated;
    }

    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID,Optional.empty());}}

    @Transactional
    public PlatformOperation recordOutcome(UUID operationUuid, OperationOutcome outcome,
            AuditOperationContext context) {
        PlatformOperation operation = require(operationUuid);
        PlatformOperationStatus before = operation.getStatus();
        Instant now = Instant.now(clock);
        if (outcome instanceof OperationOutcome.Success success) {
            operation.succeed(success.externalId(), success.safeTraceId(), now);
        } else if (outcome instanceof OperationOutcome.RetryableFailure failure) {
            if (operation.getAttemptCount() >= operation.getMaxAttempts()) {
                operation.failTerminal("PLATFORM_RETRY_LIMIT_EXHAUSTED", failure.safeTraceId(), now);
            } else {
                operation.failRetryable(failure.code(), failure.safeTraceId(), retryAt(now, operation.getAttemptCount()));
            }
        } else if (outcome instanceof OperationOutcome.TerminalFailure failure) {
            operation.failTerminal(failure.code(), failure.safeTraceId(), now);
        } else if (outcome instanceof OperationOutcome.Unknown unknown) {
            operation.unknown(unknown.safeTraceId());
        } else {
            throw new IllegalArgumentException("Unsupported platform operation outcome");
        }
        operations.saveAndFlush(operation);
        append(context, AuditAction.UPDATE, operation, outcomeChanges(before, operation));
        return operation;
    }

    @Transactional
    public PlatformOperation recordReconciliation(UUID operationUuid, ReconciliationResult result,
            AuditOperationContext context) {
        PlatformOperation operation = require(operationUuid);
        if (result instanceof ReconciliationResult.Unresolved) return operation;
        PlatformOperationStatus before = operation.getStatus();
        Instant now = Instant.now(clock);
        if (result instanceof ReconciliationResult.FoundSuccess success) {
            operation.reconcileSuccess(success.externalId(), success.safeTraceId(), now);
        } else if (result instanceof ReconciliationResult.FoundFailure failure) {
            operation.reconcileFailure(failure.code(), failure.safeTraceId(), now);
        }
        operations.saveAndFlush(operation);
        append(context, AuditAction.UPDATE, operation, outcomeChanges(before, operation));
        return operation;
    }

    @Transactional
    public PlatformOperation recoverExpiredSubmission(UUID operationUuid, long expectedVersion, Duration lease,
            AuditOperationContext context) {
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("submission lease must be positive");
        }
        PlatformOperation operation = require(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale();
        Instant now = Instant.now(clock);
        if (operation.getStatus() != PlatformOperationStatus.SUBMITTING
                || operation.getClaimedAt() == null
                || operation.getClaimedAt().plus(lease).isAfter(now)) {
            throw new PlatformOperationConflictException("PLATFORM_SUBMISSION_LEASE_ACTIVE",
                    "Platform submission lease has not expired");
        }
        operation.unknown("submission-lease-expired");
        try {
            operations.saveAndFlush(operation);
        } catch (OptimisticLockException | OptimisticLockingFailureException exception) {
            throw stale();
        }
        append(context, AuditAction.UPDATE, operation, List.of(
                change("status", "SUBMITTING", "UNKNOWN_OUTCOME", AuditValueType.ENUM, 0),
                change("safeProviderTraceId", null, "submission-lease-expired", AuditValueType.STRING, 1)));
        return operation;
    }

    @Transactional(readOnly = true)
    public PlatformOperation get(UUID operationUuid) { return require(operationUuid); }

    private UUID entity(CreatePlatformOperationCommand command, String expected) {
        return command.entityType().name().equals(expected) ? command.entityUuid() : null;
    }

    private PlatformOperation require(UUID operationUuid) {
        return operations.findById(operationUuid)
                .orElseThrow(() -> new PlatformOperationConflictException("PLATFORM_OPERATION_NOT_FOUND",
                        "Platform operation not found"));
    }

    private Instant retryAt(Instant now, int attempt) {
        long seconds = Math.min(300, 5L << Math.min(attempt - 1, 6));
        return now.plus(Duration.ofSeconds(seconds));
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
        AuditOperationContext operationContext = auditContexts.forStableOperation(
                operation.getOperationUuid(), context);
        audit.append(new AuditEvent(UUID.randomUUID(), operationContext, action, "PLATFORM_OPERATION",
                operation.getOperationUuid(), null, Instant.now(clock), changes));
    }

    private AuditChange change(String field, String oldValue, String newValue, AuditValueType type, int order) {
        return new AuditChange(field, oldValue, newValue, type, order);
    }

    private PlatformOperationConflictException stale() {
        return new PlatformOperationConflictException("PLATFORM_OPERATION_STALE", "Platform operation version is stale");
    }
}
