package com.aicommerce.platform.product.application;

public class ProductArchivedException extends RuntimeException {

    public ProductArchivedException() {
        super("Archived product cannot be modified");
    }
}
