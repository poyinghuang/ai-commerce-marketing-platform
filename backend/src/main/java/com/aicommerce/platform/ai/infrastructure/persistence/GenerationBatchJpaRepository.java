package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationBatchJpaRepository extends JpaRepository<GenerationBatch, UUID> {
}
