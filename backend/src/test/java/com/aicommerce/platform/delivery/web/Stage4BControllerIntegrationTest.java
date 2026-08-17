package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.aicommerce.platform.delivery.application.Stage4BService;
import com.aicommerce.platform.delivery.application.Stage4BLedgerCriticalSectionHook;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class Stage4BControllerIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean Stage4BService stage4BService; @MockitoSpyBean Stage4BLedgerCriticalSectionHook ledgerHook;
    @Autowired DeterministicFakePlatformAdapter fake;
    UUID plan;
    @BeforeEach void fixture(){
        reset(ledgerHook);jdbc.execute("TRUNCATE platform_budget_reservations, platform_operation_batches, platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs RESTART IDENTITY CASCADE");
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
        assertAuditEnvelope(campaignOperation,"audit-campaign-create");assertAuditEnvelope(adSetOperation,"audit-adset-create");
        assertAuditEnvelope(stateOperation,"audit-campaign-state");assertAuditEnvelope(increaseOperation,"audit-budget-increase");assertAuditEnvelope(decreaseOperation,"audit-budget-decrease");
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
    private java.util.List<String> subjects(UUID operation){var rows=jdbc.queryForList("select stage4b_operation_ordinal,entity_type from audit_logs where operation_uuid=? order by stage4b_operation_ordinal",operation);for(int i=0;i<rows.size();i++)assertThat(((Number)rows.get(i).get("stage4b_operation_ordinal")).intValue()).isEqualTo(i);return rows.stream().map(row->(String)row.get("entity_type")).toList();}
    private java.util.List<String> changes(UUID operation,String subject){return jdbc.queryForList("select c.field_name||':'||c.value_type from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and l.entity_type=? order by c.change_order",String.class,operation,subject);}
    private void assertAuditEnvelope(UUID operation,String requestId){
        var rows=jdbc.queryForList("select stage4b_operation_ordinal,action,entity_type,entity_uuid,actor_type,actor_id,source,request_id from audit_logs where operation_uuid=? order by stage4b_operation_ordinal",operation);
        assertThat(rows).isNotEmpty();
        for(int ordinal=0;ordinal<rows.size();ordinal++){
            var row=rows.get(ordinal);assertThat(((Number)row.get("stage4b_operation_ordinal")).intValue()).isEqualTo(ordinal);
            assertThat(row.get("actor_type")).isEqualTo("LOCAL_ADMIN");assertThat(row.get("actor_id")).isEqualTo("local-admin");
            assertThat(row.get("source")).isEqualTo("API");assertThat(row.get("request_id")).isEqualTo(requestId);
            assertThat(row.get("action")).isIn("CREATE","UPDATE");assertThat(row.get("entity_uuid")).isNotNull();
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
