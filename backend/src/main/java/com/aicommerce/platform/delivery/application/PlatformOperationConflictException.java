package com.aicommerce.platform.delivery.application;

public class PlatformOperationConflictException extends RuntimeException {
    private final String code;
    public PlatformOperationConflictException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
