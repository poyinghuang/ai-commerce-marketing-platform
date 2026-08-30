-- Stage 08C expands the provider-key allow-list. V1-V17 remain immutable.
DO $$
DECLARE
  cname text;
BEGIN
  SELECT con.conname INTO cname
  FROM pg_constraint con
  JOIN pg_class rel ON rel.oid = con.conrelid
  JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
  WHERE nsp.nspname = current_schema()
    AND rel.relname = 'platform_accounts'
    AND con.contype = 'c'
    AND pg_get_constraintdef(con.oid) LIKE '%provider_key%'
    AND pg_get_constraintdef(con.oid) LIKE '%FAKE_GOOGLE%'
    AND pg_get_constraintdef(con.oid) NOT LIKE '%META%'
    AND con.conname <> 'ck_platform_accounts_provider_key_v18';
  IF cname IS NULL THEN
    RAISE EXCEPTION 'platform_accounts provider_key FAKE_GOOGLE check not found';
  END IF;
  EXECUTE format('ALTER TABLE platform_accounts DROP CONSTRAINT %I', cname);
END
$$;

ALTER TABLE platform_accounts
  ADD CONSTRAINT ck_platform_accounts_provider_key_v18
  CHECK (provider_key IN ('FAKE', 'FAKE_GOOGLE', 'META'));

CREATE OR REPLACE FUNCTION is_valid_platform_evidence(value JSONB) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
 SELECT value IS NOT NULL AND jsonb_typeof(value)='object'
  AND value ?& ARRAY['schemaVersion','providerKey','attemptKind','resultKind']
  AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','providerKey','attemptKind','resultKind','externalIdFingerprint','observedState','retryAfterSeconds']))
  AND jsonb_typeof(value->'schemaVersion')='number' AND value->>'schemaVersion'='1'
  AND value->>'providerKey' IN ('FAKE','FAKE_GOOGLE','META') AND value->>'attemptKind' IN ('SUBMIT','RECONCILE')
  AND value->>'resultKind' IN ('SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL','UNKNOWN_OUTCOME','FOUND','NOT_FOUND','STILL_UNKNOWN')
  AND NOT (value->>'attemptKind'='SUBMIT' AND value->>'resultKind' IN ('FOUND','NOT_FOUND','STILL_UNKNOWN'))
  AND NOT (value->>'attemptKind'='RECONCILE' AND value->>'resultKind' IN ('SUCCEEDED','FAILED_RETRYABLE'))
  AND (NOT value?'externalIdFingerprint' OR (jsonb_typeof(value->'externalIdFingerprint')='string' AND value->>'externalIdFingerprint' ~ '^[0-9a-f]{64}$'))
  AND (NOT value?'observedState' OR (jsonb_typeof(value->'observedState')='string' AND value->>'observedState' IN ('UNKNOWN','PENDING','PAUSED','ACTIVE','COMPLETED','REJECTED','ERROR','DELETED')))
  AND (NOT value?'retryAfterSeconds' OR (jsonb_typeof(value->'retryAfterSeconds')='number' AND value->>'retryAfterSeconds' ~ '^[0-9]+$' AND (value->>'retryAfterSeconds')::integer BETWEEN 1 AND 3600))
  AND CASE value->>'resultKind'
    WHEN 'FAILED_RETRYABLE' THEN value?'retryAfterSeconds' AND NOT value?'externalIdFingerprint' AND NOT value?'observedState'
    WHEN 'SUCCEEDED' THEN NOT value?'retryAfterSeconds'
    WHEN 'FOUND' THEN NOT value?'retryAfterSeconds'
    ELSE NOT value?'externalIdFingerprint' AND NOT value?'observedState' AND NOT value?'retryAfterSeconds'
  END;
$$;
