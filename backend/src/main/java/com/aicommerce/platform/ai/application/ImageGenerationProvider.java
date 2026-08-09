package com.aicommerce.platform.ai.application;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public interface ImageGenerationProvider {

    ImageSubmission submit(ImageRequest request);

    record ImageRequest(
            UUID generationJobUuid,
            String workflowKey,
            String workflowVersion,
            Map<String, String> workflowInputs,
            String sourceHandle,
            String maskHandle,
            int width,
            int height,
            String format,
            Duration timeout) {
    }

    record ImageSubmission(String providerJobId, String modelLabel, Map<String, String> metadata) {
    }
}
