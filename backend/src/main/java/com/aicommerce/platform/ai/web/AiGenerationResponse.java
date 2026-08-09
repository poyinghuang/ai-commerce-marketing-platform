package com.aicommerce.platform.ai.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationBatch;
import com.aicommerce.platform.ai.domain.GenerationJob;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class AiGenerationResponse {

    private AiGenerationResponse() {
    }

    public record Batch(
            UUID generationBatchUuid, UUID productUuid, UUID creativePlanUuid, String status,
            String currency, BigDecimal estimatedCost, BigDecimal reservedCost, BigDecimal actualCost,
            int requestedJobCount, int succeededJobCount, int failedJobCount, int rejectedJobCount,
            Instant createdAt, Instant updatedAt, long version, List<Job> jobs) {
        public static Batch from(GenerationBatch value, List<Job> jobs) {
            return new Batch(value.getGenerationBatchUuid(), value.getProductUuid(), value.getCreativePlanUuid(),
                    value.getStatus().name(), value.getCurrency().strip(), value.getEstimatedCost(),
                    value.getReservedCost(), value.getActualCost(), value.getRequestedJobCount(),
                    value.getSucceededJobCount(), value.getFailedJobCount(), value.getRejectedJobCount(),
                    value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(), jobs);
        }
    }

    public record Job(
            UUID generationJobUuid, UUID generationBatchUuid, UUID productUuid, UUID creativePlanUuid,
            UUID promptTemplateVersionUuid, String generationType, String modelProfile, String status,
            BigDecimal estimatedCost, BigDecimal reservedCost, BigDecimal actualCost, String currency,
            String failureCode, String failureMessage, int attemptCount, Instant submittedAt,
            Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt, long version,
            UUID outputUuid) {
        public static Job from(GenerationJob value, GenerationOutput output) {
            String profile = switch (value.getModelKey()) {
                case "stub-text-low" -> "LOW_COST";
                case "stub-text-partial" -> "PARTIAL_FAILURE_FIXTURE";
                case "stub-text-cost-invariant" -> "COST_INVARIANT_FIXTURE";
                default -> "STANDARD";
            };
            return new Job(value.getGenerationJobUuid(), value.getGenerationBatchUuid(), value.getProductUuid(),
                    value.getCreativePlanUuid(), value.getPromptTemplateVersionUuid(), value.getGenerationType().name(),
                    profile, value.getStatus().name(), value.getEstimatedCost(), value.getReservedCost(),
                    value.getActualCost(), value.getCurrency().strip(), value.getFailureCode(), value.getFailureMessage(),
                    value.getAttemptCount(), value.getSubmittedAt(), value.getStartedAt(), value.getCompletedAt(),
                    value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(),
                    output == null ? null : output.getGenerationOutputUuid());
        }
    }

    public record Output(
            UUID generationOutputUuid, UUID generationJobUuid, UUID generationBatchUuid, UUID productUuid,
            String generationType, String textContent, String modelLabel, long inputUnits, long outputUnits,
            BigDecimal actualCost, String currency, JsonNode safetyFindings, String reviewStatus,
            Instant createdAt, Instant updatedAt, long version) {
        public static Output from(GenerationOutput value, ObjectMapper mapper) {
            return new Output(value.getGenerationOutputUuid(), value.getGenerationJobUuid(),
                    value.getGenerationBatchUuid(), value.getProductUuid(), value.getGenerationType().name(),
                    value.getTextContent(), value.getModelLabel(), value.getInputUnits(), value.getOutputUnits(),
                    value.getActualCost(), value.getCurrency().strip(), mapper.readTree(value.getSafetyFindings()),
                    value.getReviewStatus().name(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion());
        }
    }
}
