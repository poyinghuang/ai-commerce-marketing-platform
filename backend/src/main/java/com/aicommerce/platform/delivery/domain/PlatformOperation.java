package com.aicommerce.platform.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="platform_operations")
public class PlatformOperation extends MutableEntity {
    @Id @Column(name="operation_uuid",updatable=false) private UUID operationUuid;
    @Column(name="platform_account_uuid",nullable=false,updatable=false) private UUID platformAccountUuid;
    @Enumerated(EnumType.STRING) @Column(name="operation_type",nullable=false,updatable=false) private PlatformOperationType operationType;
    @Enumerated(EnumType.STRING) @Column(name="entity_type",nullable=false,updatable=false) private PlatformEntityType entityType;
    @Column(name="platform_campaign_uuid",updatable=false) private UUID platformCampaignUuid;
    @Column(name="platform_ad_set_uuid",updatable=false) private UUID platformAdSetUuid;
    @Column(name="platform_ad_uuid",updatable=false) private UUID platformAdUuid;
    @Column(name="client_request_uuid",nullable=false,updatable=false) private UUID clientRequestUuid;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="idempotency_key",nullable=false,updatable=false,columnDefinition="char(64)") private String idempotencyKey;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="request_payload",nullable=false,updatable=false,columnDefinition="jsonb") private String requestPayload;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="request_sha256",nullable=false,updatable=false,columnDefinition="char(64)") private String requestSha256;
    @Column(name="requested_actor_type",nullable=false,updatable=false) private String requestedActorType;
    @Column(name="requested_actor_id",nullable=false,updatable=false) private String requestedActorId;
    @Column(name="request_id",nullable=false,updatable=false) private String requestId;
    @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private PlatformOperationStatus status;
    @Column(name="attempt_count",nullable=false) private int attemptCount;
    @Column(name="reconciliation_count",nullable=false) private int reconciliationCount;
    @Column(name="max_attempts",nullable=false,updatable=false) private int maxAttempts;
    @Column(name="external_id") private String externalId;
    @Column(name="normalized_error_code") private String normalizedErrorCode;
    @Column(name="safe_provider_trace_id") private String safeProviderTraceId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="outcome_evidence",columnDefinition="jsonb") private String outcomeEvidence;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="claimed_at") private Instant claimedAt;
    @Column(name="completed_at") private Instant completedAt;

    protected PlatformOperation() { }
    public PlatformOperation(UUID id, UUID account, PlatformOperationType type, PlatformEntityType entityType,
            UUID entityUuid, UUID clientRequestUuid, String idempotencyKey, String payload, String requestSha256,
            String actorType, String actorId, String requestId, int maxAttempts) {
        this.operationUuid=Objects.requireNonNull(id); this.platformAccountUuid=Objects.requireNonNull(account);
        this.operationType=Objects.requireNonNull(type); this.entityType=Objects.requireNonNull(entityType);
        switch(entityType) { case CAMPAIGN -> platformCampaignUuid=entityUuid; case AD_SET -> platformAdSetUuid=entityUuid; case AD -> platformAdUuid=entityUuid; }
        this.clientRequestUuid=Objects.requireNonNull(clientRequestUuid); this.idempotencyKey=required(idempotencyKey,64);
        this.requestPayload=required(payload,16384); this.requestSha256=required(requestSha256,64);
        this.requestedActorType=required(actorType,32); this.requestedActorId=required(actorId,128); this.requestId=required(requestId,128);
        if(maxAttempts!=3) throw new IllegalArgumentException("maxAttempts must be 3");
        this.maxAttempts=3; this.status=PlatformOperationStatus.CREATED;
    }
    public void claim(Instant now) { Objects.requireNonNull(now); if(status!=PlatformOperationStatus.CREATED&&status!=PlatformOperationStatus.FAILED_RETRYABLE) throw new IllegalStateException("operation is not claimable"); if(status==PlatformOperationStatus.FAILED_RETRYABLE&&(nextAttemptAt==null||now.isBefore(nextAttemptAt))) throw new IllegalStateException("retry is not due"); if(attemptCount>=maxAttempts) throw new IllegalStateException("attempts exhausted"); status=PlatformOperationStatus.SUBMITTING; attemptCount++; claimedAt=now; nextAttemptAt=null; }
    public void succeed(String externalId,String trace,Instant now) { requireSubmitting(); status=PlatformOperationStatus.SUCCEEDED; this.externalId=required(externalId,128); safeProviderTraceId=optional(trace,128); completedAt=Objects.requireNonNull(now); }
    public void failRetryable(String code,String trace,Instant retryAt) { requireSubmitting(); if(attemptCount>=maxAttempts) throw new IllegalStateException("retry limit reached"); status=PlatformOperationStatus.FAILED_RETRYABLE; normalizedErrorCode=required(code,64); safeProviderTraceId=optional(trace,128); nextAttemptAt=Objects.requireNonNull(retryAt); }
    public void failTerminal(String code,String trace,Instant now) { requireSubmitting(); terminalFailure(code,trace,now); }
    public void unknown(String trace) { requireSubmitting(); status=PlatformOperationStatus.UNKNOWN_OUTCOME; safeProviderTraceId=optional(trace,128); }
    public void reconcileSuccess(String id,String trace,Instant now) { if(status!=PlatformOperationStatus.UNKNOWN_OUTCOME) throw new IllegalStateException("operation is not awaiting reconciliation"); status=PlatformOperationStatus.SUCCEEDED; externalId=required(id,128); safeProviderTraceId=optional(trace,128); completedAt=Objects.requireNonNull(now); }
    public void reconcileFailure(String code,String trace,Instant now) { if(status!=PlatformOperationStatus.UNKNOWN_OUTCOME) throw new IllegalStateException("operation is not awaiting reconciliation"); terminalFailure(code,trace,now); }
    private void terminalFailure(String code,String trace,Instant now){status=PlatformOperationStatus.FAILED_TERMINAL;normalizedErrorCode=required(code,64);safeProviderTraceId=optional(trace,128);completedAt=Objects.requireNonNull(now);}
    private void requireSubmitting(){if(status!=PlatformOperationStatus.SUBMITTING)throw new IllegalStateException("operation is not submitting");}
    private static String required(String v,int max){if(v==null||v.isBlank()||v.length()>max)throw new IllegalArgumentException("required bounded value");return v;}
    private static String optional(String v,int max){if(v==null)return null;if(v.isBlank()||v.length()>max)throw new IllegalArgumentException("invalid bounded value");return v;}
    public UUID getOperationUuid(){return operationUuid;} public UUID getPlatformAccountUuid(){return platformAccountUuid;}
    public PlatformOperationType getOperationType(){return operationType;} public PlatformEntityType getEntityType(){return entityType;}
    public UUID getEntityUuid(){return switch(entityType){case CAMPAIGN->platformCampaignUuid;case AD_SET->platformAdSetUuid;case AD->platformAdUuid;};}
    public UUID getPlatformCampaignUuid(){return platformCampaignUuid;} public UUID getPlatformAdSetUuid(){return platformAdSetUuid;} public UUID getPlatformAdUuid(){return platformAdUuid;}
    public String getRequestedActorType(){return requestedActorType;} public String getRequestedActorId(){return requestedActorId;} public String getRequestId(){return requestId;}
    public PlatformOperationStatus getStatus(){return status;} public int getAttemptCount(){return attemptCount;} public int getReconciliationCount(){return reconciliationCount;} public int getMaxAttempts(){return maxAttempts;} public String getRequestPayload(){return requestPayload;} public String getIdempotencyKey(){return idempotencyKey;} public String getRequestSha256(){return requestSha256;} public String getExternalId(){return externalId;} public String getNormalizedErrorCode(){return normalizedErrorCode;} public String getSafeProviderTraceId(){return safeProviderTraceId;} public String getOutcomeEvidence(){return outcomeEvidence;} public Instant getNextAttemptAt(){return nextAttemptAt;} public Instant getClaimedAt(){return claimedAt;} public Instant getCompletedAt(){return completedAt;}
}
