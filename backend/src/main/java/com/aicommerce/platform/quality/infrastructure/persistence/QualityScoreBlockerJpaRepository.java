package com.aicommerce.platform.quality.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.quality.domain.QualityScoreBlocker;
import org.springframework.data.repository.Repository;

public interface QualityScoreBlockerJpaRepository extends Repository<QualityScoreBlocker, UUID> {
    <S extends QualityScoreBlocker> List<S> saveAll(Iterable<S> blockers);
    List<QualityScoreBlocker> findByQualityScoreUuidOrderByBlockerCode(UUID qualityScoreUuid);
    long deleteByQualityScoreUuid(UUID qualityScoreUuid);
}
