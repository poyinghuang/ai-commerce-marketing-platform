package com.aicommerce.platform.web.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        String path,
        List<FieldErrorDetail> fieldErrors) {
}
