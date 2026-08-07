package com.aicommerce.platform.knowledge.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;

public interface ProductKnowledgeJpaRepository extends ArchivableResourceRepository<ProductKnowledge, UUID> {
}
