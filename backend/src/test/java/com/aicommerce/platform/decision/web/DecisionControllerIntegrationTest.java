package com.aicommerce.platform.decision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class DecisionControllerIntegrationTest {
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
                TRUNCATE decision_recommendation_decisions, decision_recommendations, platform_metric_snapshots,
                         platform_budget_reservations, platform_operation_batches, platform_account_budget_days,
                         platform_operation_attempts, platform_operations, platform_ads, platform_ad_sets,
                         platform_campaigns, campaign_plans, audit_log_changes, audit_logs
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void generateRejectsContentTypeBodyAndQuery() throws Exception {
        mvc.perform(post("/api/decision-recommendations/generate").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DECISION_REQUEST_INVALID"));
        mvc.perform(post("/api/decision-recommendations/generate").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/decision-recommendations/generate?asOf=2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/decision-recommendations?status=PENDING&status=APPROVED"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/decision-recommendations?foo=1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void successFixtureEmitsIncreaseBudgetOnlyWithoutAdapterOrSnapshotWrites() throws Exception {
        UUID plan = newPlan("Success plan");
        String campaign = createCampaign(plan);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")).andExpect(status().isOk());
        int writes = writeFake.invocationCount();
        int reads = readFake.invocationCount();
        int snapshots = count("platform_metric_snapshots");
        int operations = count("platform_operations");

        var response = mvc.perform(post("/api/decision-recommendations/generate"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.timezone").value("Asia/Taipei"))
                .andExpect(jsonPath("$.currency").value("TWD"))
                .andExpect(jsonPath("$.consideredCampaignCount").value(1))
                .andExpect(jsonPath("$.skippedIncompleteCount").value(0))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.items[0].recommendationType").value("INCREASE_BUDGET"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].roas").doesNotExist())
                .andExpect(jsonPath("$.items[0].evidence.roas").value("4.000000"))
                .andExpect(jsonPath("$.items[0].evidence.ctr").value("0.010000"))
                .andExpect(jsonPath("$.items[0].evidence.frequency").doesNotExist())
                .andExpect(jsonPath("$.items[0].href").value("/platforms/meta"))
                .andExpect(jsonPath("$.items[0].productUuid").doesNotExist())
                .andExpect(jsonPath("$.items[0].warnings[0]").value("DETERMINISTIC_FAKE_ONLY"))
                .andExpect(jsonPath("$.items[0].warnings[3]").value("APPROVAL_DOES_NOT_EXECUTE"))
                .andReturn().getResponse();
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("items")).hasSize(1);
        assertForbidden(response.getContentAsString());
        assertThat(response.getContentAsString()).doesNotContain("AUDIENCE_FATIGUE");
        assertThat(writeFake.invocationCount()).isEqualTo(writes);
        assertThat(readFake.invocationCount()).isEqualTo(reads);
        assertThat(count("platform_metric_snapshots")).isEqualTo(snapshots);
        assertThat(count("platform_operations")).isEqualTo(operations);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM decision_recommendations WHERE recommendation_type='AUDIENCE_FATIGUE'",
                Integer.class)).isZero();

        UUID campaignUuid = UUID.fromString(jdbc.queryForObject(
                "SELECT campaign_uuid::text FROM platform_campaigns WHERE platform_campaign_uuid=?",
                String.class, UUID.fromString(campaign)));
        String sourceFingerprint = jdbc.queryForObject("""
                SELECT source_fingerprint FROM platform_metric_snapshots
                WHERE platform_campaign_uuid=? AND entity_type='CAMPAIGN' ORDER BY revision_number DESC LIMIT 1
                """, String.class, UUID.fromString(campaign));
        String windowStart = body.get("windowStart").asText();
        String windowEnd = body.get("windowEnd").asText();
        String expected = com.aicommerce.platform.decision.application.EvidenceFingerprint.hash(
                campaignUuid, sourceFingerprint, "INCREASE_BUDGET",
                java.time.Instant.parse(windowStart), java.time.Instant.parse(windowEnd));
        assertThat(jdbc.queryForObject("SELECT evidence_fingerprint FROM decision_recommendations", String.class))
                .isEqualTo(expected);

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationUuid").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
        String dashboard = mvc.perform(get("/api/dashboard")).andReturn().getResponse().getContentAsString();
        assertThat(dashboard).doesNotContain("INCREASE_BUDGET", "recommendationType", "優化建議");
    }

    @Test
    void missingSnapshotIncrementsSkippedAndNullSpendOmitsMoneyRules() throws Exception {
        UUID missing = newPlan("Missing snapshot");
        createCampaign(missing);
        UUID nullSpend = newPlan("Null spend");
        String campaign = createCampaign(nullSpend);
        insertSnapshot(UUID.fromString(campaign), 10_000L, 100L, 4L, null, "100.000000");

        mvc.perform(post("/api/decision-recommendations/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consideredCampaignCount").value(2))
                .andExpect(jsonPath("$.skippedIncompleteCount").value(1))
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void increaseAndDecreaseCoEmitAndStalePendingIsLeftUnchanged() throws Exception {
        UUID plan = newPlan("Co-emit");
        String campaign = createCampaign(plan);
        insertSnapshot(UUID.fromString(campaign), 10_000L, 100L, 2L, "100.000000", "400.000000");
        mvc.perform(post("/api/decision-recommendations/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2));
        var types = jdbc.queryForList(
                "SELECT recommendation_type FROM decision_recommendations ORDER BY recommendation_type", String.class);
        assertThat(types).containsExactly("DECREASE_BUDGET", "INCREASE_BUDGET");

        UUID increase = jdbc.queryForObject(
                "SELECT recommendation_uuid FROM decision_recommendations WHERE recommendation_type='INCREASE_BUDGET'",
                UUID.class);
        long version = jdbc.queryForObject(
                "SELECT version FROM decision_recommendations WHERE recommendation_uuid=?", Long.class, increase);
        String fingerprint = jdbc.queryForObject(
                "SELECT evidence_fingerprint FROM decision_recommendations WHERE recommendation_uuid=?",
                String.class, increase);

        insertLaterSnapshot(UUID.fromString(campaign), 10_000L, 100L, 2L, "100.000000", "10.000000");
        mvc.perform(post("/api/decision-recommendations/generate"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM decision_recommendations WHERE recommendation_uuid=?", String.class, increase))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT version FROM decision_recommendations WHERE recommendation_uuid=?", Long.class, increase))
                .isEqualTo(version);
        assertThat(jdbc.queryForObject(
                "SELECT evidence_fingerprint FROM decision_recommendations WHERE recommendation_uuid=?",
                String.class, increase)).isEqualTo(fingerprint);
    }

    @Test
    void approveAndRejectAreDecisionRecordsOnly() throws Exception {
        UUID plan = newPlan("Decide");
        String campaign = createCampaign(plan);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")).andExpect(status().isOk());
        mvc.perform(post("/api/decision-recommendations/generate")).andExpect(status().isOk());

        UUID first = jdbc.queryForObject("SELECT recommendation_uuid FROM decision_recommendations", UUID.class);
        String etag = mvc.perform(get("/api/decision-recommendations/" + first))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"0\""))
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(post("/api/decision-recommendations/" + first + "/approve"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("DECISION_PRECONDITION_REQUIRED"));
        mvc.perform(post("/api/decision-recommendations/" + first + "/approve")
                        .header("If-Match", "W/\"9\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("DECISION_STALE"));

        String desired = jdbc.queryForObject(
                "SELECT desired_state FROM platform_campaigns WHERE platform_campaign_uuid=?", String.class,
                UUID.fromString(campaign));
        int writes = writeFake.invocationCount();
        int reads = readFake.invocationCount();
        int operations = count("platform_operations");
        int reviews = count("ai_review_decisions");
        int jobs = count("ai_generation_jobs");

        mvc.perform(post("/api/decision-recommendations/" + first + "/approve")
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decision.decision").value("APPROVED"))
                .andExpect(jsonPath("$.decision.reason").doesNotExist());

        assertThat(writeFake.invocationCount()).isEqualTo(writes);
        assertThat(readFake.invocationCount()).isEqualTo(reads);
        assertThat(count("platform_operations")).isEqualTo(operations);
        assertThat(count("ai_review_decisions")).isEqualTo(reviews);
        assertThat(count("ai_generation_jobs")).isEqualTo(jobs);
        assertThat(jdbc.queryForObject(
                "SELECT desired_state FROM platform_campaigns WHERE platform_campaign_uuid=?", String.class,
                UUID.fromString(campaign))).isEqualTo(desired);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_ad_sets", Integer.class)).isZero();

        mvc.perform(post("/api/decision-recommendations/" + first + "/approve")
                        .header("If-Match", "W/\"1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DECISION_ALREADY_DECIDED"));

        UUID otherPlan = newPlan("Reject");
        String other = createCampaign(otherPlan);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + other + "/metrics-refresh")).andExpect(status().isOk());
        mvc.perform(post("/api/decision-recommendations/generate")).andExpect(status().isOk());
        UUID pending = jdbc.queryForObject(
                "SELECT recommendation_uuid FROM decision_recommendations WHERE status='PENDING'", UUID.class);
        String rejectEtag = mvc.perform(get("/api/decision-recommendations/" + pending))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/decision-recommendations/" + pending + "/reject")
                        .header("If-Match", rejectEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not a useful suggestion\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reason").value("Not a useful suggestion"));
        assertThat(jdbc.queryForObject(
                "SELECT desired_state FROM platform_campaigns WHERE platform_campaign_uuid=?", String.class,
                UUID.fromString(other))).isEqualTo("PAUSED");
    }

    @Test
    void listDefaultsToPendingAndReplayDoesNotCreateAudit() throws Exception {
        UUID plan = newPlan("Replay");
        String campaign = createCampaign(plan);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")).andExpect(status().isOk());
        mvc.perform(post("/api/decision-recommendations/generate")).andExpect(status().isOk());
        int audits = count("audit_logs");
        mvc.perform(post("/api/decision-recommendations/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.replayedCount").value(1))
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(count("audit_logs")).isEqualTo(audits);
        mvc.perform(get("/api/decision-recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private UUID newPlan(String name) {
        UUID plan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
                VALUES (?,?,?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
                """, plan, name, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
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

    private void insertSnapshot(UUID platformCampaign, long impressions, long clicks, long conversions,
            String spend, String revenue) {
        insertSnapshotRevision(platformCampaign, impressions, clicks, conversions, spend, revenue, 1);
    }

    private void insertLaterSnapshot(UUID platformCampaign, long impressions, long clicks, long conversions,
            String spend, String revenue) {
        insertSnapshotRevision(platformCampaign, impressions, clicks, conversions, spend, revenue, 2);
    }

    private void insertSnapshotRevision(UUID platformCampaign, long impressions, long clicks, long conversions,
            String spend, String revenue, int revision) {
        UUID account = jdbc.queryForObject(
                "SELECT platform_account_uuid FROM platform_campaigns WHERE platform_campaign_uuid=?",
                UUID.class, platformCampaign);
        String fingerprint = (platformCampaign.toString().replace("-", "") + Integer.toHexString(revision) + "a".repeat(64))
                .substring(0, 64);
        jdbc.update("""
                INSERT INTO platform_metric_snapshots(
                    metric_snapshot_uuid, platform_account_uuid, entity_type, platform_campaign_uuid,
                    window_start, window_end, timezone, currency, impressions, clicks, conversions, spend, revenue,
                    revision_number, fetched_at, freshness_status, source_fingerprint)
                SELECT ?, ?, 'CAMPAIGN', ?,
                    ((platform_taipei_business_date(statement_timestamp()) - 1) AT TIME ZONE 'Asia/Taipei'),
                    (platform_taipei_business_date(statement_timestamp()) AT TIME ZONE 'Asia/Taipei'),
                    'Asia/Taipei', 'TWD', ?, ?, ?, ?::numeric, ?::numeric, ?, statement_timestamp(), 'FRESH', ?
                """, UUID.randomUUID(), account, platformCampaign, impressions, clicks, conversions, spend, revenue,
                revision, fingerprint);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void assertForbidden(String body) {
        assertThat(body).doesNotContain("platformAccountUuid", "accountReference", "externalId", "canonicalPayload",
                "requestPayload", "outcomeEvidence", "safeProviderTraceId", "sourceFingerprint",
                "evidenceFingerprint", "metricSourceFingerprint", "providerUrl", "frequency");
    }
}
