package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.Stage4DMetricFingerprint;
import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformReadAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
class Stage4DControllerIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired DeterministicFakePlatformReadAdapter readFake;
    UUID plan;

    @BeforeEach
    void fixture() {
        readFake.reset();
        jdbc.execute("""
                TRUNCATE platform_metric_snapshots, platform_budget_reservations, platform_operation_batches,
                         platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ads,
                         platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs
                RESTART IDENTITY CASCADE
                """);
        plan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
                VALUES (?,'Stage 4D',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
                """, plan, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
    }

    @Test
    void getIsPostgresOnlyAndRefreshPersistsFingerprintDerivedAndReplay() throws Exception {
        String campaign = createCampaign();
        int before = readFake.invocationCount();
        var missing = mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(false))
                .andExpect(jsonPath("$.freshnessStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.warnings[0]").value("DETERMINISTIC_FAKE_ONLY"))
                .andExpect(jsonPath("$.warnings[1]").value("NO_REAL_PROVIDER_OR_SPEND"))
                .andExpect(jsonPath("$.warnings[2]").value("NULL_METRICS_MEAN_UNKNOWN"))
                .andExpect(jsonPath("$.impressions").doesNotExist())
                .andExpect(jsonPath("$.spend").doesNotExist())
                .andReturn().getResponse();
        assertThat(missing.getHeader("ETag")).isNull();
        assertThat(missing.getHeader("Location")).isNull();
        assertThat(missing.getContentAsString()).doesNotContain("sourceFingerprint")
                .doesNotContain("platformAccountUuid")
                .doesNotContain("safeProviderTraceId")
                .doesNotContain("externalId\"");
        assertThat(readFake.invocationCount()).isEqualTo(before);

        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("PAUSED"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("W/\"")));
        assertThat(readFake.invocationCount()).isEqualTo(before);

        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshEligible").value(true))
                .andExpect(jsonPath("$.confirmable").value(true))
                .andExpect(jsonPath("$.warnings[0]").value("DETERMINISTIC_FAKE_ONLY"));
        assertThat(readFake.invocationCount()).isEqualTo(before);

        var first = mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(true))
                .andExpect(jsonPath("$.freshnessStatus").value("FRESH"))
                .andExpect(jsonPath("$.impressions").value(10000))
                .andExpect(jsonPath("$.reach").value(8000))
                .andExpect(jsonPath("$.clicks").value(100))
                .andExpect(jsonPath("$.conversions").value(4))
                .andExpect(jsonPath("$.spend").value("25.000000"))
                .andExpect(jsonPath("$.revenue").value("100.000000"))
                .andExpect(jsonPath("$.ctr").value("0.010000"))
                .andExpect(jsonPath("$.cpc").value("0.250000"))
                .andExpect(jsonPath("$.cpm").value("2.500000"))
                .andExpect(jsonPath("$.cpa").value("6.250000"))
                .andExpect(jsonPath("$.cvr").value("0.040000"))
                .andExpect(jsonPath("$.roas").value("4.000000"))
                .andReturn().getResponse();
        assertThat(first.getHeader("ETag")).isNull();
        assertThat(readFake.invocationCount()).isEqualTo(before + 1);
        assertThat(readFake.transactionObserved()).isFalse();
        JsonNode firstJson = mapper.readTree(first.getContentAsString());
        Instant windowStart = Instant.parse(firstJson.get("windowStart").asText());
        Instant windowEnd = Instant.parse(firstJson.get("windowEnd").asText());
        String expected = Stage4DMetricFingerprint.hash(PlatformEntityType.CAMPAIGN, UUID.fromString(campaign),
                windowStart, windowEnd, Optional.of(10_000L), Optional.of(8_000L), Optional.of(100L), Optional.of(4L),
                Optional.of(new BigDecimal("25.000000")), Optional.of(new BigDecimal("100.000000")), FreshnessStatus.FRESH);
        assertThat(jdbc.queryForObject(
                "SELECT source_fingerprint FROM platform_metric_snapshots WHERE platform_campaign_uuid=? AND revision_number=1",
                String.class, UUID.fromString(campaign))).isEqualTo(expected);

        waitForNextFetchSecond();
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.CORRECTED);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spend").value("26.000000"))
                .andExpect(jsonPath("$.revisionNumber").value(2));

        waitForNextFetchSecond();
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.SUCCESS);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spend").value("25.000000"))
                .andExpect(jsonPath("$.revisionNumber").value(1));
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spend").value("26.000000"))
                .andExpect(jsonPath("$.revisionNumber").value(2));
        assertThat(readFake.invocationCount()).isEqualTo(before + 3);

        Timestamp fetched = jdbc.queryForObject(
                "SELECT fetched_at FROM platform_metric_snapshots WHERE platform_campaign_uuid=? AND revision_number=1",
                Timestamp.class, UUID.fromString(campaign));
        String asOf = fetched.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics?asOf=" + asOf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.spend").value("25.000000"));
    }

    @Test
    void malformedAndThrowPersistNothingAndDeliveryNoOpThenUnavailableChangesObservation() throws Exception {
        String campaign = createCampaign();
        int audits = countAudits();
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.MALFORMED);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_CONTRACT_INVALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_metric_snapshots", Integer.class)).isZero();
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.THROW);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PLATFORM_ADAPTER_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_metric_snapshots", Integer.class)).isZero();
        assertThat(countAudits()).isEqualTo(audits);

        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.SUCCESS);
        long version = jdbc.queryForObject("SELECT version FROM platform_campaigns WHERE platform_campaign_uuid=?",
                Long.class, UUID.fromString(campaign));
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/delivery-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observedState").value("PAUSED"));
        assertThat(jdbc.queryForObject("SELECT version FROM platform_campaigns WHERE platform_campaign_uuid=?",
                Long.class, UUID.fromString(campaign))).isEqualTo(version);
        assertThat(countAudits()).isEqualTo(audits);

        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.UNAVAILABLE);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/delivery-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observedState").value("UNKNOWN"));
        assertThat(jdbc.queryForObject("SELECT observed_state FROM platform_campaigns WHERE platform_campaign_uuid=?",
                String.class, UUID.fromString(campaign))).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT desired_state FROM platform_campaigns WHERE platform_campaign_uuid=?",
                String.class, UUID.fromString(campaign))).isEqualTo("PAUSED");
        assertThat(countAudits()).isEqualTo(audits + 1);
    }

    @Test
    void requestShapeUnknownEntityPartialNullAndRefreshEligibility() throws Exception {
        String campaign = createCampaign();
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"));
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh").content("x"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")
                        .header(HttpHeaders.IF_MATCH, "W/\"1\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match"));
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/delivery?cursor=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("query"));
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics?asOf=not-an-instant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("query"));
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics?asOf=2099-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("query"));
        mvc.perform(get("/api/platform-entities/GROUP/" + campaign + "/delivery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"));
        mvc.perform(get("/api/platform-entities/CAMPAIGN/00000000-0000-4000-8000-0000000000aa/delivery"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLATFORM_RESOURCE_NOT_FOUND"));

        bypassEntityTrigger(UUID.fromString(campaign), "UPDATE platform_campaigns SET desired_state='ARCHIVED' WHERE platform_campaign_uuid=?");
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("ARCHIVED"));
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_ENTITY_ARCHIVED"));
        bypassEntityTrigger(UUID.fromString(campaign),
                "UPDATE platform_campaigns SET desired_state='PAUSED', external_id=NULL WHERE platform_campaign_uuid=?");
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/delivery-sync"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_DELIVERY_NOT_SYNCABLE"));

        UUID secondPlan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
                VALUES (?,'Stage 4D b',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
                """, secondPlan, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        plan = secondPlan;
        String other = createCampaign();
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.PARTIAL_NULL);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + other + "/metrics-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions").value(10000))
                .andExpect(jsonPath("$.clicks").value(100))
                .andExpect(jsonPath("$.ctr").value("0.010000"))
                .andExpect(jsonPath("$.spend").doesNotExist())
                .andExpect(jsonPath("$.roas").doesNotExist())
                .andExpect(jsonPath("$.reach").doesNotExist());
    }

    @Test
    void explainUsesAsOfOrRevisionIndexes() throws Exception {
        String campaign = createCampaign();
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")).andExpect(status().isOk());
        waitForNextFetchSecond();
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.CORRECTED);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")).andExpect(status().isOk());
        waitForNextFetchSecond();
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.DELAYED);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh")).andExpect(status().isOk());
        UUID id = UUID.fromString(campaign);
        UUID account = jdbc.queryForObject(
                "SELECT platform_account_uuid FROM platform_campaigns WHERE platform_campaign_uuid=?", UUID.class, id);
        String latest = explain("""
                SELECT revision_number, fetched_at, freshness_status, impressions, reach, clicks, conversions, spend, revenue
                FROM platform_metric_snapshots
                WHERE platform_account_uuid='%s' AND entity_type='CAMPAIGN' AND platform_campaign_uuid='%s'
                  AND window_start=((platform_taipei_business_date(statement_timestamp())-1) AT TIME ZONE 'Asia/Taipei')
                  AND window_end=(platform_taipei_business_date(statement_timestamp()) AT TIME ZONE 'Asia/Taipei')
                  AND timezone='Asia/Taipei' AND attribution_click_days=7 AND attribution_view_days=1 AND currency='TWD'
                ORDER BY revision_number DESC LIMIT 1
                """.formatted(account, id));
        String asOf = explain("""
                SELECT revision_number, fetched_at, freshness_status, impressions, reach, clicks, conversions, spend, revenue
                FROM platform_metric_snapshots
                WHERE platform_account_uuid='%s' AND entity_type='CAMPAIGN' AND platform_campaign_uuid='%s'
                  AND window_start=((platform_taipei_business_date(statement_timestamp())-1) AT TIME ZONE 'Asia/Taipei')
                  AND window_end=(platform_taipei_business_date(statement_timestamp()) AT TIME ZONE 'Asia/Taipei')
                  AND timezone='Asia/Taipei' AND attribution_click_days=7 AND attribution_view_days=1 AND currency='TWD'
                  AND fetched_at<=statement_timestamp()
                ORDER BY revision_number DESC LIMIT 1
                """.formatted(account, id));
        assertThat(latest).containsAnyOf("idx_platform_metrics_campaign_as_of", "uq_platform_metrics_campaign_revision");
        assertThat(asOf).containsAnyOf("idx_platform_metrics_campaign_as_of", "uq_platform_metrics_campaign_revision");
    }

    private String createCampaign() throws Exception {
        String json = mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + plan
                        + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("entityUuid").asText();
    }

    private int countAudits() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);
    }

    private static void waitForNextFetchSecond() throws InterruptedException {
        Thread.sleep(1100);
    }

    private void bypassEntityTrigger(UUID entity, String sql) {
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

    private String explain(String sql) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SET LOCAL enable_seqscan = off");
                var lines = new StringBuilder();
                try (var rs = statement.executeQuery("EXPLAIN " + sql)) {
                    while (rs.next()) {
                        if (!lines.isEmpty()) lines.append('\n');
                        lines.append(rs.getString(1));
                    }
                }
                connection.commit();
                return lines.toString();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(auto);
            }
        });
    }
}
