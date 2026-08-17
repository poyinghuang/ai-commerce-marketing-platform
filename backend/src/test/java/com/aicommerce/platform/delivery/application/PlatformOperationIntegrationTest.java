package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import com.aicommerce.platform.delivery.application.port.NormalizedPlatformEvidence;
import com.aicommerce.platform.delivery.application.port.WriteUnknownOutcome;
import com.aicommerce.platform.delivery.domain.PlatformAttemptKind;
import com.aicommerce.platform.delivery.domain.PlatformEvidenceResultKind;
import com.aicommerce.platform.delivery.domain.PlatformUnknownCode;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PlatformOperationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired PlatformOperationService service;
    @Autowired PlatformOperationTransactions transactions;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired DeterministicFakePlatformAdapter fake;
    @Autowired JdbcTemplate jdbc;

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
    void staleSubmittingRecoveryIsCompareAndSetAndNeverCallsAdapter() {
        UUID operationUuid = UUID.randomUUID();
        var context = contexts.forCurrentActor("stage4a-recovery-" + operationUuid);
        var created = service.create(createCampaign(operationUuid, UUID.randomUUID()), context);
        Instant claimedAt = Instant.parse("2026-08-17T00:00:00Z");
        transactions.claim(operationUuid, created.getVersion(), claimedAt, context);
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
        long version = transactions.get(operationUuid).getVersion();
        int calls = fake.invocationCount();

        var recovered = service.recoverStaleClaim(operationUuid, version, claimedAt.plusSeconds(300));

        assertThat(recovered.status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(recovered.normalizedErrorCode()).contains(com.aicommerce.platform.delivery.domain.PlatformStableErrorCode.PLATFORM_RECONCILIATION_INCONCLUSIVE);
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    private WriteUnknownOutcome unknownSubmit() {
        return new WriteUnknownOutcome(PlatformUnknownCode.PLATFORM_RESPONSE_AMBIGUOUS, Optional.empty(),
                new NormalizedPlatformEvidence(1, ProviderKey.FAKE, PlatformAttemptKind.SUBMIT,
                        PlatformEvidenceResultKind.UNKNOWN_OUTCOME, Optional.empty(), Optional.empty(), Optional.empty()));
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
}
