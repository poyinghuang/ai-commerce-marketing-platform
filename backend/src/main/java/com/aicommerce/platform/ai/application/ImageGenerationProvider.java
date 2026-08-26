package com.aicommerce.platform.ai.application;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ImageGenerationProvider {

    ImageSubmission submit(ImageRequest request);

    ImageResult await(ImageRequest request, ImageSubmission submission);

    default String jobProviderKey() {
        return "stub";
    }

    default String jobModelKey() {
        return "stub-image";
    }

    record ImageRequest(
            UUID generationJobUuid,
            String workflowKey,
            String workflowVersion,
            Map<String, String> workflowInputs,
            String sourceHandle,
            String maskHandle,
            byte[] sourceBytes,
            byte[] maskBytes,
            int width,
            int height,
            String format,
            Duration timeout) {
    }

    record ImageSubmission(String providerJobId, String modelLabel, Map<String, String> metadata) {
    }

    record ImageResult(byte[] bytes, String mediaType, String modelLabel, BigDecimal actualCost,
            List<String> safetyFindings, Map<String, String> metadata) {
    }
}
