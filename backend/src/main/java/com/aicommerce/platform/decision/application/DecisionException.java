package com.aicommerce.platform.decision.application;

import org.springframework.http.HttpStatus;

public final class DecisionException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final String field;

    public DecisionException(String code, HttpStatus status) {
        this(code, status, null);
    }

    public DecisionException(String code, HttpStatus status, String field) {
        super(code);
        this.code = code;
        this.status = status;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String field() {
        return field;
    }
}
