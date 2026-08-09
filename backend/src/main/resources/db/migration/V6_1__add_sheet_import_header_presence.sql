ALTER TABLE sheet_import_jobs
    ADD COLUMN header_presence_mask INTEGER NOT NULL DEFAULT 8191;

ALTER TABLE sheet_import_jobs
    ADD CONSTRAINT ck_sheet_import_jobs_header_presence CHECK (
        header_presence_mask BETWEEN 0 AND 8191
        AND (header_presence_mask & 11) = 11
    );

ALTER TABLE sheet_import_jobs
    ALTER COLUMN header_presence_mask DROP DEFAULT;

CREATE FUNCTION reject_sheet_import_job_header_presence_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.header_presence_mask IS DISTINCT FROM OLD.header_presence_mask THEN
        RAISE EXCEPTION 'sheet import job header presence is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sheet_import_jobs_immutable_header_presence
    BEFORE UPDATE ON sheet_import_jobs
    FOR EACH ROW EXECUTE FUNCTION reject_sheet_import_job_header_presence_change();
