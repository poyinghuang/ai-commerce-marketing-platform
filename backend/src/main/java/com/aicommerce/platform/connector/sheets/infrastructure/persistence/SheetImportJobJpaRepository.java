package com.aicommerce.platform.connector.sheets.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.connector.sheets.domain.SheetImportJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SheetImportJobJpaRepository extends JpaRepository<SheetImportJob, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from SheetImportJob j where j.importJobUuid = :importJobUuid")
    java.util.Optional<SheetImportJob> findForUpdate(@Param("importJobUuid") UUID importJobUuid);
}
