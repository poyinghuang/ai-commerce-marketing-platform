package com.aicommerce.platform.creativeplan.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreativePlanJpaRepository extends JpaRepository<CreativePlan, UUID> {
}
