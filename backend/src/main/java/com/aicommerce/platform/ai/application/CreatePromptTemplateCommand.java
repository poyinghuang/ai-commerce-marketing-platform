package com.aicommerce.platform.ai.application;

import com.aicommerce.platform.ai.domain.GenerationType;

public record CreatePromptTemplateCommand(String templateKey, GenerationType generationType, String displayName) {
}
