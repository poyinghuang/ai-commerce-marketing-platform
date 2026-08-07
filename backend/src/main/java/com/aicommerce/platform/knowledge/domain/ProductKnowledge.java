package com.aicommerce.platform.knowledge.domain;

import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.ArchivableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_knowledge")
public class ProductKnowledge extends ArchivableEntity {

    @Id
    @Column(name = "knowledge_uuid", nullable = false, updatable = false)
    private UUID knowledgeUuid;

    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_type", nullable = false, length = 32)
    private KnowledgeType knowledgeType;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "content", nullable = false, length = 20000)
    private String content;

    @Column(name = "source", length = 2048)
    private String source;

    protected ProductKnowledge() {
    }

    private ProductKnowledge(UUID knowledgeUuid, UUID productUuid, KnowledgeType knowledgeType,
            String title, String content, String source) {
        this.knowledgeUuid = Objects.requireNonNull(knowledgeUuid, "knowledgeUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.knowledgeType = Objects.requireNonNull(knowledgeType, "knowledgeType is required");
        this.title = requireText(title, "title");
        this.content = requireText(content, "content");
        this.source = source;
    }

    public static ProductKnowledge create(UUID knowledgeUuid, UUID productUuid, KnowledgeType knowledgeType,
            String title, String content, String source) {
        return new ProductKnowledge(knowledgeUuid, productUuid, knowledgeType, title, content, source);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    public UUID getKnowledgeUuid() { return knowledgeUuid; }
    public UUID getProductUuid() { return productUuid; }
    public KnowledgeType getKnowledgeType() { return knowledgeType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSource() { return source; }
}
