package com.aicommerce.platform.ai.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TextGenerationProvider {

    TextResult generate(TextRequest request);

    record TextRequest(
            UUID generationJobUuid,
            String renderedPrompt,
            String modelKey,
            int maximumOutputLength,
            Duration timeout) {
    }

    record TextResult(
            String text,
            long inputUnits,
            long outputUnits,
            BigDecimal actualCost,
            String modelLabel,
            List<String> safetyFindings,
            Map<String, String> metadata) {
    }
}
