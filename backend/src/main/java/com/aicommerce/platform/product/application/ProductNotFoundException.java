package com.aicommerce.platform.product.application;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productUuid) {
        super("Product not found: " + productUuid);
    }
}
