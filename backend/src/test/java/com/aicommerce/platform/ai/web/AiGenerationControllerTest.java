package com.aicommerce.platform.ai.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.application.AiBudgetPolicyProvider;
import com.aicommerce.platform.ai.application.AiGenerationException;
import com.aicommerce.platform.ai.application.CreateTextGenerationBatchCommand;
import com.aicommerce.platform.ai.application.GenerationFoundationResult;
import com.aicommerce.platform.ai.application.TextGenerationService;
import com.aicommerce.platform.ai.domain.GenerationBatch;
import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiGenerationController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class AiGenerationControllerTest {

    static final UUID PRODUCT = UUID.fromString("d4476a19-30ed-48d9-a518-f9b111bd0911");
    static final UUID PLAN = UUID.fromString("79be8758-1f0d-4ca5-bad6-f51aa923cdb9");
    static final UUID TEMPLATE_VERSION = UUID.fromString("5cf53b23-eabe-4b51-b565-62dbe4333721");
    static final UUID BATCH = UUID.fromString("ec3866a0-dc78-4db7-ab76-f13a91b94bdd");
    static final UUID JOB = UUID.fromString("cf15254e-b6ee-4824-bf02-16fa1f02e8be");
    static final UUID OUTPUT = UUID.fromString("a54e5b68-8cd7-43ef-8ee0-95bbba6c3190");

    @Autowired MockMvc mvc;
    @MockitoBean TextGenerationService service;
    @MockitoBean AiBudgetPolicyProvider budgetPolicies;

    @Test
    void createDefaultsToThreeVariationsAndReturnsLocationAndEtag() throws Exception {
        GenerationBatch batch = GenerationBatch.create(BATCH, PRODUCT, PLAN, "USD",
                new BigDecimal("1.500000"), new BigDecimal("6.000000"), 3, "local-admin");
        when(service.createBatch(any(), anyString())).thenReturn(new GenerationFoundationResult(
                batch, List.of(job(JOB)), true, null));

        mvc.perform(post("/api/products/{productUuid}/ai-generation-batches", PRODUCT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"creativePlanUuid":"%s","templateKey":"copy.default","modelProfile":"STANDARD"}
                                """.formatted(PLAN)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/ai-generation-batches/" + BATCH))
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"0\""))
                .andExpect(jsonPath("$.requestedJobCount").value(3));
        ArgumentCaptor<CreateTextGenerationBatchCommand> command =
                ArgumentCaptor.forClass(CreateTextGenerationBatchCommand.class);
        verify(service).createBatch(command.capture(), anyString());
        org.assertj.core.api.Assertions.assertThat(command.getValue().variationCount()).isEqualTo(3);
    }

    @Test
    void executeRequiresStrictWeakEtag() throws Exception {
        mvc.perform(post("/api/ai-generation-jobs/{jobUuid}/execute", JOB))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        mvc.perform(post("/api/ai-generation-jobs/{jobUuid}/execute", JOB)
                        .header(HttpHeaders.IF_MATCH, "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
        verifyNoInteractions(service);
    }

    @Test
    void executeForwardsVersionAndReturnsPendingReviewOutput() throws Exception {
        GenerationOutput output = GenerationOutput.createText(OUTPUT, JOB, BATCH, PRODUCT, "Generated copy",
                "stub-text", 1, 2, BigDecimal.ZERO, "USD", "[]", "{}");
        when(service.execute(eq(JOB), eq(2L), anyString())).thenReturn(output);
        mvc.perform(post("/api/ai-generation-jobs/{jobUuid}/execute", JOB)
                        .header(HttpHeaders.IF_MATCH, "W/\"2\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"0\""))
                .andExpect(jsonPath("$.generationOutputUuid").value(OUTPUT.toString()))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"));
    }

    @Test
    void staleExecutionUsesStableErrorContract() throws Exception {
        when(service.execute(eq(JOB), eq(1L), anyString())).thenThrow(new AiGenerationException(
                "AI_GENERATION_PRECONDITION_FAILED", "Generation job version is stale"));
        mvc.perform(post("/api/ai-generation-jobs/{jobUuid}/execute", JOB)
                        .header(HttpHeaders.IF_MATCH, "W/\"1\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_GENERATION_PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/ai-generation-jobs/" + JOB + "/execute"));
    }

    private GenerationJob job(UUID id) {
        return GenerationJob.create(id, BATCH, PRODUCT, PLAN, TEMPLATE_VERSION, GenerationType.TEXT,
                "stub", "stub-text", "Prompt", null, "{}", new BigDecimal("0.500000"),
                new BigDecimal("2.000000"), "USD");
    }
}
