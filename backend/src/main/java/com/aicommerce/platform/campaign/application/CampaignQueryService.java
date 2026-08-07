package com.aicommerce.platform.campaign.application;

import com.aicommerce.platform.campaign.domain.*;
import com.aicommerce.platform.campaign.infrastructure.persistence.*;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignQueryService {
  private final CampaignPlanJpaRepository campaigns;
  private final CampaignProductJpaRepository associations;

  public CampaignQueryService(CampaignPlanJpaRepository c, CampaignProductJpaRepository a) {
    campaigns = c;
    associations = a;
  }

  @Transactional(readOnly = true)
  public CampaignPlan get(UUID id) {
    return campaigns.findById(id).orElseThrow(CampaignNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Page<CampaignPlan> list(
      LifecycleStatus s, String k, UUID p, LifecycleStatus as, Pageable page) {
    String keyword = k == null || k.isBlank() ? "" : k.trim();
    return campaigns.search(s, keyword, p, as, page);
  }

  @Transactional(readOnly = true)
  public CampaignProduct getProduct(UUID c, UUID p) {
    get(c);
    return associations
        .findByCampaignUuidAndProductUuid(c, p)
        .orElseThrow(CampaignProductNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Page<CampaignProduct> listProducts(UUID c, LifecycleStatus s, Pageable page) {
    get(c);
    return s == null
        ? associations.findByCampaignUuid(c, page)
        : associations.findByCampaignUuidAndLifecycleStatus(c, s, page);
  }

  @Transactional(readOnly = true)
  public Map<UUID, CampaignProduct> associationsFor(List<CampaignPlan> values, UUID productUuid) {
    if (values.isEmpty()) return Map.of();
    return associations
        .findByCampaignUuidInAndProductUuid(
            values.stream().map(CampaignPlan::getCampaignUuid).toList(), productUuid)
        .stream()
        .collect(Collectors.toMap(CampaignProduct::getCampaignUuid, Function.identity()));
  }
}
