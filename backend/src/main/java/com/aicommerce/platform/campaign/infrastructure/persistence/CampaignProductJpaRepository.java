package com.aicommerce.platform.campaign.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.campaign.domain.CampaignProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignProductJpaRepository extends JpaRepository<CampaignProduct, UUID> {
}
