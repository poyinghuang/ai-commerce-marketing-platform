package com.aicommerce.platform.ai.application;

public class AiBudgetUnavailableException extends RuntimeException {
    public AiBudgetUnavailableException(String message) {
        super(message);
    }

    public AiBudgetUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
