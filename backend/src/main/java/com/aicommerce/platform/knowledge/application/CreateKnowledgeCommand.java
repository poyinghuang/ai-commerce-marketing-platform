package com.aicommerce.platform.knowledge.application;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
public record CreateKnowledgeCommand(KnowledgeType knowledgeType, String title, String content, String source) {}
