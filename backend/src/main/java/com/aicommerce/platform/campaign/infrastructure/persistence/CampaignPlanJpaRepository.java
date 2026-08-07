package com.aicommerce.platform.campaign.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.campaign.domain.CampaignPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignPlanJpaRepository extends JpaRepository<CampaignPlan, UUID> {
}
