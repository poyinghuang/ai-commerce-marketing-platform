package com.aicommerce.platform.connector.sheets.application;

import java.util.UUID;

import com.aicommerce.platform.connector.sheets.domain.SheetImportExecutionStatus;
import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SheetImportRowFailureService {
    private final SheetImportRowJpaRepository rows;

    public SheetImportRowFailureService(SheetImportRowJpaRepository rows) { this.rows = rows; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID rowUuid, String code, String message) {
        SheetImportRow row = rows.findById(rowUuid).orElseThrow();
        if (row.getExecutionStatus() != SheetImportExecutionStatus.PENDING) return;
        row.recordFailure(code, message);
        rows.saveAndFlush(row);
    }
}
