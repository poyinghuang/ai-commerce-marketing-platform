package com.aicommerce.platform.knowledge.web;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record CreateKnowledgeRequest(@NotNull KnowledgeType knowledgeType,
        @NotBlank @Size(max=256) String title, @NotBlank @Size(max=20000) String content,
        @Size(max=2048) String source) {}
