package com.aicommerce.platform.asset.application;

public class AssetValidationException extends RuntimeException {
    private final String field;
    public AssetValidationException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
