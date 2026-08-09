package com.aicommerce.platform.connector.sheets.application;

import java.util.UUID;

import com.aicommerce.platform.connector.sheets.domain.SheetImportJob;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportJobJpaRepository;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SheetImportQueryService {
    private final SheetImportJobJpaRepository jobs;
    private final SheetImportRowJpaRepository rows;

    public SheetImportQueryService(SheetImportJobJpaRepository jobs, SheetImportRowJpaRepository rows) {
        this.jobs = jobs;
        this.rows = rows;
    }

    @Transactional(readOnly = true)
    public SheetImportView get(UUID importJobUuid) {
        SheetImportJob job = jobs.findById(importJobUuid).orElseThrow(SheetImportNotFoundException::new);
        return SheetImportView.from(job, rows.findByImportJobUuidOrderByRowNumber(importJobUuid));
    }
}
