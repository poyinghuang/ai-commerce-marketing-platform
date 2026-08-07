package com.aicommerce.platform.creativeplan.infrastructure.persistence;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CreativePlanJpaRepository extends ArchivableResourceRepository<CreativePlan, UUID> {
    Optional<CreativePlan> findByCreativePlanUuidAndProductUuid(UUID creativePlanUuid, UUID productUuid);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CreativePlan c where c.creativePlanUuid=:creativePlanUuid and c.productUuid=:productUuid")
    Optional<CreativePlan> findForAssetMutation(@Param("creativePlanUuid") UUID creativePlanUuid,
            @Param("productUuid") UUID productUuid);
    Page<CreativePlan> findByProductUuidAndLifecycleStatus(UUID productUuid, LifecycleStatus status, Pageable pageable);
    Page<CreativePlan> findByProductUuid(UUID productUuid, Pageable pageable);

    @Query("""
            select c from CreativePlan c
            where c.productUuid = :productUuid
              and (:includeArchived = true or c.lifecycleStatus = com.aicommerce.platform.common.domain.LifecycleStatus.ACTIVE)
            order by c.updatedAt desc, c.creativePlanUuid asc
            """)
    List<CreativePlan> findForAggregate(@Param("productUuid") UUID productUuid,
            @Param("includeArchived") boolean includeArchived);
}
