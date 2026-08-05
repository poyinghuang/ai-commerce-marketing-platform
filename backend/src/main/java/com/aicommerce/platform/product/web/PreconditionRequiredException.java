package com.aicommerce.platform.product.web;

public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException() {
        super("If-Match is required");
    }
}
