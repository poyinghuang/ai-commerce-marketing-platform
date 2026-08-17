package com.aicommerce.platform.delivery.domain;

public enum PlatformOperationStatus {
    CREATED, SUBMITTING, SUCCEEDED, FAILED_RETRYABLE, FAILED_TERMINAL, UNKNOWN_OUTCOME, RECONCILING;

    public boolean isTerminal() { return this == SUCCEEDED || this == FAILED_TERMINAL; }
}
