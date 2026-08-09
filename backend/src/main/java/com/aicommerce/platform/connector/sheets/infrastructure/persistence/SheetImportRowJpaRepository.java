package com.aicommerce.platform.connector.sheets.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SheetImportRowJpaRepository extends JpaRepository<SheetImportRow, UUID> {
    List<SheetImportRow> findByImportJobUuidOrderByRowNumber(UUID importJobUuid);

    @Query("select r.importRowUuid from SheetImportRow r where r.importJobUuid = :jobUuid "
            + "and r.executionStatus = com.aicommerce.platform.connector.sheets.domain.SheetImportExecutionStatus.PENDING "
            + "order by r.rowNumber")
    List<UUID> findPendingRowUuids(@Param("jobUuid") UUID jobUuid);
}
