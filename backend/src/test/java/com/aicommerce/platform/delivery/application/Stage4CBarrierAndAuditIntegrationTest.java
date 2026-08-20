package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEventKind;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Stage4CBarrierAndAuditIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired Stage4CService service;
    @Autowired Stage4CTransactions tx;
    @Autowired PlatformOperationService operations;
    @Autowired PlatformOperationTransactions operationTx;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired Stage4BTransactions stage4b;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DeterministicFakePlatformAdapter fake;
    @MockitoSpyBean Stage4CCriticalSectionHook hook;
    @MockitoSpyBean PlatformAuditWriter audit;
    UUID plan;

    @BeforeEach void resetGraph() {
        jdbc.execute("TRUNCATE platform_budget_reservations, platform_operation_batches, platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ads, platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs RESTART IDENTITY CASCADE");
        plan = UUID.randomUUID();
        jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
          VALUES (?,'Stage 4C barrier',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
          """, plan, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        reset(hook, audit);
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
    }

    @Test void createTransactionAClaimAndSuccessEmitExactAuditCardinality() {
        Fixture f = readyCreate();
        reset(audit);
        var confirmation = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-audit");
        var captor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(7)).write(captor.capture(), any());
        assertThat(captor.getAllValues()).extracting(PlatformAuditEvent::eventKind).containsExactly(
                PlatformAuditEventKind.ENTITY_CREATED, PlatformAuditEventKind.OPERATION_CREATED,
                PlatformAuditEventKind.ATTEMPT_CREATED, PlatformAuditEventKind.OPERATION_TRANSITIONED,
                PlatformAuditEventKind.ATTEMPT_FINALIZED, PlatformAuditEventKind.OPERATION_TRANSITIONED,
                PlatformAuditEventKind.ENTITY_RESULT_APPLIED);
        assertThat(confirmation.operation().status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, confirmation.operation().operationUuid())).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT entity_type,action FROM audit_logs WHERE operation_uuid=? ORDER BY stage4b_operation_ordinal", confirmation.operation().operationUuid()))
                .extracting(row -> row.get("entity_type") + ":" + row.get("action"))
                .containsExactly("PLATFORM_AD:CREATE", "PLATFORM_OPERATION:CREATE", "PLATFORM_OPERATION_ATTEMPT:CREATE",
                        "PLATFORM_OPERATION:UPDATE", "PLATFORM_OPERATION_ATTEMPT:UPDATE", "PLATFORM_OPERATION:UPDATE", "PLATFORM_AD:UPDATE");
        String leaked = jdbc.queryForObject("""
                SELECT coalesce(string_agg(coalesce(c.old_value,'') || coalesce(c.new_value,''), '|'), '')
                FROM audit_log_changes c JOIN audit_logs a ON a.audit_uuid=c.audit_uuid
                WHERE a.operation_uuid=?
                """, String.class, confirmation.operation().operationUuid());
        String external = jdbc.queryForObject("SELECT external_id FROM platform_ads WHERE platform_ad_uuid=?", String.class, confirmation.operation().entityUuid());
        assertThat(leaked).doesNotContain(external).doesNotContain("request_payload").doesNotContain("authorization");
    }

    @Test void previewAndReplayEmitZeroAuditEvents() {
        Fixture f = readyCreate();
        UUID request = UUID.randomUUID();
        reset(audit);
        service.previewCreate(f.adSet, request, f.product, f.asset, f.output, f.review, f.parentVersion);
        verifyNoInteractions(audit);
        var created = service.confirmCreate(f.adSet, request, f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-replay");
        int persisted = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, created.operation().operationUuid());
        reset(audit);
        var replay = service.confirmCreate(f.adSet, request, f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-replay");
        assertThat(replay.replay()).isTrue();
        verifyNoInteractions(audit);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, created.operation().operationUuid())).isEqualTo(persisted);
    }

    @Test void auditWriterFailureAfterFinalAppendRollsBackCreateDispatch() {
        Fixture f = readyCreate();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (calls.incrementAndGet() == 7) throw new IllegalStateException("synthetic audit failure after final append");
            return result;
        }).when(audit).write(any(PlatformAuditEvent.class), any());
        assertThatThrownBy(() -> service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-audit-fail"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_type='CREATE_AD'", String.class)).isEqualTo("SUBMITTING");
        assertThat(jdbc.queryForObject("SELECT a.status FROM platform_operation_attempts a JOIN platform_operations o ON o.operation_uuid=a.operation_uuid WHERE o.operation_type='CREATE_AD' AND a.attempt_kind='SUBMIT'", String.class)).isEqualTo("STARTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_ads WHERE external_id IS NOT NULL", Integer.class)).isZero();
    }

    @Test void claimRaceOnParentStateRejectsCreateWithZeroAttemptAndProviderCall() {
        Fixture f = readyCreate();
        int calls = fake.invocationCount();
        doAnswer(invocation -> {
            jdbc.update("UPDATE platform_ad_sets SET desired_state='ACTIVE',version=version+1,updated_at=statement_timestamp() WHERE platform_ad_set_uuid=?", f.adSet);
            return null;
        }).when(hook).afterOperationLock();
        assertThatThrownBy(() -> service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-claim-race"))
                .isInstanceOf(PlatformOperationException.class)
                .satisfies(error -> assertThat(((PlatformOperationException) error).code()).isEqualTo(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_STALE_VERSION));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts a JOIN platform_operations o ON o.operation_uuid=a.operation_uuid WHERE o.operation_type='CREATE_AD'", Integer.class)).isZero();
        assertThat(fake.invocationCount()).isEqualTo(calls);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_type='CREATE_AD'", String.class)).isEqualTo("CREATED");
    }

    @Test void createTransactionAThenClaimKeepStage4AOrdinal() {
        Fixture f = readyCreate();
        reset(audit);
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-tx-a");
        var createCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(2)).write(createCaptor.capture(), any());
        assertThat(createCaptor.getAllValues()).extracting(PlatformAuditEvent::eventKind).containsExactly(
                PlatformAuditEventKind.ENTITY_CREATED, PlatformAuditEventKind.OPERATION_CREATED);
        reset(audit);
        operations.submit(created.operation().getOperationUuid(), created.operation().getVersion());
        var claimCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(5)).write(claimCaptor.capture(), any());
        assertThat(claimCaptor.getAllValues()).extracting(PlatformAuditEvent::eventKind).containsExactly(
                PlatformAuditEventKind.ATTEMPT_CREATED, PlatformAuditEventKind.OPERATION_TRANSITIONED,
                PlatformAuditEventKind.ATTEMPT_FINALIZED, PlatformAuditEventKind.OPERATION_TRANSITIONED,
                PlatformAuditEventKind.ENTITY_RESULT_APPLIED);
    }

    @Test void pauseAndResumeTransactionAEmitOneOperationCreatedEvent() {
        Fixture f = readyCreate();
        var created = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-state-audit");
        activateParents(f);
        long adVersion = jdbc.queryForObject("SELECT version FROM platform_ads WHERE platform_ad_uuid=?", Long.class, created.operation().entityUuid());
        reset(audit);
        var resume = tx.confirmState(created.operation().entityUuid(), UUID.randomUUID(), PlatformDesiredState.ACTIVE, adVersion, "stage4c-resume-a");
        var resumeCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(1)).write(resumeCaptor.capture(), any());
        assertThat(resumeCaptor.getValue().eventKind()).isEqualTo(PlatformAuditEventKind.OPERATION_CREATED);
        operations.submit(resume.operation().getOperationUuid(), resume.operation().getVersion());
        long pausedVersion = jdbc.queryForObject("SELECT version FROM platform_ads WHERE platform_ad_uuid=?", Long.class, created.operation().entityUuid());
        reset(audit);
        var pause = tx.confirmState(created.operation().entityUuid(), UUID.randomUUID(), PlatformDesiredState.PAUSED, pausedVersion, "stage4c-pause-a");
        var pauseCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(1)).write(pauseCaptor.capture(), any());
        assertThat(pauseCaptor.getValue().eventKind()).isEqualTo(PlatformAuditEventKind.OPERATION_CREATED);
    }

    @Test void concurrentClaimHasOneWinnerAndNoDeadlock() throws Exception {
        Fixture f = readyCreate();
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-winner");
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return claim(created.operation().getOperationUuid(), created.operation().getVersion()); });
            Future<String> second = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return claim(created.operation().getOperationUuid(), created.operation().getVersion()); });
            String a = first.get(20, TimeUnit.SECONDS);
            String b = second.get(20, TimeUnit.SECONDS);
            assertThat(java.util.List.of(a, b)).contains("SUCCEEDED");
            assertThat(java.util.List.of(a, b).stream().filter(s -> !"SUCCEEDED".equals(s)).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?", Integer.class, created.operation().getOperationUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_ads WHERE platform_ad_uuid=? AND external_id IS NOT NULL", Integer.class, created.operation().getEntityUuid())).isEqualTo(1);
    }

    @Test void finalizationRaceOnEvidenceRecoversUnknownWithOneProviderCall() {
        Fixture f = readyCreate();
        int calls = fake.invocationCount();
        doAnswer(invocation -> {
            jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "c".repeat(64), f.asset);
            return null;
        }).when(hook).beforeFinalize();
        var confirmation = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-final-race");
        assertThat(confirmation.operation().status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(confirmation.operation().normalizedErrorCode()).contains(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_RESPONSE_AMBIGUOUS);
        assertThat(fake.invocationCount()).isEqualTo(calls + 1);
        assertThat(jdbc.queryForObject("SELECT external_id FROM platform_ads WHERE platform_ad_uuid=?", String.class, confirmation.operation().entityUuid())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, confirmation.operation().operationUuid())).isEqualTo("UNKNOWN_OUTCOME");
    }

    @Test void claimRaceOnProductLifecycleRejectsCreateWithZeroAttempt() {
        Fixture f = readyCreate();
        int calls = fake.invocationCount();
        doAnswer(invocation -> {
            jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "c".repeat(64), f.asset);
            return null;
        }).when(hook).afterOperationLock();
        assertThatThrownBy(() -> service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-product-race"))
                .isInstanceOf(PlatformOperationException.class)
                .satisfies(error -> assertThat(((PlatformOperationException) error).code()).isEqualTo(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts a JOIN platform_operations o ON o.operation_uuid=a.operation_uuid WHERE o.operation_type='CREATE_AD'", Integer.class)).isZero();
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    @Test void resumeRejectsAfterEvidenceDivergenceWhilePauseRemainsAvailable() {
        Fixture f = readyCreate();
        var created = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-diverge");
        activateParents(f);
        UUID ad = created.operation().entityUuid();
        long adVersion = jdbc.queryForObject("SELECT version FROM platform_ads WHERE platform_ad_uuid=?", Long.class, ad);
        service.confirmState(ad, UUID.randomUUID(), PlatformDesiredState.ACTIVE, adVersion, "stage4c-activate-ad");
        jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "c".repeat(64), f.asset);
        long diverged = jdbc.queryForObject("SELECT version FROM platform_ads WHERE platform_ad_uuid=?", Long.class, ad);
        int calls = fake.invocationCount();
        var pause = service.confirmState(ad, UUID.randomUUID(), PlatformDesiredState.PAUSED, diverged, "stage4c-pause-ok");
        assertThat(pause.operation().status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThatThrownBy(() -> service.confirmState(ad, UUID.randomUUID(), PlatformDesiredState.ACTIVE,
                jdbc.queryForObject("SELECT version FROM platform_ads WHERE platform_ad_uuid=?", Long.class, ad), "stage4c-resume-blocked"))
                .isInstanceOf(PlatformOperationException.class)
                .satisfies(error -> assertThat(((PlatformOperationException) error).code()).isEqualTo(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID));
        assertThat(fake.invocationCount()).isEqualTo(calls + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCT_LIFECYCLE", "ASSET_LIFECYCLE", "OUTPUT_CHECKSUM", "OUTPUT_PRESERVATION", "OUTPUT_STATUS", "REVIEW_DECISION"})
    void claimRacesOnLiveEvidenceRejectCreateWithZeroAttempt(String kind) {
        Fixture f = readyCreate();
        int calls = fake.invocationCount();
        doAnswer(invocation -> { mutateEvidence(kind, f); return null; }).when(hook).afterOperationLock();
        assertThatThrownBy(() -> service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-claim-" + kind))
                .isInstanceOfAny(PlatformOperationException.class, DataIntegrityViolationException.class, org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts a JOIN platform_operations o ON o.operation_uuid=a.operation_uuid WHERE o.operation_type='CREATE_AD'", Integer.class)).isZero();
        assertThat(fake.invocationCount()).isEqualTo(calls);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_type='CREATE_AD'", String.class)).isEqualTo("CREATED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCT_LIFECYCLE", "ASSET_LIFECYCLE", "OUTPUT_CHECKSUM"})
    void finalizationRacesOnLiveEvidenceRecoverUnknownWithOneProviderCall(String kind) {
        Fixture f = readyCreate();
        int calls = fake.invocationCount();
        doAnswer(invocation -> { mutateEvidence(kind, f); return null; }).when(hook).beforeFinalize();
        var confirmation = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-final-" + kind);
        assertThat(confirmation.operation().status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(confirmation.operation().normalizedErrorCode()).contains(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_RESPONSE_AMBIGUOUS);
        assertThat(fake.invocationCount()).isEqualTo(calls + 1);
        assertThat(jdbc.queryForObject("SELECT external_id FROM platform_ads WHERE platform_ad_uuid=?", String.class, confirmation.operation().entityUuid())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, confirmation.operation().operationUuid())).isEqualTo("UNKNOWN_OUTCOME");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCT_LIFECYCLE", "ASSET_LIFECYCLE", "OUTPUT_CHECKSUM", "OUTPUT_PRESERVATION", "OUTPUT_STATUS", "REVIEW_DECISION"})
    void retryClaimRacesRejectWithZeroAdditionalAttempt(String kind) {
        Fixture f = readyCreate();
        UUID operation = retryableCreate(f);
        long version = jdbc.queryForObject("SELECT version FROM platform_operations WHERE operation_uuid=?", Long.class, operation);
        int attempts = jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?", Integer.class, operation);
        int calls = fake.invocationCount();
        reset(hook);
        doAnswer(invocation -> { mutateEvidence(kind, f); return null; }).when(hook).afterOperationLock();
        assertThatThrownBy(() -> operations.retry(operation, version, Instant.now()))
                .isInstanceOfAny(PlatformOperationException.class, DataIntegrityViolationException.class, org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?", Integer.class, operation)).isEqualTo(attempts);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("FAILED_RETRYABLE");
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    @Test void reconciledCommitRaceOnEvidenceRecoversUnknownWithOneProviderCall() {
        Fixture f = readyCreate();
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.AMBIGUOUS_TIMEOUT);
        var created = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-reconcile-race");
        assertThat(created.operation().status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        int calls = fake.invocationCount();
        reset(hook);
        doAnswer(invocation -> {
            jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "c".repeat(64), f.asset);
            return null;
        }).when(hook).beforeFinalize();
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.RECONCILE_FOUND);
        var reconciled = operations.reconcile(created.operation().operationUuid(), created.operation().version());
        assertThat(reconciled.status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(fake.invocationCount()).isEqualTo(calls + 1);
        assertThat(jdbc.queryForObject("SELECT external_id FROM platform_ads WHERE platform_ad_uuid=?", String.class, created.operation().entityUuid())).isNull();
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23514", "23503", "40001", "40P01"})
    void unrelatedCommitConflictsDoNotMisclassifyOrRedispatch(String sqlState) {
        Fixture f = readyCreate();
        int calls = fake.invocationCount();
        doAnswer(invocation -> {
            if ("23514".equals(sqlState)) {
                jdbc.update("UPDATE ai_generation_outputs SET preservation_status='BLOCKED' WHERE generation_output_uuid=?", f.output);
            } else if ("23503".equals(sqlState)) {
                jdbc.update("UPDATE assets SET product_uuid=? WHERE asset_uuid=?", UUID.randomUUID(), f.asset);
            } else {
                String state = sqlState;
                throw new DataIntegrityViolationException("unrelated " + state, new PSQLException("never disclose",
                        "40001".equals(state) ? PSQLState.SERIALIZATION_FAILURE : PSQLState.DEADLOCK_DETECTED));
            }
            return null;
        }).when(hook).beforeFinalize();
        if ("40001".equals(sqlState) || "40P01".equals(sqlState)) {
            var confirmation = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-sql-" + sqlState);
            assertThat(confirmation.operation().status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
            assertThat(confirmation.operation().normalizedErrorCode()).contains(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_RESPONSE_AMBIGUOUS);
        } else {
            assertThatThrownBy(() -> service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-sql-" + sqlState))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_type='CREATE_AD'", String.class)).isEqualTo("SUBMITTING");
        }
        assertThat(fake.invocationCount()).isEqualTo(calls + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_ads WHERE external_id IS NOT NULL", Integer.class)).isZero();
    }

    @Test void recoveryFailureLeavesClaimedGraphAndRestartRecoversWithoutRedispatch() {
        Fixture f = readyCreate();
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-recovery");
        var context = contexts.forCurrentActor("stage4c-recovery");
        operationTx.claimSubmit(created.operation().getOperationUuid(), created.operation().getVersion(), Instant.now(), context);
        UUID operation = created.operation().getOperationUuid();
        long version = jdbc.queryForObject("SELECT version FROM platform_operations WHERE operation_uuid=?", Long.class, operation);
        Instant claimed = jdbc.queryForObject("SELECT claimed_at FROM platform_operations WHERE operation_uuid=?", java.sql.Timestamp.class, operation).toInstant();
        int calls = fake.invocationCount();
        int auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation);
        AtomicInteger writes = new AtomicInteger();
        reset(audit);
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 1) throw new IllegalStateException("synthetic recovery audit failure");
            return invocation.callRealMethod();
        }).when(audit).write(any(PlatformAuditEvent.class), any());
        assertThatThrownBy(() -> operations.recoverStaleClaim(operation, version, claimed.plusSeconds(300)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("SUBMITTING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation)).isEqualTo(auditBefore);
        reset(audit);
        var recovered = operations.recoverStaleClaim(operation, version, claimed.plusSeconds(300));
        assertThat(recovered.status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    @Test void concurrentRecoveryHasOneWinnerAndNoRedispatch() throws Exception {
        Fixture f = readyCreate();
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-concurrent-recovery");
        operationTx.claimSubmit(created.operation().getOperationUuid(), created.operation().getVersion(), Instant.now(), contexts.forCurrentActor("stage4c-concurrent-recovery"));
        UUID operation = created.operation().getOperationUuid();
        long version = jdbc.queryForObject("SELECT version FROM platform_operations WHERE operation_uuid=?", Long.class, operation);
        Instant claimed = jdbc.queryForObject("SELECT claimed_at FROM platform_operations WHERE operation_uuid=?", java.sql.Timestamp.class, operation).toInstant();
        int calls = fake.invocationCount();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return recover(operation, version, claimed.plusSeconds(300)); });
            Future<String> second = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return recover(operation, version, claimed.plusSeconds(300)); });
            String a = first.get(20, TimeUnit.SECONDS);
            String b = second.get(20, TimeUnit.SECONDS);
            assertThat(java.util.List.of(a, b)).contains("UNKNOWN_OUTCOME");
            assertThat(java.util.List.of(a, b).stream().filter(s -> !"UNKNOWN_OUTCOME".equals(s)).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("UNKNOWN_OUTCOME");
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    @Test void isolatedClaimFinalizeAndRecoveryEmitExactTypedContentByOrdinal() {
        Fixture f = readyCreate();
        reset(audit);
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-isolated-a");
        var createCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(2)).write(createCaptor.capture(), any());
        assertThat(createCaptor.getAllValues().get(0).eventKind()).isEqualTo(PlatformAuditEventKind.ENTITY_CREATED);
        assertThat(createCaptor.getAllValues().get(0).newDesiredState()).contains(PlatformDesiredState.PAUSED);
        assertThat(createCaptor.getAllValues().get(1).eventKind()).isEqualTo(PlatformAuditEventKind.OPERATION_CREATED);
        assertThat(createCaptor.getAllValues().get(1).newOperationStatus()).contains(PlatformOperationStatus.CREATED);
        UUID operation = created.operation().getOperationUuid();
        assertThat(changes(operation, "PLATFORM_AD", "CREATE")).containsExactly("0:desiredState:null->PAUSED");
        assertThat(changes(operation, "PLATFORM_OPERATION", "CREATE")).containsExactly("0:operationStatus:null->CREATED");
        var context = contexts.forCurrentActor("stage4c-isolated-claim");
        reset(audit);
        operationTx.claimSubmit(operation, created.operation().getVersion(), Instant.now(), context);
        var claimCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(2)).write(claimCaptor.capture(), any());
        assertThat(claimCaptor.getAllValues()).extracting(PlatformAuditEvent::eventKind).containsExactly(
                PlatformAuditEventKind.ATTEMPT_CREATED, PlatformAuditEventKind.OPERATION_TRANSITIONED);
        assertThat(claimCaptor.getAllValues().get(0).newAttemptStatus()).contains(com.aicommerce.platform.delivery.domain.PlatformAttemptStatus.STARTED);
        assertThat(claimCaptor.getAllValues().get(1).previousOperationStatus()).contains(PlatformOperationStatus.CREATED);
        assertThat(claimCaptor.getAllValues().get(1).newOperationStatus()).contains(PlatformOperationStatus.SUBMITTING);
        long claimedVersion = jdbc.queryForObject("SELECT version FROM platform_operations WHERE operation_uuid=?", Long.class, operation);
        reset(audit);
        var completed = operationTx.recordWriteOutcome(operation, new DeterministicFakePlatformAdapter().submitAd(
                new com.aicommerce.platform.delivery.application.port.PlatformAdCommand(
                        new com.aicommerce.platform.delivery.application.port.PlatformCommandIdentity(operation,
                                created.operation().getPlatformAccountUuid(),
                                jdbc.queryForObject("SELECT idempotency_key FROM platform_operations WHERE operation_uuid=?", String.class, operation),
                                jdbc.queryForObject("SELECT request_sha256 FROM platform_operations WHERE operation_uuid=?", String.class, operation)),
                        created.operation().getEntityUuid(), f.adSet, f.product, f.asset, f.output, f.review, "e".repeat(64),
                        Stage4CSupport.MAPPING, PlatformDesiredState.PAUSED)), context);
        var finalizeCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(3)).write(finalizeCaptor.capture(), any());
        assertThat(finalizeCaptor.getAllValues()).extracting(PlatformAuditEvent::eventKind).containsExactly(
                PlatformAuditEventKind.ATTEMPT_FINALIZED, PlatformAuditEventKind.OPERATION_TRANSITIONED, PlatformAuditEventKind.ENTITY_RESULT_APPLIED);
        assertThat(finalizeCaptor.getAllValues().get(2).externalIdFingerprint()).isPresent();
        assertThat(completed.getStatus()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(claimedVersion).isGreaterThan(created.operation().getVersion());
        assertThat(changes(operation, "PLATFORM_OPERATION_ATTEMPT", "CREATE"))
                .containsExactly("0:attemptKind:null->SUBMIT", "1:attemptNumber:null->1", "2:attemptStatus:null->STARTED");
        assertThat(changes(operation, "PLATFORM_OPERATION", "UPDATE")).anySatisfy(row -> assertThat(row).contains("CREATED->SUBMITTING"));
        assertThat(changes(operation, "PLATFORM_AD", "UPDATE")).anySatisfy(row -> assertThat(row).contains("observedState"));
        String leaked = jdbc.queryForObject("""
                SELECT coalesce(string_agg(coalesce(c.old_value,'') || coalesce(c.new_value,''), '|'), '')
                FROM audit_log_changes c JOIN audit_logs a ON a.audit_uuid=c.audit_uuid
                WHERE a.operation_uuid=?
                """, String.class, operation);
        String external = jdbc.queryForObject("SELECT external_id FROM platform_ads WHERE platform_ad_uuid=?", String.class, created.operation().getEntityUuid());
        assertThat(leaked).doesNotContain(external).doesNotContain("request_payload").doesNotContain("authorization");
    }

    @Test void isolatedRecoveryEmitsTwoEventsAndEnumeratedZeroEventPathsWriteNothing() {
        Fixture f = readyCreate();
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-zero-recovery");
        operationTx.claimSubmit(created.operation().getOperationUuid(), created.operation().getVersion(), Instant.now(), contexts.forCurrentActor("stage4c-zero-recovery"));
        UUID operation = created.operation().getOperationUuid();
        long version = jdbc.queryForObject("SELECT version FROM platform_operations WHERE operation_uuid=?", Long.class, operation);
        Instant claimed = jdbc.queryForObject("SELECT claimed_at FROM platform_operations WHERE operation_uuid=?", java.sql.Timestamp.class, operation).toInstant();
        reset(audit);
        operations.recoverStaleClaim(operation, version, claimed.plusSeconds(300));
        var recoveryCaptor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit, times(2)).write(recoveryCaptor.capture(), any());
        assertThat(recoveryCaptor.getAllValues()).extracting(PlatformAuditEvent::eventKind).containsExactly(
                PlatformAuditEventKind.ATTEMPT_FINALIZED, PlatformAuditEventKind.OPERATION_TRANSITIONED);

        UUID request = UUID.randomUUID();
        reset(audit);
        service.previewCreate(f.adSet, request, f.product, f.asset, f.output, f.review, f.parentVersion);
        verifyNoInteractions(audit);
        assertThatThrownBy(() -> service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion + 1, "stage4c-stale"))
                .isInstanceOfAny(PlatformOperationException.class, Stage4BException.class);
        verifyNoInteractions(audit);
        var replayable = tx.confirmCreate(f.adSet, request, f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-replay-zero");
        int persisted = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, replayable.operation().getOperationUuid());
        reset(audit);
        var replay = tx.confirmCreate(f.adSet, request, f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-replay-zero");
        assertThat(replay.replay()).isTrue();
        verifyNoInteractions(audit);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, replayable.operation().getOperationUuid())).isEqualTo(persisted);
        jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "c".repeat(64), f.asset);
        assertThatThrownBy(() -> service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-evidence"))
                .isInstanceOf(PlatformOperationException.class);
        verifyNoInteractions(audit);
        assertThatThrownBy(() -> operations.retry(replayable.operation().getOperationUuid(), replayable.operation().getVersion(), Instant.now()))
                .isInstanceOf(PlatformOperationException.class);
        verifyNoInteractions(audit);
        assertThatThrownBy(() -> operations.recoverStaleClaim(replayable.operation().getOperationUuid(), replayable.operation().getVersion(), Instant.now()))
                .isInstanceOf(PlatformOperationException.class);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void throwAtEveryCreateAppendRollsBackTransactionA(int position) {
        Fixture f = readyCreate();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == position && position < 2) throw new IllegalStateException("synthetic audit failure");
            Object result = invocation.callRealMethod();
            if (call == position) throw new IllegalStateException("synthetic audit failure after append");
            return result;
        }).when(audit).write(any(PlatformAuditEvent.class), any());
        assertThatThrownBy(() -> tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-txa-fail"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operations WHERE operation_type='CREATE_AD'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_ads", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void throwAtEveryClaimAppendRollsBackAttempt(int position) {
        Fixture f = readyCreate();
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-claim-fail");
        UUID operation = created.operation().getOperationUuid();
        int auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation);
        reset(audit);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == position && position < 2) throw new IllegalArgumentException("synthetic audit failure");
            Object result = invocation.callRealMethod();
            if (call == position) throw new IllegalArgumentException("synthetic audit failure after append");
            return result;
        }).when(audit).write(any(PlatformAuditEvent.class), any());
        assertThatThrownBy(() -> operations.submit(operation, created.operation().getVersion()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?", Integer.class, operation)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation)).isEqualTo(auditBefore);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void throwAtEveryFinalizeAppendRollsBackClaimedGraph(int position) {
        Fixture f = readyCreate();
        var claimed = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-final-fail-claim");
        UUID operation = claimed.operation().getOperationUuid();
        var context = contexts.forCurrentActor("stage4c-final-fail-claim");
        operationTx.claimSubmit(operation, claimed.operation().getVersion(), Instant.now(), context);
        int auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation);
        reset(audit);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == position && position < 3) throw new IllegalStateException("synthetic audit failure");
            Object result = invocation.callRealMethod();
            if (call == position) throw new IllegalStateException("synthetic audit failure after append");
            return result;
        }).when(audit).write(any(PlatformAuditEvent.class), any());
        assertThatThrownBy(() -> operationTx.recordWriteOutcome(operation, new DeterministicFakePlatformAdapter().submitAd(
                new com.aicommerce.platform.delivery.application.port.PlatformAdCommand(
                        new com.aicommerce.platform.delivery.application.port.PlatformCommandIdentity(operation,
                                claimed.operation().getPlatformAccountUuid(),
                                jdbc.queryForObject("SELECT idempotency_key FROM platform_operations WHERE operation_uuid=?", String.class, operation),
                                jdbc.queryForObject("SELECT request_sha256 FROM platform_operations WHERE operation_uuid=?", String.class, operation)),
                        claimed.operation().getEntityUuid(), f.adSet, f.product, f.asset, f.output, f.review, "e".repeat(64),
                        Stage4CSupport.MAPPING, PlatformDesiredState.PAUSED)), context))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("SUBMITTING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation)).isEqualTo(auditBefore);
        assertThat(jdbc.queryForObject("SELECT external_id FROM platform_ads WHERE platform_ad_uuid=?", String.class, claimed.operation().getEntityUuid())).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void throwAtEveryRecoveryAppendLeavesClaimedGraph(int position) {
        Fixture f = readyCreate();
        var created = tx.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-recovery-fail");
        operationTx.claimSubmit(created.operation().getOperationUuid(), created.operation().getVersion(), Instant.now(), contexts.forCurrentActor("stage4c-recovery-fail"));
        UUID operation = created.operation().getOperationUuid();
        long version = jdbc.queryForObject("SELECT version FROM platform_operations WHERE operation_uuid=?", Long.class, operation);
        Instant claimed = jdbc.queryForObject("SELECT claimed_at FROM platform_operations WHERE operation_uuid=?", java.sql.Timestamp.class, operation).toInstant();
        int auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation);
        reset(audit);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == position && position < 2) throw new IllegalStateException("synthetic audit failure");
            Object result = invocation.callRealMethod();
            if (call == position) throw new IllegalStateException("synthetic audit failure after append");
            return result;
        }).when(audit).write(any(PlatformAuditEvent.class), any());
        assertThatThrownBy(() -> operations.recoverStaleClaim(operation, version, claimed.plusSeconds(300)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("SUBMITTING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE operation_uuid=?", Integer.class, operation)).isEqualTo(auditBefore);
    }

    private String recover(UUID operation, long version, Instant when) {
        try {
            return operations.recoverStaleClaim(operation, version, when).status().name();
        } catch (RuntimeException exception) {
            return exception.getClass().getSimpleName() + ":" + (exception instanceof PlatformOperationException poe ? poe.code().name() : exception.getMessage());
        }
    }

    private UUID retryableCreate(Fixture f) {
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.RETRYABLE_RATE_LIMIT);
        var created = service.confirmCreate(f.adSet, UUID.randomUUID(), f.product, f.asset, f.output, f.review, f.parentVersion, "stage4c-retryable");
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("UPDATE platform_operations SET next_attempt_at=statement_timestamp()-interval '1 second' WHERE operation_uuid=?", created.operation().operationUuid());
        });
        return created.operation().operationUuid();
    }

    private void mutateEvidence(String kind, Fixture f) {
        switch (kind) {
            case "PRODUCT_LIFECYCLE" -> jdbc.update("UPDATE products SET lifecycle_status='ARCHIVED',archived_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 WHERE product_uuid=?", f.product);
            case "ASSET_LIFECYCLE" -> jdbc.update("UPDATE assets SET lifecycle_status='ARCHIVED',archived_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", f.asset);
            case "OUTPUT_CHECKSUM" -> jdbc.update("UPDATE assets SET checksum_sha256=?,updated_at=statement_timestamp(),version=version+1 WHERE asset_uuid=?", "c".repeat(64), f.asset);
            case "OUTPUT_STATUS" -> jdbc.update("UPDATE ai_generation_outputs SET review_status='REJECTED',version=version+1 WHERE generation_output_uuid=?", f.output);
            case "OUTPUT_PRESERVATION" -> jdbc.update("UPDATE ai_generation_outputs SET preservation_status='BLOCKED' WHERE generation_output_uuid=?", f.output);
            case "REVIEW_DECISION" -> jdbc.update("UPDATE ai_review_decisions SET decision='REJECTED',reason='barrier' WHERE review_decision_uuid=?", f.review);
            default -> throw new IllegalArgumentException(kind);
        }
    }

    private java.util.List<String> changes(UUID operation, String subject, String action) {
        return jdbc.queryForList("""
                SELECT c.change_order||':'||c.field_name||':'||coalesce(c.old_value,'null')||'->'||coalesce(c.new_value,'null')
                FROM audit_log_changes c JOIN audit_logs l ON l.audit_uuid=c.audit_uuid
                WHERE l.operation_uuid=? AND l.entity_type=? AND l.action=?
                ORDER BY l.stage4b_operation_ordinal, c.change_order
                """, String.class, operation, subject, action);
    }

    private String claim(UUID operation, long version) {
        try {
            return operations.submit(operation, version).status().name();
        } catch (RuntimeException exception) {
            return exception.getClass().getSimpleName() + ":" + (exception instanceof PlatformOperationException poe ? poe.code().name() : exception.getMessage());
        }
    }

    private Fixture readyCreate() {
        var campaignCreated = stage4b.confirmCampaign(UUID.randomUUID(), plan, 0, "barrier-campaign");
        operations.submit(campaignCreated.operation().getOperationUuid(), campaignCreated.operation().getVersion());
        long campaignVersion = jdbc.queryForObject("SELECT version FROM platform_campaigns WHERE platform_campaign_uuid=?", Long.class, campaignCreated.operation().getEntityUuid());
        var adSetCreated = stage4b.confirmAdSet(campaignCreated.operation().getEntityUuid(), UUID.randomUUID(), com.aicommerce.platform.delivery.domain.PlatformBudgetType.DAILY, new java.math.BigDecimal("25"), 0, campaignVersion, "barrier-adset");
        operations.submit(adSetCreated.operation().getOperationUuid(), adSetCreated.operation().getVersion());
        Evidence evidence = evidence(adSetCreated.operation().getEntityUuid());
        long parent = jdbc.queryForObject("SELECT version FROM platform_ad_sets WHERE platform_ad_set_uuid=?", Long.class, adSetCreated.operation().getEntityUuid());
        return new Fixture(campaignCreated.operation().getEntityUuid(), adSetCreated.operation().getEntityUuid(), parent, evidence.product, evidence.asset, evidence.output, evidence.review);
    }

    private void activateParents(Fixture f) {
        long campaignVersion = jdbc.queryForObject("SELECT version FROM platform_campaigns WHERE platform_campaign_uuid=?", Long.class, f.campaign);
        var campaign = stage4b.confirmState(com.aicommerce.platform.delivery.domain.PlatformEntityType.CAMPAIGN, f.campaign, UUID.randomUUID(), PlatformDesiredState.ACTIVE, campaignVersion, "barrier-campaign-resume");
        operations.submit(campaign.operation().getOperationUuid(), campaign.operation().getVersion());
        long adSetVersion = jdbc.queryForObject("SELECT version FROM platform_ad_sets WHERE platform_ad_set_uuid=?", Long.class, f.adSet);
        var adSet = stage4b.confirmState(com.aicommerce.platform.delivery.domain.PlatformEntityType.AD_SET, f.adSet, UUID.randomUUID(), PlatformDesiredState.ACTIVE, adSetVersion, "barrier-adset-resume");
        operations.submit(adSet.operation().getOperationUuid(), adSet.operation().getVersion());
    }

    private Evidence evidence(UUID adSet) {
        UUID product = UUID.randomUUID(), source = UUID.randomUUID(), asset = UUID.randomUUID(), template = UUID.randomUUID(),
                templateVersion = UUID.randomUUID(), batch = UUID.randomUUID(), job = UUID.randomUUID(),
                output = UUID.randomUUID(), review = UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status) VALUES (?,?,'Barrier evidence','ACTIVE')",
                product, "PROD-" + String.format("%08d", Math.abs(product.hashCode()) % 100_000_000));
        jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) VALUES (?,?,?)", UUID.randomUUID(), plan, product);
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','SOURCE',?)", source, product, plan, "d".repeat(64));
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','GENERATED',?)", asset, product, plan, "e".repeat(64));
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,?,'IMAGE','barrier')", template, "barrier." + template);
        jdbc.update("INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) VALUES (?,?,1,'image','{}'::jsonb,?,'sql')", templateVersion, template, "a".repeat(64));
        jdbc.update("INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) VALUES (?,?,'COMPLETED','TWD',0,0,1,1,'sql')", batch, product);
        jdbc.update("INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) VALUES (?,?,?,?,'IMAGE','stub','stub','SUCCEEDED','image','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)", job, batch, product, templateVersion);
        jdbc.update("""
                INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,model_label,input_units,output_units,actual_cost,currency,safety_findings,provider_metadata,source_asset_uuid,generated_asset_uuid,generation_mode,workflow_key,workflow_version,image_width,image_height,media_type,size_bytes,source_checksum_sha256,output_checksum_sha256,protected_pixels_sha256,preservation_algorithm,preservation_status,preservation_details)
                VALUES (?,?,?,?,'IMAGE','stub',0,0,0,'TWD','[]'::jsonb,'{}'::jsonb,?,?,'BACKGROUND_COMPOSITE','sql-v1','1',1,1,'image/png',1,?,?,?,'RGBA_MASK_EXACT_V1','PASSED','{"changedPixelCount":0,"protectedPixelCount":1}'::jsonb)
                """, output, job, batch, product, source, asset, "d".repeat(64), "e".repeat(64), "f".repeat(64));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) VALUES (?,?,'APPROVED','LOCAL_ADMIN','sql','sql-review',0,current_timestamp)", review, output);
            jdbc.update("UPDATE ai_generation_outputs SET review_status='APPROVED',version=1 WHERE generation_output_uuid=?", output);
        });
        return new Evidence(product, asset, output, review);
    }

    private record Fixture(UUID campaign, UUID adSet, long parentVersion, UUID product, UUID asset, UUID output, UUID review) {}
    private record Evidence(UUID product, UUID asset, UUID output, UUID review) {}
}
