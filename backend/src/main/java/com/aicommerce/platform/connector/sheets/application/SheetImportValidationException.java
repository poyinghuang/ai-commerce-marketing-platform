package com.aicommerce.platform.connector.sheets.application;

public class SheetImportValidationException extends RuntimeException {
    private final String code;
    private final String field;

    public SheetImportValidationException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() { return code; }
    public String getField() { return field; }
}
