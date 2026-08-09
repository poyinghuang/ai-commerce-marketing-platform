package com.aicommerce.platform.connector.sheets.application;

public class SheetImportPreconditionFailedException extends RuntimeException {
    public SheetImportPreconditionFailedException() { super("Sheet import job version does not match If-Match"); }
}
