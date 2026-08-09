package com.aicommerce.platform.connector.sheets.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreviewSheetImportRequest(
        @NotBlank @Size(max = 256) String spreadsheetId,
        @NotBlank @Size(max = 128) String sheetName,
        @Size(max = 256) String range) {
}
