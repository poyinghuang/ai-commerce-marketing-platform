package com.aicommerce.platform.product.web;

import java.util.List;

import org.springframework.data.domain.Page;

public record ProductPageResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        ProductSortResponse sort) {

    public static ProductPageResponse from(Page<com.aicommerce.platform.product.domain.Product> products, String sort) {
        String[] parts = sort.split(",", -1);
        return new ProductPageResponse(
                products.getContent().stream().map(ProductResponse::from).toList(),
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages(),
                new ProductSortResponse(parts[0], parts[1]));
    }
}
