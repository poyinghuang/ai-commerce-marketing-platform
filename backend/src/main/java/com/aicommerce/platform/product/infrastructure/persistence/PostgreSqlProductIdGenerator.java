package com.aicommerce.platform.product.infrastructure.persistence;

import com.aicommerce.platform.product.application.ProductIdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlProductIdGenerator implements ProductIdGenerator {

    private static final String NEXT_ID_SQL =
            "SELECT 'PROD-' || LPAD(nextval('product_id_seq')::text, 8, '0')";

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlProductIdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String nextId() {
        String productId = jdbcTemplate.queryForObject(NEXT_ID_SQL, String.class);
        if (productId == null) {
            throw new IllegalStateException("PostgreSQL did not generate a product ID");
        }
        return productId;
    }
}
