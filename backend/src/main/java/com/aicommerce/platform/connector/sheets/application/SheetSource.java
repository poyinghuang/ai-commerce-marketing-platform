package com.aicommerce.platform.connector.sheets.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SheetSource(String spreadsheetId, String sheetName, String range) {

    private static final String SHEET_PART = "(?:'(?:''|[^'])+'|[A-Za-z0-9_ -]+)";
    private static final Pattern A1_RANGE = Pattern.compile("^(?<sheet>" + SHEET_PART
            + ")!(?<startColumn>[A-Z]{1,3})(?<startRow>[1-9][0-9]*):"
            + "(?<endColumn>[A-Z]{1,3})(?<endRow>[1-9][0-9]*)$");

    public SheetSource {
        spreadsheetId = require(spreadsheetId, "spreadsheetId", 256);
        sheetName = require(sheetName, "sheetName", 128);
        range = require(range, "range", 256);
        if (!spreadsheetId.matches("[A-Za-z0-9_-]{3,256}")) {
            throw new IllegalArgumentException("spreadsheetId has an invalid format");
        }
        if (containsControl(sheetName) || containsControl(range) || !isBoundedRange(sheetName, range)) {
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

    private static boolean isBoundedRange(String sheetName, String range) {
        Matcher matcher = A1_RANGE.matcher(range);
        if (!matcher.matches() || !decodedSheetName(matcher.group("sheet")).equals(sheetName)) return false;
        int startColumn = columnNumber(matcher.group("startColumn"));
        int endColumn = columnNumber(matcher.group("endColumn"));
        int startRow;
        int endRow;
        try {
            startRow = Integer.parseInt(matcher.group("startRow"));
            endRow = Integer.parseInt(matcher.group("endRow"));
        } catch (NumberFormatException exception) {
            return false;
        }
        return startColumn <= endColumn
                && endColumn - startColumn + 1 <= ProductSheetMapping.COLUMN_COUNT
                && startRow == 1
                && endRow >= startRow
                && endRow <= ProductSheetMapping.MAX_DATA_ROWS + 1;
    }

    private static String decodedSheetName(String value) {
        return value.startsWith("'")
                ? value.substring(1, value.length() - 1).replace("''", "'")
                : value;
    }

    private static int columnNumber(String value) {
        int result = 0;
        for (int index = 0; index < value.length(); index++) {
            result = Math.addExact(Math.multiplyExact(result, 26), value.charAt(index) - 'A' + 1);
        }
        return result;
    }
}
