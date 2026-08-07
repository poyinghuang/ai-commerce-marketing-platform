ALTER TABLE products
    ADD CONSTRAINT uq_products_uuid_product_id UNIQUE (product_uuid, product_id);

CREATE TABLE sheet_import_jobs (
    import_job_uuid UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    spreadsheet_id VARCHAR(256) NOT NULL,
    sheet_name VARCHAR(128) NOT NULL,
    source_range VARCHAR(256) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INTEGER NOT NULL,
    valid_rows INTEGER NOT NULL,
    invalid_rows INTEGER NOT NULL,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(128) NOT NULL,
    failure_code VARCHAR(64),
    failure_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_sheet_import_jobs_provider CHECK (provider = 'GOOGLE_SHEETS'),
    CONSTRAINT ck_sheet_import_jobs_source CHECK (
        BTRIM(spreadsheet_id) <> '' AND BTRIM(sheet_name) <> '' AND BTRIM(source_range) <> ''
    ),
    CONSTRAINT ck_sheet_import_jobs_fingerprint CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sheet_import_jobs_status CHECK (status IN (
        'PREVIEWED', 'EXECUTING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'
    )),
    CONSTRAINT ck_sheet_import_jobs_counts_non_negative CHECK (
        total_rows BETWEEN 0 AND 1000 AND valid_rows >= 0 AND invalid_rows >= 0
        AND created_count >= 0 AND updated_count >= 0 AND failed_count >= 0
    ),
    CONSTRAINT ck_sheet_import_jobs_row_totals CHECK (total_rows = valid_rows + invalid_rows),
    CONSTRAINT ck_sheet_import_jobs_execution_totals CHECK (
        created_count + updated_count + failed_count <= valid_rows
    ),
    CONSTRAINT ck_sheet_import_jobs_creator CHECK (BTRIM(created_by) <> ''),
    CONSTRAINT ck_sheet_import_jobs_failure_pair CHECK (
        (failure_code IS NULL AND failure_message IS NULL)
        OR (failure_code IS NOT NULL AND BTRIM(failure_code) <> ''
            AND failure_message IS NOT NULL AND BTRIM(failure_message) <> '')
    ),
    CONSTRAINT ck_sheet_import_jobs_state CHECK (
        (status = 'PREVIEWED'
            AND created_count = 0 AND updated_count = 0 AND failed_count = 0
            AND failure_code IS NULL)
        OR (status = 'EXECUTING' AND failure_code IS NULL)
        OR (status = 'COMPLETED'
            AND invalid_rows = 0 AND failed_count = 0
            AND created_count + updated_count = valid_rows
            AND failure_code IS NULL)
        OR (status = 'COMPLETED_WITH_ERRORS'
            AND (invalid_rows > 0 OR failed_count > 0)
            AND created_count + updated_count + failed_count = valid_rows
            AND failure_code IS NULL)
        OR (status = 'FAILED' AND failure_code IS NOT NULL)
    )
);

CREATE INDEX idx_sheet_import_jobs_status ON sheet_import_jobs (status);
CREATE INDEX idx_sheet_import_jobs_created_at ON sheet_import_jobs (created_at DESC);

CREATE TABLE sheet_import_rows (
    import_row_uuid UUID PRIMARY KEY,
    import_job_uuid UUID NOT NULL,
    row_number INTEGER NOT NULL,
    source_row_hash CHAR(64) NOT NULL,
    planned_action VARCHAR(16) NOT NULL,
    match_strategy VARCHAR(16) NOT NULL,
    target_product_uuid UUID,
    target_product_version BIGINT,
    source_product_uuid VARCHAR(128),
    source_product_id VARCHAR(128),
    sku VARCHAR(512),
    product_name VARCHAR(1024),
    brand VARCHAR(512),
    category VARCHAR(512),
    subcategory VARCHAR(512),
    short_description VARCHAR(4096),
    source_cost VARCHAR(128),
    source_sale_price VARCHAR(128),
    currency VARCHAR(32),
    source_stock VARCHAR(128),
    product_url VARCHAR(4096),
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    execution_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    result_product_uuid UUID,
    result_product_id VARCHAR(13),
    execution_error_code VARCHAR(64),
    execution_error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sheet_import_rows_job_row UNIQUE (import_job_uuid, row_number),
    CONSTRAINT fk_sheet_import_rows_job
        FOREIGN KEY (import_job_uuid) REFERENCES sheet_import_jobs(import_job_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_sheet_import_rows_target_product
        FOREIGN KEY (target_product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_sheet_import_rows_result_product
        FOREIGN KEY (result_product_uuid, result_product_id)
        REFERENCES products(product_uuid, product_id) ON DELETE RESTRICT,
    CONSTRAINT ck_sheet_import_rows_row_number CHECK (row_number BETWEEN 2 AND 1001),
    CONSTRAINT ck_sheet_import_rows_hash CHECK (source_row_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sheet_import_rows_action CHECK (planned_action IN ('CREATE', 'UPDATE', 'INVALID')),
    CONSTRAINT ck_sheet_import_rows_match CHECK (match_strategy IN ('NONE', 'PRODUCT_UUID', 'PRODUCT_ID')),
    CONSTRAINT ck_sheet_import_rows_target_version CHECK (
        target_product_version IS NULL OR target_product_version >= 0
    ),
    CONSTRAINT ck_sheet_import_rows_validation_errors CHECK (
        JSONB_TYPEOF(validation_errors) = 'array'
        AND OCTET_LENGTH(validation_errors::text) <= 65536
    ),
    CONSTRAINT ck_sheet_import_rows_plan CHECK (
        (planned_action = 'CREATE'
            AND match_strategy = 'NONE'
            AND target_product_uuid IS NULL
            AND target_product_version IS NULL
            AND JSONB_ARRAY_LENGTH(validation_errors) = 0)
        OR (planned_action = 'UPDATE'
            AND match_strategy IN ('PRODUCT_UUID', 'PRODUCT_ID')
            AND target_product_uuid IS NOT NULL
            AND target_product_version IS NOT NULL
            AND JSONB_ARRAY_LENGTH(validation_errors) = 0)
        OR (planned_action = 'INVALID' AND JSONB_ARRAY_LENGTH(validation_errors) > 0)
    ),
    CONSTRAINT ck_sheet_import_rows_execution_status CHECK (
        execution_status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'SKIPPED')
    ),
    CONSTRAINT ck_sheet_import_rows_result_product_id CHECK (
        result_product_id IS NULL OR result_product_id ~ '^PROD-[0-9]{8}$'
    ),
    CONSTRAINT ck_sheet_import_rows_execution_result CHECK (
        (execution_status IN ('PENDING', 'SKIPPED')
            AND result_product_uuid IS NULL AND result_product_id IS NULL
            AND execution_error_code IS NULL AND execution_error_message IS NULL)
        OR (execution_status = 'SUCCEEDED'
            AND result_product_uuid IS NOT NULL AND result_product_id IS NOT NULL
            AND execution_error_code IS NULL AND execution_error_message IS NULL)
        OR (execution_status = 'FAILED'
            AND result_product_uuid IS NULL AND result_product_id IS NULL
            AND execution_error_code IS NOT NULL AND BTRIM(execution_error_code) <> ''
            AND execution_error_message IS NOT NULL AND BTRIM(execution_error_message) <> '')
    )
);

CREATE INDEX idx_sheet_import_rows_target_product ON sheet_import_rows (target_product_uuid);
CREATE INDEX idx_sheet_import_rows_action ON sheet_import_rows (planned_action);
CREATE INDEX idx_sheet_import_rows_execution ON sheet_import_rows (execution_status);

CREATE FUNCTION reject_sheet_import_job_identity_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.import_job_uuid IS DISTINCT FROM OLD.import_job_uuid
        OR NEW.provider IS DISTINCT FROM OLD.provider
        OR NEW.spreadsheet_id IS DISTINCT FROM OLD.spreadsheet_id
        OR NEW.sheet_name IS DISTINCT FROM OLD.sheet_name
        OR NEW.source_range IS DISTINCT FROM OLD.source_range
        OR NEW.source_fingerprint IS DISTINCT FROM OLD.source_fingerprint
        OR NEW.created_by IS DISTINCT FROM OLD.created_by
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'sheet import job source identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_sheet_import_row_snapshot_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.import_row_uuid IS DISTINCT FROM OLD.import_row_uuid
        OR NEW.import_job_uuid IS DISTINCT FROM OLD.import_job_uuid
        OR NEW.row_number IS DISTINCT FROM OLD.row_number
        OR NEW.source_row_hash IS DISTINCT FROM OLD.source_row_hash
        OR NEW.planned_action IS DISTINCT FROM OLD.planned_action
        OR NEW.match_strategy IS DISTINCT FROM OLD.match_strategy
        OR NEW.target_product_uuid IS DISTINCT FROM OLD.target_product_uuid
        OR NEW.target_product_version IS DISTINCT FROM OLD.target_product_version
        OR NEW.source_product_uuid IS DISTINCT FROM OLD.source_product_uuid
        OR NEW.source_product_id IS DISTINCT FROM OLD.source_product_id
        OR NEW.sku IS DISTINCT FROM OLD.sku
        OR NEW.product_name IS DISTINCT FROM OLD.product_name
        OR NEW.brand IS DISTINCT FROM OLD.brand
        OR NEW.category IS DISTINCT FROM OLD.category
        OR NEW.subcategory IS DISTINCT FROM OLD.subcategory
        OR NEW.short_description IS DISTINCT FROM OLD.short_description
        OR NEW.source_cost IS DISTINCT FROM OLD.source_cost
        OR NEW.source_sale_price IS DISTINCT FROM OLD.source_sale_price
        OR NEW.currency IS DISTINCT FROM OLD.currency
        OR NEW.source_stock IS DISTINCT FROM OLD.source_stock
        OR NEW.product_url IS DISTINCT FROM OLD.product_url
        OR NEW.validation_errors IS DISTINCT FROM OLD.validation_errors
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'sheet import row preview snapshot is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_sheet_import_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'sheet import records cannot be deleted' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_sheet_import_jobs_immutable_source
    BEFORE UPDATE ON sheet_import_jobs
    FOR EACH ROW EXECUTE FUNCTION reject_sheet_import_job_identity_change();
CREATE TRIGGER trg_sheet_import_rows_immutable_snapshot
    BEFORE UPDATE ON sheet_import_rows
    FOR EACH ROW EXECUTE FUNCTION reject_sheet_import_row_snapshot_change();
CREATE TRIGGER trg_sheet_import_jobs_no_delete
    BEFORE DELETE ON sheet_import_jobs
    FOR EACH ROW EXECUTE FUNCTION reject_sheet_import_delete();
CREATE TRIGGER trg_sheet_import_rows_no_delete
    BEFORE DELETE ON sheet_import_rows
    FOR EACH ROW EXECUTE FUNCTION reject_sheet_import_delete();
