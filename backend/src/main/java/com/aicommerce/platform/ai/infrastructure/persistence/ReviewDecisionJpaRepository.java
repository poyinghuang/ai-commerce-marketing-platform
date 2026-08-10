package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.ReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDecisionJpaRepository extends JpaRepository<ReviewDecision, UUID> {
    Optional<ReviewDecision> findByGenerationOutputUuid(UUID generationOutputUuid);
}
