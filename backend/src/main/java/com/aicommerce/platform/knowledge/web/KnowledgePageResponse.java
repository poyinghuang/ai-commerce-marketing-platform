package com.aicommerce.platform.knowledge.web;
import java.util.List;
import org.springframework.data.domain.Page;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
public record KnowledgePageResponse(List<KnowledgeResponse> content, int page, int size, long totalElements,
        int totalPages, String status, String sort) {
    public static KnowledgePageResponse from(Page<ProductKnowledge> values, String status, String sort) { return new KnowledgePageResponse(values.getContent().stream().map(KnowledgeResponse::from).toList(), values.getNumber(), values.getSize(), values.getTotalElements(), values.getTotalPages(), status, sort); }
}
