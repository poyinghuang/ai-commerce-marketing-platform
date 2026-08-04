package com.aicommerce.platform.product.web;

import java.math.BigDecimal;

import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.ProductValidationException;
import org.springframework.stereotype.Component;

@Component
public class ProductRequestMapper {

    public CreateProductCommand toCommand(CreateProductRequest request) {
        return new CreateProductCommand(
                request.sku(),
                request.productName(),
                request.brand(),
                request.category(),
                request.subcategory(),
                request.shortDescription(),
                decimal(request.cost(), "cost"),
                decimal(request.salePrice(), "salePrice"),
                request.currency(),
                stock(request.stock()),
                request.productUrl());
    }

    BigDecimal decimal(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ProductValidationException(field, field + " must be a valid decimal");
        }
    }

    Long stock(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ProductValidationException("stock", "stock exceeds the bigint range");
        }
    }
}
