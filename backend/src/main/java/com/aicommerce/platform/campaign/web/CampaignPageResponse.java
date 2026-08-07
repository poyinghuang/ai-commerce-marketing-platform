package com.aicommerce.platform.campaign.web;

import java.util.List;

public record CampaignPageResponse(
    List<CampaignResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    SortResponse sort) {
  public record SortResponse(String field, String direction) {}
}
