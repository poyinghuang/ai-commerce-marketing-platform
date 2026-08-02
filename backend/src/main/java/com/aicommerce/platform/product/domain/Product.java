package com.aicommerce.platform.product.domain;

import java.time.Instant;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private ProductLifecycleStatus lifecycleStatus;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Product() {
    }

    private Product(UUID productUuid, String productId, String sku) {
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.productId = requireText(productId, "productId");
        this.sku = normalizeNullable(sku);
        this.lifecycleStatus = ProductLifecycleStatus.ACTIVE;
    }

    public static Product create(UUID productUuid, String productId, String sku) {
        return new Product(productUuid, productId, sku);
    }

    public void changeSku(String sku) {
        ensureActive();
        this.sku = normalizeNullable(sku);
    }

    public void archive(Instant archivedAt) {
        if (lifecycleStatus == ProductLifecycleStatus.ARCHIVED) {
            return;
        }
        this.lifecycleStatus = ProductLifecycleStatus.ARCHIVED;
        this.archivedAt = Objects.requireNonNull(archivedAt, "archivedAt is required");
    }

    public void restore() {
        this.lifecycleStatus = ProductLifecycleStatus.ACTIVE;
        this.archivedAt = null;
    }

    private void ensureActive() {
        if (lifecycleStatus == ProductLifecycleStatus.ARCHIVED) {
            throw new IllegalStateException("Archived product cannot be modified");
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
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

    public ProductLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
