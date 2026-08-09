package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationOutput;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationOutputJpaRepository extends JpaRepository<GenerationOutput, UUID> {
    Optional<GenerationOutput> findByGenerationJobUuid(UUID generationJobUuid);
    List<GenerationOutput> findByGenerationBatchUuidOrderByCreatedAt(UUID generationBatchUuid);
}
