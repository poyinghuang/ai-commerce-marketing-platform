package com.aicommerce.platform.ai.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.BudgetLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetLedgerJpaRepository extends JpaRepository<BudgetLedgerEntry, UUID> {
    List<BudgetLedgerEntry> findByGenerationJobUuidOrderByEntryOrder(UUID generationJobUuid);
}
