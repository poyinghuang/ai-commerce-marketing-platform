package com.aicommerce.platform.quality.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.quality.domain.QualityScoreBlocker;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface QualityScoreBlockerJpaRepository extends Repository<QualityScoreBlocker, UUID> {
    <S extends QualityScoreBlocker> List<S> saveAll(Iterable<S> blockers);
    List<QualityScoreBlocker> findByQualityScoreUuidOrderByBlockerCode(UUID qualityScoreUuid);
    @Modifying(flushAutomatically = true)
    @Query("delete from QualityScoreBlocker b where b.qualityScoreUuid = :qualityScoreUuid")
    int deleteByQualityScoreUuid(@Param("qualityScoreUuid") UUID qualityScoreUuid);
}
