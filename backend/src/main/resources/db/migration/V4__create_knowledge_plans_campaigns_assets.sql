CREATE TABLE product_knowledge (
    knowledge_uuid UUID PRIMARY KEY,
    product_uuid UUID NOT NULL,
    knowledge_type VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content VARCHAR(20000) NOT NULL,
    source VARCHAR(2048),
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_knowledge_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_product_knowledge_type
        CHECK (knowledge_type IN ('FEATURE', 'BENEFIT', 'AUDIENCE', 'PAIN_POINT', 'FAQ', 'PROOF', 'OTHER')),
    CONSTRAINT ck_product_knowledge_title_not_blank CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_product_knowledge_content_not_blank CHECK (BTRIM(content) <> ''),
    CONSTRAINT ck_product_knowledge_lifecycle_status
        CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_product_knowledge_archive_consistency CHECK (
        (lifecycle_status = 'ACTIVE' AND archived_at IS NULL)
        OR (lifecycle_status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX idx_product_knowledge_product_lifecycle_updated
    ON product_knowledge (product_uuid, lifecycle_status, updated_at DESC);
CREATE INDEX idx_product_knowledge_product_type
    ON product_knowledge (product_uuid, knowledge_type);

CREATE TABLE creative_plans (
    creative_plan_uuid UUID PRIMARY KEY,
    product_uuid UUID NOT NULL,
    plan_name VARCHAR(256) NOT NULL,
    primary_audience VARCHAR(2000),
    secondary_audience VARCHAR(2000),
    pain_point VARCHAR(4000),
    core_benefit VARCHAR(4000),
    creative_angle VARCHAR(4000),
    emotional_direction VARCHAR(1000),
    brand_tone VARCHAR(1000),
    visual_style VARCHAR(2000),
    main_color VARCHAR(128),
    character_setting VARCHAR(2000),
    cta VARCHAR(1000),
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_creative_plans_uuid_product UNIQUE (creative_plan_uuid, product_uuid),
    CONSTRAINT fk_creative_plans_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_creative_plans_name_not_blank CHECK (BTRIM(plan_name) <> ''),
    CONSTRAINT ck_creative_plans_lifecycle_status
        CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_creative_plans_archive_consistency CHECK (
        (lifecycle_status = 'ACTIVE' AND archived_at IS NULL)
        OR (lifecycle_status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX idx_creative_plans_product_lifecycle_updated
    ON creative_plans (product_uuid, lifecycle_status, updated_at DESC);

CREATE TABLE campaign_plans (
    campaign_uuid UUID PRIMARY KEY,
    campaign_name VARCHAR(256) NOT NULL,
    activity_type VARCHAR(64),
    start_date DATE,
    end_date DATE,
    objective VARCHAR(2000),
    platform VARCHAR(64),
    budget_daily NUMERIC(19, 4),
    budget_total NUMERIC(19, 4),
    currency VARCHAR(3),
    promotion VARCHAR(2000),
    landing_page VARCHAR(2048),
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_campaign_plans_name_not_blank CHECK (BTRIM(campaign_name) <> ''),
    CONSTRAINT ck_campaign_plans_date_range
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_campaign_plans_budget_daily_non_negative
        CHECK (budget_daily IS NULL OR budget_daily >= 0),
    CONSTRAINT ck_campaign_plans_budget_total_non_negative
        CHECK (budget_total IS NULL OR budget_total >= 0),
    CONSTRAINT ck_campaign_plans_currency_format
        CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_campaign_plans_currency_required_for_budget
        CHECK ((budget_daily IS NULL AND budget_total IS NULL) OR currency IS NOT NULL),
    CONSTRAINT ck_campaign_plans_landing_page_http
        CHECK (landing_page IS NULL OR landing_page ~* '^https?://'),
    CONSTRAINT ck_campaign_plans_lifecycle_status
        CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_campaign_plans_archive_consistency CHECK (
        (lifecycle_status = 'ACTIVE' AND archived_at IS NULL)
        OR (lifecycle_status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX idx_campaign_plans_lifecycle_updated
    ON campaign_plans (lifecycle_status, updated_at DESC);
CREATE INDEX idx_campaign_plans_dates ON campaign_plans (start_date, end_date);
CREATE INDEX idx_campaign_plans_name_lower ON campaign_plans (LOWER(campaign_name));

CREATE TABLE campaign_products (
    campaign_product_uuid UUID PRIMARY KEY,
    campaign_uuid UUID NOT NULL,
    product_uuid UUID NOT NULL,
    role VARCHAR(128),
    priority INTEGER,
    budget_weight NUMERIC(5, 2),
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_campaign_products_campaign_product UNIQUE (campaign_uuid, product_uuid),
    CONSTRAINT fk_campaign_products_campaign
        FOREIGN KEY (campaign_uuid) REFERENCES campaign_plans(campaign_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_campaign_products_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_campaign_products_priority_non_negative
        CHECK (priority IS NULL OR priority >= 0),
    CONSTRAINT ck_campaign_products_budget_weight_range
        CHECK (budget_weight IS NULL OR (budget_weight >= 0.00 AND budget_weight <= 100.00)),
    CONSTRAINT ck_campaign_products_lifecycle_status
        CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_campaign_products_archive_consistency CHECK (
        (lifecycle_status = 'ACTIVE' AND archived_at IS NULL)
        OR (lifecycle_status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX idx_campaign_products_product_lifecycle
    ON campaign_products (product_uuid, lifecycle_status);
CREATE INDEX idx_campaign_products_campaign_lifecycle
    ON campaign_products (campaign_uuid, lifecycle_status);

CREATE TABLE assets (
    asset_uuid UUID PRIMARY KEY,
    product_uuid UUID NOT NULL,
    creative_plan_uuid UUID,
    campaign_uuid UUID,
    asset_type VARCHAR(32) NOT NULL,
    purpose VARCHAR(256),
    storage_provider VARCHAR(64),
    provider_file_id VARCHAR(512),
    file_url VARCHAR(2048),
    media_type VARCHAR(255),
    original_filename VARCHAR(512),
    size_bytes BIGINT,
    checksum_sha256 VARCHAR(64),
    provider_metadata JSONB,
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_assets_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_assets_creative_plan_owner
        FOREIGN KEY (creative_plan_uuid, product_uuid)
        REFERENCES creative_plans(creative_plan_uuid, product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_assets_campaign_product_owner
        FOREIGN KEY (campaign_uuid, product_uuid)
        REFERENCES campaign_products(campaign_uuid, product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_assets_type
        CHECK (asset_type IN ('IMAGE', 'VIDEO', 'DOCUMENT', 'OTHER')),
    CONSTRAINT ck_assets_size_non_negative CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT ck_assets_checksum_sha256
        CHECK (checksum_sha256 IS NULL OR checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_assets_provider_metadata_object
        CHECK (provider_metadata IS NULL OR JSONB_TYPEOF(provider_metadata) = 'object'),
    CONSTRAINT ck_assets_provider_metadata_size
        CHECK (provider_metadata IS NULL OR OCTET_LENGTH(provider_metadata::TEXT) <= 16384),
    CONSTRAINT ck_assets_file_url_http
        CHECK (file_url IS NULL OR file_url ~* '^https?://'),
    CONSTRAINT ck_assets_lifecycle_status
        CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_assets_archive_consistency CHECK (
        (lifecycle_status = 'ACTIVE' AND archived_at IS NULL)
        OR (lifecycle_status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX idx_assets_product_lifecycle_updated
    ON assets (product_uuid, lifecycle_status, updated_at DESC);
CREATE INDEX idx_assets_creative_plan ON assets (creative_plan_uuid);
CREATE INDEX idx_assets_campaign ON assets (campaign_uuid);
CREATE INDEX idx_assets_storage_provider_file
    ON assets (storage_provider, provider_file_id);

CREATE FUNCTION reject_2c_resource_identity_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'product_knowledge' THEN
        IF NEW.knowledge_uuid IS DISTINCT FROM OLD.knowledge_uuid
            OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid THEN
            RAISE EXCEPTION 'product_knowledge identity is immutable' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'creative_plans' THEN
        IF NEW.creative_plan_uuid IS DISTINCT FROM OLD.creative_plan_uuid
            OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid THEN
            RAISE EXCEPTION 'creative_plan identity is immutable' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'campaign_plans' THEN
        IF NEW.campaign_uuid IS DISTINCT FROM OLD.campaign_uuid THEN
            RAISE EXCEPTION 'campaign_plan identity is immutable' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'campaign_products' THEN
        IF NEW.campaign_product_uuid IS DISTINCT FROM OLD.campaign_product_uuid
            OR NEW.campaign_uuid IS DISTINCT FROM OLD.campaign_uuid
            OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid THEN
            RAISE EXCEPTION 'campaign_product identity is immutable' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'assets' THEN
        IF NEW.asset_uuid IS DISTINCT FROM OLD.asset_uuid
            OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid
            OR NEW.creative_plan_uuid IS DISTINCT FROM OLD.creative_plan_uuid
            OR NEW.campaign_uuid IS DISTINCT FROM OLD.campaign_uuid THEN
            RAISE EXCEPTION 'asset identity is immutable' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_product_knowledge_immutable_identity
    BEFORE UPDATE OF knowledge_uuid, product_uuid ON product_knowledge
    FOR EACH ROW EXECUTE FUNCTION reject_2c_resource_identity_change();
CREATE TRIGGER trg_creative_plans_immutable_identity
    BEFORE UPDATE OF creative_plan_uuid, product_uuid ON creative_plans
    FOR EACH ROW EXECUTE FUNCTION reject_2c_resource_identity_change();
CREATE TRIGGER trg_campaign_plans_immutable_identity
    BEFORE UPDATE OF campaign_uuid ON campaign_plans
    FOR EACH ROW EXECUTE FUNCTION reject_2c_resource_identity_change();
CREATE TRIGGER trg_campaign_products_immutable_identity
    BEFORE UPDATE OF campaign_product_uuid, campaign_uuid, product_uuid ON campaign_products
    FOR EACH ROW EXECUTE FUNCTION reject_2c_resource_identity_change();
CREATE TRIGGER trg_assets_immutable_identity
    BEFORE UPDATE OF asset_uuid, product_uuid, creative_plan_uuid, campaign_uuid ON assets
    FOR EACH ROW EXECUTE FUNCTION reject_2c_resource_identity_change();

ALTER TABLE audit_log_changes
    DROP CONSTRAINT ck_audit_log_changes_value_type,
    ADD CONSTRAINT ck_audit_log_changes_value_type
        CHECK (value_type IN ('STRING', 'UUID', 'ENUM', 'TIMESTAMP', 'DECIMAL', 'INTEGER', 'DATE'));
