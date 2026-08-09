package com.aicommerce.platform.connector.sheets.application;

public class SheetProviderException extends RuntimeException {
    private final String code;

    public SheetProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    public SheetProviderException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
