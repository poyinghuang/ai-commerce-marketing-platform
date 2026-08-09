package com.aicommerce.platform.connector.sheets.application;

public record PreviewSheetImportCommand(String spreadsheetId, String sheetName, String range) {

    public SheetSource source() {
        String actualRange = range == null || range.isBlank()
                ? "'" + sheetName.replace("'", "''") + "'!A:M"
                : range;
        return new SheetSource(spreadsheetId, sheetName, actualRange);
    }
}
