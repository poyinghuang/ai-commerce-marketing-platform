package com.aicommerce.platform.campaign.infrastructure.persistence;

import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignProductJpaRepository
    extends ArchivableResourceRepository<CampaignProduct, UUID> {
  Optional<CampaignProduct> findByCampaignUuidAndProductUuid(UUID campaignUuid, UUID productUuid);

  Page<CampaignProduct> findByCampaignUuid(UUID campaignUuid, Pageable pageable);

  Page<CampaignProduct> findByCampaignUuidAndLifecycleStatus(
      UUID campaignUuid, LifecycleStatus status, Pageable pageable);

  List<CampaignProduct> findByCampaignUuidInAndProductUuid(
      Collection<UUID> campaignUuids, UUID productUuid);

  @Query("select cp.productUuid from CampaignProduct cp where cp.campaignUuid=:campaignUuid "
      + "and cp.lifecycleStatus=com.aicommerce.platform.common.domain.LifecycleStatus.ACTIVE "
      + "order by cp.productUuid")
  List<UUID> findActiveProductUuids(@Param("campaignUuid") UUID campaignUuid);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select cp from CampaignProduct cp where cp.campaignUuid=:campaignUuid and"
          + " cp.productUuid=:productUuid")
  Optional<CampaignProduct> findForMutation(
      @Param("campaignUuid") UUID campaignUuid, @Param("productUuid") UUID productUuid);

  @Query(
      """
      select c, cp from CampaignProduct cp
      join CampaignPlan c on c.campaignUuid = cp.campaignUuid
      where cp.productUuid = :productUuid
        and (:includeArchived = true or (
          cp.lifecycleStatus = com.aicommerce.platform.common.domain.LifecycleStatus.ACTIVE
          and c.lifecycleStatus = com.aicommerce.platform.common.domain.LifecycleStatus.ACTIVE))
      order by c.updatedAt desc, c.campaignUuid asc
      """)
  List<Object[]> findCampaignsForAggregate(
      @Param("productUuid") UUID productUuid,
      @Param("includeArchived") boolean includeArchived);
}
