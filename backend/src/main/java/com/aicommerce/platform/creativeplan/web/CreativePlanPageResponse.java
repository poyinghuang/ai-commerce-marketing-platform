package com.aicommerce.platform.creativeplan.web;

import java.util.List;
import org.springframework.data.domain.Page;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;

public record CreativePlanPageResponse(List<CreativePlanResponse> content, int page, int size,
        long totalElements, int totalPages, SortResponse sort) {
    public record SortResponse(String field, String direction) { }
    public static CreativePlanPageResponse from(Page<CreativePlan> plans, String sort) {
        String[] parts = sort.split(",", -1);
        return new CreativePlanPageResponse(plans.getContent().stream().map(CreativePlanResponse::from).toList(),
                plans.getNumber(), plans.getSize(), plans.getTotalElements(), plans.getTotalPages(),
                new SortResponse(parts[0], parts[1]));
    }
}
