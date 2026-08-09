ALTER TABLE ai_generation_outputs
    ALTER COLUMN text_content DROP NOT NULL,
    DROP CONSTRAINT ck_ai_outputs_type,
    DROP CONSTRAINT ck_ai_outputs_text;

ALTER TABLE assets
    ADD CONSTRAINT uq_assets_uuid_product UNIQUE (asset_uuid, product_uuid);

ALTER TABLE ai_generation_outputs
    ADD COLUMN source_asset_uuid UUID,
    ADD COLUMN mask_asset_uuid UUID,
    ADD COLUMN generated_asset_uuid UUID,
    ADD COLUMN generation_mode VARCHAR(32),
    ADD COLUMN workflow_key VARCHAR(128),
    ADD COLUMN workflow_version VARCHAR(64),
    ADD COLUMN image_width INTEGER,
    ADD COLUMN image_height INTEGER,
    ADD COLUMN media_type VARCHAR(64),
    ADD COLUMN size_bytes BIGINT,
    ADD COLUMN source_checksum_sha256 VARCHAR(64),
    ADD COLUMN mask_checksum_sha256 VARCHAR(64),
    ADD COLUMN output_checksum_sha256 VARCHAR(64),
    ADD COLUMN protected_pixels_sha256 VARCHAR(64),
    ADD COLUMN preservation_algorithm VARCHAR(64),
    ADD COLUMN preservation_status VARCHAR(16),
    ADD COLUMN preservation_details JSONB,
    ADD CONSTRAINT fk_ai_outputs_source_asset FOREIGN KEY (source_asset_uuid, product_uuid)
        REFERENCES assets(asset_uuid, product_uuid) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ai_outputs_mask_asset FOREIGN KEY (mask_asset_uuid, product_uuid)
        REFERENCES assets(asset_uuid, product_uuid) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ai_outputs_generated_asset FOREIGN KEY (generated_asset_uuid, product_uuid)
        REFERENCES assets(asset_uuid, product_uuid) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_ai_outputs_generated_asset UNIQUE (generated_asset_uuid),
    ADD CONSTRAINT ck_ai_outputs_type CHECK (generation_type IN ('TEXT', 'IMAGE')),
    ADD CONSTRAINT ck_ai_outputs_dimensions CHECK (
        (image_width IS NULL AND image_height IS NULL)
        OR (image_width BETWEEN 1 AND 4096 AND image_height BETWEEN 1 AND 4096)
    ),
    ADD CONSTRAINT ck_ai_outputs_media_type CHECK (media_type IS NULL OR media_type IN ('image/png', 'image/jpeg')),
    ADD CONSTRAINT ck_ai_outputs_size CHECK (size_bytes IS NULL OR size_bytes BETWEEN 1 AND 16777216),
    ADD CONSTRAINT ck_ai_outputs_mode CHECK (generation_mode IS NULL OR generation_mode = 'BACKGROUND_COMPOSITE'),
    ADD CONSTRAINT ck_ai_outputs_preservation CHECK (
        preservation_status IS NULL OR preservation_status IN ('PASSED', 'BLOCKED')
    ),
    ADD CONSTRAINT ck_ai_outputs_preservation_algorithm CHECK (
        preservation_algorithm IS NULL OR preservation_algorithm = 'RGBA_MASK_EXACT_V1'
    ),
    ADD CONSTRAINT ck_ai_outputs_preservation_details CHECK (
        preservation_details IS NULL OR (
            JSONB_TYPEOF(preservation_details) = 'object'
            AND OCTET_LENGTH(preservation_details::text) <= 8192
            AND preservation_details ? 'changedPixelCount'
            AND preservation_details ? 'protectedPixelCount'
            AND JSONB_TYPEOF(preservation_details->'changedPixelCount') = 'number'
            AND JSONB_TYPEOF(preservation_details->'protectedPixelCount') = 'number'
            AND (preservation_details->>'changedPixelCount')::BIGINT >= 0
            AND (preservation_details->>'protectedPixelCount')::BIGINT > 0
            AND preservation_details - ARRAY['changedPixelCount', 'protectedPixelCount'] = '{}'::JSONB
        )
    ),
    ADD CONSTRAINT ck_ai_outputs_checksums CHECK (
        (source_checksum_sha256 IS NULL OR source_checksum_sha256 ~ '^[0-9a-f]{64}$')
        AND (mask_checksum_sha256 IS NULL OR mask_checksum_sha256 ~ '^[0-9a-f]{64}$')
        AND (output_checksum_sha256 IS NULL OR output_checksum_sha256 ~ '^[0-9a-f]{64}$')
        AND (protected_pixels_sha256 IS NULL OR protected_pixels_sha256 ~ '^[0-9a-f]{64}$')
    ),
    ADD CONSTRAINT ck_ai_outputs_mask_pair CHECK (
        (mask_asset_uuid IS NULL AND mask_checksum_sha256 IS NULL)
        OR (mask_asset_uuid IS NOT NULL AND mask_checksum_sha256 IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_outputs_type_coherence CHECK (
        (generation_type = 'TEXT'
            AND text_content IS NOT NULL AND BTRIM(text_content) <> ''
            AND source_asset_uuid IS NULL AND mask_asset_uuid IS NULL AND generated_asset_uuid IS NULL
            AND generation_mode IS NULL AND workflow_key IS NULL AND workflow_version IS NULL
            AND image_width IS NULL AND image_height IS NULL AND media_type IS NULL AND size_bytes IS NULL
            AND source_checksum_sha256 IS NULL AND mask_checksum_sha256 IS NULL
            AND output_checksum_sha256 IS NULL AND protected_pixels_sha256 IS NULL
            AND preservation_algorithm IS NULL AND preservation_status IS NULL AND preservation_details IS NULL)
        OR
        (generation_type = 'IMAGE'
            AND text_content IS NULL
            AND source_asset_uuid IS NOT NULL AND generated_asset_uuid IS NOT NULL
            AND generation_mode = 'BACKGROUND_COMPOSITE'
            AND BTRIM(workflow_key) <> '' AND BTRIM(workflow_version) <> ''
            AND image_width IS NOT NULL AND image_height IS NOT NULL
            AND media_type IS NOT NULL AND size_bytes IS NOT NULL
            AND source_checksum_sha256 IS NOT NULL AND output_checksum_sha256 IS NOT NULL
            AND protected_pixels_sha256 IS NOT NULL
            AND preservation_algorithm = 'RGBA_MASK_EXACT_V1'
            AND preservation_status IS NOT NULL AND preservation_details IS NOT NULL)
    );

CREATE INDEX idx_ai_outputs_source_asset ON ai_generation_outputs (source_asset_uuid) WHERE source_asset_uuid IS NOT NULL;

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
    RETURN NEW;
END;
$$;
