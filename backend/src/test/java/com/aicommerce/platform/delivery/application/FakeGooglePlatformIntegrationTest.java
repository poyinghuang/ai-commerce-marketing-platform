package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakeGooglePlatformAdapter;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class FakeGooglePlatformIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired PlatformOperationService service;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired DeterministicFakeGooglePlatformAdapter google;
    @Autowired DeterministicFakePlatformAdapter meta;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void createCampaignOnFakeGoogleAccountPersistsPausedEvidenceWithoutUsingMetaAdapter() {
        assertThat(jdbc.queryForObject(
                "SELECT provider_key FROM platform_accounts WHERE platform_account_uuid=?",
                String.class, Stage7C1AccountInitializer.TEST_UUID)).isEqualTo("FAKE_GOOGLE");
        int metaBefore = meta.invocationCount();
        int googleBefore = google.invocationCount();
        UUID operationUuid = UUID.randomUUID();
        UUID requestUuid = UUID.randomUUID();
        var command = createCampaign(operationUuid, requestUuid);
        var context = contexts.forCurrentActor("stage7c1-" + operationUuid);
        var created = new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.update("""
                    INSERT INTO platform_operation_batches(
                      operation_batch_uuid,operation_uuid,platform_account_uuid,client_request_uuid,
                      requested_actor_type,requested_actor_id,expected_entity_version,currency,
                      business_date,reserved_amount,created_at,version)
                    VALUES (?,?,?,?, 'LOCAL_ADMIN','local-admin',NULL,'TWD',CURRENT_DATE,0,CURRENT_TIMESTAMP,0)
                    """, UUID.randomUUID(), operationUuid, Stage7C1AccountInitializer.TEST_UUID, requestUuid);
            return service.create(command, context);
        });
        var completed = service.submit(operationUuid, created.getVersion());

        assertThat(completed.status()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
        assertThat(completed.externalId().orElseThrow()).startsWith("fake-google-campaign-");
        assertThat(completed.outcomeEvidence().orElseThrow().providerKey()).isEqualTo(ProviderKey.FAKE_GOOGLE);
        assertThat(completed.outcomeEvidence().orElseThrow().observedState()).contains(PlatformObservedState.PAUSED);
        assertThat(jdbc.queryForObject(
                "SELECT outcome_evidence->>'providerKey' FROM platform_operations WHERE operation_uuid=?",
                String.class, operationUuid)).isEqualTo("FAKE_GOOGLE");
        assertThat(jdbc.queryForObject(
                "SELECT observed_state FROM platform_campaigns WHERE platform_campaign_uuid=?",
                String.class, created.getEntityUuid())).isEqualTo("PAUSED");
        assertThat(google.invocationCount()).isGreaterThan(googleBefore);
        assertThat(meta.invocationCount()).isEqualTo(metaBefore);
        assertThat(google.transactionObserved()).isFalse();
    }

    private CreatePlatformOperationCommand createCampaign(UUID operationUuid, UUID requestUuid) {
        UUID campaignUuid = UUID.randomUUID();
        UUID platformCampaignUuid = UUID.randomUUID();
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) VALUES (?,?,'ACTIVE',0)",
                campaignUuid, "Stage7C1 " + campaignUuid);
        String payload = """
                {"schemaVersion":1,"operationType":"CREATE_CAMPAIGN","entityType":"CAMPAIGN",
                 "entityUuid":"%s","platformCampaignUuid":"%s","campaignUuid":"%s",
                 "objective":"OUTCOME_SALES","desiredState":"PAUSED","accountTimezone":"Asia/Taipei"}
                """.formatted(platformCampaignUuid, platformCampaignUuid, campaignUuid);
        return new CreatePlatformOperationCommand(operationUuid, Stage7C1AccountInitializer.TEST_UUID,
                PlatformOperationType.CREATE_CAMPAIGN, PlatformEntityType.CAMPAIGN, platformCampaignUuid,
                requestUuid, payload, 3);
    }
}
