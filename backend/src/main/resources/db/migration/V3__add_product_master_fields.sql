ALTER TABLE products
    ADD COLUMN product_name VARCHAR(256),
    ADD COLUMN brand VARCHAR(128),
    ADD COLUMN category VARCHAR(128),
    ADD COLUMN subcategory VARCHAR(128),
    ADD COLUMN short_description VARCHAR(2000),
    ADD COLUMN cost NUMERIC(19, 4),
    ADD COLUMN sale_price NUMERIC(19, 4),
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN stock BIGINT,
    ADD COLUMN product_url VARCHAR(2048),
    ADD CONSTRAINT ck_products_product_name_not_blank
        CHECK (product_name IS NULL OR BTRIM(product_name) <> ''),
    ADD CONSTRAINT ck_products_cost_non_negative
        CHECK (cost IS NULL OR cost >= 0),
    ADD CONSTRAINT ck_products_sale_price_non_negative
        CHECK (sale_price IS NULL OR sale_price >= 0),
    ADD CONSTRAINT ck_products_stock_non_negative
        CHECK (stock IS NULL OR stock >= 0),
    ADD CONSTRAINT ck_products_currency_format
        CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_products_currency_required_for_prices
        CHECK ((cost IS NULL AND sale_price IS NULL) OR currency IS NOT NULL);

CREATE INDEX idx_products_category_lower
    ON products (LOWER(category))
    WHERE category IS NOT NULL;

CREATE INDEX idx_products_product_name_lower
    ON products (LOWER(product_name))
    WHERE product_name IS NOT NULL;
