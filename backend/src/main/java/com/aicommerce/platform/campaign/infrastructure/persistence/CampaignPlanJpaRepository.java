package com.aicommerce.platform.campaign.infrastructure.persistence;

import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignPlanJpaRepository
    extends ArchivableResourceRepository<CampaignPlan, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CampaignPlan c where c.campaignUuid=:id")
  Optional<CampaignPlan> findForMutation(@Param("id") UUID id);

  @Query(
      """
      select c from CampaignPlan c
      where (:status is null or c.lifecycleStatus = :status)
        and (:keyword = '' or lower(c.campaignName) like lower(concat('%', :keyword, '%')))
        and (:productUuid is null or exists (
              select cp.campaignProductUuid from CampaignProduct cp
              where cp.campaignUuid = c.campaignUuid
                and cp.productUuid = :productUuid
                and (:associationStatus is null or cp.lifecycleStatus = :associationStatus)))
      """)
  Page<CampaignPlan> search(
      @Param("status") LifecycleStatus status,
      @Param("keyword") String keyword,
      @Param("productUuid") UUID productUuid,
      @Param("associationStatus") LifecycleStatus associationStatus,
      Pageable pageable);
}
