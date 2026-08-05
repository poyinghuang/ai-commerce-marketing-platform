package com.aicommerce.platform.product.domain;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product extends MutableEntity {

    @Id
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;

    @Column(name = "product_id", nullable = false, updatable = false, length = 13)
    private String productId;

    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "product_name", length = 256)
    private String productName;

    @Column(name = "brand", length = 128)
    private String brand;

    @Column(name = "category", length = 128)
    private String category;

    @Column(name = "subcategory", length = 128)
    private String subcategory;

    @Column(name = "short_description", length = 2000)
    private String shortDescription;

    @Column(name = "cost", precision = 19, scale = 4)
    private BigDecimal cost;

    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "stock")
    private Long stock;

    @Column(name = "product_url", length = 2048)
    private String productUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private ProductLifecycleStatus lifecycleStatus;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Product() {
    }

    private Product(
            UUID productUuid,
            String productId,
            String sku,
            String productName,
            String brand,
            String category,
            String subcategory,
            String shortDescription,
            BigDecimal cost,
            BigDecimal salePrice,
            String currency,
            Long stock,
            String productUrl) {
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.productId = requireText(productId, "productId", 13);
        this.lifecycleStatus = ProductLifecycleStatus.ACTIVE;
        applyMasterData(
                sku,
                productName,
                brand,
                category,
                subcategory,
                shortDescription,
                cost,
                salePrice,
                currency,
                stock,
                productUrl);
    }

    public static Product create(
            UUID productUuid,
            String productId,
            String sku,
            String productName,
            String brand,
            String category,
            String subcategory,
            String shortDescription,
            BigDecimal cost,
            BigDecimal salePrice,
            String currency,
            Long stock,
            String productUrl) {
        return new Product(
                productUuid,
                productId,
                sku,
                productName,
                brand,
                category,
                subcategory,
                shortDescription,
                cost,
                salePrice,
                currency,
                stock,
                productUrl);
    }

    public void updateMasterData(
            String sku,
            String productName,
            String brand,
            String category,
            String subcategory,
            String shortDescription,
            BigDecimal cost,
            BigDecimal salePrice,
            String currency,
            Long stock,
            String productUrl) {
        ensureActive();
        applyMasterData(
                sku,
                productName,
                brand,
                category,
                subcategory,
                shortDescription,
                cost,
                salePrice,
                currency,
                stock,
                productUrl);
    }

    public boolean archive(Instant archivedAt) {
        if (lifecycleStatus == ProductLifecycleStatus.ARCHIVED) {
            return false;
        }
        this.lifecycleStatus = ProductLifecycleStatus.ARCHIVED;
        this.archivedAt = Objects.requireNonNull(archivedAt, "archivedAt is required");
        return true;
    }

    public boolean restore() {
        if (lifecycleStatus == ProductLifecycleStatus.ACTIVE) {
            return false;
        }
        this.lifecycleStatus = ProductLifecycleStatus.ACTIVE;
        this.archivedAt = null;
        return true;
    }

    private void applyMasterData(
            String sku,
            String productName,
            String brand,
            String category,
            String subcategory,
            String shortDescription,
            BigDecimal cost,
            BigDecimal salePrice,
            String currency,
            Long stock,
            String productUrl) {
        String normalizedCurrency = normalizeNullable(currency, "currency", 3);
        validateMoney(cost, "cost");
        validateMoney(salePrice, "salePrice");
        if (stock != null && stock < 0) {
            throw new IllegalArgumentException("stock must be non-negative");
        }
        if ((cost != null || salePrice != null) && normalizedCurrency == null) {
            throw new IllegalArgumentException("currency is required when cost or salePrice is provided");
        }
        if (normalizedCurrency != null) {
            if (!normalizedCurrency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("currency must be a three-letter uppercase code");
            }
            try {
                Currency.getInstance(normalizedCurrency);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("currency must be a valid ISO currency code", exception);
            }
        }

        this.sku = normalizeNullable(sku, "sku", 128);
        this.productName = requireText(productName, "productName", 256);
        this.brand = normalizeNullable(brand, "brand", 128);
        this.category = normalizeNullable(category, "category", 128);
        this.subcategory = normalizeNullable(subcategory, "subcategory", 128);
        this.shortDescription = normalizeNullable(shortDescription, "shortDescription", 2000);
        this.cost = normalizeMoney(cost);
        this.salePrice = normalizeMoney(salePrice);
        this.currency = normalizedCurrency;
        this.stock = stock;
        this.productUrl = normalizeProductUrl(productUrl);
    }

    private void ensureActive() {
        if (lifecycleStatus == ProductLifecycleStatus.ARCHIVED) {
            throw new IllegalStateException("Archived product cannot be modified");
        }
    }

    private static void validateMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
        if (value.scale() > 4 || value.precision() - value.scale() > 15) {
            throw new IllegalArgumentException(fieldName + " exceeds numeric(19,4)");
        }
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? null : value.setScale(4);
    }

    private static String normalizeProductUrl(String value) {
        String normalized = normalizeNullable(value, "productUrl", 2048);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = new URI(normalized);
            if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("productUrl must be an absolute HTTP(S) URL");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("productUrl must be a valid URL", exception);
        }
    }

    private static String normalizeNullable(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        String normalized = normalizeNullable(value, fieldName, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    public UUID getProductUuid() {
        return productUuid;
    }

    public String getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public String getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getStock() {
        return stock;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public ProductLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
