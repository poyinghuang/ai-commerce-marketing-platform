package com.aicommerce.platform.ai.application;

public class AiFoundationValidationException extends RuntimeException {
    public AiFoundationValidationException(String message) {
        super(message);
    }

    public AiFoundationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
