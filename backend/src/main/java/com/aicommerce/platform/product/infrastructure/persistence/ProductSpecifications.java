package com.aicommerce.platform.product.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.aicommerce.platform.product.application.ProductSearchCriteria;
import com.aicommerce.platform.product.domain.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matches(ProductSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("lifecycleStatus"), criteria.status()));
            }
            if (hasText(criteria.category())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("category")), criteria.category().trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.sku())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("sku")), criteria.sku().trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.productId())) {
                predicates.add(builder.equal(root.get("productId"), criteria.productId().trim()));
            }
            if (hasText(criteria.keyword())) {
                String pattern = "%" + escapeLike(criteria.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        like(builder, root.get("productId"), pattern),
                        like(builder, root.get("sku"), pattern),
                        like(builder, root.get("productName"), pattern),
                        like(builder, root.get("brand"), pattern),
                        like(builder, root.get("category"), pattern),
                        like(builder, root.get("subcategory"), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate like(
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Path<String> path,
            String pattern) {
        return builder.like(builder.lower(path), pattern, '\\');
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
