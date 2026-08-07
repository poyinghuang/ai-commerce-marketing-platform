package com.aicommerce.platform.quality.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.quality.domain.QualityScore;
public interface QualityScoreJpaRepository extends MutableProjectionRepository<QualityScore, UUID> {
    Optional<QualityScore> findByProductUuid(UUID productUuid);
}
