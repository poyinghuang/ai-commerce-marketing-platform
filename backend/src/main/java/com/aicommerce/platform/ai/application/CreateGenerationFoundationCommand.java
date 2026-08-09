package com.aicommerce.platform.ai.application;

import java.util.List;
import java.util.UUID;

public record CreateGenerationFoundationCommand(
        UUID productUuid,
        UUID creativePlanUuid,
        List<GenerationJobFoundationRequest> jobs) {
}
