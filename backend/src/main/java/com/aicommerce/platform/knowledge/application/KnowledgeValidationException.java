package com.aicommerce.platform.knowledge.application;
public class KnowledgeValidationException extends RuntimeException {
    private final String field;
    public KnowledgeValidationException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
