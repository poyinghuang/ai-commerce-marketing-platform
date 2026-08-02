CREATE SEQUENCE product_id_seq
    AS BIGINT
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 99999999
    START WITH 1
    NO CYCLE
    CACHE 1;

CREATE TABLE products (
    product_uuid UUID PRIMARY KEY,
    product_id VARCHAR(13) NOT NULL,
    sku VARCHAR(128),
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_products_product_id UNIQUE (product_id),
    CONSTRAINT ck_products_product_id_format CHECK (product_id ~ '^PROD-[0-9]{8}$'),
    CONSTRAINT ck_products_lifecycle_status CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_products_archive_consistency CHECK (
        (lifecycle_status = 'ACTIVE' AND archived_at IS NULL)
        OR (lifecycle_status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX idx_products_sku_lower
    ON products (LOWER(sku))
    WHERE sku IS NOT NULL;
CREATE INDEX idx_products_lifecycle_status ON products (lifecycle_status);
CREATE INDEX idx_products_updated_at ON products (updated_at DESC);

CREATE FUNCTION reject_product_identity_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.product_uuid IS DISTINCT FROM OLD.product_uuid THEN
        RAISE EXCEPTION 'product_uuid is immutable' USING ERRCODE = '23514';
    END IF;
    IF NEW.product_id IS DISTINCT FROM OLD.product_id THEN
        RAISE EXCEPTION 'product_id is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_products_immutable_identity
    BEFORE UPDATE OF product_uuid, product_id ON products
    FOR EACH ROW
    EXECUTE FUNCTION reject_product_identity_change();
