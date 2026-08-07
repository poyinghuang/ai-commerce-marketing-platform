package com.aicommerce.platform.knowledge.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductKnowledgeJpaRepository extends JpaRepository<ProductKnowledge, UUID> {
}
