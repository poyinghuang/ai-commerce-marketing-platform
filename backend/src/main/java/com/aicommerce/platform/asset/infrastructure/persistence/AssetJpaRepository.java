package com.aicommerce.platform.asset.infrastructure.persistence;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetJpaRepository extends ArchivableResourceRepository<Asset, UUID> {
    Optional<Asset> findByAssetUuidAndProductUuid(UUID assetUuid, UUID productUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asset a where a.assetUuid=:assetUuid and a.productUuid=:productUuid")
    Optional<Asset> findForMutation(@Param("assetUuid") UUID assetUuid, @Param("productUuid") UUID productUuid);

    @Query("""
            select a from Asset a
            where a.productUuid=:productUuid
              and (:status is null or a.lifecycleStatus=:status)
              and (:assetType is null or a.assetType=:assetType)
              and (:creativePlanUuid is null or a.creativePlanUuid=:creativePlanUuid)
              and (:campaignUuid is null or a.campaignUuid=:campaignUuid)
              and (:storageProvider is null or a.storageProvider=:storageProvider)
            """)
    Page<Asset> search(@Param("productUuid") UUID productUuid,
            @Param("status") LifecycleStatus status, @Param("assetType") AssetType assetType,
            @Param("creativePlanUuid") UUID creativePlanUuid, @Param("campaignUuid") UUID campaignUuid,
            @Param("storageProvider") String storageProvider, Pageable pageable);

    @Query("""
            select a from Asset a
            where a.productUuid = :productUuid
              and (:includeArchived = true or a.lifecycleStatus = com.aicommerce.platform.common.domain.LifecycleStatus.ACTIVE)
            order by a.updatedAt desc, a.assetUuid asc
            """)
    List<Asset> findForAggregate(@Param("productUuid") UUID productUuid,
            @Param("includeArchived") boolean includeArchived);
}
