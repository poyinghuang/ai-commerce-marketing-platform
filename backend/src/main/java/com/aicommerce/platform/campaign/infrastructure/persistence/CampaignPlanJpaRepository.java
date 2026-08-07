package com.aicommerce.platform.campaign.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;

public interface CampaignPlanJpaRepository extends ArchivableResourceRepository<CampaignPlan, UUID> {
}
