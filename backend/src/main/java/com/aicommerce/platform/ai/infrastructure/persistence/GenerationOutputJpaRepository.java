package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GenerationOutputJpaRepository extends JpaRepository<GenerationOutput, UUID> {
    Optional<GenerationOutput> findByGenerationJobUuid(UUID generationJobUuid);
    List<GenerationOutput> findByGenerationBatchUuidOrderByCreatedAt(UUID generationBatchUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select output from GenerationOutput output where output.generationOutputUuid=:id")
    Optional<GenerationOutput> findByIdForUpdate(@Param("id") UUID id);
}
