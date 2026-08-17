package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PlatformOperationAuditRollbackIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired PlatformOperationService service;
    @Autowired PlatformOperationTransactions transactions;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PlatformAuditWriter audit;

    @Test
    void auditFailureRollsBackAttemptOperationAndEntityFinalization() {
        UUID account=UUID.randomUUID(),operation=UUID.randomUUID(),campaignPlan=UUID.randomUUID(),platformCampaign=UUID.randomUUID();
        jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"audit-"+account,account.toString().replace("-","").repeat(2));
        jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) values (?,?,'ACTIVE',0)",campaignPlan,"Audit rollback");
        String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+platformCampaign+"\",\"platformCampaignUuid\":\""+platformCampaign+"\",\"campaignUuid\":\""+campaignPlan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
        var context=contexts.forCurrentActor("audit-rollback-"+operation);
        var created=service.create(new CreatePlatformOperationCommand(operation,account,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,platformCampaign,UUID.randomUUID(),payload,3),context);
        transactions.claimSubmit(operation,created.getVersion(),Instant.now(),context);
        doThrow(new IllegalStateException("synthetic audit failure")).when(audit).write(any(),any());

        assertThatThrownBy(()->transactions.recordWriteOutcome(operation,new com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter().submitCampaign(
                new com.aicommerce.platform.delivery.application.port.PlatformCampaignCommand(
                        new com.aicommerce.platform.delivery.application.port.PlatformCommandIdentity(operation,account,
                                jdbc.queryForObject("select idempotency_key from platform_operations where operation_uuid=?",String.class,operation),
                                jdbc.queryForObject("select request_sha256 from platform_operations where operation_uuid=?",String.class,operation)),
                        platformCampaign,campaignPlan,com.aicommerce.platform.delivery.domain.PlatformObjective.OUTCOME_SALES,
                        com.aicommerce.platform.delivery.domain.PlatformDesiredState.PAUSED,java.util.Optional.empty(),java.util.Optional.empty(),"Asia/Taipei")),context))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select status from platform_operations where operation_uuid=?",String.class,operation)).isEqualTo("SUBMITTING");
        assertThat(jdbc.queryForObject("select status from platform_operation_attempts where operation_uuid=?",String.class,operation)).isEqualTo("STARTED");
        assertThat(jdbc.queryForObject("select external_id from platform_campaigns where platform_campaign_uuid=?",String.class,platformCampaign)).isNull();
    }
}
