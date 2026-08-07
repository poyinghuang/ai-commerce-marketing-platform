package com.aicommerce.platform.knowledge.application;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
public record PatchKnowledgeCommand(FieldPatch<KnowledgeType> knowledgeType, FieldPatch<String> title,
        FieldPatch<String> content, FieldPatch<String> source) {}
