package com.aicommerce.platform.dashboard.application;

import org.springframework.http.HttpStatus;

public final class DashboardException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final String field;

    public DashboardException(String code, HttpStatus status) {
        this(code, status, null);
    }

    public DashboardException(String code, HttpStatus status, String field) {
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
