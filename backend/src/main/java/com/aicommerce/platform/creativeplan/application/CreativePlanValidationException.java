package com.aicommerce.platform.creativeplan.application;

public class CreativePlanValidationException extends RuntimeException {
    private final String field;
    public CreativePlanValidationException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
