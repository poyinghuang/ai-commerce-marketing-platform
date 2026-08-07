package com.aicommerce.platform.connector.sheets.domain;

import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sheet_import_jobs")
public class SheetImportJob extends MutableEntity {

    private static final int MAX_DATA_ROWS = 1_000;

    @Id
    @Column(name = "import_job_uuid", nullable = false, updatable = false)
    private UUID importJobUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    private SheetImportProvider provider;

    @Column(name = "spreadsheet_id", nullable = false, updatable = false, length = 256)
    private String spreadsheetId;

    @Column(name = "sheet_name", nullable = false, updatable = false, length = 128)
    private String sheetName;

    @Column(name = "source_range", nullable = false, updatable = false, length = 256)
    private String sourceRange;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_fingerprint", nullable = false, updatable = false, columnDefinition = "char(64)")
    private String sourceFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SheetImportStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "created_by", nullable = false, updatable = false, length = 128)
    private String createdBy;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    protected SheetImportJob() {
    }

    private SheetImportJob(UUID importJobUuid, String spreadsheetId, String sheetName, String sourceRange,
            String sourceFingerprint, int validRows, int invalidRows, String createdBy) {
        this.importJobUuid = Objects.requireNonNull(importJobUuid, "importJobUuid is required");
        this.provider = SheetImportProvider.GOOGLE_SHEETS;
        this.spreadsheetId = requireText(spreadsheetId, "spreadsheetId", 256);
        this.sheetName = requireText(sheetName, "sheetName", 128);
        this.sourceRange = requireText(sourceRange, "sourceRange", 256);
        this.sourceFingerprint = requireSha256(sourceFingerprint, "sourceFingerprint");
        if (validRows < 0 || invalidRows < 0) {
            throw new IllegalArgumentException("row counts must be non-negative");
        }
        this.validRows = validRows;
        this.invalidRows = invalidRows;
        this.totalRows = Math.addExact(validRows, invalidRows);
        if (totalRows > MAX_DATA_ROWS) {
            throw new IllegalArgumentException("totalRows cannot exceed " + MAX_DATA_ROWS);
        }
        this.createdBy = requireText(createdBy, "createdBy", 128);
        this.status = SheetImportStatus.PREVIEWED;
    }

    public static SheetImportJob previewed(UUID importJobUuid, String spreadsheetId, String sheetName,
            String sourceRange, String sourceFingerprint, int validRows, int invalidRows, String createdBy) {
        return new SheetImportJob(importJobUuid, spreadsheetId, sheetName, sourceRange,
                sourceFingerprint, validRows, invalidRows, createdBy);
    }

    public boolean startExecution() {
        if (status == SheetImportStatus.EXECUTING) {
            return false;
        }
        if (status != SheetImportStatus.PREVIEWED) {
            throw new IllegalStateException("only a previewed import can start execution");
        }
        status = SheetImportStatus.EXECUTING;
        return true;
    }

    public void complete(int createdCount, int updatedCount, int failedCount) {
        if (status != SheetImportStatus.EXECUTING) {
            throw new IllegalStateException("only an executing import can complete");
        }
        requireNonNegative(createdCount, "createdCount");
        requireNonNegative(updatedCount, "updatedCount");
        requireNonNegative(failedCount, "failedCount");
        long resultCount = (long) createdCount + updatedCount + failedCount;
        if (resultCount != validRows) {
            throw new IllegalArgumentException("execution result counts must equal validRows");
        }
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.failedCount = failedCount;
        this.status = invalidRows > 0 || failedCount > 0
                ? SheetImportStatus.COMPLETED_WITH_ERRORS
                : SheetImportStatus.COMPLETED;
    }

    public void fail(String failureCode, String failureMessage) {
        if (status == SheetImportStatus.COMPLETED || status == SheetImportStatus.COMPLETED_WITH_ERRORS) {
            throw new IllegalStateException("a completed import cannot fail");
        }
        this.failureCode = requireText(failureCode, "failureCode", 64);
        this.failureMessage = requireText(failureMessage, "failureMessage", 1000);
        this.status = SheetImportStatus.FAILED;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static String requireSha256(String value, String name) {
        String normalized = requireText(value, name, 64);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 value");
        }
        return normalized;
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    public UUID getImportJobUuid() { return importJobUuid; }
    public SheetImportProvider getProvider() { return provider; }
    public String getSpreadsheetId() { return spreadsheetId; }
    public String getSheetName() { return sheetName; }
    public String getSourceRange() { return sourceRange; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public SheetImportStatus getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getValidRows() { return validRows; }
    public int getInvalidRows() { return invalidRows; }
    public int getCreatedCount() { return createdCount; }
    public int getUpdatedCount() { return updatedCount; }
    public int getFailedCount() { return failedCount; }
    public String getCreatedBy() { return createdBy; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
}
