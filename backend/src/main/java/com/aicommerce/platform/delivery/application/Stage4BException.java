package com.aicommerce.platform.delivery.application;

import org.springframework.http.HttpStatus;

public final class Stage4BException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final String field;

    public Stage4BException(String code, HttpStatus status) {
        this(code, status, null);
    }

    public Stage4BException(String code, HttpStatus status, String field) {
        super(code);
        this.code = code;
        this.status = status;
        this.field = field;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public String field() { return field; }
}
