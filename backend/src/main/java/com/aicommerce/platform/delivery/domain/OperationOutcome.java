package com.aicommerce.platform.delivery.domain;

public sealed interface OperationOutcome permits OperationOutcome.Success, OperationOutcome.RetryableFailure,
        OperationOutcome.TerminalFailure, OperationOutcome.Unknown {
    record Success(String externalId, String safeTraceId) implements OperationOutcome { }
    record RetryableFailure(String code, String safeTraceId) implements OperationOutcome { }
    record TerminalFailure(String code, String safeTraceId) implements OperationOutcome { }
    record Unknown(String safeTraceId) implements OperationOutcome { }
}
