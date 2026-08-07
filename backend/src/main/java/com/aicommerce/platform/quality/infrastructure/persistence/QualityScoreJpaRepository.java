package com.aicommerce.platform.quality.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.quality.domain.QualityScore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface QualityScoreJpaRepository extends MutableProjectionRepository<QualityScore, UUID> {
    Optional<QualityScore> findByProductUuid(UUID productUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QualityScore q where q.productUuid = :productUuid")
    Optional<QualityScore> findForUpdate(@Param("productUuid") UUID productUuid);
}
