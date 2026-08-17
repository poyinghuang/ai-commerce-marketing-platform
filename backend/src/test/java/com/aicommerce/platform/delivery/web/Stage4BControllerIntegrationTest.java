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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class Stage4BControllerIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager transactionManager;
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
    @Test void allFiveTransactionACommandsPersistExactOrderedAuditAndTypedBudgetChanges() throws Exception {
        UUID campaignRequest=UUID.randomUUID();
        String campaign=createCampaign(plan,campaignRequest);UUID campaignOperation=operation(campaignRequest);
        UUID adSetRequest=UUID.randomUUID();String adSet=createAdSet(campaign,"25",adSetRequest);UUID adSetOperation=operation(adSetRequest);
        String campaignEtag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");UUID stateRequest=UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/resume").header("If-Match",campaignEtag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+stateRequest+"\",\"targetDesiredState\":\"ACTIVE\"}")) .andExpect(status().isAccepted());UUID stateOperation=operation(stateRequest);
        String adSetEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andReturn().getResponse().getHeader("ETag");UUID increaseRequest=UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",adSetEtag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+increaseRequest+"\",\"newBudgetAmount\":\"30\"}")) .andExpect(status().isAccepted());UUID increaseOperation=operation(increaseRequest);
        adSetEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andReturn().getResponse().getHeader("ETag");UUID decreaseRequest=UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",adSetEtag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+decreaseRequest+"\",\"newBudgetAmount\":\"20\"}")) .andExpect(status().isAccepted());UUID decreaseOperation=operation(decreaseRequest);

        assertThat(subjects(campaignOperation)).containsExactly("PLATFORM_CAMPAIGN","PLATFORM_OPERATION_BATCH","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_CAMPAIGN");
        assertThat(subjects(adSetOperation)).containsExactly("PLATFORM_AD_SET","PLATFORM_OPERATION_BATCH","PLATFORM_BUDGET_RESERVATION","PLATFORM_ACCOUNT_BUDGET_DAY","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_AD_SET");
        assertThat(subjects(stateOperation)).containsExactly("PLATFORM_OPERATION_BATCH","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_CAMPAIGN");
        assertThat(subjects(increaseOperation)).containsExactly("PLATFORM_OPERATION_BATCH","PLATFORM_BUDGET_RESERVATION","PLATFORM_ACCOUNT_BUDGET_DAY","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_AD_SET");
        assertThat(subjects(decreaseOperation)).containsExactly("PLATFORM_OPERATION_BATCH","PLATFORM_BUDGET_RESERVATION","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_OPERATION_ATTEMPT","PLATFORM_OPERATION","PLATFORM_AD_SET");
        assertThat(changes(adSetOperation,"PLATFORM_OPERATION_BATCH")).containsExactly("operationBatchUuid:UUID","budgetReservationUuid:UUID","accountBudgetDayUuid:UUID","businessDate:DATE","reservationKind:ENUM","currency:STRING","budgetAmount:DECIMAL","reservedAmount:DECIMAL");
        assertThat(changes(adSetOperation,"PLATFORM_BUDGET_RESERVATION")).containsExactly("operationBatchUuid:UUID","budgetReservationUuid:UUID","accountBudgetDayUuid:UUID","businessDate:DATE","reservationKind:ENUM","currency:STRING","budgetAmount:DECIMAL","reservedAmount:DECIMAL");
        assertThat(changes(adSetOperation,"PLATFORM_ACCOUNT_BUDGET_DAY")).containsExactly("operationBatchUuid:UUID","budgetReservationUuid:UUID","accountBudgetDayUuid:UUID","businessDate:DATE","reservationKind:ENUM","currency:STRING","reservedAmount:DECIMAL","aggregateReservedAmount:DECIMAL");
        assertThat(changes(increaseOperation,"PLATFORM_ACCOUNT_BUDGET_DAY")).containsExactly("operationBatchUuid:UUID","budgetReservationUuid:UUID","accountBudgetDayUuid:UUID","businessDate:DATE","reservationKind:ENUM","currency:STRING","reservedAmount:DECIMAL","aggregateReservedAmount:DECIMAL");
        assertThat(changes(decreaseOperation,"PLATFORM_BUDGET_RESERVATION")).containsExactly("operationBatchUuid:UUID","budgetReservationUuid:UUID","accountBudgetDayUuid:UUID","businessDate:DATE","reservationKind:ENUM","currency:STRING","budgetAmount:DECIMAL","reservedAmount:DECIMAL");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid in (?,?,?,?,?) and lower(coalesce(c.old_value,'')||coalesce(c.new_value,'')) ~ '(secret|token|credential|authorization|cookie|https?://)'",Integer.class,campaignOperation,adSetOperation,stateOperation,increaseOperation,decreaseOperation)).isZero();
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
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":null,\"campaignUuid\":\""+plan+"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("clientRequestUuid")).andExpect(jsonPath("$.fieldErrors[0].message").value("Invalid value"));
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content(valid.toUpperCase())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content(valid.substring(0,valid.length()-1)+",\"secr\u0065tToken\":\"e\u0301\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/00000000-0000-4000-8000-000000000001/resume").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"targetDesiredState\":\"ACTIVE\"}"))
            .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match")).andExpect(jsonPath("$.fieldErrors[0].message").value("Invalid If-Match"));
    }

    @Test void stateReplayBindsEntityUuidAndTypeWithZeroSideEffects() throws Exception {
        String campaignOne=createCampaign(plan);UUID otherPlan=UUID.randomUUID();jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) VALUES (?,'Other',?,?,'OUTCOME_SALES','META',100,300,'TWD')",otherPlan,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));String campaignTwo=createCampaign(otherPlan);
        String etagOne=mvc.perform(get("/api/platforms/meta/campaigns/"+campaignOne)).andReturn().getResponse().getHeader("ETag");UUID replayRequest=UUID.randomUUID();String stateBody="{\"clientRequestUuid\":\""+replayRequest+"\",\"targetDesiredState\":\"ACTIVE\"}";
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaignOne+"/resume").header("If-Match",etagOne).contentType(MediaType.APPLICATION_JSON).content(stateBody)).andExpect(status().isAccepted());
        int operations=count("platform_operations"),batches=count("platform_operation_batches"),audits=count("audit_logs"),attempts=count("platform_operation_attempts");
        String etagTwo=mvc.perform(get("/api/platforms/meta/campaigns/"+campaignTwo)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaignTwo+"/resume").header("If-Match",etagTwo).contentType(MediaType.APPLICATION_JSON).content(stateBody)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_IDEMPOTENCY_CONFLICT"));
        jdbc.update("INSERT INTO platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key,desired_state,schedule_start,schedule_end) SELECT platform_campaign_uuid,platform_campaign_uuid,platform_account_uuid,'DAILY',10,'TWD','Asia/Taipei','OFFSITE_CONVERSIONS','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1','PAUSED',schedule_start,schedule_end FROM platform_campaigns WHERE platform_campaign_uuid=?",UUID.fromString(campaignOne));
        mvc.perform(post("/api/platforms/meta/ad-sets/"+campaignOne+"/resume").header("If-Match","W/\"0\"").contentType(MediaType.APPLICATION_JSON).content(stateBody)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_IDEMPOTENCY_CONFLICT"));
        assertThat(count("platform_operations")).isEqualTo(operations);assertThat(count("platform_operation_batches")).isEqualTo(batches);assertThat(count("audit_logs")).isEqualTo(audits);assertThat(count("platform_operation_attempts")).isEqualTo(attempts);
    }

    @Test void budgetReplayBindsAdSetUuidWithZeroSideEffects() throws Exception {
        String campaign=createCampaign(plan),first=createAdSet(campaign,"10"),second=createAdSet(campaign,"10");UUID request=UUID.randomUUID();String body="{\"clientRequestUuid\":\""+request+"\",\"newBudgetAmount\":\"20\"}";
        String firstEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+first)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ad-sets/"+first+"/budget").header("If-Match",firstEtag).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        int operations=count("platform_operations"),batches=count("platform_operation_batches"),reservations=count("platform_budget_reservations"),audits=count("audit_logs"),attempts=count("platform_operation_attempts");
        String secondEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+second)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ad-sets/"+second+"/budget").header("If-Match",secondEtag).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_IDEMPOTENCY_CONFLICT"));
        assertThat(count("platform_operations")).isEqualTo(operations);assertThat(count("platform_operation_batches")).isEqualTo(batches);assertThat(count("platform_budget_reservations")).isEqualTo(reservations);assertThat(count("audit_logs")).isEqualTo(audits);assertThat(count("platform_operation_attempts")).isEqualTo(attempts);
    }

    @Test void fixedAccountResolutionPrecedesPlanAndScopesOperationActions() throws Exception {
        String campaign=createCampaign(plan);UUID operation=jdbc.queryForObject("SELECT operation_uuid FROM platform_operations WHERE platform_campaign_uuid=?",UUID.class,UUID.fromString(campaign));
        int attempts=count("platform_operation_attempts"),audits=count("audit_logs");
        UUID fixed=UUID.fromString("00000000-0000-4000-8000-00000000005b");new TransactionTemplate(transactionManager).executeWithoutResult(txStatus->{try{
            jdbc.update("UPDATE platform_accounts SET lifecycle_status='ARCHIVED',archived_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE platform_account_uuid=?",fixed);
            mvc.perform(get("/api/platform-operations/"+operation)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PLATFORM_ACCOUNT_CONFIGURATION_INVALID"));
            mvc.perform(post("/api/platform-operations/"+operation+"/retry").header("If-Match","W/\"0\"")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PLATFORM_ACCOUNT_CONFIGURATION_INVALID"));
            mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PLATFORM_ACCOUNT_CONFIGURATION_INVALID"));
            assertThat(count("platform_operation_attempts")).isEqualTo(attempts);assertThat(count("audit_logs")).isEqualTo(audits);txStatus.setRollbackOnly();
        }catch(Exception exception){throw new RuntimeException(exception);}});
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
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private UUID operation(UUID request){return jdbc.queryForObject("select operation_uuid from platform_operations where client_request_uuid=?",UUID.class,request);}
    private java.util.List<String> subjects(UUID operation){return jdbc.queryForList("select entity_type from audit_logs where operation_uuid=? order by ctid",String.class,operation);}
    private java.util.List<String> changes(UUID operation,String subject){return jdbc.queryForList("select c.field_name||':'||c.value_type from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and l.entity_type=? order by c.change_order",String.class,operation,subject);}
    private String createCampaign(UUID campaignPlan) throws Exception {String json=mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+campaignPlan+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createCampaign(UUID campaignPlan,UUID request) throws Exception {String json=mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+request+"\",\"campaignUuid\":\""+campaignPlan+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createAdSet(String campaign,String amount) throws Exception {String etag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");String json=mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\""+amount+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createAdSet(String campaign,String amount,UUID request) throws Exception {String etag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");String json=mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+request+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\""+amount+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
}
