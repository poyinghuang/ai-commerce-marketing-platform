package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(PlatformOperationIntegrationTest.ClockConfiguration.class)
class PlatformOperationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired PlatformOperationService service;
    @Autowired PlatformOperationTransactions transactions;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired DeterministicFakePlatformAdapter fake;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    UUID accountUuid;
    UUID platformCampaignUuid;

    @BeforeEach
    void fixture() {
        clock.set(Instant.parse("2026-08-16T00:00:00Z"));
        fake.clearScenarios();
        accountUuid = UUID.randomUUID();
        UUID campaignUuid = UUID.randomUUID();
        platformCampaignUuid = UUID.randomUUID();
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) VALUES (?,?,'ACTIVE',0)",
                campaignUuid, "Stage 4A " + campaignUuid);
        jdbc.update("""
                INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,
                  external_account_fingerprint,currency,timezone,lifecycle_status,version)
                VALUES (?,'FAKE','TEST',?,?,'TWD','Asia/Taipei','ACTIVE',0)
                """, accountUuid, "fake-" + accountUuid,
                accountUuid.toString().replace("-", "").repeat(2));
        jdbc.update("""
                INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,
                  desired_state,account_timezone,version) VALUES (?,?,?,'OUTCOME_SALES','PAUSED','Asia/Taipei',0)
                """, platformCampaignUuid, campaignUuid, accountUuid);
    }

    @Test
    void persistsBeforeAdapterCallThenCompletesWithTransactionalAudit() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-success-" + operationUuid);
        var created = service.create(command(operationUuid, UUID.randomUUID(), "{\"name\":\"campaign\"}"), context);
        assertThat(created.getStatus()).isEqualTo(PlatformOperationStatus.CREATED);
        assertThat(auditCount(operationUuid)).isEqualTo(1);
        assertStableAuditIdentity(operationUuid, context);

        var completed = service.execute(operationUuid, created.getVersion(), context);
        assertThat(completed.getStatus()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(completed.getExternalId()).isEqualTo("fake-" + operationUuid);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='PLATFORM_OPERATION' AND entity_uuid=?",
                Integer.class, operationUuid)).isEqualTo(3);
        assertStableAuditIdentity(operationUuid, context);
    }

    @Test
    void duplicateRequestReplaysButDifferentPayloadConflictsWithoutExtraAudit() {
        UUID operationUuid = UUID.randomUUID();
        UUID requestUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-idempotency-" + operationUuid);
        var first = service.create(command(operationUuid, requestUuid, "{\"b\":2,\"a\":1}"), context);
        var replay = service.create(command(UUID.randomUUID(), requestUuid, "{\"a\":1,\"b\":2}"), context);
        assertThat(replay.getOperationUuid()).isEqualTo(first.getOperationUuid());
        assertThatThrownBy(() -> service.create(command(UUID.randomUUID(), requestUuid, "{\"a\":2}"), context))
                .isInstanceOf(PlatformOperationConflictException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='PLATFORM_OPERATION' AND entity_uuid=?",
                Integer.class, operationUuid)).isEqualTo(1);
    }

    @Test
    void duplicateRequestWithDifferentMaxAttemptsConflictsWithoutExtraAudit() {
        UUID operationUuid = UUID.randomUUID();
        UUID requestUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-idempotency-attempts-" + operationUuid);
        service.create(command(operationUuid, requestUuid, "{\"a\":1}"), context);
        var changed = new CreatePlatformOperationCommand(UUID.randomUUID(), accountUuid,
                PlatformOperationType.CREATE_CAMPAIGN, PlatformEntityType.CAMPAIGN, platformCampaignUuid,
                requestUuid, "{\"a\":1}", 4);
        assertThatThrownBy(() -> service.create(changed, context))
                .isInstanceOf(PlatformOperationConflictException.class)
                .hasMessageContaining("different immutable input");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='PLATFORM_OPERATION' AND entity_uuid=?",
                Integer.class, operationUuid)).isEqualTo(1);
    }

    @Test
    void retryableOperationCannotClaimBeforeNextAttemptAt() {
        Instant started = Instant.parse("2026-08-16T00:00:00Z");
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-backoff-" + operationUuid);
        var created = service.create(command(operationUuid, UUID.randomUUID(), "{\"retry\":true}"), context);
        fake.setScenario(operationUuid, DeterministicFakePlatformAdapter.Scenario.RETRYABLE_FAILURE);
        var retryable = service.execute(operationUuid, created.getVersion(), context);
        assertThat(retryable.getStatus()).isEqualTo(PlatformOperationStatus.FAILED_RETRYABLE);
        assertThatThrownBy(() -> service.execute(operationUuid, retryable.getVersion(), context))
                .isInstanceOf(PlatformOperationConflictException.class);

        clock.set(started.plusSeconds(6));
        fake.setScenario(operationUuid, DeterministicFakePlatformAdapter.Scenario.SUCCESS);
        assertThat(service.execute(operationUuid, retryable.getVersion(), context).getStatus())
                .isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertStableAuditIdentity(operationUuid, context);
    }

    @Test
    void expiredSubmittingLeaseBecomesUnknownAndCanOnlyReconcile() {
        Instant started = Instant.parse("2026-08-16T00:00:00Z");
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-crash-" + operationUuid);
        var created = service.create(command(operationUuid, UUID.randomUUID(), "{\"crash\":true}"), context);
        transactions.claim(operationUuid, created.getVersion(), context); // Simulates process loss before adapter/result.
        var submitting = transactions.get(operationUuid);

        clock.set(started.plusSeconds(299));
        assertThatThrownBy(() -> service.recoverExpiredSubmission(operationUuid, submitting.getVersion(), context))
                .isInstanceOf(PlatformOperationConflictException.class);
        assertThat(transactions.get(operationUuid).getStatus()).isEqualTo(PlatformOperationStatus.SUBMITTING);

        clock.set(started.plusSeconds(301));
        var unknown = service.recoverExpiredSubmission(operationUuid, submitting.getVersion(), context);
        assertThat(unknown.getStatus()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThatThrownBy(() -> service.execute(operationUuid, unknown.getVersion(), context))
                .isInstanceOf(PlatformOperationConflictException.class);
        fake.setScenario(operationUuid, DeterministicFakePlatformAdapter.Scenario.TIMEOUT_RECONCILE_SUCCESS);
        assertThat(service.reconcile(operationUuid, context).getStatus()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertStableAuditIdentity(operationUuid, context);
    }

    @Test
    void unknownOutcomeCannotRetryAndRequiresReconciliation() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-unknown-" + operationUuid);
        var created = service.create(command(operationUuid, UUID.randomUUID(), "{\"fixture\":\"ambiguous\"}"), context);
        fake.setScenario(operationUuid, DeterministicFakePlatformAdapter.Scenario.TIMEOUT_RECONCILE_SUCCESS);
        var unknown = service.execute(operationUuid, created.getVersion(), context);
        assertThat(unknown.getStatus()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThatThrownBy(() -> service.execute(operationUuid, unknown.getVersion(), context))
                .isInstanceOf(PlatformOperationConflictException.class);
        assertThat(service.reconcile(operationUuid, context).getStatus()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertStableAuditIdentity(operationUuid, context);
    }

    @Test
    void terminalValidationAndPermissionOutcomesPersistTerminalFailure() {
        assertTerminalOutcome(DeterministicFakePlatformAdapter.Scenario.TERMINAL_VALIDATION_FAILURE,
                "FAKE_VALIDATION_REJECTED");
        assertTerminalOutcome(DeterministicFakePlatformAdapter.Scenario.TERMINAL_PERMISSION_FAILURE,
                "FAKE_PERMISSION_DENIED");
    }

    @Test
    void finalRetryableAttemptConvertsToTerminalFailure() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-final-retry-" + operationUuid);
        var oneAttempt = new CreatePlatformOperationCommand(operationUuid, accountUuid,
                PlatformOperationType.CREATE_CAMPAIGN, PlatformEntityType.CAMPAIGN, platformCampaignUuid,
                UUID.randomUUID(), "{\"retry\":\"final\"}", 1);
        var created = service.create(oneAttempt, context);
        fake.setScenario(operationUuid, DeterministicFakePlatformAdapter.Scenario.RETRYABLE_FAILURE);

        var terminal = service.execute(operationUuid, created.getVersion(), context);

        assertThat(terminal.getStatus()).isEqualTo(PlatformOperationStatus.FAILED_TERMINAL);
        assertThat(terminal.getNormalizedErrorCode()).isEqualTo("PLATFORM_RETRY_LIMIT_EXHAUSTED");
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?",
                String.class, operationUuid)).isEqualTo("FAILED_TERMINAL");
    }

    @Test
    void reconciliationFoundFailurePersistsTerminalAndUnresolvedIsNoOpWithoutAudit() {
        UUID unresolvedUuid = UUID.randomUUID();
        var unresolvedContext = contexts.forCurrentActor("platform-unresolved-" + unresolvedUuid);
        var unresolvedCreated = service.create(
                command(unresolvedUuid, UUID.randomUUID(), "{\"reconcile\":\"unresolved\"}"),
                unresolvedContext);
        fake.setScenario(unresolvedUuid,
                DeterministicFakePlatformAdapter.Scenario.TIMEOUT_RECONCILE_UNRESOLVED);
        var unknown = service.execute(unresolvedUuid, unresolvedCreated.getVersion(), unresolvedContext);
        int auditBefore = auditCount(unresolvedUuid);

        var stillUnknown = service.reconcile(unresolvedUuid, unresolvedContext);

        assertThat(stillUnknown.getStatus()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(auditCount(unresolvedUuid)).isEqualTo(auditBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?",
                String.class, unresolvedUuid)).isEqualTo("UNKNOWN_OUTCOME");

        UUID failureUuid = UUID.randomUUID();
        var failureContext = contexts.forCurrentActor("platform-reconcile-failure-" + failureUuid);
        var failureCreated = service.create(
                command(failureUuid, UUID.randomUUID(), "{\"reconcile\":\"failure\"}"), failureContext);
        fake.setScenario(failureUuid, DeterministicFakePlatformAdapter.Scenario.TIMEOUT_RECONCILE_FAILURE);
        service.execute(failureUuid, failureCreated.getVersion(), failureContext);

        var failed = service.reconcile(failureUuid, failureContext);

        assertThat(failed.getStatus()).isEqualTo(PlatformOperationStatus.FAILED_TERMINAL);
        assertThat(failed.getNormalizedErrorCode()).isEqualTo("FAKE_RECONCILED_FAILURE");
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?",
                String.class, failureUuid)).isEqualTo("FAILED_TERMINAL");
        assertStableAuditIdentity(failureUuid, failureContext);
    }

    @Test
    void staleClaimFailsWithoutAudit() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-stale-claim-" + operationUuid);
        var created = service.create(command(operationUuid, UUID.randomUUID(), "{\"stale\":true}"), context);
        int auditBefore = auditCount(operationUuid);

        assertThatThrownBy(() -> transactions.claim(operationUuid, created.getVersion() + 1, context))
                .isInstanceOf(PlatformOperationConflictException.class);

        assertThat(auditCount(operationUuid)).isEqualTo(auditBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?",
                String.class, operationUuid)).isEqualTo("CREATED");
    }

    @Test
    void optimisticConcurrentClaimAllowsOnlyOneSubmission() throws Exception {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-race-" + operationUuid);
        var created = service.create(command(operationUuid, UUID.randomUUID(), "{\"race\":true}"), context);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> executeAfter(start, operationUuid, created.getVersion(), context));
            var second = executor.submit(() -> executeAfter(start, operationUuid, created.getVersion(), context));
            start.countDown();
            long successes = java.util.stream.Stream.of(first.get(), second.get()).filter("SUCCEEDED"::equals).count();
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM platform_operations WHERE operation_uuid=?",
                Integer.class, operationUuid)).isEqualTo(1);
    }

    private String executeAfter(CountDownLatch start, UUID operationUuid, long version,
            com.aicommerce.platform.audit.domain.AuditOperationContext context) {
        try {
            start.await();
            service.execute(operationUuid, version, context);
            return "SUCCEEDED";
        } catch (Exception exception) {
            return exception.getClass().getSimpleName();
        }
    }

    private void assertTerminalOutcome(DeterministicFakePlatformAdapter.Scenario scenario, String errorCode) {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("platform-terminal-" + operationUuid);
        var created = service.create(command(operationUuid, UUID.randomUUID(), "{\"terminal\":true}"), context);
        fake.setScenario(operationUuid, scenario);
        var terminal = service.execute(operationUuid, created.getVersion(), context);
        assertThat(terminal.getStatus()).isEqualTo(PlatformOperationStatus.FAILED_TERMINAL);
        assertThat(terminal.getNormalizedErrorCode()).isEqualTo(errorCode);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?",
                String.class, operationUuid)).isEqualTo("FAILED_TERMINAL");
    }

    private int auditCount(UUID operationUuid) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='PLATFORM_OPERATION' AND entity_uuid=?",
                Integer.class, operationUuid);
    }

    private void assertStableAuditIdentity(UUID operationUuid,
            com.aicommerce.platform.audit.domain.AuditOperationContext trustedContext) {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_logs
                WHERE entity_type='PLATFORM_OPERATION' AND entity_uuid=? AND operation_uuid<>?
                """, Integer.class, operationUuid, operationUuid)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_logs
                WHERE entity_type='PLATFORM_OPERATION' AND entity_uuid=?
                  AND (actor_type<>? OR actor_id<>? OR request_id<>? OR source<>?)
                """, Integer.class, operationUuid, trustedContext.actor().type().name(),
                trustedContext.actor().id(), trustedContext.requestId(), trustedContext.source().name())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT operation_uuid) FROM audit_logs
                WHERE entity_type='PLATFORM_OPERATION' AND entity_uuid=?
                """, Integer.class, operationUuid)).isEqualTo(1);
    }

    private CreatePlatformOperationCommand command(UUID operationUuid, UUID requestUuid, String json) {
        return new CreatePlatformOperationCommand(operationUuid, accountUuid, PlatformOperationType.CREATE_CAMPAIGN,
                PlatformEntityType.CAMPAIGN, platformCampaignUuid, requestUuid, json, 3);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary
        MutableClock platformTestClock() {
            return new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant initial) { instant = new AtomicReference<>(initial); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
