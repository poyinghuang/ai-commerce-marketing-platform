package com.aicommerce.platform.delivery.application.port;
public sealed interface PlatformWriteOutcome permits WriteSucceeded,WriteRetryableFailure,WriteTerminalFailure,WriteUnknownOutcome {}
