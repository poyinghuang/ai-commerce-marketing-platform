CREATE TABLE quality_scores (
    quality_score_uuid UUID PRIMARY KEY,
    product_uuid UUID NOT NULL,
    product_master_score INTEGER NOT NULL DEFAULT 0,
    product_knowledge_score INTEGER NOT NULL DEFAULT 0,
    creative_plan_score INTEGER NOT NULL DEFAULT 0,
    asset_metadata_score INTEGER NOT NULL DEFAULT 0,
    campaign_readiness_score INTEGER NOT NULL DEFAULT 0,
    system_score INTEGER NOT NULL DEFAULT 0,
    ai_suggested_score INTEGER,
    manual_adjustment INTEGER NOT NULL DEFAULT 0,
    manual_adjustment_reason VARCHAR(1000),
    manual_adjusted_by VARCHAR(128),
    manual_adjusted_at TIMESTAMP WITH TIME ZONE,
    final_score INTEGER NOT NULL DEFAULT 0,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_quality_scores_product UNIQUE (product_uuid),
    CONSTRAINT fk_quality_scores_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_quality_scores_product_master CHECK (product_master_score BETWEEN 0 AND 35),
    CONSTRAINT ck_quality_scores_product_knowledge CHECK (product_knowledge_score BETWEEN 0 AND 25),
    CONSTRAINT ck_quality_scores_creative_plan CHECK (creative_plan_score BETWEEN 0 AND 25),
    CONSTRAINT ck_quality_scores_asset_metadata CHECK (asset_metadata_score BETWEEN 0 AND 10),
    CONSTRAINT ck_quality_scores_campaign_readiness CHECK (campaign_readiness_score BETWEEN 0 AND 5),
    CONSTRAINT ck_quality_scores_system CHECK (system_score BETWEEN 0 AND 100),
    CONSTRAINT ck_quality_scores_ai_suggested CHECK (ai_suggested_score IS NULL OR ai_suggested_score BETWEEN 0 AND 100),
    CONSTRAINT ck_quality_scores_manual_adjustment CHECK (manual_adjustment BETWEEN -20 AND 20),
    CONSTRAINT ck_quality_scores_manual_metadata CHECK (
        (manual_adjustment = 0
            AND manual_adjustment_reason IS NULL
            AND manual_adjusted_by IS NULL
            AND manual_adjusted_at IS NULL)
        OR
        (manual_adjustment <> 0
            AND manual_adjustment_reason IS NOT NULL
            AND BTRIM(manual_adjustment_reason) <> ''
            AND manual_adjusted_by IS NOT NULL
            AND BTRIM(manual_adjusted_by) <> ''
            AND manual_adjusted_at IS NOT NULL)
    ),
    CONSTRAINT ck_quality_scores_component_sum CHECK (
        system_score = product_master_score + product_knowledge_score
            + creative_plan_score + asset_metadata_score + campaign_readiness_score
    ),
    CONSTRAINT ck_quality_scores_final CHECK (
        final_score = LEAST(100, GREATEST(0, system_score + manual_adjustment))
    )
);

CREATE TABLE quality_score_blockers (
    quality_score_blocker_uuid UUID PRIMARY KEY,
    quality_score_uuid UUID NOT NULL,
    blocker_code VARCHAR(64) NOT NULL,
    field_path VARCHAR(256),
    message VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quality_score_blockers_code UNIQUE (quality_score_uuid, blocker_code),
    CONSTRAINT fk_quality_score_blockers_score
        FOREIGN KEY (quality_score_uuid) REFERENCES quality_scores(quality_score_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_quality_score_blockers_code CHECK (blocker_code IN (
        'PRODUCT_ARCHIVED', 'PRODUCT_NAME_MISSING', 'SALE_PRICE_MISSING', 'CURRENCY_MISSING',
        'KNOWLEDGE_MISSING', 'CREATIVE_PLAN_MISSING', 'IMAGE_ASSET_MISSING'
    )),
    CONSTRAINT ck_quality_score_blockers_message CHECK (BTRIM(message) <> ''),
    CONSTRAINT ck_quality_score_blockers_field_path CHECK (field_path IS NULL OR BTRIM(field_path) <> '')
);

CREATE INDEX idx_quality_score_blockers_score ON quality_score_blockers (quality_score_uuid);

CREATE TABLE workflow_status (
    workflow_status_uuid UUID PRIMARY KEY,
    product_uuid UUID NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'PRODUCT_READINESS',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    status_reason VARCHAR(512) NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_status_product UNIQUE (product_uuid),
    CONSTRAINT fk_workflow_status_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_workflow_status_stage CHECK (stage = 'PRODUCT_READINESS'),
    CONSTRAINT ck_workflow_status_status CHECK (status IN ('DRAFT', 'NEEDS_REVIEW', 'READY')),
    CONSTRAINT ck_workflow_status_reason CHECK (BTRIM(status_reason) <> '')
);

CREATE FUNCTION reject_2d_projection_identity_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'quality and workflow projection identity is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_quality_scores_immutable_identity
    BEFORE UPDATE OF quality_score_uuid, product_uuid ON quality_scores
    FOR EACH ROW EXECUTE FUNCTION reject_2d_projection_identity_change();
CREATE TRIGGER trg_quality_score_blockers_immutable_identity
    BEFORE UPDATE OF quality_score_blocker_uuid, quality_score_uuid ON quality_score_blockers
    FOR EACH ROW EXECUTE FUNCTION reject_2d_projection_identity_change();
CREATE TRIGGER trg_workflow_status_immutable_identity
    BEFORE UPDATE OF workflow_status_uuid, product_uuid ON workflow_status
    FOR EACH ROW EXECUTE FUNCTION reject_2d_projection_identity_change();

INSERT INTO quality_scores (quality_score_uuid, product_uuid)
SELECT gen_random_uuid(), product_uuid
FROM products;

INSERT INTO quality_score_blockers
    (quality_score_blocker_uuid, quality_score_uuid, blocker_code, field_path, message)
SELECT gen_random_uuid(), q.quality_score_uuid, blocker.code, blocker.field_path, blocker.message
FROM quality_scores q
JOIN products p ON p.product_uuid = q.product_uuid
CROSS JOIN LATERAL (
    SELECT 'PRODUCT_ARCHIVED', 'lifecycleStatus', 'Archived Product cannot be ready'
        WHERE p.lifecycle_status = 'ARCHIVED'
    UNION ALL SELECT 'PRODUCT_NAME_MISSING', 'productName', 'Product name is required for readiness'
        WHERE p.product_name IS NULL OR BTRIM(p.product_name) = ''
    UNION ALL SELECT 'SALE_PRICE_MISSING', 'salePrice', 'Sale price is required for readiness'
        WHERE p.sale_price IS NULL
    UNION ALL SELECT 'CURRENCY_MISSING', 'currency', 'Currency is required for readiness'
        WHERE p.currency IS NULL
    UNION ALL SELECT 'KNOWLEDGE_MISSING', 'knowledge', 'At least one active Product Knowledge entry is required'
        WHERE NOT EXISTS (SELECT 1 FROM product_knowledge k
            WHERE k.product_uuid = p.product_uuid AND k.lifecycle_status = 'ACTIVE')
    UNION ALL SELECT 'CREATIVE_PLAN_MISSING', 'creativePlans', 'At least one active Creative Plan is required'
        WHERE NOT EXISTS (SELECT 1 FROM creative_plans c
            WHERE c.product_uuid = p.product_uuid AND c.lifecycle_status = 'ACTIVE')
    UNION ALL SELECT 'IMAGE_ASSET_MISSING', 'assets', 'At least one active image Asset is required'
        WHERE NOT EXISTS (SELECT 1 FROM assets a
            WHERE a.product_uuid = p.product_uuid AND a.lifecycle_status = 'ACTIVE' AND a.asset_type = 'IMAGE')
) AS blocker(code, field_path, message);

INSERT INTO workflow_status
    (workflow_status_uuid, product_uuid, status, status_reason)
SELECT gen_random_uuid(), product_uuid, 'DRAFT', 'Initial deterministic quality projection requires recalculation'
FROM products;
