package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformReadAdapter;
import com.aicommerce.platform.delivery.infrastructure.provider.LiveMetaInsightsReadAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "platform.stage8.insights.live=true",
        "META_TEST_ACCESS_TOKEN=test-token-not-for-graph",
        "META_TEST_CAMPAIGN_ID=1234567890",
        "META_TEST_ADSET_ID=2345678901"
})
class Stage8CLiveInsightsIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformDeliveryReadPort deliveryReadPort;
    @Autowired LiveMetaInsightsReadAdapter liveAdapter;
    @Autowired ObjectProvider<DeterministicFakePlatformReadAdapter> fakeRead;
    @Autowired DeterministicFakePlatformAdapter fakeWrite;
    @Autowired PlatformOperationService operations;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired Stage4DService stage4d;
    @Autowired Stage4BTransactions stage4b;

    @Test
    void liveReadIsPrimaryFakeReadIsAbsentAndWritesStayOnFake() {
        assertThat(deliveryReadPort).isSameAs(liveAdapter);
        assertThat(fakeRead.getIfAvailable()).isNull();
        assertThat(fakeWrite).isNotNull();
        assertThat(stage4b.account()).isNotEqualTo(Stage8CAccountInitializer.TEST_UUID);
    }

    @Test
    void initializerSeedsMetaAccountWithoutInventingIdsWhenEnvPresent() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='META'
                  AND environment='TEST' AND account_reference='stage8c-meta-test'
                  AND currency='TWD' AND timezone='Asia/Taipei' AND lifecycle_status='ACTIVE'
                """, Integer.class, Stage8CAccountInitializer.TEST_UUID)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT external_id FROM platform_campaigns WHERE platform_campaign_uuid=?",
                String.class, Stage8CAccountInitializer.TEST_CAMPAIGN_UUID)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT external_id FROM platform_ad_sets WHERE platform_ad_set_uuid=?",
                String.class, Stage8CAccountInitializer.TEST_AD_SET_UUID)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform_ads WHERE platform_account_uuid=?",
                Integer.class, Stage8CAccountInitializer.TEST_UUID)).isZero();
    }

    @Test
    void getDeliveryReadsPostgresOnlyForTheMetaCampaign() throws Exception {
        var view = stage4d.delivery(PlatformEntityType.CAMPAIGN, Stage8CAccountInitializer.TEST_CAMPAIGN_UUID);
        assertThat(view.desiredState().name()).isEqualTo("PAUSED");

        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + Stage8CAccountInitializer.TEST_CAMPAIGN_UUID
                        + "/delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("PAUSED"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform_operations WHERE platform_account_uuid=?",
                Integer.class, Stage8CAccountInitializer.TEST_UUID)).isZero();
    }

    @Test
    void createOperationAgainstMetaAccountIsRejectedAndInsertsNothing() {
        UUID campaign = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        String payload = "{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\","
                + "\"entityUuid\":\"" + campaign + "\",\"platformCampaignUuid\":\"" + campaign
                + "\",\"campaignUuid\":\"" + Stage8CAccountInitializer.TEST_PLAN_UUID
                + "\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
        assertThatThrownBy(() -> operations.create(new CreatePlatformOperationCommand(
                        operation, Stage8CAccountInitializer.TEST_UUID, PlatformOperationType.CREATE_CAMPAIGN,
                        PlatformEntityType.CAMPAIGN, campaign, UUID.randomUUID(), payload, 3),
                contexts.forCurrentActor("stage8c-meta-reject-" + operation)))
                .isInstanceOfSatisfying(PlatformOperationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(PlatformStableErrorCode.PLATFORM_PROVIDER_UNSUPPORTED));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform_operations WHERE operation_uuid=?",
                Integer.class, operation)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform_operations WHERE platform_account_uuid=?",
                Integer.class, Stage8CAccountInitializer.TEST_UUID)).isZero();
    }
}
