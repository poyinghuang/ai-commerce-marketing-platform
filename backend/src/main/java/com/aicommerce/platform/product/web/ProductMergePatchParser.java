package com.aicommerce.platform.product.web;

import java.math.BigDecimal;
import java.util.Set;

import com.aicommerce.platform.product.application.PatchField;
import com.aicommerce.platform.product.application.PatchProductCommand;
import com.aicommerce.platform.product.application.ProductValidationException;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ProductMergePatchParser {

    private static final Set<String> MUTABLE_FIELDS = Set.of(
            "sku",
            "productName",
            "brand",
            "category",
            "subcategory",
            "shortDescription",
            "cost",
            "salePrice",
            "currency",
            "stock",
            "productUrl");

    private final ProductRequestMapper valueMapper;

    public ProductMergePatchParser(ProductRequestMapper valueMapper) {
        this.valueMapper = valueMapper;
    }

    public PatchProductCommand parse(JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new InvalidMergePatchException("JSON Merge Patch must be an object");
        }
        for (String field : patch.propertyNames()) {
            if (!MUTABLE_FIELDS.contains(field)) {
                throw new InvalidMergePatchException("Field is not mutable: " + field);
            }
        }
        return new PatchProductCommand(
                stringField(patch, "sku", true),
                productName(patch),
                stringField(patch, "brand", true),
                stringField(patch, "category", true),
                stringField(patch, "subcategory", true),
                stringField(patch, "shortDescription", true),
                decimalField(patch, "cost"),
                decimalField(patch, "salePrice"),
                stringField(patch, "currency", true),
                stockField(patch),
                stringField(patch, "productUrl", true));
    }

    private PatchField<String> productName(JsonNode patch) {
        PatchField<String> value = stringField(patch, "productName", false);
        if (value.present() && (value.value() == null || value.value().isBlank())) {
            throw new ProductValidationException("productName", "productName is required");
        }
        return value;
    }

    private PatchField<String> stringField(JsonNode patch, String field, boolean nullable) {
        if (!patch.has(field)) {
            return PatchField.absent();
        }
        JsonNode value = patch.get(field);
        if (value.isNull()) {
            if (!nullable) {
                throw new ProductValidationException(field, field + " cannot be null");
            }
            return PatchField.present(null);
        }
        if (!value.isString()) {
            throw new ProductValidationException(field, field + " must be a string or null");
        }
        return PatchField.present(value.stringValue());
    }

    private PatchField<BigDecimal> decimalField(JsonNode patch, String field) {
        if (!patch.has(field)) {
            return PatchField.absent();
        }
        JsonNode value = patch.get(field);
        if (value.isNull()) {
            return PatchField.present(null);
        }
        if (!value.isString()) {
            throw new ProductValidationException(field, field + " must be a decimal string or null");
        }
        return PatchField.present(valueMapper.decimal(value.stringValue(), field));
    }

    private PatchField<Long> stockField(JsonNode patch) {
        if (!patch.has("stock")) {
            return PatchField.absent();
        }
        JsonNode value = patch.get("stock");
        if (value.isNull()) {
            return PatchField.present(null);
        }
        if (!value.isString()) {
            throw new ProductValidationException("stock", "stock must be an integer string or null");
        }
        return PatchField.present(valueMapper.stock(value.stringValue()));
    }
}
