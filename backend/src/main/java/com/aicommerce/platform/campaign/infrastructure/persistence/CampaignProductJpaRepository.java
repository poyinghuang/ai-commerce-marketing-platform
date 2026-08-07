package com.aicommerce.platform.campaign.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;

public interface CampaignProductJpaRepository extends ArchivableResourceRepository<CampaignProduct, UUID> {
}
