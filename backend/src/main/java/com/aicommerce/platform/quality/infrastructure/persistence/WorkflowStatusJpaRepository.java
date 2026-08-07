package com.aicommerce.platform.quality.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.quality.domain.WorkflowStatus;
public interface WorkflowStatusJpaRepository extends MutableProjectionRepository<WorkflowStatus, UUID> {
    Optional<WorkflowStatus> findByProductUuid(UUID productUuid);
}
