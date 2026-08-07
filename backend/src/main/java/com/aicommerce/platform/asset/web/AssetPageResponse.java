package com.aicommerce.platform.asset.web;

import com.aicommerce.platform.asset.domain.Asset;
import java.util.List;
import org.springframework.data.domain.Page;

public record AssetPageResponse(List<AssetResponse> content, int page, int size, long totalElements,
        int totalPages, String sort) {
    static AssetPageResponse from(Page<Asset> source,String sort) {
        return new AssetPageResponse(source.getContent().stream().map(AssetResponse::from).toList(),
                source.getNumber(),source.getSize(),source.getTotalElements(),source.getTotalPages(),sort);
    }
}
