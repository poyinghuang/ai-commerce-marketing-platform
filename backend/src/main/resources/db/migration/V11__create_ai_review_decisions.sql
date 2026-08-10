ALTER TABLE ai_generation_outputs
    DROP CONSTRAINT ck_ai_outputs_review,
    ADD CONSTRAINT ck_ai_outputs_review
        CHECK (review_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT uq_ai_outputs_uuid_product UNIQUE (generation_output_uuid, product_uuid);

CREATE TABLE ai_review_decisions (
    review_decision_uuid UUID PRIMARY KEY,
    generation_output_uuid UUID NOT NULL UNIQUE REFERENCES ai_generation_outputs(generation_output_uuid) ON DELETE RESTRICT,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(2000),
    reviewer_type VARCHAR(32) NOT NULL CHECK (reviewer_type IN ('LOCAL_ADMIN', 'TRUSTED_ACTOR')),
    reviewer_id VARCHAR(128) NOT NULL CHECK (BTRIM(reviewer_id) <> ''),
    request_id VARCHAR(128) NOT NULL CHECK (request_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    reviewed_output_version BIGINT NOT NULL CHECK (reviewed_output_version >= 0),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_review_reason CHECK (
        (decision = 'APPROVED' AND reason IS NULL)
        OR (decision = 'REJECTED' AND reason IS NOT NULL AND BTRIM(reason) <> '')
    )
);

CREATE INDEX idx_ai_review_decisions_decided_at
    ON ai_review_decisions (decided_at DESC, review_decision_uuid);

CREATE FUNCTION reject_ai_review_decision_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'AI review decisions are append-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_ai_review_decisions_no_update
    BEFORE UPDATE ON ai_review_decisions
    FOR EACH ROW EXECUTE FUNCTION reject_ai_review_decision_update();
CREATE TRIGGER trg_ai_review_decisions_no_delete
    BEFORE DELETE ON ai_review_decisions
    FOR EACH ROW EXECUTE FUNCTION reject_ai_record_delete();

CREATE OR REPLACE FUNCTION protect_ai_generation_output_mutation()
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
        OR NEW.source_asset_uuid IS DISTINCT FROM OLD.source_asset_uuid
        OR NEW.mask_asset_uuid IS DISTINCT FROM OLD.mask_asset_uuid
        OR NEW.generated_asset_uuid IS DISTINCT FROM OLD.generated_asset_uuid
        OR NEW.generation_mode IS DISTINCT FROM OLD.generation_mode
        OR NEW.workflow_key IS DISTINCT FROM OLD.workflow_key
        OR NEW.workflow_version IS DISTINCT FROM OLD.workflow_version
        OR NEW.image_width IS DISTINCT FROM OLD.image_width
        OR NEW.image_height IS DISTINCT FROM OLD.image_height
        OR NEW.media_type IS DISTINCT FROM OLD.media_type
        OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes
        OR NEW.source_checksum_sha256 IS DISTINCT FROM OLD.source_checksum_sha256
        OR NEW.mask_checksum_sha256 IS DISTINCT FROM OLD.mask_checksum_sha256
        OR NEW.output_checksum_sha256 IS DISTINCT FROM OLD.output_checksum_sha256
        OR NEW.protected_pixels_sha256 IS DISTINCT FROM OLD.protected_pixels_sha256
        OR NEW.preservation_algorithm IS DISTINCT FROM OLD.preservation_algorithm
        OR NEW.preservation_status IS DISTINCT FROM OLD.preservation_status
        OR NEW.preservation_details IS DISTINCT FROM OLD.preservation_details
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'AI generation output immutable fields cannot change' USING ERRCODE = '23514';
    END IF;
    IF NEW.review_status IS DISTINCT FROM OLD.review_status
        AND NOT (OLD.review_status = 'PENDING_REVIEW' AND NEW.review_status IN ('APPROVED', 'REJECTED')) THEN
        RAISE EXCEPTION 'AI generation output review transition is invalid' USING ERRCODE = '23514';
    END IF;
    IF NEW.review_status IS NOT DISTINCT FROM OLD.review_status
        AND (NEW.version IS DISTINCT FROM OLD.version OR NEW.updated_at IS DISTINCT FROM OLD.updated_at) THEN
        RAISE EXCEPTION 'AI generation output version changes require a review transition' USING ERRCODE = '23514';
    END IF;
    IF NEW.review_status IS DISTINCT FROM OLD.review_status AND NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'AI generation output review transition must increment version once' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION verify_ai_review_coherence()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    output_id UUID := COALESCE(NEW.generation_output_uuid, OLD.generation_output_uuid);
    output_status VARCHAR(24);
    output_version BIGINT;
    stored_decision VARCHAR(16);
    stored_version BIGINT;
BEGIN
    SELECT review_status, version INTO output_status, output_version
      FROM ai_generation_outputs WHERE generation_output_uuid = output_id;
    SELECT decision, reviewed_output_version INTO stored_decision, stored_version
      FROM ai_review_decisions WHERE generation_output_uuid = output_id;

    IF output_status = 'PENDING_REVIEW' THEN
        IF stored_decision IS NOT NULL THEN
            RAISE EXCEPTION 'Pending output cannot have a review decision' USING ERRCODE = '23514';
        END IF;
    ELSIF stored_decision IS NULL OR stored_decision <> output_status OR output_version <> stored_version + 1 THEN
        RAISE EXCEPTION 'Terminal output and review decision are incoherent' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ai_outputs_review_coherence
    AFTER INSERT OR UPDATE OF review_status, version ON ai_generation_outputs
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_ai_review_coherence();

CREATE CONSTRAINT TRIGGER trg_ai_review_decisions_coherence
    AFTER INSERT ON ai_review_decisions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_ai_review_coherence();
