package com.aicommerce.platform.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformReadAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired DeterministicFakePlatformAdapter writeFake;
    @Autowired DeterministicFakePlatformReadAdapter readFake;

    @BeforeEach
    void reset() {
        writeFake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
        readFake.reset();
        jdbc.execute("""
                TRUNCATE platform_metric_snapshots, platform_budget_reservations, platform_operation_batches,
                         platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ads,
                         platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs,
                         ai_review_decisions, ai_generation_outputs, ai_generation_jobs, ai_generation_batches,
                         quality_score_blockers, quality_scores, workflow_status, products
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void summaryRejectsQueryAndUnknownSectionAndOmitsAbsentOptionals() throws Exception {
        mvc.perform(get("/api/dashboard?asOf=2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DASHBOARD_REQUEST_INVALID"));
        mvc.perform(get("/api/dashboard/unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DASHBOARD_REQUEST_INVALID"));
        mvc.perform(get("/api/dashboard/todos?page=0&page=1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/todos?size=0"))
                .andExpect(status().isBadRequest());
        var response = mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos.available").value(true))
                .andExpect(jsonPath("$.products.available").value(true))
                .andExpect(jsonPath("$.reviews.available").value(true))
                .andExpect(jsonPath("$.campaigns.available").value(true))
                .andExpect(jsonPath("$.platformCampaigns.available").value(true))
                .andExpect(jsonPath("$.anomalies.available").value(true))
                .andExpect(jsonPath("$.kpis.available").value(true))
                .andExpect(jsonPath("$.kpis.timezone").value("Asia/Taipei"))
                .andExpect(jsonPath("$.kpis.currency").value("TWD"))
                .andExpect(jsonPath("$.kpis.eligibleCampaignCount").value(0))
                .andExpect(jsonPath("$.kpis.presentCampaignCount").value(0))
                .andExpect(jsonPath("$.kpis.incomplete").value(false))
                .andExpect(jsonPath("$.kpis.spend").doesNotExist())
                .andExpect(jsonPath("$.kpis.impressions").doesNotExist())
                .andReturn().getResponse();
        assertThat(response.getHeader("ETag")).isNull();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertForbidden(response.getContentAsString());
        mvc.perform(get("/api/dashboard/platform-campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void todosIncludeReadinessAndPendingReviewAndExcludePausedCreate() throws Exception {
        UUID product = createProduct("Dashboard Product");
        pendingReview(product, "[]", null);
        UUID plan = newPlan();
        String campaign = createCampaign(plan);
        writeFake.useScenario(DeterministicFakePlatformAdapter.Scenario.AMBIGUOUS_TIMEOUT);
        UUID unknownPlan = newPlan();
        mvc.perform(post("/api/platforms/meta/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID()
                                + "\",\"campaignUuid\":\"" + unknownPlan
                                + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted());
        bypass("UPDATE platform_campaigns SET observed_state='ERROR' WHERE platform_campaign_uuid=?",
                UUID.fromString(campaign));

        int writes = writeFake.invocationCount();
        int reads = readFake.invocationCount();
        var body = mapper.readTree(mvc.perform(get("/api/dashboard")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(writeFake.invocationCount()).isEqualTo(writes);
        assertThat(readFake.invocationCount()).isEqualTo(reads);
        assertThat(kinds(body.get("todos").get("items"))).contains("PRODUCT_READINESS", "PENDING_REVIEW",
                "UNKNOWN_OPERATION", "PLATFORM_ERROR");
        assertThat(kinds(body.get("todos").get("items"))).doesNotContain("PAUSED");
        assertThat(body.get("platformCampaigns").get("items").get(0).get("desiredState").asText()).isEqualTo("PAUSED");
        assertThat(body.get("reviews").get("items").get(0).get("reviewStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(body.get("reviews").get("items").get(0).get("approvalBlocked").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_metric_snapshots", Integer.class)).isZero();
    }

    @Test
    void kpiUsesCampaignGrainOnlyAndOmitsNullSpendWithoutZeroFill() throws Exception {
        UUID plan = newPlan();
        String campaign = createCampaign(plan);
        String etag = mvc.perform(get("/api/platforms/meta/campaigns/" + campaign)).andReturn().getResponse()
                .getHeader("ETag");
        String adSetJson = mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/ad-sets")
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID()
                                + "\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"10\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String adSet = mapper.readTree(adSetJson).get("entityUuid").asText();
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")).andExpect(status().isOk());
        mvc.perform(post("/api/platform-entities/AD_SET/" + adSet + "/metrics-refresh")).andExpect(status().isOk());

        int reads = readFake.invocationCount();
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.available").value(true))
                .andExpect(jsonPath("$.kpis.eligibleCampaignCount").value(1))
                .andExpect(jsonPath("$.kpis.presentCampaignCount").value(1))
                .andExpect(jsonPath("$.kpis.incomplete").value(false))
                .andExpect(jsonPath("$.kpis.impressions").value(10000))
                .andExpect(jsonPath("$.kpis.spend").value("25.000000"))
                .andExpect(jsonPath("$.kpis.ctr").value("0.010000"))
                .andExpect(jsonPath("$.kpis.roas").value("4.000000"));
        assertThat(readFake.invocationCount()).isEqualTo(reads);

        UUID second = newPlan();
        String other = createCampaign(second);
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.PARTIAL_NULL);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + other + "/metrics-refresh")).andExpect(status().isOk());
        var kpi = mapper.readTree(mvc.perform(get("/api/dashboard")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("kpis");
        assertThat(kpi.get("eligibleCampaignCount").asInt()).isEqualTo(2);
        assertThat(kpi.get("presentCampaignCount").asInt()).isEqualTo(2);
        assertThat(kpi.get("incomplete").asBoolean()).isTrue();
        assertThat(kpi.get("impressions").asLong()).isEqualTo(20000);
        assertThat(kpi.get("clicks").asLong()).isEqualTo(200);
        assertThat(kpi.has("spend")).isFalse();
        assertThat(kpi.has("roas")).isFalse();
        assertThat(kpi.has("cpc")).isFalse();
    }

    @Test
    void blockedReviewSetsApprovalBlockedAndPagedReviewsStayPending() throws Exception {
        UUID product = createProduct("Blocked Review Product");
        pendingReview(product, "[\"unsafe\"]", null);
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.items[0].approvalBlocked").value(true))
                .andExpect(jsonPath("$.reviews.items[0].blockerCount").value(1));
        mvc.perform(get("/api/dashboard/reviews?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private UUID createProduct(String name) throws Exception {
        String json = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"" + name + "\",\"brand\":\"Dashboard\",\"category\":\"Ops\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(json).get("productUuid").asText());
    }

    private void pendingReview(UUID product, String safetyFindings, String failureCode) {
        UUID template = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,?,'TEXT','Dashboard')",
                template, "dash." + template.toString().substring(0, 8));
        UUID templateVersion = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,
                    version_number,template_text,input_schema,content_sha256,created_by)
                VALUES (?,?,1,'Dashboard','{}'::jsonb,?,'tester')
                """, templateVersion, template, (template.toString() + templateVersion.toString()).replace("-", ""));
        UUID batch = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,
                    estimated_cost,reserved_cost,actual_cost,requested_job_count,succeeded_job_count,created_by)
                VALUES (?,?,'COMPLETED','USD',0.1,0.1,0.1,1,1,'tester')
                """, batch, product);
        UUID job = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,
                    prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,
                    input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,failure_code,
                    submitted_at,started_at,completed_at)
                VALUES (?,?,?,?,'TEXT','stub','stub-text','SUCCEEDED','Dashboard','{}'::jsonb,
                    0.1,0.1,0.1,'USD',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, job, batch, product, templateVersion, failureCode);
        UUID output = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,
                    product_uuid,generation_type,text_content,model_label,input_units,output_units,actual_cost,currency,
                    safety_findings)
                VALUES (?,?,?,?,'TEXT','Generated','stub-text',1,1,0.1,'USD',?::jsonb)
                """, output, job, batch, product, safetyFindings);
    }

    private UUID newPlan() {
        UUID plan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
                VALUES (?,'Dashboard plan',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
                """, plan, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        return plan;
    }

    private String createCampaign(UUID plan) throws Exception {
        String json = mvc.perform(post("/api/platforms/meta/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + plan
                                + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("entityUuid").asText();
    }

    private void bypass(String sql, UUID entity) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var role = connection.createStatement(); var update = connection.prepareStatement(sql)) {
                role.execute("SET session_replication_role = replica");
                update.setObject(1, entity);
                update.executeUpdate();
                role.execute("SET session_replication_role = DEFAULT");
            }
            return null;
        });
    }

    private static java.util.List<String> kinds(JsonNode items) {
        java.util.List<String> values = new java.util.ArrayList<>();
        items.forEach(item -> values.add(item.get("kind").asText()));
        return values;
    }

    private static void assertForbidden(String body) {
        assertThat(body).doesNotContain("platformAccountUuid", "accountReference", "externalId", "canonicalPayload",
                "requestPayload", "outcomeEvidence", "safeProviderTraceId", "sourceFingerprint", "providerUrl");
    }
}
