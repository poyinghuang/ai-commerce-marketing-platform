package com.aicommerce.platform.product.web;

public class InvalidIfMatchException extends RuntimeException {

    public InvalidIfMatchException() {
        super("If-Match must use the format W/\"<version>\"");
    }
}
