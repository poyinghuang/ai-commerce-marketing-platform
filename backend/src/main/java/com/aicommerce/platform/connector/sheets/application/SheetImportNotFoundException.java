package com.aicommerce.platform.connector.sheets.application;

public class SheetImportNotFoundException extends RuntimeException {
    public SheetImportNotFoundException() { super("Sheet import job was not found"); }
}
