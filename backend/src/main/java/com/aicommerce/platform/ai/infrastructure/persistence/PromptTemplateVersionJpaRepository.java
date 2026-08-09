package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.PromptTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateVersionJpaRepository extends JpaRepository<PromptTemplateVersion, UUID> {
    List<PromptTemplateVersion> findByPromptTemplateUuidOrderByVersionNumberDesc(UUID promptTemplateUuid);
}
