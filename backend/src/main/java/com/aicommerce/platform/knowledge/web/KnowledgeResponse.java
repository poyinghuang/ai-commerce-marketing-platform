package com.aicommerce.platform.knowledge.web;
import java.time.Instant;
import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
public record KnowledgeResponse(UUID knowledgeUuid, UUID productUuid, KnowledgeType knowledgeType,
        String title, String content, String source, LifecycleStatus lifecycleStatus, Instant archivedAt,
        Instant createdAt, Instant updatedAt, long version) {
    public static KnowledgeResponse from(ProductKnowledge k) { return new KnowledgeResponse(k.getKnowledgeUuid(), k.getProductUuid(), k.getKnowledgeType(), k.getTitle(), k.getContent(), k.getSource(), k.getLifecycleStatus(), k.getArchivedAt(), k.getCreatedAt(), k.getUpdatedAt(), k.getVersion()); }
}
