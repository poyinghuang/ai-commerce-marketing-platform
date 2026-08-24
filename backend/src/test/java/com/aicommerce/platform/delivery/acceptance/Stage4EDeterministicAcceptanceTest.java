package com.aicommerce.platform.delivery.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.PlatformOperationService;
import com.aicommerce.platform.delivery.application.PlatformOperationTransactions;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage4EDeterministicAcceptanceTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DeterministicFakePlatformAdapter fake;
    @Autowired DeterministicFakePlatformReadAdapter readFake;
    @Autowired PlatformOperationService operations;
    @Autowired PlatformOperationTransactions operationTx;
    UUID plan;

    @BeforeEach
    void fixture() {
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
        readFake.reset();
        jdbc.execute("""
                TRUNCATE platform_metric_snapshots, platform_budget_reservations, platform_operation_batches,
                         platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ads,
                         platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs
                RESTART IDENTITY CASCADE
                """);
        plan = newPlan("Stage 4E");
    }

    @Test
    void idempotencyReplayKeepsExistingOperationWithoutCapacityChange() throws Exception {
        UUID request = UUID.randomUUID();
        String body = "{\"clientRequestUuid\":\"" + request + "\",\"campaignUuid\":\"" + plan
                + "\",\"expectedCampaignPlanVersion\":0}";
        String created = mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String campaign = mapper.readTree(created).get("entityUuid").asText();
        String operation = mapper.readTree(created).get("operationUuid").asText();
        int audit = count("audit_logs");
        int batches = count("platform_operation_batches");
        mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationUuid").value(operation));
        assertThat(count("audit_logs")).isEqualTo(audit);
        assertThat(count("platform_operation_batches")).isEqualTo(batches);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform_operation_batches WHERE client_request_uuid=?", Integer.class, request))
                .isEqualTo(1);

        String originalEtag = etag("/api/platforms/meta/campaigns/" + campaign);
        UUID adSetRequest = UUID.randomUUID();
        String adSetBody = "{\"clientRequestUuid\":\"" + adSetRequest
                + "\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"50\",\"expectedCampaignPlanVersion\":0}";
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/ad-sets").header("If-Match", originalEtag)
                        .contentType(MediaType.APPLICATION_JSON).content(adSetBody))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/resume").header("If-Match", originalEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/ad-sets").header("If-Match", originalEtag)
                        .contentType(MediaType.APPLICATION_JSON).content(adSetBody))
                .andExpect(status().isOk());
        int operations = count("platform_operations");
        int reservations = count("platform_budget_reservations");
        String currentEtag = etag("/api/platforms/meta/campaigns/" + campaign);
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/ad-sets").header("If-Match", currentEtag)
                        .contentType(MediaType.APPLICATION_JSON).content(adSetBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_IDEMPOTENCY_CONFLICT"));
        assertThat(count("platform_operations")).isEqualTo(operations);
        assertThat(count("platform_budget_reservations")).isEqualTo(reservations);

        String pausedOne = createCampaign(newPlan("Stage 4E state a"));
        String pausedTwo = createCampaign(newPlan("Stage 4E state b"));
        UUID stateRequest = UUID.randomUUID();
        String stateBody = "{\"clientRequestUuid\":\"" + stateRequest + "\",\"targetDesiredState\":\"ACTIVE\"}";
        mvc.perform(post("/api/platforms/meta/campaigns/" + pausedOne + "/resume")
                        .header("If-Match", etag("/api/platforms/meta/campaigns/" + pausedOne))
                        .contentType(MediaType.APPLICATION_JSON).content(stateBody))
                .andExpect(status().isAccepted());
        int afterStateReplay = count("platform_operations");
        mvc.perform(post("/api/platforms/meta/campaigns/" + pausedTwo + "/resume")
                        .header("If-Match", etag("/api/platforms/meta/campaigns/" + pausedTwo))
                        .contentType(MediaType.APPLICATION_JSON).content(stateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_IDEMPOTENCY_CONFLICT"));
        assertThat(count("platform_operations")).isEqualTo(afterStateReplay);

        String budgetCampaign = createCampaign(newPlan("Stage 4E budget bind"));
        String first = createAdSet(budgetCampaign, "10");
        String second = createAdSet(budgetCampaign, "10");
        UUID budgetRequest = UUID.randomUUID();
        String budgetBody = "{\"clientRequestUuid\":\"" + budgetRequest + "\",\"newBudgetAmount\":\"20\"}";
        mvc.perform(post("/api/platforms/meta/ad-sets/" + first + "/budget")
                        .header("If-Match", etag("/api/platforms/meta/ad-sets/" + first))
                        .contentType(MediaType.APPLICATION_JSON).content(budgetBody))
                .andExpect(status().isAccepted());
        int afterBudget = count("platform_operations");
        int afterBudgetReservations = count("platform_budget_reservations");
        mvc.perform(post("/api/platforms/meta/ad-sets/" + second + "/budget")
                        .header("If-Match", etag("/api/platforms/meta/ad-sets/" + second))
                        .contentType(MediaType.APPLICATION_JSON).content(budgetBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_IDEMPOTENCY_CONFLICT"));
        assertThat(count("platform_operations")).isEqualTo(afterBudget);
        assertThat(count("platform_budget_reservations")).isEqualTo(afterBudgetReservations);
    }

    @Test
    void ambiguousReconciliationDoesNotAutoFireAndStaleRecoverCallsZeroAdapter() throws Exception {
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.AMBIGUOUS_TIMEOUT);
        String created = mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + plan
                                + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UNKNOWN_OUTCOME"))
                .andReturn().getResponse().getContentAsString();
        String campaign = mapper.readTree(created).get("entityUuid").asText();
        UUID operation = UUID.fromString(mapper.readTree(created).get("operationUuid").asText());
        int attempts = attempts(operation);
        int calls = fake.invocationCount();
        mvc.perform(get("/api/platforms/meta/campaigns/" + campaign)).andExpect(status().isOk());
        mvc.perform(get("/api/platform-operations/" + operation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN_OUTCOME"));
        assertThat(attempts(operation)).isEqualTo(attempts);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=? AND attempt_kind='RECONCILE'",
                Integer.class, operation)).isZero();
        assertThat(fake.invocationCount()).isEqualTo(calls);

        var current = operationTx.get(operation);
        operationTx.claimReconciliation(operation, current.getVersion(), Instant.now(),
                operationTx.operationContext(operation));
        var claimed = operationTx.get(operation);
        var recovered = operations.recoverStaleClaim(operation, claimed.getVersion(),
                claimed.getClaimedAt().plusSeconds(300));
        assertThat(recovered.status()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
        assertThat(recovered.normalizedErrorCode()).contains(PlatformStableErrorCode.PLATFORM_RECONCILIATION_INCONCLUSIVE);
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    @Test
    void staleEntityIfMatchOnPauseResumeAndBudgetCreatesZeroAdapterCallsAndLedgerRows() throws Exception {
        String campaign = createCampaign(plan);
        String adSet = createAdSet(campaign, "10");
        String campaignEtag = etag("/api/platforms/meta/campaigns/" + campaign);
        String adSetEtag = etag("/api/platforms/meta/ad-sets/" + adSet);
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/resume").header("If-Match", campaignEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/budget").header("If-Match", adSetEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"newBudgetAmount\":\"20\"}"))
                .andExpect(status().isAccepted());
        int calls = fake.invocationCount();
        int reservations = count("platform_budget_reservations");
        int days = count("platform_account_budget_days");
        int operationRows = count("platform_operations");
        staleState("/api/platforms/meta/campaigns/" + campaign + "/pause", campaignEtag, "PAUSED");
        staleState("/api/platforms/meta/campaigns/" + campaign + "/resume", campaignEtag, "ACTIVE");
        staleState("/api/platforms/meta/ad-sets/" + adSet + "/pause", adSetEtag, "PAUSED");
        staleState("/api/platforms/meta/ad-sets/" + adSet + "/resume", adSetEtag, "ACTIVE");
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/budget").header("If-Match", adSetEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"newBudgetAmount\":\"30\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PLATFORM_ENTITY_STALE"));
        assertThat(fake.invocationCount()).isEqualTo(calls);
        assertThat(count("platform_budget_reservations")).isEqualTo(reservations);
        assertThat(count("platform_account_budget_days")).isEqualTo(days);
        assertThat(count("platform_operations")).isEqualTo(operationRows);
    }

    @Test
    void budgetOverCeilingConfirmRejectsWithoutAttemptOrProviderCall() throws Exception {
        String campaign = createCampaign(plan);
        String etag = etag("/api/platforms/meta/campaigns/" + campaign);
        int calls = fake.invocationCount();
        int attempts = count("platform_operation_attempts");
        int reservations = count("platform_budget_reservations");
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/ad-sets").header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID()
                                + "\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"101\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_POLICY_REJECTED"));
        assertThat(fake.invocationCount()).isEqualTo(calls);
        assertThat(count("platform_operation_attempts")).isEqualTo(attempts);
        assertThat(count("platform_budget_reservations")).isEqualTo(reservations);

        for (int i = 0; i < 3; i++) {
            createAdSet(createCampaign(newPlan("Stage 4E cap " + i)), "300", "LIFETIME");
        }
        createAdSet(createCampaign(newPlan("Stage 4E cap daily")), "100", "DAILY");
        String capped = createCampaign(newPlan("Stage 4E cap final"));
        int capCalls = fake.invocationCount();
        int capAttempts = count("platform_operation_attempts");
        int capReservations = count("platform_budget_reservations");
        mvc.perform(post("/api/platforms/meta/campaigns/" + capped + "/ad-sets")
                        .header("If-Match", etag("/api/platforms/meta/campaigns/" + capped))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID()
                                + "\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"1\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_BUDGET_CAP_EXCEEDED"));
        assertThat(fake.invocationCount()).isEqualTo(capCalls);
        assertThat(count("platform_operation_attempts")).isEqualTo(capAttempts);
        assertThat(count("platform_budget_reservations")).isEqualTo(capReservations);
    }

    @Test
    void approvalEnforcementBlocksCreateWithoutApprovedImageAsset() throws Exception {
        String campaign = createCampaign(plan);
        String adSet = createAdSet(campaign, "25");
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        jdbc.update("""
                UPDATE products SET lifecycle_status='ARCHIVED', archived_at=statement_timestamp(),
                  updated_at=statement_timestamp(), version=version+1 WHERE product_uuid=?
                """, evidence.product);
        int calls = fake.invocationCount();
        int adAttempts = jdbc.queryForObject(
                "SELECT count(*) FROM platform_operation_attempts a JOIN platform_operations o ON o.operation_uuid=a.operation_uuid WHERE o.operation_type='CREATE_AD'",
                Integer.class);
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads")
                        .header("If-Match", etag("/api/platforms/meta/ad-sets/" + adSet))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID(), evidence)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_AD_EVIDENCE_INVALID"));
        assertThat(fake.invocationCount()).isEqualTo(calls);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform_operation_attempts a JOIN platform_operations o ON o.operation_uuid=a.operation_uuid WHERE o.operation_type='CREATE_AD'",
                Integer.class)).isEqualTo(adAttempts);

        UUID livePlan = newPlan("Stage 4E live ad");
        String liveCampaign = createCampaign(livePlan);
        String liveAdSet = createAdSet(liveCampaign, "25");
        Evidence live = evidence(livePlan, UUID.fromString(liveAdSet));
        String created = mvc.perform(post("/api/platforms/meta/ad-sets/" + liveAdSet + "/ads")
                        .header("If-Match", etag("/api/platforms/meta/ad-sets/" + liveAdSet))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID(), live)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String ad = mapper.readTree(created).get("entityUuid").asText();
        mvc.perform(post("/api/platforms/meta/campaigns/" + liveCampaign + "/resume")
                        .header("If-Match", etag("/api/platforms/meta/campaigns/" + liveCampaign))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/ad-sets/" + liveAdSet + "/resume")
                        .header("If-Match", etag("/api/platforms/meta/ad-sets/" + liveAdSet))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/platforms/meta/ads/" + ad + "/resume")
                        .header("If-Match", etag("/api/platforms/meta/ads/" + ad))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        jdbc.update("UPDATE assets SET checksum_sha256=?, updated_at=statement_timestamp(), version=version+1 WHERE asset_uuid=?",
                "c".repeat(64), live.asset);
        mvc.perform(post("/api/platforms/meta/ads/" + ad + "/pause")
                        .header("If-Match", etag("/api/platforms/meta/ads/" + ad))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"PAUSED\"}"))
                .andExpect(status().isAccepted());
        int afterPause = fake.invocationCount();
        mvc.perform(post("/api/platforms/meta/ads/" + ad + "/resume")
                        .header("If-Match", etag("/api/platforms/meta/ads/" + ad))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_AD_EVIDENCE_INVALID"));
        assertThat(fake.invocationCount()).isEqualTo(afterPause);
    }

    @Test
    void pauseResumeCreateStaysPausedAndResumeNeedsSecondConfirm() throws Exception {
        UUID request = UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/campaigns/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + request + "\",\"campaignUuid\":\"" + plan + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("PAUSED"));
        assertThat(count("platform_operations")).isZero();
        String campaign = createCampaign(plan, request);
        mvc.perform(get("/api/platforms/meta/campaigns/" + campaign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("PAUSED"));
        int operations = count("platform_operations");
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/state-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("ACTIVE"));
        mvc.perform(get("/api/platforms/meta/campaigns/" + campaign))
                .andExpect(jsonPath("$.desiredState").value("PAUSED"));
        assertThat(count("platform_operations")).isEqualTo(operations);
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/resume")
                        .header("If-Match", etag("/api/platforms/meta/campaigns/" + campaign))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/platforms/meta/campaigns/" + campaign))
                .andExpect(jsonPath("$.desiredState").value("ACTIVE"));
        assertThat(count("platform_operations")).isEqualTo(operations + 1);
    }

    @Test
    void metricsGetIsPostgresOnlyAndRefreshIsExplicitSuccessCorrectedReplay() throws Exception {
        String campaign = createCampaign(plan);
        int before = readFake.invocationCount();
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(false))
                .andExpect(jsonPath("$.impressions").doesNotExist())
                .andExpect(jsonPath("$.spend").doesNotExist())
                .andExpect(jsonPath("$.warnings[2]").value("NULL_METRICS_MEAN_UNKNOWN"));
        mvc.perform(get("/api/platform-entities/CAMPAIGN/" + campaign + "/delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("PAUSED"));
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmable").value(true));
        assertThat(readFake.invocationCount()).isEqualTo(before);

        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spend").value("25.000000"))
                .andExpect(jsonPath("$.revisionNumber").value(1));
        assertThat(readFake.invocationCount()).isEqualTo(before + 1);
        Thread.sleep(1100);
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.CORRECTED);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + campaign + "/metrics-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spend").value("26.000000"))
                .andExpect(jsonPath("$.revisionNumber").value(2));
        Thread.sleep(1100);
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

        String other = createCampaign(newPlan("Stage 4E metrics null"));
        readFake.useScenario(DeterministicFakePlatformReadAdapter.Scenario.PARTIAL_NULL);
        mvc.perform(post("/api/platform-entities/CAMPAIGN/" + other + "/metrics-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions").value(10000))
                .andExpect(jsonPath("$.spend").doesNotExist())
                .andExpect(jsonPath("$.roas").doesNotExist());
    }

    @Test
    void noAiDirectWriteToPlatformPorts() throws Exception {
        new AiHasNoPlatformWritePathTest().aiPackageHasNoPlatformWriteOrRefreshPorts();
    }

    private void staleState(String path, String etag, String target) throws Exception {
        mvc.perform(post(path).header("If-Match", etag).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"" + target + "\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PLATFORM_ENTITY_STALE"));
    }

    private String createCampaign(UUID campaignPlan) throws Exception {
        return createCampaign(campaignPlan, UUID.randomUUID());
    }

    private String createCampaign(UUID campaignPlan, UUID request) throws Exception {
        String json = mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + request + "\",\"campaignUuid\":\"" + campaignPlan
                                + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("entityUuid").asText();
    }

    private String createAdSet(String campaign, String amount) throws Exception {
        return createAdSet(campaign, amount, "DAILY");
    }

    private String createAdSet(String campaign, String amount, String budgetType) throws Exception {
        String json = mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/ad-sets")
                        .header("If-Match", etag("/api/platforms/meta/campaigns/" + campaign))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"budgetType\":\"" + budgetType
                                + "\",\"budgetAmount\":\"" + amount + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("entityUuid").asText();
    }

    private UUID newPlan(String name) {
        UUID value = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
                VALUES (?,?,?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
                """, value, name, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        return value;
    }

    private String etag(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int attempts(UUID operation) {
        return jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?",
                Integer.class, operation);
    }

    private String createBody(UUID request, Evidence evidence) {
        return "{\"clientRequestUuid\":\"" + request + "\",\"productUuid\":\"" + evidence.product
                + "\",\"assetUuid\":\"" + evidence.asset + "\",\"generationOutputUuid\":\"" + evidence.output
                + "\",\"reviewDecisionUuid\":\"" + evidence.review + "\"}";
    }

    private Evidence evidence(UUID campaignPlan, UUID adSet) {
        UUID product = UUID.randomUUID(), source = UUID.randomUUID(), asset = UUID.randomUUID(), template = UUID.randomUUID(),
                templateVersion = UUID.randomUUID(), batch = UUID.randomUUID(), job = UUID.randomUUID(),
                output = UUID.randomUUID(), review = UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status) VALUES (?,?,'Stage 4E evidence','ACTIVE')",
                product, "PROD-" + String.format("%08d", Math.abs(product.hashCode()) % 100_000_000));
        jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) VALUES (?,?,?)",
                UUID.randomUUID(), campaignPlan, product);
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','SOURCE',?)",
                source, product, campaignPlan, "d".repeat(64));
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','GENERATED',?)",
                asset, product, campaignPlan, "e".repeat(64));
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,?,'IMAGE','4E image')",
                template, "stage4e." + template);
        jdbc.update("INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) VALUES (?,?,1,'image','{}'::jsonb,?,'sql')",
                templateVersion, template, "a".repeat(64));
        jdbc.update("INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) VALUES (?,?,'COMPLETED','TWD',0,0,1,1,'sql')",
                batch, product);
        jdbc.update("INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) VALUES (?,?,?,?,'IMAGE','stub','stub','SUCCEEDED','image','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)",
                job, batch, product, templateVersion);
        jdbc.update("""
                INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,model_label,input_units,output_units,actual_cost,currency,safety_findings,provider_metadata,source_asset_uuid,generated_asset_uuid,generation_mode,workflow_key,workflow_version,image_width,image_height,media_type,size_bytes,source_checksum_sha256,output_checksum_sha256,protected_pixels_sha256,preservation_algorithm,preservation_status,preservation_details)
                VALUES (?,?,?,?,'IMAGE','stub',0,0,0,'TWD','[]'::jsonb,'{}'::jsonb,?,?,'BACKGROUND_COMPOSITE','sql-v1','1',1,1,'image/png',1,?,?,?,'RGBA_MASK_EXACT_V1','PASSED','{"changedPixelCount":0,"protectedPixelCount":1}'::jsonb)
                """, output, job, batch, product, source, asset, "d".repeat(64), "e".repeat(64), "f".repeat(64));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) VALUES (?,?,'APPROVED','LOCAL_ADMIN','sql','sql-review',0,current_timestamp)",
                    review, output);
            jdbc.update("UPDATE ai_generation_outputs SET review_status='APPROVED',version=1 WHERE generation_output_uuid=?", output);
        });
        return new Evidence(product, asset, output, review);
    }

    private record Evidence(UUID product, UUID asset, UUID output, UUID review) {}
}
