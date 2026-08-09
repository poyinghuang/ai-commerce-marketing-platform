package com.aicommerce.platform.ai.application;

public class AiBudgetExceededException extends RuntimeException {
    public AiBudgetExceededException(String limit) {
        super("AI " + limit + " budget limit would be exceeded");
    }
}
