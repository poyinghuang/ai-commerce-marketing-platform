package com.aicommerce.platform.creativeplan.infrastructure.persistence;

import java.util.UUID;
import java.util.Optional;

import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CreativePlanJpaRepository extends ArchivableResourceRepository<CreativePlan, UUID> {
    Optional<CreativePlan> findByCreativePlanUuidAndProductUuid(UUID creativePlanUuid, UUID productUuid);
    Page<CreativePlan> findByProductUuidAndLifecycleStatus(UUID productUuid, LifecycleStatus status, Pageable pageable);
    Page<CreativePlan> findByProductUuid(UUID productUuid, Pageable pageable);
}
