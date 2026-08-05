package com.aicommerce.platform.product.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @Size(max = 128) String sku,
        @NotBlank @Size(max = 256) String productName,
        @Size(max = 128) String brand,
        @Size(max = 128) String category,
        @Size(max = 128) String subcategory,
        @Size(max = 2000) String shortDescription,
        @Pattern(regexp = "^(0|[1-9][0-9]{0,14})(\\.[0-9]{1,4})?$") String cost,
        @Pattern(regexp = "^(0|[1-9][0-9]{0,14})(\\.[0-9]{1,4})?$") String salePrice,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Pattern(regexp = "^[0-9]{1,19}$") String stock,
        @Size(max = 2048) String productUrl) {
}
