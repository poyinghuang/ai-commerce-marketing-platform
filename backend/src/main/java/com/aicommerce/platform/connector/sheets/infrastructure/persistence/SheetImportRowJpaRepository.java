package com.aicommerce.platform.connector.sheets.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SheetImportRowJpaRepository extends JpaRepository<SheetImportRow, UUID> {
    List<SheetImportRow> findByImportJobUuidOrderByRowNumber(UUID importJobUuid);
}
