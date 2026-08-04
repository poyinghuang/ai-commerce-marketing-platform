package com.aicommerce.platform.product.application;

public class ProductPreconditionFailedException extends RuntimeException {

    public ProductPreconditionFailedException() {
        super("Product version does not match If-Match");
    }
}
