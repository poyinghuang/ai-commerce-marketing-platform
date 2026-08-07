package com.aicommerce.platform.knowledge.application;

import java.time.Instant;
import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;

public record KnowledgeSnapshot(UUID knowledgeUuid, UUID productUuid, KnowledgeType knowledgeType,
        String title, String content, String source, LifecycleStatus lifecycleStatus, Instant archivedAt) {
    public static KnowledgeSnapshot from(ProductKnowledge value) {
        return new KnowledgeSnapshot(value.getKnowledgeUuid(), value.getProductUuid(), value.getKnowledgeType(),
                value.getTitle(), value.getContent(), value.getSource(), value.getLifecycleStatus(), value.getArchivedAt());
    }
}
