package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.PromptTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromptTemplateJpaRepository extends JpaRepository<PromptTemplate, UUID> {
    Optional<PromptTemplate> findByTemplateKey(String templateKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select template from PromptTemplate template where template.promptTemplateUuid = :id")
    Optional<PromptTemplate> findByIdForUpdate(@Param("id") UUID id);
}
