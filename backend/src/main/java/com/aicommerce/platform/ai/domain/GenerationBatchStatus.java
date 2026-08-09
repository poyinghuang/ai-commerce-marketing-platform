package com.aicommerce.platform.ai.domain;

public enum GenerationBatchStatus {
    CREATED,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    BUDGET_REJECTED,
    CANCELLED
}
