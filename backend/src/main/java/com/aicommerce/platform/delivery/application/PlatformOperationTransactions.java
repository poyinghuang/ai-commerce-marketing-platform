package com.aicommerce.platform.delivery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.audit.PlatformAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEventKind;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
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

    public PlatformOperationTransactions(PlatformOperationJpaRepository operations, JdbcTemplate jdbc,
            PlatformAuditWriter platformAudit, Clock clock, ObjectMapper mapper,
            EntityManager entityManager) {
        this.operations = operations;
        this.jdbc = jdbc;
        this.platformAudit = platformAudit;
        this.clock = clock;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public PlatformOperation createOrReplay(CreatePlatformOperationCommand command,
            PlatformOperationInputCanonicalizer.CanonicalInput input, String idempotencyKey,
            AuditOperationContext context) {
        if (context.actor().type().name().equals("SYSTEM")) {
            throw new PlatformOperationConflictException("PLATFORM_TRUSTED_ACTOR_REQUIRED",
                    "A trusted human actor is required to create a platform operation");
        }
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
        if(entityCreated) platformAudit.write(entityCreatedEvent(stored),context);
        append(context, AuditAction.CREATE, stored, List.of(
                change("status", null, "CREATED", AuditValueType.ENUM, 0),
                change("operationType", null, stored.getOperationType().name(), AuditValueType.ENUM, 1),
                change("entityType", null, stored.getEntityType().name(), AuditValueType.ENUM, 2),
                change("requestSha256", null, stored.getRequestSha256(), AuditValueType.STRING, 3)));
        return stored;
    }

    @Transactional
    public PlatformCommand claim(UUID operationUuid, long expectedVersion, Instant claimTime, AuditOperationContext context) {
        PlatformOperation operation = require(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale();
        PlatformOperationStatus before = operation.getStatus();
        int attempts = operation.getAttemptCount();
        try {
            operation.claim(Objects.requireNonNull(claimTime));
            operations.saveAndFlush(operation);
            UUID attemptUuid=UUID.randomUUID(); jdbc.update("""
                    INSERT INTO platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,
                      attempt_number,status,started_at,version) VALUES (?,?,'SUBMIT',?,'STARTED',?,0)
                    """, attemptUuid, operationUuid, operation.getAttemptCount(), ts(claimTime));
            platformAudit.write(PlatformAuditEvent.attempt(operation,attemptUuid,PlatformAttemptKind.SUBMIT,
                    operation.getAttemptCount(),null,com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,
                    null,null,true),context);
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
    public PlatformReconciliationQuery claimReconciliation(UUID operationUuid, long expectedVersion, Instant claimTime,
            AuditOperationContext context) {
        PlatformOperation operation = require(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale();
        if (operation.getStatus() != PlatformOperationStatus.UNKNOWN_OUTCOME) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,
                    Optional.of(operationUuid));
        }
        if (operation.getReconciliationCount() >= 3) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_MAX_RECONCILIATIONS_EXCEEDED,
                    Optional.of(operationUuid));
        }
        int updated = jdbc.update("""
                UPDATE platform_operations SET status='RECONCILING', reconciliation_count=reconciliation_count+1,
                  claimed_at=?, normalized_error_code=NULL, safe_provider_trace_id=NULL, outcome_evidence=NULL,
                  next_attempt_at=NULL, completed_at=NULL, updated_at=?, version=version+1
                WHERE operation_uuid=? AND version=? AND status='UNKNOWN_OUTCOME'
                """, ts(claimTime), ts(claimTime), operationUuid, expectedVersion);
        if (updated != 1) throw stale();
        int reconciliationNumber = operation.getReconciliationCount() + 1;
        UUID attemptUuid=UUID.randomUUID(); jdbc.update("""
                INSERT INTO platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,
                  attempt_number,status,started_at,version) VALUES (?,?,'RECONCILE',?,'STARTED',?,0)
                """, attemptUuid, operationUuid, reconciliationNumber, ts(claimTime));
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
        PlatformOperation operation=require(operationUuid);
        if(operation.getStatus()!=PlatformOperationStatus.SUBMITTING) throw new PlatformOperationException(
                PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,Optional.of(operationUuid));
        Instant now=Instant.now(clock); String status; String code=null; Integer retry=null;
        String trace; NormalizedPlatformEvidence evidence; String external=null;
        String entityFingerprint=null;
        if(outcome instanceof WriteSucceeded x){
            trace=x.safeProviderTraceId().orElse(null);
            if(!applyDirectEntity(operation,x,now)){
                status="UNKNOWN_OUTCOME"; code="PLATFORM_RESPONSE_AMBIGUOUS";
                evidence=recoveryEvidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.UNKNOWN_OUTCOME);
            }else{status="SUCCEEDED";evidence=x.evidence();external=x.externalId().orElse(null);entityFingerprint=x.evidence().externalIdFingerprint().orElse(null);}
        }
        else if(outcome instanceof WriteRetryableFailure x){trace=x.safeProviderTraceId().orElse(null);if(operation.getAttemptCount()>=3){status="FAILED_TERMINAL";code="PLATFORM_MAX_ATTEMPTS_EXCEEDED";evidence=new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty());}else{status="FAILED_RETRYABLE";code=x.errorCode().name();retry=x.retryAfterSeconds();evidence=x.evidence();}}
        else if(outcome instanceof WriteTerminalFailure x){status="FAILED_TERMINAL";code=x.errorCode().name();trace=x.safeProviderTraceId().orElse(null);evidence=x.evidence();}
        else {WriteUnknownOutcome x=(WriteUnknownOutcome)outcome;status="UNKNOWN_OUTCOME";code=x.errorCode().name();trace=x.safeProviderTraceId().orElse(null);evidence=x.evidence();}
        String evidenceJson=json(evidence); String attemptStatus=status;
        UUID attemptUuid=jdbc.queryForObject("SELECT operation_attempt_uuid FROM platform_operation_attempts WHERE operation_uuid=? AND attempt_kind='SUBMIT' AND attempt_number=?",UUID.class,operationUuid,operation.getAttemptCount());
        jdbc.update("UPDATE platform_operation_attempts SET status=?,safe_provider_trace_id=?,normalized_error_code=?,evidence=?::jsonb,completed_at=?,version=1 WHERE operation_uuid=? AND attempt_kind='SUBMIT' AND attempt_number=? AND status='STARTED'",attemptStatus,trace,code,evidenceJson,ts(now),operationUuid,operation.getAttemptCount());
        Instant next=retry==null?null:now.plusSeconds(retry); Instant completed=status.equals("SUCCEEDED")||status.equals("FAILED_TERMINAL")?now:null;
        jdbc.update("UPDATE platform_operations SET status=?,external_id=?,normalized_error_code=?,safe_provider_trace_id=?,outcome_evidence=?::jsonb,next_attempt_at=?,completed_at=?,updated_at=?,version=version+1 WHERE operation_uuid=? AND status='SUBMITTING'",status,external,code,trace,evidenceJson,ts(next),ts(completed),ts(now),operationUuid);
        entityManager.clear(); PlatformOperation updated=require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(updated,attemptUuid,PlatformAttemptKind.SUBMIT,updated.getAttemptCount(),com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.valueOf(attemptStatus),code==null?null:PlatformStableErrorCode.valueOf(code),trace,false),context);
        append(context,AuditAction.UPDATE,updated,outcomeChanges(PlatformOperationStatus.SUBMITTING,updated));
        if(entityFingerprint!=null)platformAudit.write(entityExternalResultEvent(updated,entityFingerprint),context);
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
        PlatformOperation operation = require(operationUuid);
        if (operation.getStatus() != PlatformOperationStatus.RECONCILING) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,
                    Optional.of(operationUuid));
        }
        String status; String attemptStatus; String code = null; String trace; String external = null;
        NormalizedPlatformEvidence evidence;
        if (outcome instanceof ReconciliationFound found) {
            trace = found.safeProviderTraceId().orElse(null);
            if (!applyReconciledEntity(operation, found, completionTime)) {
                status = "UNKNOWN_OUTCOME"; attemptStatus = "UNKNOWN_OUTCOME";
                code = "PLATFORM_RESPONSE_AMBIGUOUS";
                evidence = recoveryEvidence(PlatformAttemptKind.RECONCILE, PlatformEvidenceResultKind.STILL_UNKNOWN);
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
        if (attemptUpdated != 1) throw stale();
        Instant completed = status.equals("SUCCEEDED") || status.equals("FAILED_TERMINAL") ? completionTime : null;
        int opUpdated = jdbc.update("""
                UPDATE platform_operations SET status=?,external_id=?,normalized_error_code=?,safe_provider_trace_id=?,
                  outcome_evidence=?::jsonb,next_attempt_at=NULL,completed_at=?,updated_at=?,version=version+1
                WHERE operation_uuid=? AND status='RECONCILING'
                """, status, external, code, trace, evidenceJson, ts(completed), ts(completionTime), operationUuid);
        if (opUpdated != 1) throw stale();
        entityManager.clear();
        PlatformOperation updated = require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(updated,attemptUuid,PlatformAttemptKind.RECONCILE,updated.getReconciliationCount(),com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.valueOf(attemptStatus),code==null?null:PlatformStableErrorCode.valueOf(code),trace,false),context);
        append(context, AuditAction.UPDATE, updated, outcomeChanges(PlatformOperationStatus.RECONCILING, updated));
        return updated;
    }

    @Transactional
    public PlatformOperation recoverStaleClaim(UUID operationUuid, long expectedVersion, Instant recoveryTime,
            AuditOperationContext context) {
        PlatformOperation operation = require(operationUuid);
        if (operation.getVersion() != expectedVersion) throw stale();
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
        if (attemptUpdated != 1) throw stale();
        int opUpdated = jdbc.update("""
                UPDATE platform_operations SET status='UNKNOWN_OUTCOME',external_id=NULL,normalized_error_code=?,
                  safe_provider_trace_id=NULL,outcome_evidence=?::jsonb,next_attempt_at=NULL,completed_at=NULL,
                  updated_at=?,version=version+1 WHERE operation_uuid=? AND version=? AND status=?
                """, code, evidence, ts(recoveryTime), operationUuid, expectedVersion, operation.getStatus().name());
        if (opUpdated != 1) throw stale();
        entityManager.clear();
        PlatformOperation updated = require(operationUuid);
        platformAudit.write(PlatformAuditEvent.attempt(updated,attemptUuid,kind,number,
                com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED,
                com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.UNKNOWN_OUTCOME,
                PlatformStableErrorCode.valueOf(code),null,false),context);
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

    private PlatformOperation require(UUID operationUuid) {
        return operations.findById(operationUuid)
                .orElseThrow(() -> new PlatformOperationConflictException("PLATFORM_OPERATION_NOT_FOUND",
                        "Platform operation not found"));
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

    private PlatformAuditEvent entityExternalResultEvent(PlatformOperation operation,String fingerprint){
        var subject=switch(operation.getEntityType()){case CAMPAIGN->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_CAMPAIGN;case AD_SET->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_AD_SET;case AD->com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType.PLATFORM_AD;};
        return new PlatformAuditEvent(subject,operation.getEntityUuid(),AuditAction.UPDATE,PlatformAuditEventKind.ENTITY_RESULT_APPLIED,
                operation.getOperationUuid(),operation.getOperationType(),operation.getEntityType(),operation.getEntityUuid(),
                Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),
                Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.of(fingerprint),Optional.empty(),Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private boolean applyDirectEntity(PlatformOperation operation, WriteSucceeded success, Instant now) {
        try {
            var payload = mapper.readValue(operation.getRequestPayload(), java.util.Map.class);
            if (operation.getOperationType().name().startsWith("CREATE_")) {
                String external = success.externalId().orElseThrow();
                String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
                String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
                return jdbc.update("UPDATE " + table + " SET external_id=?,observed_state=?,updated_at=?,version=version+1 WHERE " + column + "=? AND external_id IS NULL",
                        external, success.observedState().map(Enum::name).orElse(null), ts(now),
                        operation.getEntityUuid()) == 1;
            }
            long expected = Long.parseLong(payload.get("expectedEntityVersion").toString());
            String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
            String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
            if (operation.getOperationType() == com.aicommerce.platform.delivery.domain.PlatformOperationType.UPDATE_BUDGET) {
                return jdbc.update("UPDATE platform_ad_sets SET budget_amount=?,last_budget_operation_uuid=?,observed_state=?,updated_at=?,version=version+1 WHERE platform_ad_set_uuid=? AND version=? AND budget_amount=?",
                        payload.get("newBudgetAmount"), operation.getOperationUuid(),
                        success.observedState().map(Enum::name).orElse(null), ts(now), operation.getEntityUuid(), expected,
                        payload.get("previousBudgetAmount")) == 1;
            }
            return jdbc.update("UPDATE " + table + " SET desired_state=?,observed_state=?,updated_at=?,version=version+1 WHERE " + column + "=? AND version=?",
                    payload.get("targetDesiredState"), success.observedState().map(Enum::name).orElse(null), ts(now),
                    operation.getEntityUuid(), expected) == 1;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String entityExternalOrNull(PlatformOperation operation) {
        String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
        String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
        return jdbc.queryForObject("SELECT external_id FROM " + table + " WHERE " + column + "=?", String.class,
                operation.getEntityUuid());
    }

    @SuppressWarnings("unchecked")
    private boolean applyReconciledEntity(PlatformOperation operation, ReconciliationFound found, Instant now) {
        try {
            var payload = mapper.readValue(operation.getRequestPayload(), java.util.Map.class);
            if (operation.getOperationType().name().startsWith("CREATE_")) {
                String external = found.externalId().orElseThrow();
                String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
                String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
                return jdbc.update("UPDATE " + table + " SET external_id=?,observed_state=?,updated_at=?,version=version+1 WHERE " + column + "=? AND external_id IS NULL",
                        external, found.observedState().map(Enum::name).orElse(null), ts(now), operation.getEntityUuid()) == 1;
            }
            long expected = Long.parseLong(payload.get("expectedEntityVersion").toString());
            String table = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaigns"; case AD_SET -> "platform_ad_sets"; case AD -> "platform_ads"; };
            String column = switch (operation.getEntityType()) { case CAMPAIGN -> "platform_campaign_uuid"; case AD_SET -> "platform_ad_set_uuid"; case AD -> "platform_ad_uuid"; };
            if (operation.getOperationType() == com.aicommerce.platform.delivery.domain.PlatformOperationType.UPDATE_BUDGET) {
                return jdbc.update("UPDATE platform_ad_sets SET budget_amount=?,last_budget_operation_uuid=?,observed_state=?,updated_at=?,version=version+1 WHERE platform_ad_set_uuid=? AND version=? AND budget_amount=?",
                        payload.get("newBudgetAmount"), operation.getOperationUuid(), found.observedState().map(Enum::name).orElse(null), ts(now),
                        operation.getEntityUuid(), expected, payload.get("previousBudgetAmount")) == 1;
            }
            return jdbc.update("UPDATE " + table + " SET desired_state=?,observed_state=?,updated_at=?,version=version+1 WHERE " + column + "=? AND version=?",
                    payload.get("targetDesiredState"), found.observedState().map(Enum::name).orElse(null), ts(now),
                    operation.getEntityUuid(), expected) == 1;
        } catch (RuntimeException exception) {
            return false;
        }
    }

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

    private PlatformOperationConflictException stale() {
        return new PlatformOperationConflictException("PLATFORM_OPERATION_STALE", "Platform operation version is stale");
    }
}
