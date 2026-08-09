package com.aicommerce.platform.connector.sheets.application;

public record SheetSource(String spreadsheetId, String sheetName, String range) {

    private static final String SHEET_PART = "(?:'(?:''|[^'])+'|[A-Za-z0-9_ -]+)";
    private static final String CELL_PART = "[A-Z]{1,3}(?:[1-9][0-9]*)?";

    public SheetSource {
        spreadsheetId = require(spreadsheetId, "spreadsheetId", 256);
        sheetName = require(sheetName, "sheetName", 128);
        range = require(range, "range", 256);
        if (!spreadsheetId.matches("[A-Za-z0-9_-]{3,256}")) {
            throw new IllegalArgumentException("spreadsheetId has an invalid format");
        }
        if (containsControl(sheetName) || containsControl(range)
                || !range.matches("^" + SHEET_PART + "!" + CELL_PART + ":" + CELL_PART + "$")) {
            throw new IllegalArgumentException("range has an invalid format");
        }
    }

    private static String require(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
