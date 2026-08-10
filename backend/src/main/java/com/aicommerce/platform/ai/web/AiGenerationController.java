package com.aicommerce.platform.ai.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.application.AiBudgetPolicy;
import com.aicommerce.platform.ai.application.AiBudgetPolicyProvider;
import com.aicommerce.platform.ai.application.CreateTextGenerationBatchCommand;
import com.aicommerce.platform.ai.application.GenerationFoundationResult;
import com.aicommerce.platform.ai.application.TextGenerationService;
import com.aicommerce.platform.ai.application.ImageGenerationService;
import com.aicommerce.platform.ai.application.ReviewDecisionService;
import com.aicommerce.platform.ai.application.CreateImageGenerationBatchCommand;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import com.aicommerce.platform.product.web.ProductEtag;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class AiGenerationController {

    private final TextGenerationService service;
    private final ImageGenerationService images;
    private final AiBudgetPolicyProvider budgetPolicies;
    private final ReviewDecisionService reviews;
    private final ObjectMapper objectMapper;

    public AiGenerationController(TextGenerationService service, ImageGenerationService images,
            AiBudgetPolicyProvider budgetPolicies, ReviewDecisionService reviews,
            ObjectMapper objectMapper) {
        this.service = service;
        this.images = images;
        this.budgetPolicies = budgetPolicies;
        this.reviews = reviews;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/products/{productUuid}/ai-generation-batches")
    public ResponseEntity<AiGenerationResponse.Batch> create(
            @PathVariable UUID productUuid, @Valid @RequestBody CreateTextGenerationBatchRequest request,
            HttpServletRequest servletRequest) {
        boolean image = "IMAGE".equals(request.generationType());
        int count = request.variationCount() == null ? (image ? 1 : 3) : request.variationCount();
        if (image && count != 1) throw new com.aicommerce.platform.ai.application.AiGenerationException(
                "AI_PROMPT_INPUT_INVALID", "Image batch count must be one");
        GenerationFoundationResult result = image
                ? images.createBatch(new CreateImageGenerationBatchCommand(productUuid, request.creativePlanUuid(),
                        request.templateKey(), request.workflowKey(), request.modelProfile(),
                        request.sourceAssetUuid(), request.maskAssetUuid()), requestId(servletRequest))
                : service.createBatch(new CreateTextGenerationBatchCommand(
                        productUuid, request.creativePlanUuid(), request.templateKey(), request.modelProfile(), count),
                        requestId(servletRequest));
        List<AiGenerationResponse.Job> jobs = result.jobs().stream()
                .map(job -> AiGenerationResponse.Job.from(job, null)).toList();
        URI location = URI.create("/api/ai-generation-batches/" + result.batch().getGenerationBatchUuid());
        return ResponseEntity.created(location).eTag(ProductEtag.fromVersion(result.batch().getVersion()))
                .body(AiGenerationResponse.Batch.from(result.batch(), jobs));
    }

    @GetMapping("/products/{productUuid}/ai-generation-batches")
    public List<AiGenerationResponse.Batch> list(@PathVariable UUID productUuid) {
        return service.listBatches(productUuid).stream().map(batch -> AiGenerationResponse.Batch.from(batch,
                service.getBatchJobs(batch.getGenerationBatchUuid()).stream().map(this::jobResponse).toList())).toList();
    }

    @GetMapping("/ai-generation-batches/{batchUuid}")
    public ResponseEntity<AiGenerationResponse.Batch> batch(@PathVariable UUID batchUuid) {
        var batch = service.getBatch(batchUuid);
        var response = AiGenerationResponse.Batch.from(batch,
                service.getBatchJobs(batchUuid).stream().map(this::jobResponse).toList());
        return ResponseEntity.ok().eTag(ProductEtag.fromVersion(batch.getVersion())).body(response);
    }

    @GetMapping("/ai-generation-jobs/{jobUuid}")
    public ResponseEntity<AiGenerationResponse.Job> job(@PathVariable UUID jobUuid) {
        GenerationJob job = service.getJob(jobUuid);
        return ResponseEntity.ok().eTag(ProductEtag.fromVersion(job.getVersion())).body(jobResponse(job));
    }

    @PostMapping("/ai-generation-jobs/{jobUuid}/execute")
    public ResponseEntity<AiGenerationResponse.Output> execute(@PathVariable UUID jobUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            HttpServletRequest servletRequest) {
        long expectedVersion = ProductEtag.requireVersion(ifMatch);
        GenerationJob job = service.getJob(jobUuid);
        GenerationOutput output = job.getGenerationType() == GenerationType.IMAGE
                ? images.execute(jobUuid, expectedVersion, requestId(servletRequest))
                : service.execute(jobUuid, expectedVersion, requestId(servletRequest));
        return outputResponse(reviews.details(output));
    }

    @GetMapping("/ai-generation-outputs/{outputUuid}")
    public ResponseEntity<AiGenerationResponse.Output> output(@PathVariable UUID outputUuid) {
        GenerationOutput output = service.getOutput(outputUuid);
        return outputResponse(reviews.details(output));
    }

    @PostMapping("/ai-generation-outputs/{outputUuid}/approve")
    public ResponseEntity<AiGenerationResponse.Output> approve(@PathVariable UUID outputUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) tools.jackson.databind.JsonNode body, HttpServletRequest request) {
        if (body != null && (!body.isObject() || !body.isEmpty())) {
            throw new com.aicommerce.platform.ai.application.AiGenerationException(
                    "AI_PROMPT_INPUT_INVALID", "Approval body must be an empty object");
        }
        return outputResponse(reviews.approve(outputUuid, ProductEtag.requireVersion(ifMatch), requestId(request)));
    }

    @PostMapping("/ai-generation-outputs/{outputUuid}/reject")
    public ResponseEntity<AiGenerationResponse.Output> reject(@PathVariable UUID outputUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody tools.jackson.databind.JsonNode body, HttpServletRequest request) {
        if (!body.isObject() || body.size() != 1 || !body.has("reason") || !body.get("reason").isString()) {
            throw new com.aicommerce.platform.ai.application.AiGenerationException(
                    "AI_REVIEW_REASON_REQUIRED", "Rejection requires only a textual reason");
        }
        String reason = body.get("reason").asText();
        return outputResponse(reviews.reject(outputUuid, ProductEtag.requireVersion(ifMatch), reason, requestId(request)));
    }

    private ResponseEntity<AiGenerationResponse.Output> outputResponse(ReviewDecisionService.ReviewDetails details) {
        GenerationOutput output = details.output();
        return ResponseEntity.ok().eTag(ProductEtag.fromVersion(output.getVersion()))
                .body(AiGenerationResponse.Output.from(output, objectMapper, details.blockers(), details.decision()));
    }

    @GetMapping("/ai-budget/status")
    public BudgetStatus budgetStatus() {
        AiBudgetPolicy policy = budgetPolicies.currentPolicy();
        return new BudgetStatus(policy.currency(), policy.maximumJobCost(), policy.maximumBatchCost(),
                policy.maximumDailyCost(), service.availableModelProfiles(),
                service.textTemplateKeys(), images.availableModelProfiles(), images.imageTemplateKeys(),
                images.availableWorkflowKeys());
    }

    private AiGenerationResponse.Job jobResponse(GenerationJob job) {
        return AiGenerationResponse.Job.from(job, service.getJobOutput(job.getGenerationJobUuid()).orElse(null));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
    }

    public record BudgetStatus(String currency, java.math.BigDecimal maximumJobCost,
            java.math.BigDecimal maximumBatchCost, java.math.BigDecimal maximumDailyCost,
            List<String> modelProfiles, List<String> textTemplateKeys,
            List<String> imageModelProfiles, List<String> imageTemplateKeys,
            List<String> imageWorkflowKeys) {
    }
}
