package com.aicommerce.platform.ai.application;

public class AiProviderException extends RuntimeException {

    private final String code;

    public AiProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
