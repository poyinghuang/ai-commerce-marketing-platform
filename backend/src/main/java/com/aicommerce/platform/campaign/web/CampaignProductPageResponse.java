package com.aicommerce.platform.campaign.web;

import com.aicommerce.platform.campaign.domain.CampaignProduct;
import java.util.List;
import org.springframework.data.domain.Page;

public record CampaignProductPageResponse(
    List<CampaignProductResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    SortResponse sort) {
  public record SortResponse(String field, String direction) {}

  public static CampaignProductPageResponse from(Page<CampaignProduct> p, String sort) {
    String[] s = sort.split(",");
    return new CampaignProductPageResponse(
        p.getContent().stream().map(CampaignProductResponse::from).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        new SortResponse(s[0], s[1]));
  }
}
