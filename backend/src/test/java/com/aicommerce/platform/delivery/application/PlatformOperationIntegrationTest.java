package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import com.aicommerce.platform.delivery.application.port.NormalizedPlatformEvidence;
import com.aicommerce.platform.delivery.application.port.WriteUnknownOutcome;
import com.aicommerce.platform.delivery.application.port.WriteRetryableFailure;
import com.aicommerce.platform.delivery.application.port.ReconciliationFound;
import com.aicommerce.platform.delivery.domain.PlatformAttemptKind;
import com.aicommerce.platform.delivery.domain.PlatformEvidenceResultKind;
import com.aicommerce.platform.delivery.domain.PlatformUnknownCode;
import com.aicommerce.platform.delivery.domain.PlatformRetryableCode;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.postgresql.util.PSQLException;

@Testcontainers
@SpringBootTest(properties={"spring.flyway.target=12","spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
class PlatformOperationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired PlatformOperationService service;
    @Autowired PlatformOperationTransactions transactions;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired DeterministicFakePlatformAdapter fake;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    UUID accountUuid;

    @BeforeEach
    void account() {
        accountUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,
                  external_account_fingerprint,currency,timezone,lifecycle_status,version)
                VALUES (?,'FAKE','TEST',?,?,'TWD','Asia/Taipei','ACTIVE',0)
                """, accountUuid, "fake-" + accountUuid, accountUuid.toString().replace("-", "").repeat(2));
    }

    @Test
    void persistsClaimBeforeOutOfTransactionAdapterCallAndFinalizesAttemptWithAudit() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("stage4a-" + operationUuid);
        var created = service.create(createCampaign(operationUuid, UUID.randomUUID()), context);

        var completed = service.submit(operationUuid, created.getVersion());

        assertThat(completed.status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(fake.transactionObserved()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_operation_attempts WHERE operation_uuid=? AND status='SUCCEEDED'", Integer.class, operationUuid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operationUuid)).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT entity_type,action FROM audit_logs WHERE operation_uuid=?", operationUuid))
                .extracting(row -> row.get("entity_type") + ":" + row.get("action"))
                .containsExactlyInAnyOrder("PLATFORM_CAMPAIGN:CREATE", "PLATFORM_OPERATION:CREATE",
                        "PLATFORM_OPERATION:UPDATE", "PLATFORM_OPERATION:UPDATE", "PLATFORM_OPERATION_ATTEMPT:CREATE",
                        "PLATFORM_OPERATION_ATTEMPT:UPDATE", "PLATFORM_CAMPAIGN:UPDATE");
    }

    @Test
    void directSqlCannotReplayCreateOrStateSuccessWithoutItsOwnEntityMutation() {
        UUID firstCreateId=UUID.randomUUID();
        var firstCreateCommand=createCampaign(firstCreateId,UUID.randomUUID()); UUID campaign=firstCreateCommand.entityUuid();
        var firstCreate=service.create(firstCreateCommand,contexts.forCurrentActor("provenance-create-1"));
        var created=service.submit(firstCreateId,firstCreate.getVersion());

        UUID replayCreateId=UUID.randomUUID();
        insertRawCampaignOperation(new CreatePlatformOperationCommand(replayCreateId,accountUuid,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,campaign,UUID.randomUUID(),firstCreateCommand.normalizedRequestJson(),3));
        claimDirect(replayCreateId);
        assertDirectReplayRejected(replayCreateId,created.externalId().orElseThrow(),jdbc.queryForObject("select outcome_evidence::text from platform_operations where operation_uuid=?",String.class,firstCreateId));

        long version=jdbc.queryForObject("select version from platform_campaigns where platform_campaign_uuid=?",Long.class,campaign);
        UUID resumeId=UUID.randomUUID();
        var resume=service.create(stateCommand(resumeId,UUID.randomUUID(),campaign,version,PlatformOperationType.RESUME),contexts.forCurrentActor("provenance-state-1"));
        service.submit(resumeId,resume.getVersion());

        UUID replayStateId=UUID.randomUUID();
        insertRawCampaignOperation(stateCommand(replayStateId,UUID.randomUUID(),campaign,version,PlatformOperationType.RESUME));
        claimDirect(replayStateId);
        String stateEvidence=jdbc.queryForObject("select outcome_evidence::text from platform_operations where operation_uuid=?",String.class,resumeId);
        assertDirectReplayRejected(replayStateId,null,stateEvidence);
        assertThat(jdbc.queryForObject("select desired_state from platform_campaigns where platform_campaign_uuid=?",String.class,campaign)).isEqualTo("ACTIVE");
        UUID attempt=jdbc.queryForObject("select operation_attempt_uuid from platform_operation_attempts where operation_uuid=?",UUID.class,replayStateId);
        assertThatThrownBy(()->jdbc.update("delete from platform_operation_attempts where operation_attempt_uuid=?",attempt)).isInstanceOf(RuntimeException.class);
    }

    private void insertRawCampaignOperation(CreatePlatformOperationCommand command) {
        String randomHash=UUID.randomUUID().toString().replace("-","").repeat(2);
        jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,max_attempts) values (?,?,?,?,?,?,?,?::jsonb,?,'LOCAL_ADMIN','direct-provenance',?,3)",command.operationUuid(),command.platformAccountUuid(),command.operationType().name(),command.entityType().name(),command.entityUuid(),command.clientRequestUuid(),randomHash,command.normalizedRequestJson(),UUID.randomUUID().toString().replace("-","").repeat(2),"direct-"+command.operationUuid());
    }

    private void claimDirect(UUID operationUuid) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=1,claimed_at=current_timestamp,updated_at=current_timestamp,version=1 where operation_uuid=?",operationUuid);
            Instant claimed=jdbc.queryForObject("select claimed_at from platform_operations where operation_uuid=?",java.sql.Timestamp.class,operationUuid).toInstant();
            jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) values (?,?, 'SUBMIT',1,'STARTED',?,0)",UUID.randomUUID(),operationUuid,java.sql.Timestamp.from(claimed));
        });
    }

    private void assertDirectReplayRejected(UUID operationUuid,String externalId,String evidence) {
        UUID attempt=jdbc.queryForObject("select operation_attempt_uuid from platform_operation_attempts where operation_uuid=?",UUID.class,operationUuid);
        assertThatThrownBy(()->new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            jdbc.update("update platform_operation_attempts set status='SUCCEEDED',evidence=?::jsonb,completed_at=current_timestamp,version=1 where operation_attempt_uuid=?",evidence,attempt);
            jdbc.update("update platform_operations set status='SUCCEEDED',external_id=?,outcome_evidence=?::jsonb,completed_at=current_timestamp,updated_at=current_timestamp,version=2 where operation_uuid=?",externalId,evidence,operationUuid);
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select status from platform_operations where operation_uuid=?",String.class,operationUuid)).isEqualTo("SUBMITTING");
        assertThat(jdbc.queryForObject("select status from platform_operation_attempts where operation_attempt_uuid=?",String.class,attempt)).isEqualTo("STARTED");
    }

    @Test
    void staleSubmittingRecoveryIsCompareAndSetAndNeverCallsAdapter() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("stage4a-recovery-" + operationUuid);
        var created = service.create(createCampaign(operationUuid, UUID.randomUUID()), context);
        Instant claimedAt = Instant.parse("2026-08-17T00:00:00Z");
        transactions.claim(operationUuid, created.getVersion(), claimedAt, context);
        claimedAt = transactions.get(operationUuid).getClaimedAt();
        long version = transactions.get(operationUuid).getVersion();
        int calls = fake.invocationCount();

        var recovered = service.recoverStaleClaim(operationUuid, version, claimedAt.plusSeconds(300));

        assertThat(recovered.status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(recovered.normalizedErrorCode()).contains(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_RESPONSE_AMBIGUOUS);
        assertThat(fake.invocationCount()).isEqualTo(calls);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operation_attempts WHERE operation_uuid=?", String.class, operationUuid)).isEqualTo("UNKNOWN_OUTCOME");
    }

    @Test
    void optimisticClaimHasExactlyOneWinner() throws Exception {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("stage4a-race-" + operationUuid);
        var created = service.create(createCampaign(operationUuid, UUID.randomUUID()), context);
        var gate = new CountDownLatch(1);
        var winners = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) pool.submit(() -> {
                try { gate.await(); transactions.claim(operationUuid, created.getVersion(), Instant.now(), context); winners.incrementAndGet(); }
                catch (Exception ignored) { }
            });
            gate.countDown();
        }
        assertThat(winners).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_operation_attempts WHERE operation_uuid=?", Integer.class, operationUuid)).isEqualTo(1);
    }

    @Test
    void concurrentFinalizersPersistOneResultAndOneExactAuditPair() throws Exception {
        assertConcurrentFinalization(false);
        assertConcurrentFinalization(true);
    }

    private void assertConcurrentFinalization(boolean differentOutcomes) throws Exception {
        UUID operationUuid=UUID.randomUUID(); var context=contexts.forCurrentActor("finalize-race-"+operationUuid);
        var created=service.create(createCampaign(operationUuid,UUID.randomUUID()),context);
        transactions.claimSubmit(operationUuid,created.getVersion(),Instant.now(),context);
        var gate=new CountDownLatch(1); var winners=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)){
            pool.submit(()->{try{gate.await();transactions.recordWriteOutcome(operationUuid,unknownSubmit(),context);winners.incrementAndGet();}catch(Exception ignored){}});
            pool.submit(()->{try{gate.await();transactions.recordWriteOutcome(operationUuid,differentOutcomes?retryable(60):unknownSubmit(),context);winners.incrementAndGet();}catch(Exception ignored){}});
            gate.countDown();
        }
        assertThat(winners).hasValue(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_operation_attempts where operation_uuid=? and status<>'STARTED'",Integer.class,operationUuid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationUuid)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=? and entity_type='PLATFORM_OPERATION_ATTEMPT' and action='UPDATE'",Integer.class,operationUuid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=? and entity_type='PLATFORM_OPERATION' and action='UPDATE'",Integer.class,operationUuid)).isEqualTo(2);
    }

    @Test
    void unknownSubmissionReconcilesThroughASeparateDurableAttempt() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("stage4a-reconcile-" + operationUuid);
        var created = service.create(createCampaign(operationUuid, UUID.randomUUID()), context);
        transactions.claim(operationUuid, created.getVersion(), Instant.now(), context);
        var unknown = transactions.recordWriteOutcome(operationUuid, unknownSubmit(), context);

        var reconciled = service.reconcile(operationUuid, unknown.getVersion());

        assertThat(reconciled.status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(reconciled.reconciliationCount()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT attempt_kind,status FROM platform_operation_attempts WHERE operation_uuid=? ORDER BY created_at", operationUuid))
                .extracting(row -> row.get("attempt_kind") + ":" + row.get("status"))
                .containsExactly("SUBMIT:UNKNOWN_OUTCOME", "RECONCILE:SUCCEEDED");
        assertThat(fake.transactionObserved()).isFalse();
    }

    @Test
    void staleReconciliationReturnsToUnknownWithoutAdapterCall() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("stage4a-stale-reconcile-" + operationUuid);
        var created = service.create(createCampaign(operationUuid, UUID.randomUUID()), context);
        transactions.claim(operationUuid, created.getVersion(), Instant.now(), context);
        var unknown = transactions.recordWriteOutcome(operationUuid, unknownSubmit(), context);
        Instant claimedAt = Instant.parse("2026-08-17T00:00:00Z");
        transactions.claimReconciliation(operationUuid, unknown.getVersion(), claimedAt, context);
        claimedAt = transactions.get(operationUuid).getClaimedAt();
        long version = transactions.get(operationUuid).getVersion();
        int calls = fake.invocationCount();

        var recovered = service.recoverStaleClaim(operationUuid, version, claimedAt.plusSeconds(300));

        assertThat(recovered.status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(recovered.normalizedErrorCode()).contains(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_RECONCILIATION_INCONCLUSIVE);
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    @Test
    void submitAndRetryHaveDisjointZeroSideEffectEligibility() {
        UUID createdId=UUID.randomUUID();
        var createdContext=contexts.forCurrentActor("eligibility-created-"+createdId);
        var created=service.create(createCampaign(createdId,UUID.randomUUID()),createdContext);
        int auditBefore=jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?",Integer.class,createdId);
        assertThatThrownBy(()->service.retry(createdId,created.getVersion(),Instant.now()))
                .isInstanceOfSatisfying(PlatformOperationException.class,e->assertThat(e.code()).isEqualTo(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?",Integer.class,createdId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?",Integer.class,createdId)).isEqualTo(auditBefore);

        transactions.claimSubmit(createdId,created.getVersion(),Instant.now(),createdContext);
        var retryable=transactions.recordWriteOutcome(createdId,retryable(60),createdContext);
        int attempts=jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?",Integer.class,createdId);
        int audit=jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?",Integer.class,createdId);
        assertThatThrownBy(()->service.submit(createdId,retryable.getVersion()))
                .isInstanceOfSatisfying(PlatformOperationException.class,e->assertThat(e.code()).isEqualTo(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?",Integer.class,createdId)).isEqualTo(attempts);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?",Integer.class,createdId)).isEqualTo(audit);
    }

    @Test
    void retryClearsPriorResultAndAttemptThreeConvertsToExactTerminal() {
        UUID operationUuid=UUID.randomUUID();
        var context=contexts.forCurrentActor("retry-matrix-"+operationUuid);
        var operation=service.create(createCampaign(operationUuid,UUID.randomUUID()),context);
        Instant first=Instant.parse("2026-08-17T01:00:00Z");
        transactions.claimSubmit(operationUuid,operation.getVersion(),first,context);
        operation=transactions.recordWriteOutcome(operationUuid,retryable(1),context);
        String identity=operation.getIdempotencyKey(); String payload=operation.getRequestPayload();
        for(int attempt=2;attempt<=3;attempt++){
            jdbc.queryForObject("select pg_sleep(1.5)",Object.class);
            Instant due=operation.getNextAttemptAt();
            transactions.claimRetry(operationUuid,operation.getVersion(),due,context);
            var claimed=jdbc.queryForMap("SELECT normalized_error_code,safe_provider_trace_id,outcome_evidence,next_attempt_at FROM platform_operations WHERE operation_uuid=?",operationUuid);
            assertThat(claimed.values()).allMatch(java.util.Objects::isNull);
            operation=transactions.recordWriteOutcome(operationUuid,retryable(1),context);
        }
        assertThat(operation.getStatus()).isEqualTo(PlatformOperationStatus.FAILED_TERMINAL);
        assertThat(operation.getNormalizedErrorCode()).isEqualTo("PLATFORM_MAX_ATTEMPTS_EXCEEDED");
        assertThat(operation.getIdempotencyKey()).isEqualTo(identity);
        assertThat(operation.getRequestPayload()).isEqualTo(payload);
        assertThat(jdbc.queryForObject("SELECT outcome_evidence->>'resultKind' FROM platform_operations WHERE operation_uuid=?",String.class,operationUuid)).isEqualTo("FAILED_TERMINAL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?",Integer.class,operationUuid)).isEqualTo(3);
        int audit=jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?",Integer.class,operationUuid);
        long terminalVersion=operation.getVersion();
        assertThatThrownBy(()->service.retry(operationUuid,terminalVersion,Instant.now()))
                .isInstanceOfSatisfying(PlatformOperationException.class,e->assertThat(e.code()).isEqualTo(PlatformStableErrorCode.PLATFORM_MAX_ATTEMPTS_EXCEEDED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?",Integer.class,operationUuid)).isEqualTo(audit);
    }

    @Test
    void canonicallyEquivalentActorIdsShareOnePersistedIdentity() {
        UUID firstId=UUID.randomUUID(),requestId=UUID.randomUUID();
        var firstCommand=createCampaign(firstId,requestId);
        var decomposed=new AuditOperationContext(UUID.randomUUID(),"nfc-one",new AuditActor(AuditActorType.LOCAL_ADMIN,"e\u0301ric"),AuditSource.API);
        var first=service.create(firstCommand,decomposed);
        var replayCommand=new CreatePlatformOperationCommand(UUID.randomUUID(),firstCommand.platformAccountUuid(),firstCommand.operationType(),firstCommand.entityType(),firstCommand.entityUuid(),firstCommand.clientRequestUuid(),firstCommand.normalizedRequestJson(),3);
        var composed=new AuditOperationContext(UUID.randomUUID(),"nfc-two",new AuditActor(AuditActorType.LOCAL_ADMIN,"\u00e9ric"),AuditSource.API);
        var replay=service.create(replayCommand,composed);
        assertThat(replay.getOperationUuid()).isEqualTo(first.getOperationUuid());
        assertThat(replay.getRequestedActorId()).isEqualTo("\u00e9ric");
        assertThat(jdbc.queryForObject("select count(*) from platform_operations where platform_account_uuid=? and client_request_uuid=?",Integer.class,accountUuid,requestId)).isEqualTo(1);
    }

    @Test
    void mutationTargetFailuresOccurBeforeClaimCallAttemptOrAudit() {
        UUID plan=UUID.randomUUID(),missingEntity=UUID.randomUUID();
        jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) values (?,?,'ACTIVE',0)",plan,"Missing external");
        jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",missingEntity,plan,accountUuid);
        UUID missingOp=UUID.randomUUID(); var missingContext=contexts.forCurrentActor("missing-external-"+missingOp);
        var missing=service.create(stateCommand(missingOp,UUID.randomUUID(),missingEntity,0,PlatformOperationType.RESUME),missingContext);
        assertZeroSideEffectFailure(missingOp,()->service.submit(missingOp,missing.getVersion()),PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID);

        UUID createId=UUID.randomUUID(); var createContext=contexts.forCurrentActor("mutation-base-"+createId);
        var createCommand=createCampaign(createId,UUID.randomUUID()); var created=service.create(createCommand,createContext);
        var completed=service.submit(createId,created.getVersion());
        assertThat(completed.status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        long entityVersion=jdbc.queryForObject("select version from platform_campaigns where platform_campaign_uuid=?",Long.class,createCommand.entityUuid());

        UUID staleOp=UUID.randomUUID();var staleContext=contexts.forCurrentActor("stale-entity-"+staleOp);
        var stale=service.create(stateCommand(staleOp,UUID.randomUUID(),createCommand.entityUuid(),entityVersion-1,PlatformOperationType.RESUME),staleContext);
        assertZeroSideEffectFailure(staleOp,()->service.submit(staleOp,stale.getVersion()),PlatformStableErrorCode.PLATFORM_STALE_VERSION);

        UUID noopOp=UUID.randomUUID();var noopContext=contexts.forCurrentActor("noop-state-"+noopOp);
        var noop=service.create(stateCommand(noopOp,UUID.randomUUID(),createCommand.entityUuid(),entityVersion,PlatformOperationType.PAUSE),noopContext);
        assertZeroSideEffectFailure(noopOp,()->service.submit(noopOp,noop.getVersion()),PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE);

        UUID resumeOp=UUID.randomUUID();var resumeContext=contexts.forCurrentActor("resume-valid-"+resumeOp);
        var resume=service.create(stateCommand(resumeOp,UUID.randomUUID(),createCommand.entityUuid(),entityVersion,PlatformOperationType.RESUME),resumeContext);
        assertThat(service.submit(resumeOp,resume.getVersion()).status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select desired_state from platform_campaigns where platform_campaign_uuid=?",String.class,createCommand.entityUuid())).isEqualTo("ACTIVE");
        long activeVersion=jdbc.queryForObject("select version from platform_campaigns where platform_campaign_uuid=?",Long.class,createCommand.entityUuid());
        UUID pauseOp=UUID.randomUUID();var pauseContext=contexts.forCurrentActor("pause-valid-"+pauseOp);
        var pause=service.create(stateCommand(pauseOp,UUID.randomUUID(),createCommand.entityUuid(),activeVersion,PlatformOperationType.PAUSE),pauseContext);
        assertThat(service.submit(pauseOp,pause.getVersion()).status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select desired_state from platform_campaigns where platform_campaign_uuid=?",String.class,createCommand.entityUuid())).isEqualTo("PAUSED");
    }


    @Test
    void budgetMutationIncreaseDecreaseAndStaleEvidenceAreBoundedBeforeCall() {
        UUID campaignOp=UUID.randomUUID();var campaignContext=contexts.forCurrentActor("budget-campaign-"+campaignOp);
        var campaignCommand=createCampaign(campaignOp,UUID.randomUUID());var campaign=service.create(campaignCommand,campaignContext);
        service.submit(campaignOp,campaign.getVersion());
        UUID adSetOp=UUID.randomUUID(),adSetUuid=UUID.randomUUID();var adSetContext=contexts.forCurrentActor("budget-adset-"+adSetOp);
        var adSet=service.create(createAdSet(adSetOp,UUID.randomUUID(),adSetUuid,campaignCommand.entityUuid()),adSetContext);
        service.submit(adSetOp,adSet.getVersion());

        UUID increaseOp=UUID.randomUUID();var increaseContext=contexts.forCurrentActor("budget-up-"+increaseOp);
        var increase=service.create(budgetCommand(increaseOp,UUID.randomUUID(),adSetUuid,1,"20","30"),increaseContext);
        assertThat(service.submit(increaseOp,increase.getVersion()).status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select budget_amount from platform_ad_sets where platform_ad_set_uuid=?",java.math.BigDecimal.class,adSetUuid)).isEqualByComparingTo("30");

        UUID staleOp=UUID.randomUUID();var staleContext=contexts.forCurrentActor("budget-stale-"+staleOp);
        var stale=service.create(budgetCommand(staleOp,UUID.randomUUID(),adSetUuid,1,"20","25"),staleContext);
        assertZeroSideEffectFailure(staleOp,()->service.submit(staleOp,stale.getVersion()),PlatformStableErrorCode.PLATFORM_STALE_VERSION);

        UUID decreaseOp=UUID.randomUUID();var decreaseContext=contexts.forCurrentActor("budget-down-"+decreaseOp);
        var decrease=service.create(budgetCommand(decreaseOp,UUID.randomUUID(),adSetUuid,2,"30","15"),decreaseContext);
        assertThat(service.submit(decreaseOp,decrease.getVersion()).status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select budget_amount from platform_ad_sets where platform_ad_set_uuid=?",java.math.BigDecimal.class,adSetUuid)).isEqualByComparingTo("15");
        assertThat(jdbc.queryForObject("select last_budget_operation_uuid from platform_ad_sets where platform_ad_set_uuid=?",UUID.class,adSetUuid)).isEqualTo(decreaseOp);
    }

    @Test
    void directAndReconciledBudgetSuccessRequireReciprocalAmountAndProvenanceMutation() {
        UUID campaignId=UUID.randomUUID();var campaignContext=contexts.forCurrentActor("budget-reciprocal-campaign-"+campaignId);var campaignCommand=createCampaign(campaignId,UUID.randomUUID());var campaign=service.create(campaignCommand,campaignContext);service.submit(campaignId,campaign.getVersion());
        UUID adSetId=UUID.randomUUID(),adSetCreateId=UUID.randomUUID();var adSetContext=contexts.forCurrentActor("budget-reciprocal-adset-"+adSetCreateId);var adSet=service.create(createAdSet(adSetCreateId,UUID.randomUUID(),adSetId,campaignCommand.entityUuid()),adSetContext);service.submit(adSetCreateId,adSet.getVersion());
        BudgetFixture fixture=new BudgetFixture(adSetId,adSetCreateId);
        assertBudgetFailure(fixture,adSetCreateId,"23514","entity update must apply exactly one operation result",null,()->jdbc.update("update platform_ad_sets set budget_amount=30,version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",adSetId));
        assertBudgetFailure(fixture,adSetCreateId,"23514","entity update must apply exactly one operation result",null,()->jdbc.update("update platform_ad_sets set last_budget_operation_uuid=?,version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",adSetCreateId,adSetId));

        UUID directId=UUID.randomUUID();var directContext=contexts.forCurrentActor("budget-reciprocal-direct-"+directId);var direct=service.create(budgetCommand(directId,UUID.randomUUID(),adSetId,1,"20","30"),directContext);transactions.claimSubmit(directId,direct.getVersion(),Instant.now(),directContext);
        assertBudgetSuccessWithoutEntityRejected(directId,"{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"SUCCEEDED\",\"observedState\":\"PAUSED\"}");

        UUID reconciledId=UUID.randomUUID();var reconciledContext=contexts.forCurrentActor("budget-reciprocal-reconcile-"+reconciledId);var reconciled=service.create(budgetCommand(reconciledId,UUID.randomUUID(),adSetId,1,"20","10"),reconciledContext);transactions.claimSubmit(reconciledId,reconciled.getVersion(),Instant.now(),reconciledContext);var unknown=transactions.recordWriteOutcome(reconciledId,unknownSubmit(),reconciledContext);transactions.claimReconciliation(reconciledId,unknown.getVersion(),Instant.now(),reconciledContext);
        assertBudgetSuccessWithoutEntityRejected(reconciledId,"{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"RECONCILE\",\"resultKind\":\"FOUND\",\"observedState\":\"PAUSED\"}");
    }

    @Test
    void wrongNonSuccessReusedBudgetOperationsAndPolicyCurrencyBoundsRollbackWithExactInvariant() {
        for(String invalidAmount:java.util.List.of("0","-1","101")){
            BudgetFixture fixture=budgetFixture("budget-bound-"+invalidAmount.replace('-','n')); UUID pendingId=createPendingBudget(fixture,"30");
            assertBudgetFailure(fixture,pendingId,"23514",null,"ck_platform_ad_sets_budget",()->jdbc.update("update platform_ad_sets set budget_amount=?,last_budget_operation_uuid=?,version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",new java.math.BigDecimal(invalidAmount),pendingId,fixture.adSetUuid()));
        }

        BudgetFixture wrong=budgetFixture("budget-wrong-kind");
        assertBudgetFailure(wrong,wrong.createOperationUuid(),"23514","successful budget operation is incoherent",null,()->new TransactionTemplate(transactionManager).executeWithoutResult(status->jdbc.update("update platform_ad_sets set budget_amount=30,last_budget_operation_uuid=?,version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",wrong.createOperationUuid(),wrong.adSetUuid())));

        BudgetFixture nonSuccess=budgetFixture("budget-non-success"); UUID pendingId=createPendingBudget(nonSuccess,"30");
        assertBudgetFailure(nonSuccess,pendingId,"23514","successful budget operation is incoherent",null,()->new TransactionTemplate(transactionManager).executeWithoutResult(status->jdbc.update("update platform_ad_sets set budget_amount=30,last_budget_operation_uuid=?,version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",pendingId,nonSuccess.adSetUuid())));

        BudgetFixture currency=budgetFixture("budget-currency");
        assertBudgetFailure(currency,currency.createOperationUuid(),"23514","ad set policy is immutable",null,()->jdbc.update("update platform_ad_sets set currency='USD',version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",currency.adSetUuid()));
        BudgetFixture policy=budgetFixture("budget-policy");
        assertBudgetFailure(policy,policy.createOperationUuid(),"23514","ad set policy is immutable",null,()->jdbc.update("update platform_ad_sets set budget_type='LIFETIME',version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",policy.adSetUuid()));

        BudgetFixture reused=budgetFixture("budget-reused"); UUID firstId=UUID.randomUUID();var firstContext=contexts.forCurrentActor("budget-used-first-"+firstId);var first=service.create(budgetCommand(firstId,UUID.randomUUID(),reused.adSetUuid(),1,"20","30"),firstContext);service.submit(firstId,first.getVersion());UUID secondId=UUID.randomUUID();var secondContext=contexts.forCurrentActor("budget-used-second-"+secondId);var second=service.create(budgetCommand(secondId,UUID.randomUUID(),reused.adSetUuid(),2,"30","15"),secondContext);service.submit(secondId,second.getVersion());
        assertBudgetFailure(reused,firstId,"23514","successful budget operation is incoherent",null,()->new TransactionTemplate(transactionManager).executeWithoutResult(status->jdbc.update("update platform_ad_sets set budget_amount=30,last_budget_operation_uuid=?,version=version+1,updated_at=current_timestamp where platform_ad_set_uuid=?",firstId,reused.adSetUuid())));
    }

    private void assertBudgetSuccessWithoutEntityRejected(UUID operationUuid,String evidence) {
        String kind=jdbc.queryForObject("select case when status='RECONCILING' then 'RECONCILE' else 'SUBMIT' end from platform_operations where operation_uuid=?",String.class,operationUuid);
        UUID attempt=jdbc.queryForObject("select operation_attempt_uuid from platform_operation_attempts where operation_uuid=? and attempt_kind=? and status='STARTED'",UUID.class,operationUuid,kind);
        UUID adSetUuid=jdbc.queryForObject("select platform_ad_set_uuid from platform_operations where operation_uuid=?",UUID.class,operationUuid);BudgetSnapshot before=budgetSnapshot(adSetUuid,operationUuid);
        assertThatThrownBy(()->new TransactionTemplate(transactionManager).executeWithoutResult(status->{jdbc.update("update platform_operation_attempts set status='SUCCEEDED',evidence=?::jsonb,completed_at=current_timestamp,version=1 where operation_attempt_uuid=?",evidence,attempt);jdbc.update("update platform_operations set status='SUCCEEDED',outcome_evidence=?::jsonb,completed_at=current_timestamp,updated_at=current_timestamp,version=version+1 where operation_uuid=?",evidence,operationUuid);})).isInstanceOf(DataAccessException.class).satisfies(error->{assertThat(sqlState(error)).isEqualTo("23514");assertThat(sqlMessage(error)).contains("succeeded budget operation was not applied");});
        assertThat(budgetSnapshot(adSetUuid,operationUuid)).isEqualTo(before);
    }

    private BudgetFixture budgetFixture(String prefix){UUID campaignId=UUID.randomUUID();var campaignContext=contexts.forCurrentActor(prefix+"-campaign-"+campaignId);var campaignCommand=createCampaign(campaignId,UUID.randomUUID());var campaign=service.create(campaignCommand,campaignContext);service.submit(campaignId,campaign.getVersion());UUID adSetUuid=UUID.randomUUID(),createId=UUID.randomUUID();var createContext=contexts.forCurrentActor(prefix+"-adset-"+createId);var adSet=service.create(createAdSet(createId,UUID.randomUUID(),adSetUuid,campaignCommand.entityUuid()),createContext);service.submit(createId,adSet.getVersion());return new BudgetFixture(adSetUuid,createId);}
    private UUID createPendingBudget(BudgetFixture fixture,String next){UUID id=UUID.randomUUID();service.create(budgetCommand(id,UUID.randomUUID(),fixture.adSetUuid(),1,"20",next),contexts.forCurrentActor("pending-budget-"+id));return id;}
    private void assertBudgetFailure(BudgetFixture fixture,UUID operationUuid,String state,String invariant,String constraint,org.assertj.core.api.ThrowableAssert.ThrowingCallable call){BudgetSnapshot before=budgetSnapshot(fixture.adSetUuid(),operationUuid);assertThatThrownBy(call).isInstanceOf(DataAccessException.class).satisfies(error->{assertThat(sqlState(error)).isEqualTo(state);if(invariant!=null)assertThat(sqlMessage(error)).contains(invariant);if(constraint!=null)assertThat(sqlConstraint(error)).isEqualTo(constraint);});assertThat(budgetSnapshot(fixture.adSetUuid(),operationUuid)).isEqualTo(before);}
    private BudgetSnapshot budgetSnapshot(UUID adSetUuid,UUID operationUuid){return new BudgetSnapshot(jdbc.queryForMap("select * from platform_ad_sets where platform_ad_set_uuid=?",adSetUuid),jdbc.queryForList("select * from platform_operations where operation_uuid=?",operationUuid),jdbc.queryForList("select * from platform_operation_attempts where operation_uuid=? order by operation_attempt_uuid",operationUuid),jdbc.queryForList("select * from audit_logs where operation_uuid=? or entity_uuid=? order by audit_uuid",operationUuid,adSetUuid),jdbc.queryForList("select c.* from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? or l.entity_uuid=? order by c.audit_uuid,c.change_order",operationUuid,adSetUuid));}
    private String sqlState(Throwable error){Throwable current=error;while(current!=null){if(current instanceof SQLException sql)return sql.getSQLState();current=current.getCause();}throw new AssertionError("missing SQLException",error);}
    private String sqlMessage(Throwable error){Throwable current=error;while(current!=null){if(current instanceof SQLException sql)return sql.getMessage();current=current.getCause();}throw new AssertionError("missing SQLException",error);}
    private String sqlConstraint(Throwable error){Throwable current=error;while(current!=null){if(current instanceof PSQLException sql)return sql.getServerErrorMessage().getConstraint();current=current.getCause();}throw new AssertionError("missing PSQLException",error);}
    private record BudgetFixture(UUID adSetUuid,UUID createOperationUuid){}
    private record BudgetSnapshot(java.util.Map<String,Object> entity,java.util.List<java.util.Map<String,Object>> operation,java.util.List<java.util.Map<String,Object>> attempts,java.util.List<java.util.Map<String,Object>> audit,java.util.List<java.util.Map<String,Object>> auditChanges){}

    @Test
    void reconciledFoundStaleMutationsUseExactAmbiguousRepresentationAtomically() {
        assertStaleStateReconciliation(PlatformOperationType.RESUME);
        assertStaleStateReconciliation(PlatformOperationType.PAUSE);
        assertStaleBudgetReconciliation("20", "30", "25");
        assertStaleBudgetReconciliation("20", "10", "15");
    }

    @Test
    void reconciledFoundSuccessfullyAppliesPauseResumeAndBudgetIncreaseDecreaseAtomically() {
        assertSuccessfulStateReconciliation(PlatformOperationType.RESUME);
        assertSuccessfulStateReconciliation(PlatformOperationType.PAUSE);
        assertSuccessfulBudgetReconciliation("30");
        assertSuccessfulBudgetReconciliation("10");
    }

    private void assertSuccessfulStateReconciliation(PlatformOperationType type) {
        UUID createId=UUID.randomUUID(); var createContext=contexts.forCurrentActor("reconcile-success-state-create-"+createId);
        var createCommand=createCampaign(createId,UUID.randomUUID()); var create=service.create(createCommand,createContext); service.submit(createId,create.getVersion());
        long version=1;
        if(type==PlatformOperationType.PAUSE){UUID resumeId=UUID.randomUUID();var resumeContext=contexts.forCurrentActor("reconcile-success-state-prime-"+resumeId);var resume=service.create(stateCommand(resumeId,UUID.randomUUID(),createCommand.entityUuid(),version,PlatformOperationType.RESUME),resumeContext);service.submit(resumeId,resume.getVersion());version=2;}
        UUID operationId=UUID.randomUUID();var context=contexts.forCurrentActor("reconcile-success-state-"+operationId);var operation=service.create(stateCommand(operationId,UUID.randomUUID(),createCommand.entityUuid(),version,type),context);
        transactions.claimSubmit(operationId,operation.getVersion(),Instant.now(),context);var unknown=transactions.recordWriteOutcome(operationId,unknownSubmit(),context);transactions.claimReconciliation(operationId,unknown.getVersion(),Instant.now(),context);
        int auditBefore=jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationId);
        var completed=transactions.recordReconciliationOutcome(operationId,foundMutation(),Instant.now(),context);
        assertThat(completed.getStatus()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select desired_state from platform_campaigns where platform_campaign_uuid=?",String.class,createCommand.entityUuid())).isEqualTo(type==PlatformOperationType.PAUSE?"PAUSED":"ACTIVE");
        assertThat(jdbc.queryForList("select attempt_kind||':'||status from platform_operation_attempts where operation_uuid=? order by created_at",String.class,operationId)).containsExactly("SUBMIT:UNKNOWN_OUTCOME","RECONCILE:SUCCEEDED");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationId)).isEqualTo(auditBefore+3);
    }

    private void assertSuccessfulBudgetReconciliation(String requested) {
        UUID campaignId=UUID.randomUUID();var campaignContext=contexts.forCurrentActor("reconcile-success-budget-campaign-"+campaignId);var campaignCommand=createCampaign(campaignId,UUID.randomUUID());var campaign=service.create(campaignCommand,campaignContext);service.submit(campaignId,campaign.getVersion());
        UUID adSetId=UUID.randomUUID(),createAdSetId=UUID.randomUUID();var adSetContext=contexts.forCurrentActor("reconcile-success-budget-adset-"+createAdSetId);var adSet=service.create(createAdSet(createAdSetId,UUID.randomUUID(),adSetId,campaignCommand.entityUuid()),adSetContext);service.submit(createAdSetId,adSet.getVersion());
        UUID operationId=UUID.randomUUID();var context=contexts.forCurrentActor("reconcile-success-budget-"+operationId);var operation=service.create(budgetCommand(operationId,UUID.randomUUID(),adSetId,1,"20",requested),context);
        transactions.claimSubmit(operationId,operation.getVersion(),Instant.now(),context);var unknown=transactions.recordWriteOutcome(operationId,unknownSubmit(),context);transactions.claimReconciliation(operationId,unknown.getVersion(),Instant.now(),context);
        int auditBefore=jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationId);
        var completed=transactions.recordReconciliationOutcome(operationId,foundMutation(),Instant.now(),context);
        assertThat(completed.getStatus()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select budget_amount from platform_ad_sets where platform_ad_set_uuid=?",java.math.BigDecimal.class,adSetId)).isEqualByComparingTo(requested);
        assertThat(jdbc.queryForObject("select last_budget_operation_uuid from platform_ad_sets where platform_ad_set_uuid=?",UUID.class,adSetId)).isEqualTo(operationId);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationId)).isEqualTo(auditBefore+3);
    }

    private void assertStaleStateReconciliation(PlatformOperationType type) {
        UUID createId=UUID.randomUUID(); var createContext=contexts.forCurrentActor("reconcile-state-create-"+createId);
        var createCommand=createCampaign(createId,UUID.randomUUID()); var create=service.create(createCommand,createContext);
        service.submit(createId,create.getVersion());
        long version=1;
        if(type==PlatformOperationType.PAUSE){
            UUID resumeId=UUID.randomUUID(); var resumeContext=contexts.forCurrentActor("reconcile-state-prime-"+resumeId);
            var resume=service.create(stateCommand(resumeId,UUID.randomUUID(),createCommand.entityUuid(),version,PlatformOperationType.RESUME),resumeContext);
            service.submit(resumeId,resume.getVersion()); version=2;
        }
        UUID staleId=UUID.randomUUID(); var staleContext=contexts.forCurrentActor("reconcile-state-stale-"+staleId);
        var stale=service.create(stateCommand(staleId,UUID.randomUUID(),createCommand.entityUuid(),version,type),staleContext);
        transactions.claimSubmit(staleId,stale.getVersion(),Instant.now(),staleContext);
        var unknown=transactions.recordWriteOutcome(staleId,unknownSubmit(),staleContext);

        UUID winnerId=UUID.randomUUID(); var winnerContext=contexts.forCurrentActor("reconcile-state-winner-"+winnerId);
        var winner=service.create(stateCommand(winnerId,UUID.randomUUID(),createCommand.entityUuid(),version,type),winnerContext);
        service.submit(winnerId,winner.getVersion());
        String desired=jdbc.queryForObject("select desired_state from platform_campaigns where platform_campaign_uuid=?",String.class,createCommand.entityUuid());

        transactions.claimReconciliation(staleId,unknown.getVersion(),Instant.now(),staleContext);
        int auditBefore=jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,staleId);
        var completed=transactions.recordReconciliationOutcome(staleId,foundMutation(),Instant.now(),staleContext);
        assertAmbiguousStaleReconciliation(staleId,completed,auditBefore);
        assertThat(jdbc.queryForObject("select desired_state from platform_campaigns where platform_campaign_uuid=?",String.class,createCommand.entityUuid())).isEqualTo(desired);
    }

    private void assertStaleBudgetReconciliation(String original,String requested,String winnerAmount) {
        UUID campaignId=UUID.randomUUID(); var campaignContext=contexts.forCurrentActor("reconcile-budget-campaign-"+campaignId);
        var campaignCommand=createCampaign(campaignId,UUID.randomUUID()); var campaign=service.create(campaignCommand,campaignContext);
        service.submit(campaignId,campaign.getVersion());
        UUID adSetId=UUID.randomUUID(),createAdSetId=UUID.randomUUID(); var adSetContext=contexts.forCurrentActor("reconcile-budget-adset-"+createAdSetId);
        var adSet=service.create(createAdSet(createAdSetId,UUID.randomUUID(),adSetId,campaignCommand.entityUuid()),adSetContext);
        service.submit(createAdSetId,adSet.getVersion());

        UUID staleId=UUID.randomUUID(); var staleContext=contexts.forCurrentActor("reconcile-budget-stale-"+staleId);
        var stale=service.create(budgetCommand(staleId,UUID.randomUUID(),adSetId,1,original,requested),staleContext);
        transactions.claimSubmit(staleId,stale.getVersion(),Instant.now(),staleContext);
        var unknown=transactions.recordWriteOutcome(staleId,unknownSubmit(),staleContext);
        UUID winnerId=UUID.randomUUID(); var winnerContext=contexts.forCurrentActor("reconcile-budget-winner-"+winnerId);
        var winner=service.create(budgetCommand(winnerId,UUID.randomUUID(),adSetId,1,original,winnerAmount),winnerContext);
        service.submit(winnerId,winner.getVersion());

        transactions.claimReconciliation(staleId,unknown.getVersion(),Instant.now(),staleContext);
        int auditBefore=jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,staleId);
        var completed=transactions.recordReconciliationOutcome(staleId,foundMutation(),Instant.now(),staleContext);
        assertAmbiguousStaleReconciliation(staleId,completed,auditBefore);
        assertThat(jdbc.queryForObject("select budget_amount from platform_ad_sets where platform_ad_set_uuid=?",java.math.BigDecimal.class,adSetId)).isEqualByComparingTo(winnerAmount);
        assertThat(jdbc.queryForObject("select last_budget_operation_uuid from platform_ad_sets where platform_ad_set_uuid=?",UUID.class,adSetId)).isEqualTo(winnerId);
    }

    private ReconciliationFound foundMutation(){
        return new ReconciliationFound(Optional.empty(),Optional.of("fake-trace-stale"),Optional.of(PlatformObservedState.PAUSED),
                new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FOUND,Optional.empty(),Optional.of(PlatformObservedState.PAUSED),Optional.empty()));
    }

    private void assertAmbiguousStaleReconciliation(UUID operationUuid,com.aicommerce.platform.delivery.domain.PlatformOperation completed,int auditBefore){
        assertThat(completed.getStatus()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(completed.getNormalizedErrorCode()).isEqualTo("PLATFORM_RESPONSE_AMBIGUOUS");
        assertThat(jdbc.queryForObject("select (outcome_evidence->>'attemptKind') || '/' || (outcome_evidence->>'resultKind') from platform_operations where operation_uuid=?",String.class,operationUuid)).isEqualTo("RECONCILE/UNKNOWN_OUTCOME");
        assertThat(jdbc.queryForMap("select status,normalized_error_code,evidence->>'resultKind' as result_kind from platform_operation_attempts where operation_uuid=? and attempt_kind='RECONCILE'",operationUuid))
                .containsEntry("status","UNKNOWN_OUTCOME").containsEntry("normalized_error_code","PLATFORM_RESPONSE_AMBIGUOUS").containsEntry("result_kind","UNKNOWN_OUTCOME");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationUuid)).isEqualTo(auditBefore+2);
    }

    private WriteUnknownOutcome unknownSubmit() {
        return new WriteUnknownOutcome(PlatformUnknownCode.PLATFORM_RESPONSE_AMBIGUOUS, Optional.empty(),
                new NormalizedPlatformEvidence(1, ProviderKey.FAKE, PlatformAttemptKind.SUBMIT,
                        PlatformEvidenceResultKind.UNKNOWN_OUTCOME, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private WriteRetryableFailure retryable(int seconds){
        return new WriteRetryableFailure(seconds==60?PlatformRetryableCode.PLATFORM_RATE_LIMITED:PlatformRetryableCode.PLATFORM_TEMPORARILY_UNAVAILABLE,seconds,Optional.of("retry-trace"),
                new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_RETRYABLE,Optional.empty(),Optional.empty(),Optional.of(seconds)));
    }

    private CreatePlatformOperationCommand createCampaign(UUID operationUuid, UUID requestUuid) {
        UUID campaignUuid = UUID.randomUUID();
        UUID platformCampaignUuid = UUID.randomUUID();
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) VALUES (?,?,'ACTIVE',0)", campaignUuid, "Stage4A " + campaignUuid);
        String payload = """
                {"schemaVersion":1,"operationType":"CREATE_CAMPAIGN","entityType":"CAMPAIGN",
                 "entityUuid":"%s","platformCampaignUuid":"%s","campaignUuid":"%s",
                 "objective":"OUTCOME_SALES","desiredState":"PAUSED","accountTimezone":"Asia/Taipei"}
                """.formatted(platformCampaignUuid, platformCampaignUuid, campaignUuid);
        return new CreatePlatformOperationCommand(operationUuid, accountUuid, PlatformOperationType.CREATE_CAMPAIGN,
                PlatformEntityType.CAMPAIGN, platformCampaignUuid, requestUuid, payload, 3);
    }

    private CreatePlatformOperationCommand stateCommand(UUID operationUuid,UUID requestUuid,UUID entityUuid,long expected,PlatformOperationType type){
        String target=type==PlatformOperationType.PAUSE?"PAUSED":"ACTIVE";
        String payload="{\"schemaVersion\":1,\"operationType\":\""+type+"\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+entityUuid+"\",\"expectedEntityVersion\":"+expected+",\"targetDesiredState\":\""+target+"\"}";
        return new CreatePlatformOperationCommand(operationUuid,accountUuid,type,PlatformEntityType.CAMPAIGN,entityUuid,requestUuid,payload,3);
    }
    private CreatePlatformOperationCommand createAdSet(UUID operationUuid,UUID requestUuid,UUID adSetUuid,UUID campaignUuid){
        String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_AD_SET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+adSetUuid+"\",\"platformAdSetUuid\":\""+adSetUuid+"\",\"platformCampaignUuid\":\""+campaignUuid+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":20,\"currency\":\"TWD\",\"accountTimezone\":\"Asia/Taipei\",\"optimizationGoal\":\"OFFSITE_CONVERSIONS\",\"targetingProfileKey\":\"TW_BROAD_FEEDS_V1\",\"placementProfileKey\":\"TW_BROAD_FEEDS_V1\",\"desiredState\":\"PAUSED\"}";
        return new CreatePlatformOperationCommand(operationUuid,accountUuid,PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET,adSetUuid,requestUuid,payload,3);
    }
    private CreatePlatformOperationCommand budgetCommand(UUID operationUuid,UUID requestUuid,UUID adSetUuid,long expected,String previous,String next){
        String payload="{\"schemaVersion\":1,\"operationType\":\"UPDATE_BUDGET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+adSetUuid+"\",\"platformAdSetUuid\":\""+adSetUuid+"\",\"expectedEntityVersion\":"+expected+",\"budgetType\":\"DAILY\",\"currency\":\"TWD\",\"previousBudgetAmount\":"+previous+",\"newBudgetAmount\":"+next+"}";
        return new CreatePlatformOperationCommand(operationUuid,accountUuid,PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,adSetUuid,requestUuid,payload,3);
    }
    private void assertZeroSideEffectFailure(UUID operationUuid,org.assertj.core.api.ThrowableAssert.ThrowingCallable call,PlatformStableErrorCode code){
        int audit=jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationUuid);int invocations=fake.invocationCount();
        assertThatThrownBy(call).isInstanceOfSatisfying(PlatformOperationException.class,e->assertThat(e.code()).isEqualTo(code));
        assertThat(jdbc.queryForObject("select status from platform_operations where operation_uuid=?",String.class,operationUuid)).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("select count(*) from platform_operation_attempts where operation_uuid=?",Integer.class,operationUuid)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,operationUuid)).isEqualTo(audit);
        assertThat(fake.invocationCount()).isEqualTo(invocations);
    }
}
