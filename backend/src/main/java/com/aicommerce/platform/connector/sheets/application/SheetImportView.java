package com.aicommerce.platform.connector.sheets.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.connector.sheets.domain.SheetImportJob;
import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;

public record SheetImportView(
        UUID importJobUuid,
        String provider,
        String spreadsheetId,
        String sheetName,
        String sourceRange,
        String sourceFingerprint,
        int headerPresenceMask,
        String status,
        int totalRows,
        int validRows,
        int invalidRows,
        int createdCount,
        int updatedCount,
        int failedCount,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        long version,
        List<Row> rows) {

    public SheetImportView {
        rows = List.copyOf(rows);
    }

    public static SheetImportView from(SheetImportJob job, List<SheetImportRow> rows) {
        return new SheetImportView(job.getImportJobUuid(), job.getProvider().name(), job.getSpreadsheetId(),
                job.getSheetName(), job.getSourceRange(), job.getSourceFingerprint(), job.getHeaderPresenceMask(),
                job.getStatus().name(), job.getTotalRows(), job.getValidRows(), job.getInvalidRows(),
                job.getCreatedCount(), job.getUpdatedCount(), job.getFailedCount(), job.getFailureCode(),
                job.getFailureMessage(), job.getCreatedAt(), job.getUpdatedAt(), job.getVersion(),
                rows.stream().map(Row::from).toList());
    }

    public record Row(
            UUID importRowUuid,
            int rowNumber,
            String plannedAction,
            String matchStrategy,
            UUID targetProductUuid,
            Long targetProductVersion,
            com.aicommerce.platform.connector.sheets.domain.SheetProductRowSnapshot source,
            List<com.aicommerce.platform.connector.sheets.domain.SheetValidationError> validationErrors,
            String executionStatus,
            UUID resultProductUuid,
            String resultProductId,
            String executionErrorCode,
            String executionErrorMessage,
            long version) {
        public static Row from(SheetImportRow row) {
            return new Row(row.getImportRowUuid(), row.getRowNumber(), row.getPlannedAction().name(),
                    row.getMatchStrategy().name(), row.getTargetProductUuid(), row.getTargetProductVersion(),
                    row.getSnapshot(), row.getValidationErrors(), row.getExecutionStatus().name(),
                    row.getResultProductUuid(), row.getResultProductId(), row.getExecutionErrorCode(),
                    row.getExecutionErrorMessage(), row.getVersion());
        }
    }
}
