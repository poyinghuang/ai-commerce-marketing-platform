CREATE TABLE audit_logs (
    audit_uuid UUID PRIMARY KEY,
    operation_uuid UUID NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    source VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_uuid UUID NOT NULL,
    product_uuid UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_logs_request_id
        CHECK (request_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    CONSTRAINT ck_audit_logs_actor_type
        CHECK (actor_type IN ('LOCAL_ADMIN', 'SYSTEM', 'TRUSTED_ACTOR')),
    CONSTRAINT ck_audit_logs_source
        CHECK (source IN ('API', 'SYSTEM')),
    CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('CREATE', 'UPDATE', 'ARCHIVE', 'RESTORE')),
    CONSTRAINT ck_audit_logs_actor_id_not_blank CHECK (BTRIM(actor_id) <> ''),
    CONSTRAINT ck_audit_logs_entity_type_not_blank CHECK (BTRIM(entity_type) <> '')
);

CREATE TABLE audit_log_changes (
    audit_change_uuid UUID PRIMARY KEY,
    audit_uuid UUID NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    value_type VARCHAR(32) NOT NULL,
    change_order SMALLINT NOT NULL,
    CONSTRAINT fk_audit_log_changes_audit
        FOREIGN KEY (audit_uuid) REFERENCES audit_logs(audit_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_audit_log_changes_order UNIQUE (audit_uuid, change_order),
    CONSTRAINT ck_audit_log_changes_order CHECK (change_order >= 0),
    CONSTRAINT ck_audit_log_changes_value_type
        CHECK (value_type IN ('STRING', 'UUID', 'ENUM', 'TIMESTAMP')),
    CONSTRAINT ck_audit_log_changes_field_not_blank CHECK (BTRIM(field_name) <> ''),
    CONSTRAINT ck_audit_log_changes_old_value_length
        CHECK (old_value IS NULL OR CHAR_LENGTH(old_value) <= 4096),
    CONSTRAINT ck_audit_log_changes_new_value_length
        CHECK (new_value IS NULL OR CHAR_LENGTH(new_value) <= 4096)
);

CREATE INDEX idx_audit_logs_operation_uuid ON audit_logs (operation_uuid);
CREATE INDEX idx_audit_logs_request_id ON audit_logs (request_id);
CREATE INDEX idx_audit_logs_product_uuid_occurred_at
    ON audit_logs (product_uuid, occurred_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_uuid);
CREATE INDEX idx_audit_log_changes_audit_field
    ON audit_log_changes (audit_uuid, field_name);

CREATE FUNCTION reject_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION reject_audit_mutation();

CREATE TRIGGER trg_audit_log_changes_append_only
    BEFORE UPDATE OR DELETE ON audit_log_changes
    FOR EACH ROW
    EXECUTE FUNCTION reject_audit_mutation();
