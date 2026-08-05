package com.aicommerce.platform.product.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditValueType;
import org.springframework.stereotype.Component;

@Component
public class ProductAuditChangeFactory {

    public List<AuditChange> forCreate(ProductSnapshot after) {
        return differences(null, after);
    }

    public List<AuditChange> between(ProductSnapshot before, ProductSnapshot after) {
        return differences(before, after);
    }

    private List<AuditChange> differences(ProductSnapshot before, ProductSnapshot after) {
        List<AuditChange> changes = new ArrayList<>();
        add(changes, "product_id", value(before, ProductSnapshot::productId), after.productId(), AuditValueType.STRING);
        add(changes, "sku", value(before, ProductSnapshot::sku), after.sku(), AuditValueType.STRING);
        add(changes, "product_name", value(before, ProductSnapshot::productName), after.productName(), AuditValueType.STRING);
        add(changes, "brand", value(before, ProductSnapshot::brand), after.brand(), AuditValueType.STRING);
        add(changes, "category", value(before, ProductSnapshot::category), after.category(), AuditValueType.STRING);
        add(changes, "subcategory", value(before, ProductSnapshot::subcategory), after.subcategory(), AuditValueType.STRING);
        add(
                changes,
                "short_description",
                value(before, ProductSnapshot::shortDescription),
                after.shortDescription(),
                AuditValueType.STRING);
        add(changes, "cost", decimal(before == null ? null : before.cost()), decimal(after.cost()), AuditValueType.STRING);
        add(
                changes,
                "sale_price",
                decimal(before == null ? null : before.salePrice()),
                decimal(after.salePrice()),
                AuditValueType.STRING);
        add(changes, "currency", value(before, ProductSnapshot::currency), after.currency(), AuditValueType.STRING);
        add(
                changes,
                "stock",
                before == null || before.stock() == null ? null : before.stock().toString(),
                after.stock() == null ? null : after.stock().toString(),
                AuditValueType.STRING);
        add(changes, "product_url", value(before, ProductSnapshot::productUrl), after.productUrl(), AuditValueType.STRING);
        add(
                changes,
                "lifecycle_status",
                before == null ? null : before.lifecycleStatus().name(),
                after.lifecycleStatus().name(),
                AuditValueType.ENUM);
        add(
                changes,
                "archived_at",
                instant(before == null ? null : before.archivedAt()),
                instant(after.archivedAt()),
                AuditValueType.TIMESTAMP);
        return List.copyOf(changes);
    }

    private <T> String value(ProductSnapshot snapshot, java.util.function.Function<ProductSnapshot, T> getter) {
        if (snapshot == null) {
            return null;
        }
        T value = getter.apply(snapshot);
        return value == null ? null : value.toString();
    }

    private void add(
            List<AuditChange> changes,
            String field,
            String oldValue,
            String newValue,
            AuditValueType type) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        changes.add(new AuditChange(field, oldValue, newValue, type, changes.size()));
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }
}
