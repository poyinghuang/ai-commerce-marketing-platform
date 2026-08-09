package com.aicommerce.platform.ai.application;

public record AppendPromptTemplateVersionCommand(
        String templateText,
        String negativePrompt,
        String inputSchema) {
}
