package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.aicommerce.platform.delivery.application.Stage4BService;
import com.aicommerce.platform.delivery.application.Stage4BLedgerCriticalSectionHook;
import com.aicommerce.platform.delivery.application.Stage4BUuidSource;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import tools.jackson.databind.ObjectMapper;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class Stage4BControllerIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager transactionManager; @Autowired ObjectMapper mapper;
    @MockitoSpyBean Stage4BService stage4BService; @MockitoSpyBean Stage4BLedgerCriticalSectionHook ledgerHook;
    @Autowired DeterministicFakePlatformAdapter fake; @Autowired Stage4BUuidSource uuids;
    UUID plan;
    @BeforeEach void fixture(){
        reset(ledgerHook);fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);jdbc.execute("TRUNCATE platform_budget_reservations, platform_operation_batches, platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs RESTART IDENTITY CASCADE");
        plan=UUID.randomUUID();jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
          VALUES (?,'Stage 4B',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
          """,plan,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));
    }
    @ParameterizedTest
    @EnumSource(value=DeterministicFakePlatformAdapter.Scenario.class,names={"RETRYABLE_RATE_LIMIT","RETRYABLE_TEMPORARILY_UNAVAILABLE"})
    void retryableFakeCrossesEveryApplicableWriteRouteAndPersistsSafeEvidence(DeterministicFakePlatformAdapter.Scenario scenario) throws Exception {
        String expected=scenario==DeterministicFakePlatformAdapter.Scenario.RETRYABLE_RATE_LIMIT?"PLATFORM_RATE_LIMITED":"PLATFORM_TEMPORARILY_UNAVAILABLE";
        String campaign=createCampaign(plan),adSet=createAdSet(campaign,"10");fake.useScenario(scenario);

        UUID campaignCreate=UUID.randomUUID(),failedPlan=newPlan(),campaignEntity=uuids.request(campaignCreate,"campaign-entity");
        Instant lower=dbNow();assertRetryable(mvc.perform(post("/api/platforms/meta/campaigns").header("X-Request-ID","rr7-campaign-create").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+campaignCreate+"\",\"campaignUuid\":\""+failedPlan+"\",\"expectedCampaignPlanVersion\":0}")),lower,campaignCreate,"rr7-campaign-create",PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,campaignEntity,expected);

        UUID adSetCreate=UUID.randomUUID(),adSetEntity=uuids.request(adSetCreate,"ad-set-entity");String campaignEtag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");
        lower=dbNow();assertRetryable(mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",campaignEtag).header("X-Request-ID","rr7-adset-create").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+adSetCreate+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"11\",\"expectedCampaignPlanVersion\":0}")),lower,adSetCreate,"rr7-adset-create",PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET,adSetEntity,expected);

        UUID campaignState=UUID.randomUUID();
        lower=dbNow();assertRetryable(mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/resume").header("If-Match",campaignEtag).header("X-Request-ID","rr7-campaign-resume").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+campaignState+"\",\"targetDesiredState\":\"ACTIVE\"}")),lower,campaignState,"rr7-campaign-resume",PlatformOperationType.RESUME,PlatformEntityType.CAMPAIGN,UUID.fromString(campaign),expected);

        String adSetEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andReturn().getResponse().getHeader("ETag");UUID adSetState=UUID.randomUUID();
        lower=dbNow();assertRetryable(mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/resume").header("If-Match",adSetEtag).header("X-Request-ID","rr7-adset-resume").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+adSetState+"\",\"targetDesiredState\":\"ACTIVE\"}")),lower,adSetState,"rr7-adset-resume",PlatformOperationType.RESUME,PlatformEntityType.AD_SET,UUID.fromString(adSet),expected);

        UUID budget=UUID.randomUUID();
        lower=dbNow();assertRetryable(mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",adSetEtag).header("X-Request-ID","rr7-budget").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+budget+"\",\"newBudgetAmount\":\"12\"}")),lower,budget,"rr7-budget",PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,UUID.fromString(adSet),expected);

        // Reconcile has no retryable provider outcome in the approved Stage 4A contract. Retry-route
        // serialization remains covered by the route matrix; due eligibility is DB-server-time owned.
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
        String campaign=createCampaign(plan,campaignRequest,"audit-campaign-create");UUID campaignOperation=operation(campaignRequest);
        UUID adSetRequest=UUID.randomUUID();String adSet=createAdSet(campaign,"25",adSetRequest,"audit-adset-create");UUID adSetOperation=operation(adSetRequest);
        String campaignEtag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");UUID stateRequest=UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/resume").header("If-Match",campaignEtag).header("X-Request-ID","audit-campaign-state").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+stateRequest+"\",\"targetDesiredState\":\"ACTIVE\"}")) .andExpect(status().isAccepted());UUID stateOperation=operation(stateRequest);
        String adSetEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andReturn().getResponse().getHeader("ETag");UUID increaseRequest=UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",adSetEtag).header("X-Request-ID","audit-budget-increase").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+increaseRequest+"\",\"newBudgetAmount\":\"30\"}")) .andExpect(status().isAccepted());UUID increaseOperation=operation(increaseRequest);
        adSetEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andReturn().getResponse().getHeader("ETag");UUID decreaseRequest=UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",adSetEtag).header("X-Request-ID","audit-budget-decrease").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+decreaseRequest+"\",\"newBudgetAmount\":\"20\"}")) .andExpect(status().isAccepted());UUID decreaseOperation=operation(decreaseRequest);

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
        assertBudgetAudit(adSetOperation,"INITIAL",null,"25","25",true);
        assertBudgetAudit(increaseOperation,"INCREASE","25","30","5",true);
        assertBudgetAudit(decreaseOperation,"DECREASE_NO_RELEASE","30","20","0",false);
        assertBatchAudit(campaignOperation,"0",false);assertBatchAudit(stateOperation,"0",false);
        assertExactAudit(campaignOperation,"audit-campaign-create",AuditShape.CAMPAIGN_CREATE);assertExactAudit(adSetOperation,"audit-adset-create",AuditShape.AD_SET_CREATE);
        assertExactAudit(stateOperation,"audit-campaign-state",AuditShape.CAMPAIGN_STATE);assertExactAudit(increaseOperation,"audit-budget-increase",AuditShape.BUDGET_INCREASE);assertExactAudit(decreaseOperation,"audit-budget-decrease",AuditShape.BUDGET_DECREASE);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid in (?,?,?,?,?) and actor_type='LOCAL_ADMIN' and actor_id='local-admin' and source='API' and request_id ~ '^[A-Za-z0-9._:-]{1,128}$'",Integer.class,campaignOperation,adSetOperation,stateOperation,increaseOperation,decreaseOperation)).isEqualTo(subjects(campaignOperation).size()+subjects(adSetOperation).size()+subjects(stateOperation).size()+subjects(increaseOperation).size()+subjects(decreaseOperation).size());
        assertThat(jdbc.queryForObject("select count(*) from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid in (?,?,?,?,?) and lower(coalesce(c.old_value,'')||coalesce(c.new_value,'')) ~ '(secret|token|credential|authorization|cookie|https?://)'",Integer.class,campaignOperation,adSetOperation,stateOperation,increaseOperation,decreaseOperation)).isZero();
    }
    @Test void replayReturnsExistingOperationWithoutCapacityChange() throws Exception {
        UUID request=UUID.randomUUID();String body="{\"clientRequestUuid\":\""+request+"\",\"campaignUuid\":\""+plan+"\",\"expectedCampaignPlanVersion\":0}";
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        int audit=count("audit_logs"),changes=count("audit_log_changes");String auditBefore=auditSnapshot();
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE client_request_uuid=?",Integer.class,request)).isEqualTo(1);
        assertThat(count("audit_logs")).isEqualTo(audit);assertThat(count("audit_log_changes")).isEqualTo(changes);assertThat(auditSnapshot()).isEqualTo(auditBefore);
    }
    @Test void invalidStaleAndCapFailuresPreserveByteEquivalentAuditGraph() throws Exception {
        String before=auditSnapshot();
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+plan+"\",\"budgetAmount\":\"1e1\"}"))
            .andExpect(status().isBadRequest());assertThat(auditSnapshot()).isEqualTo(before);
        String staleCampaign=createCampaign(plan),oldEtag=mvc.perform(get("/api/platforms/meta/campaigns/"+staleCampaign)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/campaigns/"+staleCampaign+"/resume").header("If-Match",oldEtag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"targetDesiredState\":\"ACTIVE\"}")) .andExpect(status().isAccepted());
        before=auditSnapshot();mvc.perform(post("/api/platforms/meta/campaigns/"+staleCampaign+"/pause").header("If-Match",oldEtag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"targetDesiredState\":\"PAUSED\"}"))
            .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("PLATFORM_ENTITY_STALE"));assertThat(auditSnapshot()).isEqualTo(before);
        for(int i=0;i<3;i++){String c=createCampaign(newPlan());createAdSet(c,"300",PlatformBudgetType.LIFETIME);}String finalCampaign=createCampaign(newPlan());createAdSet(finalCampaign,"100",PlatformBudgetType.DAILY);
        String cappedCampaign=createCampaign(newPlan()),etag=mvc.perform(get("/api/platforms/meta/campaigns/"+cappedCampaign)).andReturn().getResponse().getHeader("ETag");before=auditSnapshot();
        mvc.perform(post("/api/platforms/meta/campaigns/"+cappedCampaign+"/ad-sets").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"1\",\"expectedCampaignPlanVersion\":0}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_BUDGET_CAP_EXCEEDED"));assertThat(auditSnapshot()).isEqualTo(before);
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
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+plan+"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("expectedCampaignPlanVersion"));
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+plan+"\",\"expectedCampaignPlanVersion\":null}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("expectedCampaignPlanVersion"));
        String campaign=createCampaign(plan);String etag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"1.0000001\",\"expectedCampaignPlanVersion\":0}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("budgetAmount"));
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content(valid.toUpperCase())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON).content(valid.substring(0,valid.length()-1)+",\"secr\u0065tToken\":\"e\u0301\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/platforms/meta/campaigns/00000000-0000-4000-8000-000000000001/resume").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"targetDesiredState\":\"ACTIVE\"}"))
            .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match")).andExpect(jsonPath("$.fieldErrors[0].message").value("Invalid If-Match"));
    }

    @Test void canonicalMoneyBoundaryPreservesDeclaredFieldForEveryInvalidLexicalAndStructuralCase() throws Exception {
        String campaign=createCampaign(plan),adSet=createAdSet(campaign,"10");
        String[] invalid={"1e1","+1","01","1.0","1.230","1.0000001","0","-1"};
        int operations=count("platform_operations"),audits=count("audit_logs");
        for(String amount:invalid){
            mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets/preview").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\""+amount+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("budgetAmount"));
            mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget-preview").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"newBudgetAmount\":\""+amount+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newBudgetAmount"));
        }
        assertThat(count("platform_operations")).isEqualTo(operations);assertThat(count("audit_logs")).isEqualTo(audits);
    }

    @Test void canonicalPolicyAmountsUse409AtDailyAndLifetimeCreateAndUpdateBounds() throws Exception {
        for(var budgetType:java.util.List.of(PlatformBudgetType.DAILY,PlatformBudgetType.LIFETIME)){
            String boundary=budgetType==PlatformBudgetType.DAILY?"100":"300",over=budgetType==PlatformBudgetType.DAILY?"101":"301";
            String boundaryCampaign=createCampaign(newPlan()),boundaryEtag=mvc.perform(get("/api/platforms/meta/campaigns/"+boundaryCampaign)).andReturn().getResponse().getHeader("ETag");
            mvc.perform(post("/api/platforms/meta/campaigns/"+boundaryCampaign+"/ad-sets").header("If-Match",boundaryEtag).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\""+budgetType+"\",\"budgetAmount\":\""+boundary+"\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted());
            String overCampaign=createCampaign(newPlan()),overEtag=mvc.perform(get("/api/platforms/meta/campaigns/"+overCampaign)).andReturn().getResponse().getHeader("ETag");
            String before=persistentSnapshot();
            mvc.perform(post("/api/platforms/meta/campaigns/"+overCampaign+"/ad-sets").header("If-Match",overEtag).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\""+budgetType+"\",\"budgetAmount\":\""+over+"\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_POLICY_REJECTED")).andExpect(jsonPath("$.fieldErrors").doesNotExist());
            assertThat(persistentSnapshot()).isEqualTo(before);

            String updateCampaign=createCampaign(newPlan()),updateAdSet=createAdSet(updateCampaign,"10",budgetType);String updateEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+updateAdSet)).andReturn().getResponse().getHeader("ETag");
            mvc.perform(post("/api/platforms/meta/ad-sets/"+updateAdSet+"/budget").header("If-Match",updateEtag).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"newBudgetAmount\":\""+boundary+"\"}"))
                .andExpect(status().isAccepted());
            updateEtag=mvc.perform(get("/api/platforms/meta/ad-sets/"+updateAdSet)).andReturn().getResponse().getHeader("ETag");before=persistentSnapshot();
            mvc.perform(post("/api/platforms/meta/ad-sets/"+updateAdSet+"/budget").header("If-Match",updateEtag).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"newBudgetAmount\":\""+over+"\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_POLICY_REJECTED")).andExpect(jsonPath("$.fieldErrors").doesNotExist());
            assertThat(persistentSnapshot()).isEqualTo(before);
        }
    }

    @ParameterizedTest @ValueSource(strings={"40001","40P01"})
    void routePropagatesCommitClassDatabaseConflictOnceWithoutRetryProviderOrPersistence(String sqlState) throws Exception {
        String campaign=createCampaign(plan),adSet=createAdSet(campaign,"10");String etag=mvc.perform(get("/api/platforms/meta/ad-sets/"+adSet)).andReturn().getResponse().getHeader("ETag");
        String before=persistentSnapshot();int providerCalls=fake.invocationCount();clearInvocations(stage4BService,ledgerHook);
        doThrow(new DataIntegrityViolationException("controlled",new SQLException("never disclose",sqlState))).when(ledgerHook).beforeAccountDayClaim();
        try{
            mvc.perform(post("/api/platforms/meta/ad-sets/"+adSet+"/budget").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"newBudgetAmount\":\"20\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_LEDGER_CONCURRENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("The budget authorization changed concurrently"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("never disclose"))));
            verify(stage4BService,times(1)).confirmBudget(eq(UUID.fromString(adSet)),any(),eq("20"),anyLong(),any());
            verify(ledgerHook,times(1)).beforeAccountDayClaim();assertThat(fake.invocationCount()).isEqualTo(providerCalls);assertThat(persistentSnapshot()).isEqualTo(before);
        } finally {reset(ledgerHook);}
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
    private String persistentSnapshot(){return jdbc.queryForObject("SELECT jsonb_build_object('operations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_uuid) FROM platform_operations t),'attempts',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_attempt_uuid) FROM platform_operation_attempts t),'batches',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_batch_uuid) FROM platform_operation_batches t),'reservations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY budget_reservation_uuid) FROM platform_budget_reservations t),'days',(SELECT jsonb_agg(to_jsonb(t) ORDER BY account_budget_day_uuid) FROM platform_account_budget_days t),'campaigns',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_campaign_uuid) FROM platform_campaigns t),'adsets',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_ad_set_uuid) FROM platform_ad_sets t),'audit',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid) FROM audit_logs t),'changes',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid,change_order) FROM audit_log_changes t))::text",String.class);}
    private String auditSnapshot(){return jdbc.queryForObject("SELECT jsonb_build_object('audit',(SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY audit_uuid),'[]') FROM audit_logs t),'changes',(SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY audit_uuid,change_order),'[]') FROM audit_log_changes t))::text",String.class);}
    private UUID operation(UUID request){return jdbc.queryForObject("select operation_uuid from platform_operations where client_request_uuid=?",UUID.class,request);}
    private void assertRetryable(ResultActions action,Instant lower,UUID request,String requestId,PlatformOperationType expectedType,PlatformEntityType expectedEntityType,UUID expectedEntity,String expectedCode) throws Exception {
        var returned=action.andExpect(status().isTooManyRequests()).andReturn();Instant upper=dbNow();UUID operation=uuids.request(request,"operation");int retrySeconds="PLATFORM_RATE_LIMITED".equals(expectedCode)?60:30;String trace="fake-trace-"+sha256(operation.toString()).substring(0,24);
        assertThat(returned.getResponse().getHeader("Location")).isEqualTo("/api/platform-operations/"+operation);assertThat(returned.getResponse().getHeader("ETag")).isEqualTo("W/\"2\"");
        var json=mapper.readTree(returned.getResponse().getContentAsString());assertThat(json.properties().stream().map(java.util.Map.Entry::getKey).toList()).containsExactlyInAnyOrder("operationUuid","operationType","entityType","entityUuid","status","attemptCount","reconciliationCount","maxAttempts","normalizedErrorCode","nextAttemptAt","createdAt","updatedAt","version");
        assertThat(json.get("operationUuid").asText()).isEqualTo(operation.toString());assertThat(json.get("operationType").asText()).isEqualTo(expectedType.name());assertThat(json.get("entityType").asText()).isEqualTo(expectedEntityType.name());assertThat(json.get("entityUuid").asText()).isEqualTo(expectedEntity.toString());assertThat(json.get("status").asText()).isEqualTo("FAILED_RETRYABLE");assertThat(json.get("attemptCount").asInt()).isOne();assertThat(json.get("reconciliationCount").asInt()).isZero();assertThat(json.get("maxAttempts").asInt()).isEqualTo(3);assertThat(json.get("normalizedErrorCode").asText()).isEqualTo(expectedCode);assertThat(json.get("version").asLong()).isEqualTo(2);
        var row=jdbc.queryForMap("SELECT operation_uuid,platform_account_uuid,operation_type,entity_type,coalesce(platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid) entity_uuid,client_request_uuid,idempotency_key,request_payload::text request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,status,attempt_count,reconciliation_count,max_attempts,external_id,normalized_error_code,safe_provider_trace_id,outcome_evidence::text evidence,next_attempt_at,claimed_at,completed_at,created_at,updated_at,version FROM platform_operations WHERE operation_uuid=?",operation);
        assertThat(row.get("operation_uuid")).isEqualTo(operation);assertThat(row.get("platform_account_uuid")).isEqualTo(UUID.fromString("00000000-0000-4000-8000-00000000005b"));assertThat(row.get("operation_type")).isEqualTo(expectedType.name());assertThat(row.get("entity_type")).isEqualTo(expectedEntityType.name());assertThat(row.get("entity_uuid")).isEqualTo(expectedEntity);assertThat(row.get("client_request_uuid")).isEqualTo(request);assertThat(row.get("requested_actor_type")).isEqualTo("LOCAL_ADMIN");assertThat(row.get("requested_actor_id")).isEqualTo("local-admin");assertThat(row.get("request_id")).isEqualTo(requestId);assertThat(row.get("status")).isEqualTo("FAILED_RETRYABLE");assertThat(((Number)row.get("attempt_count")).intValue()).isOne();assertThat(((Number)row.get("reconciliation_count")).intValue()).isZero();assertThat(((Number)row.get("max_attempts")).intValue()).isEqualTo(3);assertThat(row.get("external_id")).isNull();assertThat(row.get("normalized_error_code")).isEqualTo(expectedCode);assertThat(row.get("safe_provider_trace_id")).isEqualTo(trace);assertThat(row.get("completed_at")).isNull();assertThat(((Number)row.get("version")).longValue()).isEqualTo(2);assertThat(row.get("idempotency_key")).isEqualTo(sha256("platform-operation-v1\n00000000-0000-4000-8000-00000000005b\nLOCAL_ADMIN\nlocal-admin\n"+request.toString().toLowerCase()));assertThat(row.get("request_sha256")).isEqualTo(canonicalSha((String)row.get("request_payload")));
        Instant created=((java.sql.Timestamp)row.get("created_at")).toInstant(),claimed=((java.sql.Timestamp)row.get("claimed_at")).toInstant(),updated=((java.sql.Timestamp)row.get("updated_at")).toInstant(),next=((java.sql.Timestamp)row.get("next_attempt_at")).toInstant();assertThat(created).isBetween(lower,upper);assertThat(claimed).isBetween(lower,upper);assertThat(updated).isBetween(lower.minusSeconds(2),upper.plusSeconds(2));assertThat(next).isEqualTo(updated.plusSeconds(retrySeconds));assertThat(json.get("createdAt").asText()).isEqualTo(created.toString());assertThat(json.get("updatedAt").asText()).isEqualTo(updated.toString());assertThat(json.get("nextAttemptAt").asText()).isEqualTo(next.toString());
        var attempt=jdbc.queryForMap("SELECT operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,safe_provider_trace_id,normalized_error_code,evidence::text evidence,started_at,completed_at,created_at,version FROM platform_operation_attempts WHERE operation_uuid=?",operation);assertThat(attempt.get("operation_attempt_uuid")).isInstanceOf(UUID.class);assertThat(attempt.get("operation_uuid")).isEqualTo(operation);assertThat(attempt.get("attempt_kind")).isEqualTo("SUBMIT");assertThat(((Number)attempt.get("attempt_number")).intValue()).isOne();assertThat(attempt.get("status")).isEqualTo("FAILED_RETRYABLE");assertThat(attempt.get("safe_provider_trace_id")).isEqualTo(trace);assertThat(attempt.get("normalized_error_code")).isEqualTo(expectedCode);assertThat(((Number)attempt.get("version")).longValue()).isOne();Instant started=((java.sql.Timestamp)attempt.get("started_at")).toInstant(),attemptCompleted=((java.sql.Timestamp)attempt.get("completed_at")).toInstant(),attemptCreated=((java.sql.Timestamp)attempt.get("created_at")).toInstant();assertThat(started).isEqualTo(claimed);assertThat(attemptCompleted).isEqualTo(updated);assertThat(attemptCreated).isBetween(lower,upper);assertThat(attempt.get("evidence")).isEqualTo(row.get("evidence"));var evidence=mapper.readTree((String)row.get("evidence"));assertThat(evidence).isEqualTo(mapper.readTree("{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"FAILED_RETRYABLE\",\"retryAfterSeconds\":"+retrySeconds+"}"));assertThat(json.has("externalId")||json.has("safeProviderTraceId")||json.has("outcomeEvidence")||json.has("claimedAt")||json.has("completedAt")).isFalse();
    }
    private Instant dbNow(){return jdbc.queryForObject("SELECT statement_timestamp()",java.sql.Timestamp.class).toInstant();}
    @SuppressWarnings("unchecked") private String canonicalSha(String json) throws Exception {var parsed=mapper.readValue(json,java.util.Map.class);return sha256(mapper.writeValueAsString(new java.util.TreeMap<String,Object>(parsed)));}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
    private java.util.List<String> subjects(UUID operation){var rows=jdbc.queryForList("select stage4b_operation_ordinal,entity_type from audit_logs where operation_uuid=? order by stage4b_operation_ordinal",operation);for(int i=0;i<rows.size();i++)assertThat(((Number)rows.get(i).get("stage4b_operation_ordinal")).intValue()).isEqualTo(i);return rows.stream().map(row->(String)row.get("entity_type")).toList();}
    private java.util.List<String> changes(UUID operation,String subject){return jdbc.queryForList("select c.field_name||':'||c.value_type from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and l.entity_type=? order by c.change_order",String.class,operation,subject);}
    private void assertExactAudit(UUID operation,String requestId,AuditShape shape){
        UUID entity=jdbc.queryForObject("select coalesce(platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid) from platform_operations where operation_uuid=?",UUID.class,operation),batch=jdbc.queryForObject("select operation_batch_uuid from platform_operation_batches where operation_uuid=?",UUID.class,operation),attempt=jdbc.queryForObject("select operation_attempt_uuid from platform_operation_attempts where operation_uuid=? and attempt_kind='SUBMIT' and attempt_number=1",UUID.class,operation);
        java.util.List<java.util.Map<String,Object>> reservationRows=jdbc.queryForList("select budget_reservation_uuid,account_budget_day_uuid,business_date from platform_budget_reservations where operation_uuid=?",operation);UUID reservation=reservationRows.isEmpty()?null:(UUID)reservationRows.getFirst().get("budget_reservation_uuid"),day=reservationRows.isEmpty()?null:(UUID)reservationRows.getFirst().get("account_budget_day_uuid");LocalDate date=jdbc.queryForObject("select business_date from platform_operation_batches where operation_uuid=?",LocalDate.class,operation);String trace="fake-trace-"+sha256(operation.toString()).substring(0,24),fingerprint=null;if(shape==AuditShape.CAMPAIGN_CREATE)fingerprint=sha256(jdbc.queryForObject("select external_id from platform_campaigns where platform_campaign_uuid=?",String.class,entity));if(shape==AuditShape.AD_SET_CREATE)fingerprint=sha256(jdbc.queryForObject("select external_id from platform_ad_sets where platform_ad_set_uuid=?",String.class,entity));
        var expected=new java.util.ArrayList<ExpectedAudit>();
        if(shape==AuditShape.CAMPAIGN_CREATE)expected.add(a("CREATE","PLATFORM_CAMPAIGN",entity,c("desiredState","ENUM",null,"PAUSED")));
        if(shape==AuditShape.AD_SET_CREATE)expected.add(a("CREATE","PLATFORM_AD_SET",entity,c("desiredState","ENUM",null,"PAUSED")));
        expected.add(a("CREATE","PLATFORM_OPERATION_BATCH",batch,batchChanges(batch,reservation,day,date,shape)));
        if(reservation!=null){expected.add(a("CREATE","PLATFORM_BUDGET_RESERVATION",reservation,reservationChanges(batch,reservation,day,date,shape)));if(shape!=AuditShape.BUDGET_DECREASE)expected.add(a("UPDATE","PLATFORM_ACCOUNT_BUDGET_DAY",day,dayChanges(batch,reservation,day,date,shape)));}
        expected.add(a("CREATE","PLATFORM_OPERATION",operation,c("operationStatus","ENUM",null,"CREATED")));
        expected.add(a("CREATE","PLATFORM_OPERATION_ATTEMPT",attempt,c("attemptKind","ENUM",null,"SUBMIT"),c("attemptNumber","INTEGER",null,"1"),c("attemptStatus","ENUM",null,"STARTED")));
        expected.add(a("UPDATE","PLATFORM_OPERATION",operation,c("operationStatus","ENUM","CREATED","SUBMITTING")));
        expected.add(a("UPDATE","PLATFORM_OPERATION_ATTEMPT",attempt,c("attemptKind","ENUM",null,"SUBMIT"),c("attemptNumber","INTEGER",null,"1"),c("attemptStatus","ENUM","STARTED","SUCCEEDED"),c("safeProviderTraceId","STRING",null,trace)));
        expected.add(a("UPDATE","PLATFORM_OPERATION",operation,c("operationStatus","ENUM","SUBMITTING","SUCCEEDED"),c("safeProviderTraceId","STRING",null,trace)));
        switch(shape){case CAMPAIGN_CREATE->expected.add(a("UPDATE","PLATFORM_CAMPAIGN",entity,c("observedState","ENUM",null,"PAUSED"),c("externalIdFingerprint","STRING",null,fingerprint)));case AD_SET_CREATE->expected.add(a("UPDATE","PLATFORM_AD_SET",entity,c("observedState","ENUM",null,"PAUSED"),c("externalIdFingerprint","STRING",null,fingerprint)));case CAMPAIGN_STATE->expected.add(a("UPDATE","PLATFORM_CAMPAIGN",entity,c("desiredState","ENUM","PAUSED","ACTIVE"),c("observedState","ENUM","PAUSED","PAUSED")));case BUDGET_INCREASE->expected.add(a("UPDATE","PLATFORM_AD_SET",entity,c("observedState","ENUM","PAUSED","PAUSED"),c("budgetAmount","DECIMAL","25","30")));case BUDGET_DECREASE->expected.add(a("UPDATE","PLATFORM_AD_SET",entity,c("observedState","ENUM","PAUSED","PAUSED"),c("budgetAmount","DECIMAL","30","20")));}
        var actual=jdbc.queryForList("select l.stage4b_operation_ordinal,l.action,l.entity_type,l.entity_uuid,l.actor_type,l.actor_id,l.source,l.request_id,c.change_order,c.field_name,c.value_type,c.old_value,c.new_value from audit_logs l join audit_log_changes c on c.audit_uuid=l.audit_uuid where l.operation_uuid=? order by l.stage4b_operation_ordinal,c.change_order",operation);var flattened=new java.util.ArrayList<String>();for(var row:actual)flattened.add(row.get("stage4b_operation_ordinal")+"|"+row.get("action")+"|"+row.get("entity_type")+"|"+row.get("entity_uuid")+"|"+row.get("actor_type")+"|"+row.get("actor_id")+"|"+row.get("source")+"|"+row.get("request_id")+"|"+row.get("change_order")+"|"+row.get("field_name")+"|"+row.get("value_type")+"|"+row.get("old_value")+"|"+row.get("new_value"));var expectedFlat=new java.util.ArrayList<String>();for(int ordinal=0;ordinal<expected.size();ordinal++){var audit=expected.get(ordinal);for(int order=0;order<audit.changes.size();order++){var change=audit.changes.get(order);expectedFlat.add(ordinal+"|"+audit.action+"|"+audit.subjectType+"|"+audit.subjectUuid+"|LOCAL_ADMIN|local-admin|API|"+requestId+"|"+order+"|"+change.field+"|"+change.type+"|"+change.oldValue+"|"+change.newValue);}}assertThat(flattened).containsExactlyElementsOf(expectedFlat);
    }
    private ExpectedChange[] batchChanges(UUID batch,UUID reservation,UUID day,LocalDate date,AuditShape shape){var values=new java.util.ArrayList<ExpectedChange>();values.add(c("operationBatchUuid","UUID",null,batch.toString()));if(reservation!=null){values.add(c("budgetReservationUuid","UUID",null,reservation.toString()));values.add(c("accountBudgetDayUuid","UUID",null,day.toString()));}values.add(c("businessDate","DATE",null,date.toString()));if(reservation!=null)values.add(c("reservationKind","ENUM",null,reservationKind(shape)));values.add(c("currency","STRING",null,"TWD"));if(reservation!=null)values.add(c("budgetAmount","DECIMAL",previous(shape),next(shape)));values.add(c("reservedAmount","DECIMAL",null,reserved(shape)));return values.toArray(ExpectedChange[]::new);}
    private ExpectedChange[] reservationChanges(UUID batch,UUID reservation,UUID day,LocalDate date,AuditShape shape){return new ExpectedChange[]{c("operationBatchUuid","UUID",null,batch.toString()),c("budgetReservationUuid","UUID",null,reservation.toString()),c("accountBudgetDayUuid","UUID",null,day.toString()),c("businessDate","DATE",null,date.toString()),c("reservationKind","ENUM",null,reservationKind(shape)),c("currency","STRING",null,"TWD"),c("budgetAmount","DECIMAL",previous(shape),next(shape)),c("reservedAmount","DECIMAL",null,reserved(shape))};}
    private ExpectedChange[] dayChanges(UUID batch,UUID reservation,UUID day,LocalDate date,AuditShape shape){String before=shape==AuditShape.AD_SET_CREATE?"0.000000":"25.000000",after=shape==AuditShape.AD_SET_CREATE?"25.000000":"30.000000";return new ExpectedChange[]{c("operationBatchUuid","UUID",null,batch.toString()),c("budgetReservationUuid","UUID",null,reservation.toString()),c("accountBudgetDayUuid","UUID",null,day.toString()),c("businessDate","DATE",null,date.toString()),c("reservationKind","ENUM",null,reservationKind(shape)),c("currency","STRING",null,"TWD"),c("reservedAmount","DECIMAL",null,reserved(shape)),c("aggregateReservedAmount","DECIMAL",before,after)};}
    private static String reservationKind(AuditShape s){return switch(s){case AD_SET_CREATE->"INITIAL";case BUDGET_INCREASE->"INCREASE";case BUDGET_DECREASE->"DECREASE_NO_RELEASE";default->null;};}private static String previous(AuditShape s){return switch(s){case BUDGET_INCREASE->"25.000000";case BUDGET_DECREASE->"30.000000";default->null;};}private static String next(AuditShape s){return switch(s){case AD_SET_CREATE->"25.000000";case BUDGET_INCREASE->"30.000000";case BUDGET_DECREASE->"20.000000";default->null;};}private static String reserved(AuditShape s){return switch(s){case AD_SET_CREATE->"25.000000";case BUDGET_INCREASE->"5.000000";default->"0.000000";};}
    private static ExpectedAudit a(String action,String type,UUID uuid,ExpectedChange...changes){return new ExpectedAudit(action,type,uuid,java.util.List.of(changes));}private static ExpectedChange c(String field,String type,String oldValue,String newValue){return new ExpectedChange(field,type,oldValue,newValue);}private enum AuditShape{CAMPAIGN_CREATE,AD_SET_CREATE,CAMPAIGN_STATE,BUDGET_INCREASE,BUDGET_DECREASE}private record ExpectedAudit(String action,String subjectType,UUID subjectUuid,java.util.List<ExpectedChange> changes){}private record ExpectedChange(String field,String type,String oldValue,String newValue){}
    private void assertAuditEnvelope(UUID operation,String requestId){
        var rows=jdbc.queryForList("select stage4b_operation_ordinal,action,entity_type,entity_uuid,actor_type,actor_id,source,request_id from audit_logs where operation_uuid=? order by stage4b_operation_ordinal",operation);
        assertThat(rows).isNotEmpty();
        UUID entity=jdbc.queryForObject("select coalesce(platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid) from platform_operations where operation_uuid=?",UUID.class,operation),attempt=jdbc.queryForObject("select operation_attempt_uuid from platform_operation_attempts where operation_uuid=? and attempt_kind='SUBMIT' and attempt_number=1",UUID.class,operation);int operationSeen=0,attemptSeen=0,entitySeen=0;
        for(int ordinal=0;ordinal<rows.size();ordinal++){
            var row=rows.get(ordinal);assertThat(((Number)row.get("stage4b_operation_ordinal")).intValue()).isEqualTo(ordinal);
            assertThat(row.get("actor_type")).isEqualTo("LOCAL_ADMIN");assertThat(row.get("actor_id")).isEqualTo("local-admin");
            assertThat(row.get("source")).isEqualTo("API");assertThat(row.get("request_id")).isEqualTo(requestId);
            String type=(String)row.get("entity_type"),expectedAction;UUID expectedUuid;
            switch(type){case "PLATFORM_OPERATION"->{expectedAction=operationSeen++==0?"CREATE":"UPDATE";expectedUuid=operation;}case "PLATFORM_OPERATION_ATTEMPT"->{expectedAction=attemptSeen++==0?"CREATE":"UPDATE";expectedUuid=attempt;}case "PLATFORM_CAMPAIGN","PLATFORM_AD_SET"->{expectedAction=ordinal==0&&entitySeen++==0?"CREATE":"UPDATE";expectedUuid=entity;}case "PLATFORM_OPERATION_BATCH","PLATFORM_BUDGET_RESERVATION"->{expectedAction="CREATE";expectedUuid=(UUID)row.get("entity_uuid");}case "PLATFORM_ACCOUNT_BUDGET_DAY"->{expectedAction="UPDATE";expectedUuid=(UUID)row.get("entity_uuid");}default->throw new AssertionError(type);}
            assertThat(row.get("action")).isEqualTo(expectedAction);assertThat(row.get("entity_uuid")).isEqualTo(expectedUuid);
            var changes=jdbc.queryForList("select change_order,field_name,value_type,old_value,new_value from audit_log_changes where audit_uuid=(select audit_uuid from audit_logs where operation_uuid=? and stage4b_operation_ordinal=?) order by change_order",operation,ordinal);assertThat(changes).isNotEmpty();for(int index=0;index<changes.size();index++){var change=changes.get(index);assertThat(((Number)change.get("change_order")).intValue()).isEqualTo(index);assertThat(change.get("field_name")).isNotNull();assertThat(change.get("value_type")).isNotNull();assertThat(change.get("old_value")!=null||change.get("new_value")!=null).isTrue();}
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and (c.change_order<0 or c.field_name is null or c.value_type is null)",Integer.class,operation)).isZero();
    }
    private void assertBudgetAudit(UUID operation,String kind,String previous,String next,String reserved,boolean dayEvent){
        UUID batch=jdbc.queryForObject("select operation_batch_uuid from platform_operation_batches where operation_uuid=?",UUID.class,operation),reservation=jdbc.queryForObject("select budget_reservation_uuid from platform_budget_reservations where operation_uuid=?",UUID.class,operation),day=jdbc.queryForObject("select account_budget_day_uuid from platform_budget_reservations where operation_uuid=?",UUID.class,operation);
        java.time.LocalDate date=jdbc.queryForObject("select business_date from platform_operation_batches where operation_uuid=?",java.time.LocalDate.class,operation);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=? and entity_type='PLATFORM_OPERATION_BATCH' and action='CREATE' and entity_uuid=?",Integer.class,operation,batch)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=? and entity_type='PLATFORM_BUDGET_RESERVATION' and action='CREATE' and entity_uuid=?",Integer.class,operation,reservation)).isEqualTo(1);
        assertThat(change(operation,"PLATFORM_OPERATION_BATCH","operationBatchUuid","new_value")).isEqualTo(batch.toString());assertThat(change(operation,"PLATFORM_OPERATION_BATCH","budgetReservationUuid","new_value")).isEqualTo(reservation.toString());assertThat(change(operation,"PLATFORM_OPERATION_BATCH","accountBudgetDayUuid","new_value")).isEqualTo(day.toString());assertThat(change(operation,"PLATFORM_OPERATION_BATCH","businessDate","new_value")).isEqualTo(date.toString());assertThat(change(operation,"PLATFORM_OPERATION_BATCH","currency","new_value")).isEqualTo("TWD");assertThat(new java.math.BigDecimal(change(operation,"PLATFORM_OPERATION_BATCH","reservedAmount","new_value"))).isEqualByComparingTo(reserved);
        assertThat(change(operation,"PLATFORM_BUDGET_RESERVATION","operationBatchUuid","new_value")).isEqualTo(batch.toString());assertThat(change(operation,"PLATFORM_BUDGET_RESERVATION","budgetReservationUuid","new_value")).isEqualTo(reservation.toString());assertThat(change(operation,"PLATFORM_BUDGET_RESERVATION","accountBudgetDayUuid","new_value")).isEqualTo(day.toString());assertThat(change(operation,"PLATFORM_BUDGET_RESERVATION","businessDate","new_value")).isEqualTo(date.toString());assertThat(change(operation,"PLATFORM_BUDGET_RESERVATION","currency","new_value")).isEqualTo("TWD");assertThat(change(operation,"PLATFORM_BUDGET_RESERVATION","reservationKind","new_value")).isEqualTo(kind);if(previous==null)assertThat(change(operation,"PLATFORM_BUDGET_RESERVATION","budgetAmount","old_value")).isNull();else assertThat(new java.math.BigDecimal(change(operation,"PLATFORM_BUDGET_RESERVATION","budgetAmount","old_value"))).isEqualByComparingTo(previous);assertThat(new java.math.BigDecimal(change(operation,"PLATFORM_BUDGET_RESERVATION","budgetAmount","new_value"))).isEqualByComparingTo(next);assertThat(new java.math.BigDecimal(change(operation,"PLATFORM_BUDGET_RESERVATION","reservedAmount","new_value"))).isEqualByComparingTo(reserved);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=? and entity_type='PLATFORM_ACCOUNT_BUDGET_DAY' and action='UPDATE' and entity_uuid=?",Integer.class,operation,day)).isEqualTo(dayEvent?1:0);
        if(dayEvent){java.math.BigDecimal oldValue=new java.math.BigDecimal(change(operation,"PLATFORM_ACCOUNT_BUDGET_DAY","aggregateReservedAmount","old_value")),newValue=new java.math.BigDecimal(change(operation,"PLATFORM_ACCOUNT_BUDGET_DAY","aggregateReservedAmount","new_value"));assertThat(newValue.subtract(oldValue)).isEqualByComparingTo(reserved);}
    }
    private void assertBatchAudit(UUID operation,String reserved,boolean hasReservation){UUID batch=jdbc.queryForObject("select operation_batch_uuid from platform_operation_batches where operation_uuid=?",UUID.class,operation);java.time.LocalDate date=jdbc.queryForObject("select business_date from platform_operation_batches where operation_uuid=?",java.time.LocalDate.class,operation);assertThat(change(operation,"PLATFORM_OPERATION_BATCH","operationBatchUuid","new_value")).isEqualTo(batch.toString());assertThat(change(operation,"PLATFORM_OPERATION_BATCH","businessDate","new_value")).isEqualTo(date.toString());assertThat(change(operation,"PLATFORM_OPERATION_BATCH","currency","new_value")).isEqualTo("TWD");assertThat(new java.math.BigDecimal(change(operation,"PLATFORM_OPERATION_BATCH","reservedAmount","new_value"))).isEqualByComparingTo(reserved);assertThat(jdbc.queryForObject("select count(*) from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and l.entity_type='PLATFORM_OPERATION_BATCH' and c.field_name in ('budgetReservationUuid','accountBudgetDayUuid','reservationKind')",Integer.class,operation)).isEqualTo(hasReservation?3:0);}
    private String change(UUID operation,String subject,String field,String column){return jdbc.queryForObject("select c."+column+" from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and l.entity_type=? and c.field_name=?",String.class,operation,subject,field);}
    private String createCampaign(UUID campaignPlan) throws Exception {String json=mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"campaignUuid\":\""+campaignPlan+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createCampaign(UUID campaignPlan,UUID request) throws Exception {String json=mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+request+"\",\"campaignUuid\":\""+campaignPlan+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createCampaign(UUID campaignPlan,UUID request,String requestId) throws Exception {String json=mvc.perform(post("/api/platforms/meta/campaigns").header("X-Request-ID",requestId).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+request+"\",\"campaignUuid\":\""+campaignPlan+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createAdSet(String campaign,String amount) throws Exception {String etag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");String json=mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\""+amount+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createAdSet(String campaign,String amount,PlatformBudgetType type) throws Exception {String etag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");String json=mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+UUID.randomUUID()+"\",\"budgetType\":\""+type+"\",\"budgetAmount\":\""+amount+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createAdSet(String campaign,String amount,UUID request) throws Exception {String etag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");String json=mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",etag).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+request+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\""+amount+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private String createAdSet(String campaign,String amount,UUID request,String requestId) throws Exception {String etag=mvc.perform(get("/api/platforms/meta/campaigns/"+campaign)).andReturn().getResponse().getHeader("ETag");String json=mvc.perform(post("/api/platforms/meta/campaigns/"+campaign+"/ad-sets").header("If-Match",etag).header("X-Request-ID",requestId).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestUuid\":\""+request+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":\""+amount+"\",\"expectedCampaignPlanVersion\":0}")) .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*","$1");}
    private UUID newPlan(){UUID value=UUID.randomUUID();jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) VALUES (?,'Stage 4B additional',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')",value,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));return value;}
}
