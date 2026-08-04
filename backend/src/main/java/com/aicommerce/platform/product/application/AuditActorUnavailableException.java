package com.aicommerce.platform.product.application;

public class AuditActorUnavailableException extends RuntimeException {

    public AuditActorUnavailableException(Throwable cause) {
        super("A trusted audit actor is unavailable", cause);
    }
}
