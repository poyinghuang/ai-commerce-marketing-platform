package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
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
class Stage4CControllerIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;
    @Autowired DeterministicFakePlatformAdapter fake;
    UUID plan;

    @BeforeEach void fixture() {
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
        jdbc.execute("TRUNCATE platform_budget_reservations, platform_operation_batches, platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ads, platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs RESTART IDENTITY CASCADE");
        plan = UUID.randomUUID();
        jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
          VALUES (?,'Stage 4C',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
          """, plan, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
    }

    @Test void previewConfirmReadReplayPauseResumeAndErrorMatrix() throws Exception {
        String campaign = createCampaign();
        String adSet = createAdSet(campaign);
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        String etag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        UUID request = UUID.randomUUID();
        String body = createBody(request, evidence);

        var preview = mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview")
                        .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creativeMappingKey").value("APPROVED_IMAGE_ASSET_V1"))
                .andExpect(jsonPath("$.newAdDesiredState").value("PAUSED"))
                .andExpect(jsonPath("$.evidenceEligible").value(true))
                .andExpect(jsonPath("$.confirmable").value(true))
                .andExpect(jsonPath("$.warnings[0]").value("DETERMINISTIC_FAKE_ONLY"))
                .andExpect(jsonPath("$.warnings[1]").value("NO_REAL_PROVIDER_OR_SPEND"))
                .andExpect(jsonPath("$.warnings[2]").value("EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME"))
                .andReturn().getResponse();
        assertThat(preview.getHeader("ETag")).isNull();
        assertThat(preview.getHeader("Location")).isNull();
        assertThat(preview.getContentAsString()).doesNotContain("operation").doesNotContain("replay");

        var created = mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads")
                        .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.operationType").value("CREATE_AD"))
                .andExpect(jsonPath("$.entityType").value("AD"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("W/\"")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/platform-operations/")))
                .andReturn().getResponse();
        String createdJson = created.getContentAsString();
        var tree = mapper.readTree(createdJson);
        assertThat(tree.propertyNames()).doesNotContain("operation", "replay");
        String ad = tree.get("entityUuid").asText();

        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads")
                        .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationUuid").value(tree.get("operationUuid").asText()));

        var adGet = mvc.perform(get("/api/platforms/meta/ads/" + ad))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("PAUSED"))
                .andExpect(jsonPath("$.creativeMappingKey").value("APPROVED_IMAGE_ASSET_V1"))
                .andExpect(jsonPath("$.approvedChecksumFingerprint").isString())
                .andReturn().getResponse();
        assertThat(adGet.getHeader("ETag")).isEqualTo("W/\"1\"");
        assertThat(adGet.getContentAsString()).doesNotContain("externalId\"");

        String campaignEtag = mvc.perform(get("/api/platforms/meta/campaigns/" + campaign)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/resume").header("If-Match", campaignEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        String adSetEtag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/resume").header("If-Match", adSetEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());

        String adEtag = adGet.getHeader("ETag");
        UUID resumeRequest = UUID.randomUUID();
        mvc.perform(post("/api/platforms/meta/ads/" + ad + "/state/preview").header("If-Match", adEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + resumeRequest + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("AD"))
                .andExpect(jsonPath("$.targetDesiredState").value("ACTIVE"));
        mvc.perform(post("/api/platforms/meta/ads/" + ad + "/resume").header("If-Match", adEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + resumeRequest + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        adEtag = mvc.perform(get("/api/platforms/meta/ads/" + ad)).andExpect(jsonPath("$.desiredState").value("ACTIVE"))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ads/" + ad + "/pause").header("If-Match", adEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"PAUSED\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test void missingAndMalformedIfMatchAndUnknownAdAndParentState() throws Exception {
        String campaign = createCampaign();
        String adSet = createAdSet(campaign);
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        UUID request = UUID.randomUUID();
        String body = createBody(request, evidence);
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PLATFORM_IF_MATCH_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match"));
        mvc.perform(get("/api/platforms/meta/ads/00000000-0000-4000-8000-0000000000ad"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLATFORM_AD_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Platform Ad was not found"));
        String campaignEtag = mvc.perform(get("/api/platforms/meta/campaigns/" + campaign)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/resume").header("If-Match", campaignEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        String adSetEtag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/resume").header("If-Match", adSetEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        adSetEtag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview").header("If-Match", adSetEtag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_PARENT_STATE_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1\"", "W/\"01\"", "W/\"-1\"", "*", "W/\"1\", W/\"2\"", "w/\"1\"", " W/\"1\"", "W/\"9223372036854775808\""})
    void malformedIfMatchIsRequestInvalid(String token) throws Exception {
        String campaign = createCampaign();
        String adSet = createAdSet(campaign);
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview").header("If-Match", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID(), evidence)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Invalid If-Match"));
        mvc.perform(post("/api/platforms/meta/ads/" + UUID.randomUUID() + "/pause").header("If-Match", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"PAUSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Invalid If-Match"));
    }

    @ParameterizedTest
    @EnumSource(value = DeterministicFakePlatformAdapter.Scenario.class, names = {
            "RETRYABLE_RATE_LIMIT", "RETRYABLE_TEMPORARILY_UNAVAILABLE", "TERMINAL_VALIDATION",
            "TERMINAL_PERMISSION", "MALFORMED_RESULT", "AMBIGUOUS_TIMEOUT"})
    void createAdProviderOutcomesPersistThroughMockMvc(DeterministicFakePlatformAdapter.Scenario scenario) throws Exception {
        String campaign = createCampaign();
        String adSet = createAdSet(campaign);
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        String etag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        fake.useScenario(scenario);
        int calls = fake.invocationCount();
        var result = mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads")
                        .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), evidence)))
                .andReturn().getResponse();
        String expectedStatus = switch (scenario) {
            case RETRYABLE_RATE_LIMIT, RETRYABLE_TEMPORARILY_UNAVAILABLE -> "FAILED_RETRYABLE";
            case TERMINAL_VALIDATION, TERMINAL_PERMISSION -> "FAILED_TERMINAL";
            default -> "UNKNOWN_OUTCOME";
        };
        String expectedCode = switch (scenario) {
            case RETRYABLE_RATE_LIMIT -> "PLATFORM_RATE_LIMITED";
            case RETRYABLE_TEMPORARILY_UNAVAILABLE -> "PLATFORM_TEMPORARILY_UNAVAILABLE";
            case TERMINAL_VALIDATION -> "PLATFORM_VALIDATION_FAILED";
            case TERMINAL_PERMISSION -> "PLATFORM_PERMISSION_DENIED";
            default -> "PLATFORM_RESPONSE_AMBIGUOUS";
        };
        int expectedHttp = switch (scenario) {
            case RETRYABLE_RATE_LIMIT, RETRYABLE_TEMPORARILY_UNAVAILABLE -> 429;
            default -> 202;
        };
        assertThat(result.getStatus()).isEqualTo(expectedHttp);
        var tree = mapper.readTree(result.getContentAsString());
        assertThat(tree.get("status").asText()).isEqualTo(expectedStatus);
        assertThat(tree.get("operationType").asText()).isEqualTo("CREATE_AD");
        assertThat(tree.get("normalizedErrorCode").asText()).isEqualTo(expectedCode);
        assertThat(tree.propertyNames()).doesNotContain("operation", "replay");
        UUID operation = UUID.fromString(tree.get("operationUuid").asText());
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo(expectedStatus);
        assertThat(jdbc.queryForObject("SELECT normalized_error_code FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo(expectedCode);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_attempts WHERE operation_uuid=?", Integer.class, operation)).isEqualTo(1);
        assertThat(fake.invocationCount()).isEqualTo(calls + 1);
        if ("UNKNOWN_OUTCOME".equals(expectedStatus)) {
            fake.useScenario(DeterministicFakePlatformAdapter.Scenario.RECONCILE_FOUND);
            String opEtag = result.getHeader("ETag");
            mvc.perform(post("/api/platform-operations/" + operation + "/reconcile").header("If-Match", opEtag))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"));
            assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class, operation)).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject("SELECT external_id FROM platform_ads WHERE platform_ad_uuid=?", String.class, UUID.fromString(tree.get("entityUuid").asText()))).isNotBlank();
        }
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
    }

    @ParameterizedTest
    @EnumSource(value = DeterministicFakePlatformAdapter.Scenario.class, names = {
            "RETRYABLE_RATE_LIMIT", "TERMINAL_VALIDATION", "AMBIGUOUS_TIMEOUT"})
    void pauseAndResumeProviderOutcomesPersistThroughMockMvc(DeterministicFakePlatformAdapter.Scenario scenario) throws Exception {
        String campaign = createCampaign();
        String adSet = createAdSet(campaign);
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        String etag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        String created = mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads")
                        .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), evidence)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String ad = mapper.readTree(created).get("entityUuid").asText();
        String campaignEtag = mvc.perform(get("/api/platforms/meta/campaigns/" + campaign)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/resume").header("If-Match", campaignEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        String adSetEtag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/resume").header("If-Match", adSetEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted());
        String adEtag = mvc.perform(get("/api/platforms/meta/ads/" + ad)).andReturn().getResponse().getHeader("ETag");
        fake.useScenario(scenario);
        var resume = mvc.perform(post("/api/platforms/meta/ads/" + ad + "/resume").header("If-Match", adEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andReturn().getResponse();
        String expectedStatus = switch (scenario) {
            case RETRYABLE_RATE_LIMIT -> "FAILED_RETRYABLE";
            case TERMINAL_VALIDATION -> "FAILED_TERMINAL";
            default -> "UNKNOWN_OUTCOME";
        };
        int expectedHttp = scenario == DeterministicFakePlatformAdapter.Scenario.RETRYABLE_RATE_LIMIT ? 429 : 202;
        assertThat(resume.getStatus()).isEqualTo(expectedHttp);
        assertThat(mapper.readTree(resume.getContentAsString()).get("status").asText()).isEqualTo(expectedStatus);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class,
                UUID.fromString(mapper.readTree(resume.getContentAsString()).get("operationUuid").asText()))).isEqualTo(expectedStatus);
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
        adEtag = mvc.perform(get("/api/platforms/meta/ads/" + ad)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ads/" + ad + "/resume").header("If-Match", adEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"ACTIVE\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        String activeEtag = mvc.perform(get("/api/platforms/meta/ads/" + ad)).andExpect(jsonPath("$.desiredState").value("ACTIVE"))
                .andReturn().getResponse().getHeader("ETag");
        fake.useScenario(scenario);
        var pause = mvc.perform(post("/api/platforms/meta/ads/" + ad + "/pause").header("If-Match", activeEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"targetDesiredState\":\"PAUSED\"}"))
                .andReturn().getResponse();
        assertThat(pause.getStatus()).isEqualTo(expectedHttp);
        assertThat(mapper.readTree(pause.getContentAsString()).get("status").asText()).isEqualTo(expectedStatus);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_operations WHERE operation_uuid=?", String.class,
                UUID.fromString(mapper.readTree(pause.getContentAsString()).get("operationUuid").asText()))).isEqualTo(expectedStatus);
        fake.useScenario(DeterministicFakePlatformAdapter.Scenario.SUCCESS);
    }

    @Test void queryParametersAreRejectedOnAdRoutesWithQueryField() throws Exception {
        String campaign = createCampaign();
        String adSet = createAdSet(campaign);
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        String etag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview?account=x").header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID(), evidence)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("query"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Query parameters are not allowed"));
        mvc.perform(get("/api/platforms/meta/ads/" + UUID.randomUUID() + "?account=x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("query"));
    }

    @Test void unknownDuplicateAndNullFieldsAreRejectedOnAdRoutes() throws Exception {
        String campaign = createCampaign();
        String adSet = createAdSet(campaign);
        Evidence evidence = evidence(plan, UUID.fromString(adSet));
        String etag = mvc.perform(get("/api/platforms/meta/ad-sets/" + adSet)).andReturn().getResponse().getHeader("ETag");
        String valid = createBody(UUID.randomUUID(), evidence);
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview").header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(valid.replace("}", ",\"extraField\":\"x\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"));
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview").header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":\"" + UUID.randomUUID()
                                + "\",\"clientRequestUuid\":\"" + UUID.randomUUID()
                                + "\",\"productUuid\":\"" + evidence.product()
                                + "\",\"assetUuid\":\"" + evidence.asset()
                                + "\",\"generationOutputUuid\":\"" + evidence.output()
                                + "\",\"reviewDecisionUuid\":\"" + evidence.review() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLATFORM_REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("body"));
        mvc.perform(post("/api/platforms/meta/ad-sets/" + adSet + "/ads/preview").header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestUuid\":null,\"productUuid\":\"" + evidence.product()
                                + "\",\"assetUuid\":\"" + evidence.asset()
                                + "\",\"generationOutputUuid\":\"" + evidence.output()
                                + "\",\"reviewDecisionUuid\":\"" + evidence.review() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("clientRequestUuid"));
    }

    private String createCampaign() throws Exception {
        String json = mvc.perform(post("/api/platforms/meta/campaigns").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"campaignUuid\":\"" + plan + "\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private String createAdSet(String campaign) throws Exception {
        String etag = mvc.perform(get("/api/platforms/meta/campaigns/" + campaign)).andReturn().getResponse().getHeader("ETag");
        String json = mvc.perform(post("/api/platforms/meta/campaigns/" + campaign + "/ad-sets").header("If-Match", etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestUuid\":\"" + UUID.randomUUID() + "\",\"budgetType\":\"DAILY\",\"budgetAmount\":\"25\",\"expectedCampaignPlanVersion\":0}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\\\"entityUuid\\\":\\\"([^\\\"]+)\\\".*", "$1");
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
        jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status) VALUES (?,?,'Stage 4C evidence','ACTIVE')",
                product, "PROD-" + String.format("%08d", Math.abs(product.hashCode()) % 100_000_000));
        jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) VALUES (?,?,?)",
                UUID.randomUUID(), campaignPlan, product);
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','SOURCE',?)",
                source, product, campaignPlan, "d".repeat(64));
        jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','GENERATED',?)",
                asset, product, campaignPlan, "e".repeat(64));
        jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,?,'IMAGE','4C image')",
                template, "stage4c." + template);
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
