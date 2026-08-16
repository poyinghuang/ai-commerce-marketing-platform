package com.aicommerce.platform.delivery.domain;

public sealed interface ReconciliationResult permits ReconciliationResult.FoundSuccess,
        ReconciliationResult.FoundFailure, ReconciliationResult.Unresolved {
    record FoundSuccess(String externalId, String safeTraceId) implements ReconciliationResult { }
    record FoundFailure(String code, String safeTraceId) implements ReconciliationResult { }
    record Unresolved(String safeTraceId) implements ReconciliationResult { }
}
