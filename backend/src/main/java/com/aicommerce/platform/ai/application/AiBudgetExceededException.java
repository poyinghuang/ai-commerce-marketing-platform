package com.aicommerce.platform.ai.application;

public class AiBudgetExceededException extends RuntimeException {

    private final String code;

    public AiBudgetExceededException(String limit) {
        super("AI " + limit + " budget limit would be exceeded");
        this.code = switch (limit) {
            case "job" -> "AI_JOB_BUDGET_EXCEEDED";
            case "batch" -> "AI_BATCH_BUDGET_EXCEEDED";
            case "daily" -> "AI_DAILY_BUDGET_EXCEEDED";
            default -> throw new IllegalArgumentException("Unknown AI budget limit: " + limit);
        };
    }

    public String code() {
        return code;
    }
}
