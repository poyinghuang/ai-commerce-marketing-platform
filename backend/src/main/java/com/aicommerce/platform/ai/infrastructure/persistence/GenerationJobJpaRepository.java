package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GenerationJobJpaRepository extends JpaRepository<GenerationJob, UUID> {
    List<GenerationJob> findByGenerationBatchUuidOrderByCreatedAt(UUID generationBatchUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GenerationJob job where job.generationJobUuid=:id")
    java.util.Optional<GenerationJob> findByIdForUpdate(@Param("id") UUID id);
}
