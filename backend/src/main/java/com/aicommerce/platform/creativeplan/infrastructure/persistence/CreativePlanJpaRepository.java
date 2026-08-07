package com.aicommerce.platform.creativeplan.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;

public interface CreativePlanJpaRepository extends ArchivableResourceRepository<CreativePlan, UUID> {
}
