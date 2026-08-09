CREATE TABLE product_storage_folders (
    storage_folder_uuid UUID PRIMARY KEY,
    product_uuid UUID NOT NULL UNIQUE,
    storage_provider VARCHAR(32) NOT NULL,
    root_folder_id VARCHAR(256) NOT NULL,
    shared_drive_id VARCHAR(256),
    product_folder_id VARCHAR(256) NOT NULL UNIQUE,
    provider_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_storage_folders_product
        FOREIGN KEY (product_uuid) REFERENCES products(product_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_product_storage_folders_provider CHECK (storage_provider = 'GOOGLE_DRIVE'),
    CONSTRAINT ck_product_storage_folders_ids CHECK (
        BTRIM(root_folder_id) <> ''
        AND BTRIM(product_folder_id) <> ''
        AND (shared_drive_id IS NULL OR BTRIM(shared_drive_id) <> '')
    ),
    CONSTRAINT ck_product_storage_folders_metadata CHECK (
        JSONB_TYPEOF(provider_metadata) = 'object'
        AND OCTET_LENGTH(provider_metadata::text) <= 16384
    )
);

CREATE INDEX idx_product_storage_folders_product ON product_storage_folders (product_uuid);

CREATE TABLE product_storage_subfolders (
    storage_subfolder_uuid UUID PRIMARY KEY,
    storage_folder_uuid UUID NOT NULL,
    folder_role VARCHAR(32) NOT NULL,
    provider_folder_id VARCHAR(256) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_storage_subfolders_folder
        FOREIGN KEY (storage_folder_uuid) REFERENCES product_storage_folders(storage_folder_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_product_storage_subfolders_role UNIQUE (storage_folder_uuid, folder_role),
    CONSTRAINT ck_product_storage_subfolders_role CHECK (
        folder_role IN ('ORIGINAL', 'IMAGES', 'VIDEOS', 'DOCUMENTS', 'CAMPAIGNS', 'ARCHIVE')
    ),
    CONSTRAINT ck_product_storage_subfolders_id CHECK (BTRIM(provider_folder_id) <> '')
);

CREATE INDEX idx_product_storage_subfolders_folder ON product_storage_subfolders (storage_folder_uuid);

CREATE FUNCTION reject_product_storage_folder_identity_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.storage_folder_uuid IS DISTINCT FROM OLD.storage_folder_uuid
        OR NEW.product_uuid IS DISTINCT FROM OLD.product_uuid
        OR NEW.storage_provider IS DISTINCT FROM OLD.storage_provider
        OR NEW.root_folder_id IS DISTINCT FROM OLD.root_folder_id
        OR NEW.shared_drive_id IS DISTINCT FROM OLD.shared_drive_id
        OR NEW.product_folder_id IS DISTINCT FROM OLD.product_folder_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'product storage folder identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_product_storage_subfolder_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'product storage subfolder is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE FUNCTION reject_product_storage_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'product storage records cannot be deleted' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_product_storage_folders_immutable_identity
    BEFORE UPDATE ON product_storage_folders
    FOR EACH ROW EXECUTE FUNCTION reject_product_storage_folder_identity_change();
CREATE TRIGGER trg_product_storage_subfolders_immutable
    BEFORE UPDATE ON product_storage_subfolders
    FOR EACH ROW EXECUTE FUNCTION reject_product_storage_subfolder_change();
CREATE TRIGGER trg_product_storage_folders_no_delete
    BEFORE DELETE ON product_storage_folders
    FOR EACH ROW EXECUTE FUNCTION reject_product_storage_delete();
CREATE TRIGGER trg_product_storage_subfolders_no_delete
    BEFORE DELETE ON product_storage_subfolders
    FOR EACH ROW EXECUTE FUNCTION reject_product_storage_delete();
