package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.Stage4BAccountInitializer;
import com.aicommerce.platform.delivery.application.Stage7C1AccountInitializer;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakeGooglePlatformAdapter;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage7C2ControllerIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DeterministicFakeGooglePlatformAdapter google;
    @Autowired DeterministicFakePlatformAdapter meta;

    @BeforeEach void fixture() {
        google.useScenario(DeterministicFakeGooglePlatformAdapter.Scenario.SUCCESS);
        meta.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
        jdbc.execute("TRUNCATE platform_budget_reservations, platform_operation_batches, platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs RESTART IDENTITY CASCADE");
    }

    @Test void googlePreviewConfirmUsesFakeGoogleAccountAndLeavesMetaUntouched() throws Exception {
        UUID googlePlan = plan("Google 7C-2");
        UUID metaPlan = plan("Meta control");
        int googleBefore = google.invocationCount();
        int metaBefore = meta.invocationCount();

        mvc.perform(post("/api/platforms/google/campaigns/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + googlePlan + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("PAUSED"))
                .andExpect(jsonPath("$.warnings").isArray());

        String googleJson = mvc.perform(post("/api/platforms/google/campaigns").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + googlePlan + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn().getResponse().getContentAsString();
        String googleCampaign = googleJson.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String googleOperation = googleJson.replaceAll(".*\\\"operationUuid\\\":\\\"([^\\\"]+)\\\".*", "$1");

        assertThat(jdbc.queryForObject("SELECT platform_account_uuid FROM platform_campaigns WHERE platform_campaign_uuid=?",
                UUID.class, UUID.fromString(googleCampaign))).isEqualTo(Stage7C1AccountInitializer.TEST_UUID);
        assertThat(jdbc.queryForObject("SELECT outcome_evidence->>'providerKey' FROM platform_operations WHERE operation_uuid=?",
                String.class, UUID.fromString(googleOperation))).isEqualTo("FAKE_GOOGLE");
        assertThat(jdbc.queryForObject("SELECT observed_state FROM platform_campaigns WHERE platform_campaign_uuid=?",
                String.class, UUID.fromString(googleCampaign))).isEqualTo("PAUSED");
        assertThat(google.invocationCount()).isGreaterThan(googleBefore);
        assertThat(meta.invocationCount()).isEqualTo(metaBefore);

        String metaJson = mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + metaPlan + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String metaCampaign = metaJson.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(jdbc.queryForObject("SELECT platform_account_uuid FROM platform_campaigns WHERE platform_campaign_uuid=?",
                UUID.class, UUID.fromString(metaCampaign))).isEqualTo(Stage4BAccountInitializer.TEST_UUID);

        mvc.perform(get("/api/platforms/google/campaigns/" + googleCampaign)).andExpect(status().isOk());
        mvc.perform(get("/api/platforms/google/operations/" + googleOperation)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operationUuid").value(googleOperation));
        mvc.perform(get("/api/platforms/meta/campaigns/" + googleCampaign)).andExpect(status().isNotFound());
        mvc.perform(get("/api/platforms/google/campaigns/" + metaCampaign)).andExpect(status().isNotFound());
        mvc.perform(post("/api/platforms/google/campaigns/preview?account=forbidden").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"));
    }

    private UUID plan(String name) {
        UUID value = UUID.randomUUID();
        jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
          VALUES (?,?,?,?,'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
          """, value, name, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        return value;
    }
}
