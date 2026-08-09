package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationJobJpaRepository extends JpaRepository<GenerationJob, UUID> {
    List<GenerationJob> findByGenerationBatchUuidOrderByCreatedAt(UUID generationBatchUuid);
}
