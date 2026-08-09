ALTER TABLE ai_generation_jobs
    ADD CONSTRAINT uq_ai_jobs_output_relationship
    UNIQUE (generation_job_uuid, generation_batch_uuid, product_uuid);

CREATE TABLE ai_generation_outputs (
    generation_output_uuid UUID PRIMARY KEY,
    generation_job_uuid UUID NOT NULL,
    generation_batch_uuid UUID NOT NULL,
    product_uuid UUID NOT NULL,
    generation_type VARCHAR(16) NOT NULL,
    text_content VARCHAR(16000) NOT NULL,
    model_label VARCHAR(128) NOT NULL,
    input_units BIGINT NOT NULL,
    output_units BIGINT NOT NULL,
    actual_cost NUMERIC(19,6) NOT NULL,
    currency CHAR(3) NOT NULL,
    safety_findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    provider_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ai_outputs_job UNIQUE (generation_job_uuid),
    CONSTRAINT fk_ai_outputs_job_relationship
        FOREIGN KEY (generation_job_uuid, generation_batch_uuid, product_uuid)
        REFERENCES ai_generation_jobs(generation_job_uuid, generation_batch_uuid, product_uuid)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_outputs_type CHECK (generation_type = 'TEXT'),
    CONSTRAINT ck_ai_outputs_text CHECK (BTRIM(text_content) <> ''),
    CONSTRAINT ck_ai_outputs_model CHECK (BTRIM(model_label) <> ''),
    CONSTRAINT ck_ai_outputs_usage CHECK (input_units >= 0 AND output_units >= 0),
    CONSTRAINT ck_ai_outputs_cost CHECK (actual_cost >= 0),
    CONSTRAINT ck_ai_outputs_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ai_outputs_safety CHECK (
        JSONB_TYPEOF(safety_findings) = 'array'
        AND OCTET_LENGTH(safety_findings::text) <= 8192
    ),
    CONSTRAINT ck_ai_outputs_metadata CHECK (
        JSONB_TYPEOF(provider_metadata) = 'object'
        AND OCTET_LENGTH(provider_metadata::text) <= 8192
    ),
    CONSTRAINT ck_ai_outputs_review CHECK (review_status = 'PENDING_REVIEW'),
    CONSTRAINT ck_ai_outputs_version CHECK (version >= 0)
);

CREATE INDEX idx_ai_outputs_product_created
    ON ai_generation_outputs (product_uuid, created_at DESC);
CREATE INDEX idx_ai_outputs_batch_created
    ON ai_generation_outputs (generation_batch_uuid, created_at);

CREATE FUNCTION protect_ai_generation_output_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.generation_output_uuid IS DISTINCT FROM OLD.generation_output_uuid
        OR NEW.generation_job_uuid IS DISTINCT FROM OLD.generation_job_uuid
        OR NEW.generation_batch_uuid IS DISTINCT FROM OLD.generation_batch_uuid
        OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid
        OR NEW.generation_type IS DISTINCT FROM OLD.generation_type
        OR NEW.text_content IS DISTINCT FROM OLD.text_content
        OR NEW.model_label IS DISTINCT FROM OLD.model_label
        OR NEW.input_units IS DISTINCT FROM OLD.input_units
        OR NEW.output_units IS DISTINCT FROM OLD.output_units
        OR NEW.actual_cost IS DISTINCT FROM OLD.actual_cost
        OR NEW.currency IS DISTINCT FROM OLD.currency
        OR NEW.safety_findings IS DISTINCT FROM OLD.safety_findings
        OR NEW.provider_metadata IS DISTINCT FROM OLD.provider_metadata
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'AI generation output immutable fields cannot change' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ai_outputs_protect
    BEFORE UPDATE ON ai_generation_outputs
    FOR EACH ROW EXECUTE FUNCTION protect_ai_generation_output_mutation();
CREATE TRIGGER trg_ai_outputs_no_delete
    BEFORE DELETE ON ai_generation_outputs
    FOR EACH ROW EXECUTE FUNCTION reject_ai_record_delete();
