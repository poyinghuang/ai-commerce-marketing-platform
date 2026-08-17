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
            .isEqualByComparingTo("60");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations",Integer.class)).isEqualTo(3);
    }
    @Test void replayReturnsExistingOperationWithoutCapacityChange() throws Exception {
        UUID request=UUID.randomUUID();String body="{\"clientRequestUuid\":\""+request+"\",\"campaignUuid\":\""+plan+"\",\"expectedCampaignPlanVersion\":0}";
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE client_request_uuid=?",Integer.class,request)).isEqualTo(1);
    }
}
