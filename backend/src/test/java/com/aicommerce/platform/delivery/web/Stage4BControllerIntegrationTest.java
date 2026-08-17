package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class Stage4BControllerIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    UUID plan;
    @BeforeEach void fixture(){
        plan=UUID.randomUUID();jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
          VALUES (?,'Stage 4B',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
          """,plan,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));
    }
    @Test void campaignAndAdSetCreateUsePausedFakeOperationsAndLedger() throws Exception {
        int batchAudit=auditCount("PLATFORM_OPERATION_BATCH"),reservationAudit=auditCount("PLATFORM_BUDGET_RESERVATION"),dayAudit=auditCount("PLATFORM_ACCOUNT_BUDGET_DAY");
        UUID campaignRequest=UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\""+campaignRequest+"\",\"campaignUuid\":\""+plan+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.desiredState").value("PAUSED"));
        String campaignJson=mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\""+campaignRequest+"\",\"campaignUuid\":\""+plan+"\",\"expectedCampaignPlanVersion\":0}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andReturn().getResponse().getContentAsString();
        String campaign=campaignJson.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");
        var get=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andExpect(status().isOk())
            .andExpect(jsonPath("$.desiredState").value("PAUSED")).andReturn().getResponse();
        UUID adRequest=UUID.randomUUID();
        String adSetJson=mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",get.getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+adRequest+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"50\",\"expectedCampaignPlanVersion\":0}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andReturn().getResponse().getContentAsString();
        String adSet=adSetJson.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");
        var adSetGet=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/resume").header("If-Match",adSetGet.getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"targetDesiredState\":\"ACTIVE\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        adSetGet=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andExpect(status().isOk()).andExpect(jsonPath("$.desiredState").value("ACTIVE")).andReturn().getResponse();
        mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",adSetGet.getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"newBudgetAmount\":\"60\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        adSetGet=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andExpect(status().isOk()).andExpect(jsonPath("$.budgetAmount").value("60")).andReturn().getResponse();
        mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",adSetGet.getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"newBudgetAmount\":\"40\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days",java.math.BigDecimal.class))
            .isEqualByComparingTo(jdbc.queryForObject("SELECT sum(reserved_amount) FROM platform_budget_reservations",java.math.BigDecimal.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations",Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(auditCount("PLATFORM_OPERATION_BATCH")-batchAudit).isEqualTo(5);
        assertThat(auditCount("PLATFORM_BUDGET_RESERVATION")-reservationAudit).isEqualTo(3);
        assertThat(auditCount("PLATFORM_ACCOUNT_BUDGET_DAY")-dayAudit).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes c JOIN audit_logs l ON l.audit_uuid=c.audit_uuid WHERE l.entity_type LIKE 'PLATFORM_%' AND (lower(coalesce(c.old_value,'')) LIKE '%secret%' OR lower(coalesce(c.new_value,'')) LIKE '%secret%')",Integer.class)).isZero();
    }
    @Test void replayReturnsExistingOperationWithoutCapacityChange() throws Exception {
        UUID request=UUID.randomUUID();String body="{\"clientRequestUuid\":\""+request+"\",\"campaignUuid\":\""+plan+"\",\"expectedCampaignPlanVersion\":0}";
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE client_request_uuid=?",Integer.class,request)).isEqualTo(1);
    }
    @Test void adSetReplayPreservesOriginalParentVersionAndChangedIntentHasZeroSideEffects() throws Exception {
        UUID campaignRequest=UUID.randomUUID();String campaignJson=mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+campaignRequest+"\",\"campaignUuid\":\""+plan+"\",\"expectedCampaignPlanVersion\":0}"))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String campaign=campaignJson.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");
        var original=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse();
        UUID request=UUID.randomUUID();String body="{\"clientRequestUuid\":\""+request+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"50\",\"expectedCampaignPlanVersion\":0}";
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",original.getHeader("ETag")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/resume").header("If-Match",original.getHeader("ETag")).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"targetDesiredState\":\"ACTIVE\"}")).andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",original.getHeader("ETag")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        int operations=jdbc.queryForObject("SELECT count(*) FROM platform_operations",Integer.class),batches=jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches",Integer.class),reservations=jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations",Integer.class),audit=jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class);
        var current=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse();
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",current.getHeader("ETag")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_IDEMPOTENCY_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operations",Integer.class)).isEqualTo(operations);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches",Integer.class)).isEqualTo(batches);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations",Integer.class)).isEqualTo(reservations);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)).isEqualTo(audit);
    }
    @Test void strictBoundaryRejectsOversizeQueryContentTypeDuplicateUnknownNullUppercaseAndNonNfc() throws Exception {
        String valid="{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+plan+"\"}";
        mvc.perform(post("/api/platforms/meta/campaigns/preview?account=x").contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.TEXT_PLAIN).content(valid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content("x".repeat(16385))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+plan+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content(valid.substring(0,valid.length()-1)+",\"unknown\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":null,\"campaignUuid\":\""+plan+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content(valid.toUpperCase())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content(valid.substring(0,valid.length()-1)+",\"secr\u0065tToken\":\"e\u0301\"}")).andExpect(status().isBadRequest());
    }
    @Test @Transactional void wrongAccountEntityIsNotDisclosedAndAmbiguousFixedAccountFailsClosed() throws Exception {
        UUID account=UUID.randomUUID(),foreignPlan=UUID.randomUUID(),foreignCampaign=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','LOCAL',?,?, 'TWD','Asia/Taipei')",account,"foreign-"+account,account.toString().replace("-","").repeat(2));
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name) VALUES (?,'Foreign')",foreignPlan);
        jdbc.update("INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) VALUES (?,?,?,'OUTCOME_SALES','Asia/Taipei')",foreignCampaign,foreignPlan,account);
        mvc.perform(get("/api/platforms/meta/campaigns/"+foreignCampaign)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLATFORM_RESOURCE_NOT_FOUND"));
        jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','LOCAL','stage4b-test',?, 'TWD','Asia/Taipei')",UUID.randomUUID(),"9".repeat(64));
        mvc.perform(get("/api/platforms/meta/campaigns/"+foreignCampaign)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PLATFORM_ACCOUNT_CONFIGURATION_INVALID"));
    }
    private int auditCount(String subject){return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type=?",Integer.class,subject);}
}
