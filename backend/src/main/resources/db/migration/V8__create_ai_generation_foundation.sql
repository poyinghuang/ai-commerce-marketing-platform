CREATE TABLE ai_prompt_templates (
    prompt_template_uuid UUID PRIMARY KEY,
    template_key VARCHAR(128) NOT NULL UNIQUE,
    generation_type VARCHAR(16) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_prompt_templates_key CHECK (template_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$'),
    CONSTRAINT ck_ai_prompt_templates_type CHECK (generation_type IN ('TEXT', 'IMAGE')),
    CONSTRAINT ck_ai_prompt_templates_name CHECK (BTRIM(display_name) <> ''),
    CONSTRAINT ck_ai_prompt_templates_lifecycle CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_ai_prompt_templates_version CHECK (version >= 0)
);

CREATE TABLE ai_prompt_template_versions (
    prompt_template_version_uuid UUID PRIMARY KEY,
    prompt_template_uuid UUID NOT NULL,
    version_number INTEGER NOT NULL,
    template_text VARCHAR(16000) NOT NULL,
    negative_prompt VARCHAR(8000),
    input_schema JSONB NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_prompt_versions_template
        FOREIGN KEY (prompt_template_uuid) REFERENCES ai_prompt_templates(prompt_template_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_ai_prompt_versions_number UNIQUE (prompt_template_uuid, version_number),
    CONSTRAINT ck_ai_prompt_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_ai_prompt_versions_text CHECK (BTRIM(template_text) <> ''),
    CONSTRAINT ck_ai_prompt_versions_negative CHECK (negative_prompt IS NULL OR BTRIM(negative_prompt) <> ''),
    CONSTRAINT ck_ai_prompt_versions_schema CHECK (
        JSONB_TYPEOF(input_schema) = 'object' AND OCTET_LENGTH(input_schema::text) <= 16384
    ),
    CONSTRAINT ck_ai_prompt_versions_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_prompt_versions_creator CHECK (BTRIM(created_by) <> '')
);

CREATE INDEX idx_ai_prompt_versions_template
    ON ai_prompt_template_versions (prompt_template_uuid, version_number DESC);

CREATE TABLE ai_generation_batches (
    generation_batch_uuid UUID PRIMARY KEY,
    product_uuid UUID NOT NULL,
    creative_plan_uuid UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    currency CHAR(3) NOT NULL,
    estimated_cost NUMERIC(19,6) NOT NULL,
    reserved_cost NUMERIC(19,6) NOT NULL,
    actual_cost NUMERIC(19,6) NOT NULL DEFAULT 0,
    requested_job_count INTEGER NOT NULL,
    succeeded_job_count INTEGER NOT NULL DEFAULT 0,
    failed_job_count INTEGER NOT NULL DEFAULT 0,
    rejected_job_count INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_batches_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_batches_creative_plan_product
        FOREIGN KEY (creative_plan_uuid, product_uuid)
        REFERENCES creative_plans(creative_plan_uuid, product_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_ai_batches_product UNIQUE (generation_batch_uuid, product_uuid),
    CONSTRAINT ck_ai_batches_status CHECK (
        status IN ('CREATED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'BUDGET_REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_ai_batches_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ai_batches_costs CHECK (
        estimated_cost >= 0 AND reserved_cost >= 0 AND actual_cost >= 0
        AND estimated_cost <= reserved_cost
    ),
    CONSTRAINT ck_ai_batches_requested_count CHECK (requested_job_count > 0),
    CONSTRAINT ck_ai_batches_result_counts CHECK (
        succeeded_job_count >= 0 AND failed_job_count >= 0 AND rejected_job_count >= 0
        AND succeeded_job_count + failed_job_count + rejected_job_count <= requested_job_count
    ),
    CONSTRAINT ck_ai_batches_creator CHECK (BTRIM(created_by) <> ''),
    CONSTRAINT ck_ai_batches_version CHECK (version >= 0)
);

CREATE INDEX idx_ai_batches_product_created
    ON ai_generation_batches (product_uuid, created_at DESC);
CREATE INDEX idx_ai_batches_status_created
    ON ai_generation_batches (status, created_at);

CREATE TABLE ai_generation_jobs (
    generation_job_uuid UUID PRIMARY KEY,
    generation_batch_uuid UUID NOT NULL,
    product_uuid UUID NOT NULL,
    creative_plan_uuid UUID,
    prompt_template_version_uuid UUID NOT NULL,
    generation_type VARCHAR(16) NOT NULL,
    provider_key VARCHAR(64) NOT NULL,
    model_key VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    rendered_prompt VARCHAR(16000) NOT NULL,
    negative_prompt VARCHAR(8000),
    input_snapshot JSONB,
    provider_job_id VARCHAR(256),
    estimated_cost NUMERIC(19,6) NOT NULL,
    reserved_cost NUMERIC(19,6) NOT NULL,
    actual_cost NUMERIC(19,6) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    failure_code VARCHAR(64),
    failure_message VARCHAR(1000),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_jobs_batch_product
        FOREIGN KEY (generation_batch_uuid, product_uuid)
        REFERENCES ai_generation_batches(generation_batch_uuid, product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_jobs_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_jobs_creative_plan_product
        FOREIGN KEY (creative_plan_uuid, product_uuid)
        REFERENCES creative_plans(creative_plan_uuid, product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_jobs_prompt_version
        FOREIGN KEY (prompt_template_version_uuid)
        REFERENCES ai_prompt_template_versions(prompt_template_version_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_jobs_type CHECK (generation_type IN ('TEXT', 'IMAGE')),
    CONSTRAINT ck_ai_jobs_provider CHECK (BTRIM(provider_key) <> '' AND BTRIM(model_key) <> ''),
    CONSTRAINT ck_ai_jobs_status CHECK (
        status IN ('CREATED', 'SUBMITTED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'BUDGET_REJECTED')
    ),
    CONSTRAINT ck_ai_jobs_prompt CHECK (BTRIM(rendered_prompt) <> ''),
    CONSTRAINT ck_ai_jobs_negative CHECK (negative_prompt IS NULL OR BTRIM(negative_prompt) <> ''),
    CONSTRAINT ck_ai_jobs_snapshot CHECK (
        input_snapshot IS NULL OR (
            JSONB_TYPEOF(input_snapshot) = 'object' AND OCTET_LENGTH(input_snapshot::text) <= 32768
        )
    ),
    CONSTRAINT ck_ai_jobs_provider_job_id CHECK (
        provider_job_id IS NULL OR (BTRIM(provider_job_id) <> '' AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT ck_ai_jobs_costs CHECK (
        estimated_cost >= 0 AND reserved_cost >= 0 AND actual_cost >= 0
        AND estimated_cost <= reserved_cost
    ),
    CONSTRAINT ck_ai_jobs_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ai_jobs_failure CHECK (
        (failure_code IS NULL AND failure_message IS NULL)
        OR (failure_code IS NOT NULL AND BTRIM(failure_code) <> '')
    ),
    CONSTRAINT ck_ai_jobs_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_ai_jobs_timestamps CHECK (
        (started_at IS NULL OR submitted_at IS NOT NULL)
        AND (completed_at IS NULL OR submitted_at IS NOT NULL OR status IN ('BUDGET_REJECTED', 'CANCELLED'))
    ),
    CONSTRAINT ck_ai_jobs_version CHECK (version >= 0)
);

CREATE INDEX idx_ai_jobs_batch_created
    ON ai_generation_jobs (generation_batch_uuid, created_at);
CREATE INDEX idx_ai_jobs_product_created
    ON ai_generation_jobs (product_uuid, created_at DESC);
CREATE INDEX idx_ai_jobs_status_created
    ON ai_generation_jobs (status, created_at);

CREATE TABLE ai_budget_ledger (
    budget_ledger_uuid UUID PRIMARY KEY,
    generation_job_uuid UUID NOT NULL,
    budget_date DATE NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount NUMERIC(19,6) NOT NULL,
    currency CHAR(3) NOT NULL,
    entry_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_budget_ledger_job
        FOREIGN KEY (generation_job_uuid) REFERENCES ai_generation_jobs(generation_job_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_ai_budget_ledger_order UNIQUE (generation_job_uuid, entry_order),
    CONSTRAINT uq_ai_budget_ledger_type UNIQUE (generation_job_uuid, entry_type),
    CONSTRAINT ck_ai_budget_ledger_type CHECK (entry_type IN ('RESERVE', 'COMMIT', 'RELEASE')),
    CONSTRAINT ck_ai_budget_ledger_amount CHECK (amount > 0),
    CONSTRAINT ck_ai_budget_ledger_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ai_budget_ledger_order CHECK (entry_order >= 0)
);

CREATE INDEX idx_ai_budget_ledger_day_currency
    ON ai_budget_ledger (budget_date, currency, generation_job_uuid);

CREATE FUNCTION validate_ai_generation_relationships()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    batch_plan UUID;
    batch_currency CHAR(3);
    template_type VARCHAR(16);
BEGIN
    SELECT creative_plan_uuid, currency
      INTO batch_plan, batch_currency
      FROM ai_generation_batches
     WHERE generation_batch_uuid = NEW.generation_batch_uuid;
    IF NEW.creative_plan_uuid IS DISTINCT FROM batch_plan OR NEW.currency <> batch_currency THEN
        RAISE EXCEPTION 'AI job relationship does not match its batch' USING ERRCODE = '23514';
    END IF;

    SELECT t.generation_type
      INTO template_type
      FROM ai_prompt_template_versions v
      JOIN ai_prompt_templates t ON t.prompt_template_uuid = v.prompt_template_uuid
     WHERE v.prompt_template_version_uuid = NEW.prompt_template_version_uuid;
    IF NEW.generation_type <> template_type THEN
        RAISE EXCEPTION 'AI job generation type does not match its prompt template' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_ai_prompt_template_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.prompt_template_uuid IS DISTINCT FROM OLD.prompt_template_uuid
        OR NEW.template_key IS DISTINCT FROM OLD.template_key
        OR NEW.generation_type IS DISTINCT FROM OLD.generation_type
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'AI prompt template identity is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.lifecycle_status = 'ARCHIVED' AND NEW.lifecycle_status <> 'ARCHIVED' THEN
        RAISE EXCEPTION 'Archived AI prompt template cannot be restored' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_ai_batch_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.generation_batch_uuid IS DISTINCT FROM OLD.generation_batch_uuid
        OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid
        OR NEW.creative_plan_uuid IS DISTINCT FROM OLD.creative_plan_uuid
        OR NEW.currency IS DISTINCT FROM OLD.currency
        OR NEW.estimated_cost IS DISTINCT FROM OLD.estimated_cost
        OR NEW.reserved_cost IS DISTINCT FROM OLD.reserved_cost
        OR NEW.requested_job_count IS DISTINCT FROM OLD.requested_job_count
        OR NEW.created_by IS DISTINCT FROM OLD.created_by
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'AI generation batch identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_ai_job_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.generation_job_uuid IS DISTINCT FROM OLD.generation_job_uuid
        OR NEW.generation_batch_uuid IS DISTINCT FROM OLD.generation_batch_uuid
        OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid
        OR NEW.creative_plan_uuid IS DISTINCT FROM OLD.creative_plan_uuid
        OR NEW.prompt_template_version_uuid IS DISTINCT FROM OLD.prompt_template_version_uuid
        OR NEW.generation_type IS DISTINCT FROM OLD.generation_type
        OR NEW.provider_key IS DISTINCT FROM OLD.provider_key
        OR NEW.model_key IS DISTINCT FROM OLD.model_key
        OR NEW.rendered_prompt IS DISTINCT FROM OLD.rendered_prompt
        OR NEW.negative_prompt IS DISTINCT FROM OLD.negative_prompt
        OR NEW.input_snapshot IS DISTINCT FROM OLD.input_snapshot
        OR NEW.estimated_cost IS DISTINCT FROM OLD.estimated_cost
        OR NEW.reserved_cost IS DISTINCT FROM OLD.reserved_cost
        OR NEW.currency IS DISTINCT FROM OLD.currency
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR (OLD.provider_job_id IS NOT NULL AND NEW.provider_job_id IS DISTINCT FROM OLD.provider_job_id) THEN
        RAISE EXCEPTION 'AI generation job immutable fields cannot change' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_ai_record_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% cannot be deleted', TG_TABLE_NAME USING ERRCODE = '23514';
END;
$$;

CREATE FUNCTION reject_ai_append_only_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME USING ERRCODE = '23514';
END;
$$;

CREATE FUNCTION validate_ai_budget_ledger_entry()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    reserved_amount NUMERIC(19,6);
    committed_amount NUMERIC(19,6);
    reserved_date DATE;
    reserved_currency CHAR(3);
BEGIN
    IF NEW.entry_type = 'RESERVE' THEN
        IF NEW.entry_order <> 0 THEN
            RAISE EXCEPTION 'AI budget reservation must have entry order zero' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    SELECT amount, budget_date, currency
      INTO reserved_amount, reserved_date, reserved_currency
      FROM ai_budget_ledger
     WHERE generation_job_uuid = NEW.generation_job_uuid AND entry_type = 'RESERVE';
    IF reserved_amount IS NULL THEN
        RAISE EXCEPTION 'AI budget settlement requires a reservation' USING ERRCODE = '23514';
    END IF;
    IF NEW.budget_date <> reserved_date OR NEW.currency <> reserved_currency THEN
        RAISE EXCEPTION 'AI budget settlement must match reservation date and currency' USING ERRCODE = '23514';
    END IF;

    SELECT amount INTO committed_amount
      FROM ai_budget_ledger
     WHERE generation_job_uuid = NEW.generation_job_uuid AND entry_type = 'COMMIT';
    IF NEW.entry_type = 'COMMIT' AND NEW.entry_order <> 1 THEN
        RAISE EXCEPTION 'AI budget commit must have entry order one' USING ERRCODE = '23514';
    END IF;
    IF NEW.entry_type = 'RELEASE' THEN
        IF NEW.entry_order <> (CASE WHEN committed_amount IS NULL THEN 1 ELSE 2 END)
            OR NEW.amount <> (CASE
                WHEN committed_amount IS NULL THEN reserved_amount
                ELSE GREATEST(reserved_amount - committed_amount, 0)
            END) THEN
            RAISE EXCEPTION 'AI budget release does not reconcile its reservation' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ai_prompt_templates_protect
    BEFORE UPDATE ON ai_prompt_templates
    FOR EACH ROW EXECUTE FUNCTION protect_ai_prompt_template_mutation();
CREATE TRIGGER trg_ai_prompt_templates_no_delete
    BEFORE DELETE ON ai_prompt_templates
    FOR EACH ROW EXECUTE FUNCTION reject_ai_record_delete();
CREATE TRIGGER trg_ai_prompt_versions_append_only
    BEFORE UPDATE OR DELETE ON ai_prompt_template_versions
    FOR EACH ROW EXECUTE FUNCTION reject_ai_append_only_mutation();
CREATE TRIGGER trg_ai_batches_protect
    BEFORE UPDATE ON ai_generation_batches
    FOR EACH ROW EXECUTE FUNCTION protect_ai_batch_mutation();
CREATE TRIGGER trg_ai_batches_no_delete
    BEFORE DELETE ON ai_generation_batches
    FOR EACH ROW EXECUTE FUNCTION reject_ai_record_delete();
CREATE TRIGGER trg_ai_jobs_validate_relationships
    BEFORE INSERT OR UPDATE ON ai_generation_jobs
    FOR EACH ROW EXECUTE FUNCTION validate_ai_generation_relationships();
CREATE TRIGGER trg_ai_jobs_protect
    BEFORE UPDATE ON ai_generation_jobs
    FOR EACH ROW EXECUTE FUNCTION protect_ai_job_mutation();
CREATE TRIGGER trg_ai_jobs_no_delete
    BEFORE DELETE ON ai_generation_jobs
    FOR EACH ROW EXECUTE FUNCTION reject_ai_record_delete();
CREATE TRIGGER trg_ai_budget_ledger_append_only
    BEFORE UPDATE OR DELETE ON ai_budget_ledger
    FOR EACH ROW EXECUTE FUNCTION reject_ai_append_only_mutation();
CREATE TRIGGER trg_ai_budget_ledger_validate_entry
    BEFORE INSERT ON ai_budget_ledger
    FOR EACH ROW EXECUTE FUNCTION validate_ai_budget_ledger_entry();
