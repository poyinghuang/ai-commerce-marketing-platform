package com.aicommerce.platform.quality.application;

public class QualityValidationException extends RuntimeException {
    private final String field;
    public QualityValidationException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
