package com.aicommerce.platform.ai.application;

import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationType;

public record GenerationJobFoundationRequest(
        UUID promptTemplateVersionUuid,
        GenerationType generationType,
        String providerKey,
        String modelKey,
        String renderedPrompt,
        String negativePrompt,
        String inputSnapshot) {
}
