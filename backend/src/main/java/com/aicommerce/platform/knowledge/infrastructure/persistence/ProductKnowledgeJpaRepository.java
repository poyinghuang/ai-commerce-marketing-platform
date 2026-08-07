package com.aicommerce.platform.knowledge.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductKnowledgeJpaRepository extends ArchivableResourceRepository<ProductKnowledge, UUID> {
    @Query("select k from ProductKnowledge k where k.productUuid = :productUuid and "
            + "(:status is null or k.lifecycleStatus = :status)")
    Page<ProductKnowledge> findByProductUuidAndStatus(
            @Param("productUuid") UUID productUuid,
            @Param("status") LifecycleStatus status,
            Pageable pageable);

    @Query("select k from ProductKnowledge k where k.knowledgeUuid = :knowledgeUuid and k.productUuid = :productUuid")
    java.util.Optional<ProductKnowledge> findOwned(
            @Param("productUuid") UUID productUuid,
            @Param("knowledgeUuid") UUID knowledgeUuid);
}
