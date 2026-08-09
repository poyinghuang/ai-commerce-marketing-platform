package com.aicommerce.platform.connector.sheets.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.connector.sheets.domain.SheetImportExecutionStatus;
import com.aicommerce.platform.connector.sheets.domain.SheetImportJob;
import com.aicommerce.platform.connector.sheets.domain.SheetImportPlannedAction;
import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;
import com.aicommerce.platform.connector.sheets.domain.SheetImportStatus;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportJobJpaRepository;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SheetImportJobLifecycleService {
    private final SheetImportJobJpaRepository jobs;
    private final SheetImportRowJpaRepository rows;
    private final AuditWriter audit;
    private final Clock clock;

    public SheetImportJobLifecycleService(SheetImportJobJpaRepository jobs, SheetImportRowJpaRepository rows,
            AuditWriter audit, Clock clock) {
        this.jobs = jobs;
        this.rows = rows;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public boolean begin(UUID jobUuid, long expectedVersion, AuditOperationContext context) {
        SheetImportJob job = locked(jobUuid);
        if (job.getVersion() != expectedVersion) throw new SheetImportPreconditionFailedException();
        if (job.getStatus() == SheetImportStatus.COMPLETED
                || job.getStatus() == SheetImportStatus.COMPLETED_WITH_ERRORS) return false;
        if (job.getStatus() != SheetImportStatus.PREVIEWED)
            throw new SheetImportStateConflictException("Only a previewed import can execute");
        String before = job.getStatus().name();
        job.startExecution();
        jobs.saveAndFlush(job);
        append(job, context, List.of(change("status", before, job.getStatus().name(), AuditValueType.ENUM, 0)));
        return true;
    }

    @Transactional
    public void requireRecoverable(UUID jobUuid) {
        SheetImportJob job = locked(jobUuid);
        if (job.getStatus() != SheetImportStatus.EXECUTING)
            throw new SheetImportStateConflictException("Only an interrupted executing import can recover");
    }

    @Transactional
    public SheetImportView finish(UUID jobUuid, AuditOperationContext context) {
        SheetImportJob job = locked(jobUuid);
        if (job.getStatus() != SheetImportStatus.EXECUTING)
            throw new SheetImportStateConflictException("Import is not executing");
        List<SheetImportRow> actualRows = rows.findByImportJobUuidOrderByRowNumber(jobUuid);
        int created = (int) actualRows.stream().filter(row -> row.getPlannedAction() == SheetImportPlannedAction.CREATE
                && row.getExecutionStatus() == SheetImportExecutionStatus.SUCCEEDED).count();
        int updated = (int) actualRows.stream().filter(row -> row.getPlannedAction() == SheetImportPlannedAction.UPDATE
                && row.getExecutionStatus() == SheetImportExecutionStatus.SUCCEEDED).count();
        int failed = (int) actualRows.stream().filter(row -> row.getExecutionStatus() == SheetImportExecutionStatus.FAILED).count();
        int pending = (int) actualRows.stream().filter(row -> row.getExecutionStatus() == SheetImportExecutionStatus.PENDING).count();
        if (pending != 0) throw new SheetImportStateConflictException("Import still has pending rows");
        String beforeStatus = job.getStatus().name();
        job.complete(created, updated, failed);
        jobs.saveAndFlush(job);
        List<AuditChange> changes = new ArrayList<>();
        changes.add(change("status", beforeStatus, job.getStatus().name(), AuditValueType.ENUM, changes.size()));
        changes.add(change("created_count", "0", Integer.toString(created), AuditValueType.STRING, changes.size()));
        changes.add(change("updated_count", "0", Integer.toString(updated), AuditValueType.STRING, changes.size()));
        changes.add(change("failed_count", "0", Integer.toString(failed), AuditValueType.STRING, changes.size()));
        append(job, context, changes.stream().filter(value -> !value.oldValue().equals(value.newValue())).toList());
        return SheetImportView.from(job, actualRows);
    }

    private SheetImportJob locked(UUID jobUuid) {
        return jobs.findForUpdate(jobUuid).orElseThrow(SheetImportNotFoundException::new);
    }

    private AuditChange change(String field, String before, String after, AuditValueType type, int order) {
        return new AuditChange(field, before, after, type, order);
    }

    private void append(SheetImportJob job, AuditOperationContext context, List<AuditChange> changes) {
        if (changes.isEmpty()) return;
        audit.append(new AuditEvent(UUID.randomUUID(), context, AuditAction.UPDATE, "SHEET_IMPORT_JOB",
                job.getImportJobUuid(), null, Instant.now(clock), changes));
    }
}
