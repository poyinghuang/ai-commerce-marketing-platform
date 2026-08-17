package com.aicommerce.platform.delivery.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformOperationBatchJpaRepository extends JpaRepository<PlatformOperationBatchEntity, UUID> {}
