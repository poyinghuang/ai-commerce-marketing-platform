package com.aicommerce.platform.delivery.application;

import org.springframework.http.HttpStatus;

public final class Stage4BException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public Stage4BException(String code, HttpStatus status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
