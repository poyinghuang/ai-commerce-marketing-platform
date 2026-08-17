-- Stage 04A is deliberately FAKE-only.  All changes are additive and V1-V11 remain immutable.
CREATE FUNCTION is_valid_platform_evidence(value JSONB) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
 SELECT value IS NOT NULL AND jsonb_typeof(value)='object'
  AND value ?& ARRAY['schemaVersion','providerKey','attemptKind','resultKind']
  AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','providerKey','attemptKind','resultKind','externalIdFingerprint','observedState','retryAfterSeconds']))
  AND jsonb_typeof(value->'schemaVersion')='number' AND value->>'schemaVersion'='1'
  AND value->>'providerKey'='FAKE' AND value->>'attemptKind' IN ('SUBMIT','RECONCILE')
  AND value->>'resultKind' IN ('SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL','UNKNOWN_OUTCOME','FOUND','NOT_FOUND','STILL_UNKNOWN')
  AND NOT (value->>'attemptKind'='SUBMIT' AND value->>'resultKind' IN ('FOUND','NOT_FOUND','STILL_UNKNOWN'))
  AND NOT (value->>'attemptKind'='RECONCILE' AND value->>'resultKind' IN ('SUCCEEDED','FAILED_RETRYABLE','UNKNOWN_OUTCOME'))
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

CREATE FUNCTION is_valid_platform_request(value JSONB,op TEXT,entity TEXT,entity_id UUID) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
 SELECT value IS NOT NULL AND jsonb_typeof(value)='object' AND value?&ARRAY['schemaVersion','operationType','entityType','entityUuid']
  AND jsonb_typeof(value->'schemaVersion')='number' AND value->>'schemaVersion'='1'
  AND jsonb_typeof(value->'operationType')='string' AND value->>'operationType'=op
  AND jsonb_typeof(value->'entityType')='string' AND value->>'entityType'=entity
  AND jsonb_typeof(value->'entityUuid')='string' AND value->>'entityUuid'=lower(entity_id::text)
  AND NOT EXISTS (SELECT 1 FROM jsonb_each(value) e WHERE jsonb_typeof(e.value) IN ('null','array','object'))
  AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE lower(k) SIMILAR TO '%(authorization|cookie|credential|password|secret|token|url)%')
  AND CASE op
   WHEN 'CREATE_CAMPAIGN' THEN entity='CAMPAIGN' AND value?&ARRAY['platformCampaignUuid','campaignUuid','objective','desiredState','accountTimezone']
     AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','platformCampaignUuid','campaignUuid','objective','desiredState','accountTimezone','scheduleStart','scheduleEnd']))
     AND value->>'platformCampaignUuid'=lower(entity_id::text) AND value->>'campaignUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND value->>'objective'='OUTCOME_SALES' AND value->>'desiredState'='PAUSED' AND value->>'accountTimezone'='Asia/Taipei'
     AND (value?'scheduleStart')=(value?'scheduleEnd')
     AND (NOT value?'scheduleStart' OR (jsonb_typeof(value->'scheduleStart')='string' AND jsonb_typeof(value->'scheduleEnd')='string' AND value->>'scheduleStart'~'^20[0-9]{2}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,6})?Z$' AND (value->>'scheduleEnd')::timestamptz>(value->>'scheduleStart')::timestamptz))
   WHEN 'CREATE_AD_SET' THEN entity='AD_SET' AND value?&ARRAY['platformAdSetUuid','platformCampaignUuid','budgetType','budgetAmount','currency','accountTimezone','optimizationGoal','targetingProfileKey','placementProfileKey','desiredState']
     AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','platformAdSetUuid','platformCampaignUuid','budgetType','budgetAmount','currency','accountTimezone','optimizationGoal','targetingProfileKey','placementProfileKey','desiredState','scheduleStart','scheduleEnd']))
     AND value->>'platformAdSetUuid'=lower(entity_id::text) AND value->>'platformCampaignUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND jsonb_typeof(value->'budgetAmount')='number' AND value->>'budgetAmount'~'^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$'
     AND ((value->>'budgetType'='DAILY' AND (value->>'budgetAmount')::numeric>0 AND (value->>'budgetAmount')::numeric<=100) OR (value->>'budgetType'='LIFETIME' AND (value->>'budgetAmount')::numeric>0 AND (value->>'budgetAmount')::numeric<=300))
     AND value->>'currency'='TWD' AND value->>'accountTimezone'='Asia/Taipei' AND jsonb_typeof(value->'optimizationGoal')='string' AND btrim(value->>'optimizationGoal')<>''
     AND value->>'targetingProfileKey'='TW_BROAD_FEEDS_V1' AND value->>'placementProfileKey'='TW_BROAD_FEEDS_V1' AND value->>'desiredState'='PAUSED'
     AND (value?'scheduleStart')=(value?'scheduleEnd')
     AND (NOT value?'scheduleStart' OR (jsonb_typeof(value->'scheduleStart')='string' AND jsonb_typeof(value->'scheduleEnd')='string' AND value->>'scheduleStart'~'^20[0-9]{2}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,6})?Z$' AND (value->>'scheduleEnd')::timestamptz>(value->>'scheduleStart')::timestamptz))
   WHEN 'CREATE_AD' THEN entity='AD' AND value?&ARRAY['platformAdUuid','platformAdSetUuid','productUuid','assetUuid','generationOutputUuid','reviewDecisionUuid','approvedChecksumSha256','creativeMappingKey','desiredState']
     AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','platformAdUuid','platformAdSetUuid','productUuid','assetUuid','generationOutputUuid','reviewDecisionUuid','approvedChecksumSha256','creativeMappingKey','desiredState']))
     AND value->>'platformAdUuid'=lower(entity_id::text) AND value->>'platformAdSetUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND value->>'productUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' AND value->>'assetUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND value->>'generationOutputUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' AND value->>'reviewDecisionUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND value->>'approvedChecksumSha256'~'^[0-9a-f]{64}$' AND jsonb_typeof(value->'creativeMappingKey')='string' AND btrim(value->>'creativeMappingKey')<>'' AND value->>'desiredState'='PAUSED'
   WHEN 'PAUSE' THEN jsonb_typeof(value->'expectedEntityVersion')='number' AND value->>'expectedEntityVersion'~'^(0|[1-9][0-9]*)$' AND value->>'targetDesiredState'='PAUSED' AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','expectedEntityVersion','targetDesiredState']))
   WHEN 'RESUME' THEN jsonb_typeof(value->'expectedEntityVersion')='number' AND value->>'expectedEntityVersion'~'^(0|[1-9][0-9]*)$' AND value->>'targetDesiredState'='ACTIVE' AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','expectedEntityVersion','targetDesiredState']))
   WHEN 'UPDATE_BUDGET' THEN entity='AD_SET' AND value?&ARRAY['platformAdSetUuid','expectedEntityVersion','budgetType','currency','previousBudgetAmount','newBudgetAmount'] AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','platformAdSetUuid','expectedEntityVersion','budgetType','currency','previousBudgetAmount','newBudgetAmount']))
     AND value->>'platformAdSetUuid'=lower(entity_id::text) AND jsonb_typeof(value->'expectedEntityVersion')='number' AND value->>'expectedEntityVersion'~'^(0|[1-9][0-9]*)$' AND value->>'currency'='TWD'
     AND jsonb_typeof(value->'previousBudgetAmount')='number' AND jsonb_typeof(value->'newBudgetAmount')='number' AND value->>'previousBudgetAmount'~'^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$' AND value->>'newBudgetAmount'~'^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$'
     AND (value->>'previousBudgetAmount')::numeric>0 AND (value->>'newBudgetAmount')::numeric<>(value->>'previousBudgetAmount')::numeric
     AND ((value->>'budgetType'='DAILY' AND (value->>'newBudgetAmount')::numeric<=100) OR (value->>'budgetType'='LIFETIME' AND (value->>'newBudgetAmount')::numeric<=300))
   ELSE FALSE END;
$$;
CREATE TABLE platform_accounts (
    platform_account_uuid UUID PRIMARY KEY,
    provider_key VARCHAR(32) NOT NULL CHECK (provider_key = 'FAKE'),
    environment VARCHAR(16) NOT NULL CHECK (environment IN ('LOCAL','TEST')),
    account_reference VARCHAR(128) NOT NULL CHECK (BTRIM(account_reference) <> ''),
    external_account_fingerprint CHAR(64) NOT NULL CHECK (external_account_fingerprint ~ '^[0-9a-f]{64}$'),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    timezone VARCHAR(64) NOT NULL CHECK (BTRIM(timezone) <> ''),
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (lifecycle_status IN ('ACTIVE','ARCHIVED')),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_platform_accounts_reference UNIQUE (provider_key,environment,account_reference),
    CONSTRAINT uq_platform_accounts_fingerprint UNIQUE (provider_key,environment,external_account_fingerprint),
    CONSTRAINT uq_platform_accounts_uuid_provider UNIQUE (platform_account_uuid,provider_key),
    CONSTRAINT ck_platform_accounts_archive CHECK ((lifecycle_status='ACTIVE' AND archived_at IS NULL) OR (lifecycle_status='ARCHIVED' AND archived_at IS NOT NULL))
);
CREATE INDEX idx_platform_accounts_lifecycle ON platform_accounts(lifecycle_status,updated_at DESC);

CREATE TABLE platform_campaigns (
    platform_campaign_uuid UUID PRIMARY KEY,
    campaign_uuid UUID NOT NULL REFERENCES campaign_plans(campaign_uuid) ON DELETE RESTRICT,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    objective VARCHAR(32) NOT NULL CHECK (objective='OUTCOME_SALES'),
    desired_state VARCHAR(16) NOT NULL DEFAULT 'PAUSED' CHECK (desired_state IN ('DRAFT','PAUSED','ACTIVE','ARCHIVED')),
    observed_state VARCHAR(24) CHECK (observed_state IN ('UNKNOWN','PENDING','PAUSED','ACTIVE','COMPLETED','REJECTED','ERROR','DELETED')),
    schedule_start TIMESTAMPTZ,
    schedule_end TIMESTAMPTZ,
    account_timezone VARCHAR(64) NOT NULL CHECK (account_timezone='Asia/Taipei'),
    external_id VARCHAR(128) CHECK (external_id IS NULL OR external_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_platform_campaigns_uuid_account UNIQUE(platform_campaign_uuid,platform_account_uuid),
    CONSTRAINT uq_platform_campaigns_plan_account UNIQUE(campaign_uuid,platform_account_uuid),
    CONSTRAINT ck_platform_campaigns_schedule CHECK ((schedule_start IS NULL AND schedule_end IS NULL) OR (schedule_start IS NOT NULL AND schedule_end > schedule_start))
);
CREATE UNIQUE INDEX uq_platform_campaigns_external ON platform_campaigns(platform_account_uuid,external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_platform_campaigns_plan ON platform_campaigns(campaign_uuid);
CREATE INDEX idx_platform_campaigns_account ON platform_campaigns(platform_account_uuid);

CREATE TABLE platform_ad_sets (
    platform_ad_set_uuid UUID PRIMARY KEY,
    platform_campaign_uuid UUID NOT NULL,
    platform_account_uuid UUID NOT NULL,
    budget_type VARCHAR(16) NOT NULL CHECK (budget_type IN ('DAILY','LIFETIME')),
    budget_amount NUMERIC(19,6) NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency='TWD'),
    schedule_start TIMESTAMPTZ,
    schedule_end TIMESTAMPTZ,
    account_timezone VARCHAR(64) NOT NULL CHECK (account_timezone='Asia/Taipei'),
    optimization_goal VARCHAR(64) NOT NULL CHECK (BTRIM(optimization_goal)<>''),
    targeting_profile_key VARCHAR(128) NOT NULL CHECK (targeting_profile_key='TW_BROAD_FEEDS_V1'),
    placement_profile_key VARCHAR(128) NOT NULL CHECK (placement_profile_key='TW_BROAD_FEEDS_V1'),
    desired_state VARCHAR(16) NOT NULL DEFAULT 'PAUSED' CHECK (desired_state IN ('DRAFT','PAUSED','ACTIVE','ARCHIVED')),
    observed_state VARCHAR(24) CHECK (observed_state IN ('UNKNOWN','PENDING','PAUSED','ACTIVE','COMPLETED','REJECTED','ERROR','DELETED')),
    external_id VARCHAR(128) CHECK (external_id IS NULL OR external_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    last_budget_operation_uuid UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT fk_platform_ad_sets_campaign FOREIGN KEY(platform_campaign_uuid,platform_account_uuid) REFERENCES platform_campaigns(platform_campaign_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_platform_ad_sets_uuid_account UNIQUE(platform_ad_set_uuid,platform_account_uuid),
    CONSTRAINT ck_platform_ad_sets_budget CHECK ((budget_type='DAILY' AND budget_amount>0 AND budget_amount<=100.000000) OR (budget_type='LIFETIME' AND budget_amount>0 AND budget_amount<=300.000000)),
    CONSTRAINT ck_platform_ad_sets_schedule CHECK ((schedule_start IS NULL AND schedule_end IS NULL) OR (schedule_start IS NOT NULL AND schedule_end > schedule_start))
);
CREATE UNIQUE INDEX uq_platform_ad_sets_external ON platform_ad_sets(platform_account_uuid,external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_platform_ad_sets_campaign_account ON platform_ad_sets(platform_campaign_uuid,platform_account_uuid);

ALTER TABLE ai_review_decisions ADD CONSTRAINT uq_ai_review_decisions_uuid_output UNIQUE(review_decision_uuid,generation_output_uuid);

CREATE TABLE platform_ads (
    platform_ad_uuid UUID PRIMARY KEY,
    platform_ad_set_uuid UUID NOT NULL,
    platform_account_uuid UUID NOT NULL,
    product_uuid UUID NOT NULL,
    asset_uuid UUID NOT NULL,
    generation_output_uuid UUID NOT NULL,
    review_decision_uuid UUID NOT NULL,
    approved_checksum_sha256 CHAR(64) NOT NULL CHECK (approved_checksum_sha256 ~ '^[0-9a-f]{64}$'),
    creative_mapping_key VARCHAR(128) NOT NULL CHECK (BTRIM(creative_mapping_key)<>''),
    desired_state VARCHAR(16) NOT NULL DEFAULT 'PAUSED' CHECK (desired_state IN ('DRAFT','PAUSED','ACTIVE','ARCHIVED')),
    observed_state VARCHAR(24) CHECK (observed_state IN ('UNKNOWN','PENDING','PAUSED','ACTIVE','COMPLETED','REJECTED','ERROR','DELETED')),
    external_id VARCHAR(128) CHECK (external_id IS NULL OR external_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT fk_platform_ads_ad_set FOREIGN KEY(platform_ad_set_uuid,platform_account_uuid) REFERENCES platform_ad_sets(platform_ad_set_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ads_asset FOREIGN KEY(asset_uuid,product_uuid) REFERENCES assets(asset_uuid,product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ads_output FOREIGN KEY(generation_output_uuid,product_uuid) REFERENCES ai_generation_outputs(generation_output_uuid,product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ads_review FOREIGN KEY(review_decision_uuid,generation_output_uuid) REFERENCES ai_review_decisions(review_decision_uuid,generation_output_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_platform_ads_uuid_account UNIQUE(platform_ad_uuid,platform_account_uuid)
);
CREATE UNIQUE INDEX uq_platform_ads_external ON platform_ads(platform_account_uuid,external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_platform_ads_ad_set_account ON platform_ads(platform_ad_set_uuid,platform_account_uuid);
CREATE INDEX idx_platform_ads_asset_product ON platform_ads(asset_uuid,product_uuid);
CREATE INDEX idx_platform_ads_output_product ON platform_ads(generation_output_uuid,product_uuid);
CREATE INDEX idx_platform_ads_review_output ON platform_ads(review_decision_uuid,generation_output_uuid);

CREATE TABLE platform_operations (
    operation_uuid UUID PRIMARY KEY,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    operation_type VARCHAR(32) NOT NULL CHECK (operation_type IN ('CREATE_CAMPAIGN','CREATE_AD_SET','CREATE_AD','PAUSE','RESUME','UPDATE_BUDGET')),
    entity_type VARCHAR(16) NOT NULL CHECK (entity_type IN ('CAMPAIGN','AD_SET','AD')),
    platform_campaign_uuid UUID,
    platform_ad_set_uuid UUID,
    platform_ad_uuid UUID,
    client_request_uuid UUID NOT NULL,
    idempotency_key CHAR(64) NOT NULL CHECK (idempotency_key ~ '^[0-9a-f]{64}$'),
    request_payload JSONB NOT NULL CHECK (JSONB_TYPEOF(request_payload)='object' AND OCTET_LENGTH(request_payload::text)<=16384),
    request_sha256 CHAR(64) NOT NULL CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    requested_actor_type VARCHAR(32) NOT NULL CHECK (requested_actor_type IN ('LOCAL_ADMIN','SYSTEM')),
    requested_actor_id VARCHAR(128) NOT NULL CHECK (BTRIM(requested_actor_id)<>''),
    request_id VARCHAR(128) NOT NULL CHECK (request_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    status VARCHAR(24) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','SUBMITTING','SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL','UNKNOWN_OUTCOME','RECONCILING')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
    reconciliation_count INTEGER NOT NULL DEFAULT 0 CHECK (reconciliation_count BETWEEN 0 AND 3),
    max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts=3),
    external_id VARCHAR(128) CHECK (external_id IS NULL OR external_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    normalized_error_code VARCHAR(64) CHECK (normalized_error_code IS NULL OR normalized_error_code IN ('PLATFORM_RATE_LIMITED','PLATFORM_TEMPORARILY_UNAVAILABLE','PLATFORM_VALIDATION_FAILED','PLATFORM_PERMISSION_DENIED','PLATFORM_MAX_ATTEMPTS_EXCEEDED','PLATFORM_RESPONSE_AMBIGUOUS','PLATFORM_RECONCILIATION_NOT_FOUND','PLATFORM_RECONCILIATION_INCONCLUSIVE','PLATFORM_RECONCILIATION_TERMINAL')),
    safe_provider_trace_id VARCHAR(128) CHECK (safe_provider_trace_id IS NULL OR safe_provider_trace_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    outcome_evidence JSONB CHECK (outcome_evidence IS NULL OR (OCTET_LENGTH(outcome_evidence::text)<=8192 AND is_valid_platform_evidence(outcome_evidence))),
    next_attempt_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
    CONSTRAINT fk_platform_operations_campaign FOREIGN KEY(platform_campaign_uuid,platform_account_uuid) REFERENCES platform_campaigns(platform_campaign_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_operations_ad_set FOREIGN KEY(platform_ad_set_uuid,platform_account_uuid) REFERENCES platform_ad_sets(platform_ad_set_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_operations_ad FOREIGN KEY(platform_ad_uuid,platform_account_uuid) REFERENCES platform_ads(platform_ad_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_platform_operations_request UNIQUE(platform_account_uuid,requested_actor_type,requested_actor_id,client_request_uuid),
    CONSTRAINT uq_platform_operations_idempotency UNIQUE(platform_account_uuid,idempotency_key),
    CONSTRAINT ck_platform_operations_entity CHECK ((entity_type='CAMPAIGN' AND platform_campaign_uuid IS NOT NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD_SET' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NOT NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NOT NULL)),
    CONSTRAINT ck_platform_operations_type_entity CHECK ((operation_type='CREATE_CAMPAIGN' AND entity_type='CAMPAIGN') OR (operation_type IN ('CREATE_AD_SET','UPDATE_BUDGET') AND entity_type='AD_SET') OR (operation_type='CREATE_AD' AND entity_type='AD') OR operation_type IN ('PAUSE','RESUME')),
    CONSTRAINT ck_platform_operations_request_contract CHECK (is_valid_platform_request(request_payload,operation_type,entity_type,COALESCE(platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid))),
    CONSTRAINT ck_platform_operations_timestamps CHECK ((status='CREATED' AND attempt_count=0 AND reconciliation_count=0 AND claimed_at IS NULL AND completed_at IS NULL AND next_attempt_at IS NULL) OR (status IN ('SUBMITTING','RECONCILING') AND claimed_at IS NOT NULL AND completed_at IS NULL AND next_attempt_at IS NULL) OR (status='FAILED_RETRYABLE' AND next_attempt_at IS NOT NULL AND completed_at IS NULL) OR (status='UNKNOWN_OUTCOME' AND next_attempt_at IS NULL AND completed_at IS NULL) OR (status IN ('SUCCEEDED','FAILED_TERMINAL') AND completed_at IS NOT NULL AND next_attempt_at IS NULL))
);
CREATE INDEX idx_platform_operations_account_status ON platform_operations(platform_account_uuid,status,next_attempt_at,created_at);
CREATE INDEX idx_platform_operations_campaign_account ON platform_operations(platform_campaign_uuid,platform_account_uuid) WHERE platform_campaign_uuid IS NOT NULL;
CREATE INDEX idx_platform_operations_ad_set_account ON platform_operations(platform_ad_set_uuid,platform_account_uuid) WHERE platform_ad_set_uuid IS NOT NULL;
CREATE INDEX idx_platform_operations_ad_account ON platform_operations(platform_ad_uuid,platform_account_uuid) WHERE platform_ad_uuid IS NOT NULL;

ALTER TABLE platform_ad_sets ADD CONSTRAINT fk_platform_ad_sets_last_budget_operation FOREIGN KEY(last_budget_operation_uuid) REFERENCES platform_operations(operation_uuid) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE platform_operation_attempts (
    operation_attempt_uuid UUID PRIMARY KEY,
    operation_uuid UUID NOT NULL REFERENCES platform_operations(operation_uuid) ON DELETE RESTRICT,
    attempt_kind VARCHAR(16) NOT NULL CHECK (attempt_kind IN ('SUBMIT','RECONCILE')),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 1 AND 3),
    status VARCHAR(24) NOT NULL DEFAULT 'STARTED' CHECK (status IN ('STARTED','SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL','UNKNOWN_OUTCOME','NOT_FOUND')),
    safe_provider_trace_id VARCHAR(128) CHECK (safe_provider_trace_id IS NULL OR safe_provider_trace_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    normalized_error_code VARCHAR(64) CHECK (normalized_error_code IS NULL OR normalized_error_code IN ('PLATFORM_RATE_LIMITED','PLATFORM_TEMPORARILY_UNAVAILABLE','PLATFORM_VALIDATION_FAILED','PLATFORM_PERMISSION_DENIED','PLATFORM_MAX_ATTEMPTS_EXCEEDED','PLATFORM_RESPONSE_AMBIGUOUS','PLATFORM_RECONCILIATION_NOT_FOUND','PLATFORM_RECONCILIATION_INCONCLUSIVE','PLATFORM_RECONCILIATION_TERMINAL')),
    evidence JSONB CHECK (evidence IS NULL OR (OCTET_LENGTH(evidence::text)<=8192 AND is_valid_platform_evidence(evidence))),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_attempt_kind_number UNIQUE(operation_uuid,attempt_kind,attempt_number),
    CONSTRAINT ck_platform_attempt_completion CHECK ((status='STARTED' AND completed_at IS NULL AND evidence IS NULL AND normalized_error_code IS NULL AND safe_provider_trace_id IS NULL AND version=0) OR (status<>'STARTED' AND completed_at IS NOT NULL AND evidence IS NOT NULL AND version=1)),
    CONSTRAINT ck_platform_attempt_kind_status CHECK (NOT (attempt_kind='SUBMIT' AND status='NOT_FOUND'))
);
CREATE INDEX idx_platform_attempts_operation_kind ON platform_operation_attempts(operation_uuid,attempt_kind,attempt_number DESC);

CREATE TABLE platform_metric_snapshots (
    metric_snapshot_uuid UUID PRIMARY KEY,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    entity_type VARCHAR(16) NOT NULL CHECK (entity_type IN ('CAMPAIGN','AD_SET','AD')),
    platform_campaign_uuid UUID,
    platform_ad_set_uuid UUID,
    platform_ad_uuid UUID,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(64) NOT NULL CHECK (timezone='Asia/Taipei'),
    attribution_click_days SMALLINT NOT NULL DEFAULT 7 CHECK (attribution_click_days=7),
    attribution_view_days SMALLINT NOT NULL DEFAULT 1 CHECK (attribution_view_days=1),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    impressions BIGINT CHECK (impressions IS NULL OR impressions>=0),
    reach BIGINT CHECK (reach IS NULL OR reach>=0),
    clicks BIGINT CHECK (clicks IS NULL OR clicks>=0),
    conversions BIGINT CHECK (conversions IS NULL OR conversions>=0),
    spend NUMERIC(19,6) CHECK (spend IS NULL OR spend>=0),
    revenue NUMERIC(19,6) CHECK (revenue IS NULL OR revenue>=0),
    revision_number INTEGER NOT NULL CHECK (revision_number>0),
    fetched_at TIMESTAMPTZ NOT NULL,
    freshness_status VARCHAR(16) NOT NULL CHECK (freshness_status IN ('FRESH','DELAYED','UNAVAILABLE')),
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_metrics_campaign FOREIGN KEY(platform_campaign_uuid,platform_account_uuid) REFERENCES platform_campaigns(platform_campaign_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_metrics_ad_set FOREIGN KEY(platform_ad_set_uuid,platform_account_uuid) REFERENCES platform_ad_sets(platform_ad_set_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_metrics_ad FOREIGN KEY(platform_ad_uuid,platform_account_uuid) REFERENCES platform_ads(platform_ad_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_metrics_entity CHECK ((entity_type='CAMPAIGN' AND platform_campaign_uuid IS NOT NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD_SET' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NOT NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NOT NULL)),
    CONSTRAINT ck_platform_metrics_window CHECK (window_end>window_start)
);
CREATE UNIQUE INDEX uq_platform_metrics_campaign_revision ON platform_metric_snapshots(platform_account_uuid,platform_campaign_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency,revision_number) WHERE platform_campaign_uuid IS NOT NULL;
CREATE UNIQUE INDEX uq_platform_metrics_ad_set_revision ON platform_metric_snapshots(platform_account_uuid,platform_ad_set_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency,revision_number) WHERE platform_ad_set_uuid IS NOT NULL;
CREATE UNIQUE INDEX uq_platform_metrics_ad_revision ON platform_metric_snapshots(platform_account_uuid,platform_ad_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency,revision_number) WHERE platform_ad_uuid IS NOT NULL;
CREATE UNIQUE INDEX uq_platform_metrics_campaign_fingerprint ON platform_metric_snapshots(platform_account_uuid,platform_campaign_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency,source_fingerprint) WHERE platform_campaign_uuid IS NOT NULL;
CREATE UNIQUE INDEX uq_platform_metrics_ad_set_fingerprint ON platform_metric_snapshots(platform_account_uuid,platform_ad_set_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency,source_fingerprint) WHERE platform_ad_set_uuid IS NOT NULL;
CREATE UNIQUE INDEX uq_platform_metrics_ad_fingerprint ON platform_metric_snapshots(platform_account_uuid,platform_ad_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency,source_fingerprint) WHERE platform_ad_uuid IS NOT NULL;
CREATE INDEX idx_platform_metrics_campaign_account ON platform_metric_snapshots(platform_campaign_uuid,platform_account_uuid) WHERE platform_campaign_uuid IS NOT NULL;
CREATE INDEX idx_platform_metrics_ad_set_account ON platform_metric_snapshots(platform_ad_set_uuid,platform_account_uuid) WHERE platform_ad_set_uuid IS NOT NULL;
CREATE INDEX idx_platform_metrics_ad_account ON platform_metric_snapshots(platform_ad_uuid,platform_account_uuid) WHERE platform_ad_uuid IS NOT NULL;

CREATE FUNCTION reject_platform_hard_delete() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '% is append/protect only',TG_TABLE_NAME USING ERRCODE='23514'; END $$;
CREATE TRIGGER trg_platform_accounts_no_delete BEFORE DELETE ON platform_accounts FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
CREATE TRIGGER trg_platform_campaigns_no_delete BEFORE DELETE ON platform_campaigns FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
CREATE TRIGGER trg_platform_ad_sets_no_delete BEFORE DELETE ON platform_ad_sets FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
CREATE TRIGGER trg_platform_ads_no_delete BEFORE DELETE ON platform_ads FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
CREATE TRIGGER trg_platform_operations_no_delete BEFORE DELETE ON platform_operations FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
CREATE TRIGGER trg_platform_attempts_no_delete BEFORE DELETE ON platform_operation_attempts FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
CREATE TRIGGER trg_platform_metrics_no_delete BEFORE DELETE ON platform_metric_snapshots FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();

CREATE FUNCTION protect_platform_entity_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.desired_state<>'PAUSED' OR NEW.observed_state IS NOT NULL OR NEW.external_id IS NOT NULL OR NEW.version<>0 THEN RAISE EXCEPTION 'platform entity must be inserted pristine and paused' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='platform_ad_sets' AND (to_jsonb(NEW)->>'last_budget_operation_uuid') IS NOT NULL THEN RAISE EXCEPTION 'initial budget provenance must be null' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_campaigns_insert BEFORE INSERT ON platform_campaigns FOR EACH ROW EXECUTE FUNCTION protect_platform_entity_insert();
CREATE TRIGGER trg_platform_ad_sets_insert BEFORE INSERT ON platform_ad_sets FOR EACH ROW EXECUTE FUNCTION protect_platform_entity_insert();
CREATE TRIGGER trg_platform_ads_insert BEFORE INSERT ON platform_ads FOR EACH ROW EXECUTE FUNCTION protect_platform_entity_insert();

CREATE FUNCTION protect_platform_account_update() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.platform_account_uuid,NEW.provider_key,NEW.environment,NEW.account_reference,NEW.external_account_fingerprint,NEW.currency,NEW.timezone,NEW.created_at) IS DISTINCT FROM (OLD.platform_account_uuid,OLD.provider_key,OLD.environment,OLD.account_reference,OLD.external_account_fingerprint,OLD.currency,OLD.timezone,OLD.created_at) THEN RAISE EXCEPTION 'platform account identity is immutable' USING ERRCODE='23514'; END IF;
 IF NOT (OLD.lifecycle_status='ACTIVE' AND NEW.lifecycle_status='ARCHIVED' AND NEW.archived_at IS NOT NULL AND NEW.version=OLD.version+1) THEN RAISE EXCEPTION 'invalid platform account transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_accounts_protect BEFORE UPDATE ON platform_accounts FOR EACH ROW EXECUTE FUNCTION protect_platform_account_update();

CREATE FUNCTION protect_platform_entity_update() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE old_desired TEXT; new_desired TEXT; allowed_shape BOOLEAN;
BEGIN
 IF TG_TABLE_NAME='platform_campaigns' THEN
   IF (NEW.platform_campaign_uuid,NEW.campaign_uuid,NEW.platform_account_uuid,NEW.objective,NEW.schedule_start,NEW.schedule_end,NEW.account_timezone,NEW.created_at) IS DISTINCT FROM (OLD.platform_campaign_uuid,OLD.campaign_uuid,OLD.platform_account_uuid,OLD.objective,OLD.schedule_start,OLD.schedule_end,OLD.account_timezone,OLD.created_at) THEN RAISE EXCEPTION 'campaign policy is immutable' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='platform_ad_sets' THEN
   IF (NEW.platform_ad_set_uuid,NEW.platform_campaign_uuid,NEW.platform_account_uuid,NEW.budget_type,NEW.currency,NEW.schedule_start,NEW.schedule_end,NEW.account_timezone,NEW.optimization_goal,NEW.targeting_profile_key,NEW.placement_profile_key,NEW.created_at) IS DISTINCT FROM (OLD.platform_ad_set_uuid,OLD.platform_campaign_uuid,OLD.platform_account_uuid,OLD.budget_type,OLD.currency,OLD.schedule_start,OLD.schedule_end,OLD.account_timezone,OLD.optimization_goal,OLD.targeting_profile_key,OLD.placement_profile_key,OLD.created_at) THEN RAISE EXCEPTION 'ad set policy is immutable' USING ERRCODE='23514'; END IF;
 ELSE
   IF (NEW.platform_ad_uuid,NEW.platform_ad_set_uuid,NEW.platform_account_uuid,NEW.product_uuid,NEW.asset_uuid,NEW.generation_output_uuid,NEW.review_decision_uuid,NEW.approved_checksum_sha256,NEW.creative_mapping_key,NEW.created_at) IS DISTINCT FROM (OLD.platform_ad_uuid,OLD.platform_ad_set_uuid,OLD.platform_account_uuid,OLD.product_uuid,OLD.asset_uuid,OLD.generation_output_uuid,OLD.review_decision_uuid,OLD.approved_checksum_sha256,OLD.creative_mapping_key,OLD.created_at) THEN RAISE EXCEPTION 'ad evidence is immutable' USING ERRCODE='23514'; END IF;
 END IF;
 IF OLD.external_id IS NOT NULL AND NEW.external_id IS DISTINCT FROM OLD.external_id THEN RAISE EXCEPTION 'external id is write once' USING ERRCODE='23514'; END IF;
 IF OLD.observed_state='DELETED' AND NEW.observed_state IS DISTINCT FROM OLD.observed_state THEN RAISE EXCEPTION 'deleted observation is terminal' USING ERRCODE='23514'; END IF;
 old_desired:=OLD.desired_state; new_desired:=NEW.desired_state;
 IF new_desired IS DISTINCT FROM old_desired AND NOT ((old_desired='DRAFT' AND new_desired='PAUSED') OR (old_desired='PAUSED' AND new_desired IN ('ACTIVE','ARCHIVED')) OR (old_desired='ACTIVE' AND new_desired='PAUSED')) THEN RAISE EXCEPTION 'invalid desired state transition' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='platform_ad_sets' THEN
   allowed_shape:=
     (OLD.external_id IS NULL AND NEW.external_id IS NOT NULL AND NEW.desired_state=OLD.desired_state AND NEW.budget_amount=OLD.budget_amount AND NEW.last_budget_operation_uuid IS NOT DISTINCT FROM OLD.last_budget_operation_uuid)
     OR (NEW.external_id IS NOT DISTINCT FROM OLD.external_id AND NEW.desired_state IS DISTINCT FROM OLD.desired_state AND NEW.budget_amount=OLD.budget_amount AND NEW.last_budget_operation_uuid IS NOT DISTINCT FROM OLD.last_budget_operation_uuid)
     OR (NEW.external_id IS NOT DISTINCT FROM OLD.external_id AND NEW.desired_state=OLD.desired_state AND NEW.budget_amount IS DISTINCT FROM OLD.budget_amount AND NEW.last_budget_operation_uuid IS DISTINCT FROM OLD.last_budget_operation_uuid);
 ELSE
   allowed_shape:=(OLD.external_id IS NULL AND NEW.external_id IS NOT NULL AND NEW.desired_state=OLD.desired_state)
     OR (NEW.external_id IS NOT DISTINCT FROM OLD.external_id AND NEW.desired_state IS DISTINCT FROM OLD.desired_state);
 END IF;
 IF NOT allowed_shape THEN RAISE EXCEPTION 'entity update must apply exactly one operation result' USING ERRCODE='23514'; END IF;
 IF NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'entity version must increment once' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_campaigns_protect BEFORE UPDATE ON platform_campaigns FOR EACH ROW EXECUTE FUNCTION protect_platform_entity_update();
CREATE TRIGGER trg_platform_ad_sets_protect BEFORE UPDATE ON platform_ad_sets FOR EACH ROW EXECUTE FUNCTION protect_platform_entity_update();
CREATE TRIGGER trg_platform_ads_protect BEFORE UPDATE ON platform_ads FOR EACH ROW EXECUTE FUNCTION protect_platform_entity_update();

CREATE FUNCTION verify_platform_ad_evidence_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE ok BOOLEAN;
BEGIN
 SELECT TRUE INTO ok FROM products p JOIN assets a ON a.asset_uuid=NEW.asset_uuid AND a.product_uuid=p.product_uuid JOIN ai_generation_outputs o ON o.generation_output_uuid=NEW.generation_output_uuid AND o.product_uuid=p.product_uuid JOIN ai_review_decisions d ON d.review_decision_uuid=NEW.review_decision_uuid AND d.generation_output_uuid=o.generation_output_uuid WHERE p.product_uuid=NEW.product_uuid AND p.lifecycle_status='ACTIVE' AND a.asset_type='IMAGE' AND a.lifecycle_status='ACTIVE' AND a.checksum_sha256 IS NOT NULL AND a.checksum_sha256 ~ '^[0-9a-f]{64}$' AND o.generation_type='IMAGE' AND o.generated_asset_uuid=a.asset_uuid AND o.review_status='APPROVED' AND o.preservation_status='PASSED' AND o.output_checksum_sha256=a.checksum_sha256 AND o.output_checksum_sha256=NEW.approved_checksum_sha256 AND d.decision='APPROVED' FOR SHARE OF p,a,o,d;
 IF ok IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'platform ad evidence snapshot is invalid' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_ad_evidence_snapshot AFTER INSERT ON platform_ads DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_ad_evidence_snapshot();

CREATE FUNCTION protect_platform_operation_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status<>'CREATED' OR NEW.attempt_count<>0 OR NEW.reconciliation_count<>0 OR NEW.max_attempts<>3 OR NEW.external_id IS NOT NULL OR NEW.normalized_error_code IS NOT NULL OR NEW.safe_provider_trace_id IS NOT NULL OR NEW.outcome_evidence IS NOT NULL OR NEW.next_attempt_at IS NOT NULL OR NEW.claimed_at IS NOT NULL OR NEW.completed_at IS NOT NULL OR NEW.version<>0 THEN RAISE EXCEPTION 'operation must be inserted unclaimed' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_operations_insert BEFORE INSERT ON platform_operations FOR EACH ROW EXECUTE FUNCTION protect_platform_operation_insert();

CREATE FUNCTION protect_platform_operation_update() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.operation_uuid,NEW.platform_account_uuid,NEW.operation_type,NEW.entity_type,NEW.platform_campaign_uuid,NEW.platform_ad_set_uuid,NEW.platform_ad_uuid,NEW.client_request_uuid,NEW.idempotency_key,NEW.request_payload,NEW.request_sha256,NEW.requested_actor_type,NEW.requested_actor_id,NEW.request_id,NEW.max_attempts,NEW.created_at) IS DISTINCT FROM (OLD.operation_uuid,OLD.platform_account_uuid,OLD.operation_type,OLD.entity_type,OLD.platform_campaign_uuid,OLD.platform_ad_set_uuid,OLD.platform_ad_uuid,OLD.client_request_uuid,OLD.idempotency_key,OLD.request_payload,OLD.request_sha256,OLD.requested_actor_type,OLD.requested_actor_id,OLD.request_id,OLD.max_attempts,OLD.created_at) THEN RAISE EXCEPTION 'operation identity is immutable' USING ERRCODE='23514'; END IF;
 IF OLD.status IN ('SUCCEEDED','FAILED_TERMINAL') THEN RAISE EXCEPTION 'terminal operation is immutable' USING ERRCODE='23514'; END IF;
 IF NOT ((OLD.status IN ('CREATED','FAILED_RETRYABLE') AND NEW.status='SUBMITTING') OR (OLD.status='SUBMITTING' AND NEW.status IN ('SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL','UNKNOWN_OUTCOME')) OR (OLD.status='UNKNOWN_OUTCOME' AND NEW.status='RECONCILING') OR (OLD.status='RECONCILING' AND NEW.status IN ('SUCCEEDED','FAILED_TERMINAL','UNKNOWN_OUTCOME'))) THEN RAISE EXCEPTION 'invalid operation transition' USING ERRCODE='23514'; END IF;
 IF NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'operation version must increment once' USING ERRCODE='23514'; END IF;
 IF NEW.status='SUBMITTING' AND (NEW.attempt_count<>OLD.attempt_count+1 OR NEW.reconciliation_count<>OLD.reconciliation_count) THEN RAISE EXCEPTION 'submit claim counter mismatch' USING ERRCODE='23514'; END IF;
 IF NEW.status='RECONCILING' AND (NEW.reconciliation_count<>OLD.reconciliation_count+1 OR NEW.attempt_count<>OLD.attempt_count) THEN RAISE EXCEPTION 'reconcile claim counter mismatch' USING ERRCODE='23514'; END IF;
 IF NEW.status NOT IN ('SUBMITTING','RECONCILING') AND (NEW.attempt_count<>OLD.attempt_count OR NEW.reconciliation_count<>OLD.reconciliation_count) THEN RAISE EXCEPTION 'counter changed outside claim' USING ERRCODE='23514'; END IF;
 IF NEW.status IN ('SUBMITTING','RECONCILING') AND (NEW.claimed_at IS NULL OR NEW.next_attempt_at IS NOT NULL OR NEW.completed_at IS NOT NULL OR NEW.normalized_error_code IS NOT NULL OR NEW.safe_provider_trace_id IS NOT NULL OR NEW.outcome_evidence IS NOT NULL) THEN RAISE EXCEPTION 'claim fields are incoherent' USING ERRCODE='23514'; END IF;
 IF NEW.status='FAILED_RETRYABLE' AND (NEW.normalized_error_code NOT IN ('PLATFORM_RATE_LIMITED','PLATFORM_TEMPORARILY_UNAVAILABLE') OR NEW.next_attempt_at IS NULL OR NEW.external_id IS NOT NULL) THEN RAISE EXCEPTION 'retryable result fields are incoherent' USING ERRCODE='23514'; END IF;
 IF NEW.status='UNKNOWN_OUTCOME' AND (NEW.normalized_error_code NOT IN ('PLATFORM_RESPONSE_AMBIGUOUS','PLATFORM_RECONCILIATION_NOT_FOUND','PLATFORM_RECONCILIATION_INCONCLUSIVE') OR NEW.next_attempt_at IS NOT NULL OR NEW.completed_at IS NOT NULL OR NEW.external_id IS NOT NULL) THEN RAISE EXCEPTION 'unknown result fields are incoherent' USING ERRCODE='23514'; END IF;
 IF NEW.status='SUCCEEDED' AND (NEW.normalized_error_code IS NOT NULL OR NEW.next_attempt_at IS NOT NULL OR NEW.outcome_evidence IS NULL OR (NEW.operation_type LIKE 'CREATE_%') IS DISTINCT FROM (NEW.external_id IS NOT NULL)) THEN RAISE EXCEPTION 'success result fields are incoherent' USING ERRCODE='23514'; END IF;
 IF NEW.status='FAILED_TERMINAL' AND (NEW.normalized_error_code IS NULL OR NEW.external_id IS NOT NULL OR NEW.outcome_evidence IS NULL) THEN RAISE EXCEPTION 'terminal result fields are incoherent' USING ERRCODE='23514'; END IF;
 IF OLD.external_id IS NOT NULL AND NEW.external_id IS DISTINCT FROM OLD.external_id THEN RAISE EXCEPTION 'operation external id is write once' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_operations_protect BEFORE UPDATE ON platform_operations FOR EACH ROW EXECUTE FUNCTION protect_platform_operation_update();

CREATE FUNCTION protect_platform_attempt_update() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status<>'STARTED' OR NEW.status='STARTED' OR (NEW.operation_attempt_uuid,NEW.operation_uuid,NEW.attempt_kind,NEW.attempt_number,NEW.started_at,NEW.created_at) IS DISTINCT FROM (OLD.operation_attempt_uuid,OLD.operation_uuid,OLD.attempt_kind,OLD.attempt_number,OLD.started_at,OLD.created_at) OR OLD.version<>0 OR NEW.version<>1 THEN RAISE EXCEPTION 'attempt may finalize exactly once' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_attempts_protect BEFORE UPDATE ON platform_operation_attempts FOR EACH ROW EXECUTE FUNCTION protect_platform_attempt_update();

CREATE FUNCTION protect_platform_attempt_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE op platform_operations%ROWTYPE;
BEGIN
 SELECT * INTO op FROM platform_operations WHERE operation_uuid=NEW.operation_uuid FOR SHARE;
 IF NEW.status<>'STARTED' OR NEW.version<>0 OR (NEW.attempt_kind='SUBMIT' AND (op.status<>'SUBMITTING' OR NEW.attempt_number<>op.attempt_count)) OR (NEW.attempt_kind='RECONCILE' AND (op.status<>'RECONCILING' OR NEW.attempt_number<>op.reconciliation_count)) THEN RAISE EXCEPTION 'attempt must match the active claim' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_attempts_insert BEFORE INSERT ON platform_operation_attempts FOR EACH ROW EXECUTE FUNCTION protect_platform_attempt_insert();

CREATE FUNCTION verify_platform_attempt_coherence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE op platform_operations%ROWTYPE; a platform_operation_attempts%ROWTYPE;
BEGIN
 SELECT * INTO op FROM platform_operations WHERE operation_uuid=COALESCE(NEW.operation_uuid,OLD.operation_uuid);
 IF op.status='SUBMITTING' THEN SELECT * INTO a FROM platform_operation_attempts WHERE operation_uuid=op.operation_uuid AND attempt_kind='SUBMIT' AND attempt_number=op.attempt_count; IF a.status IS DISTINCT FROM 'STARTED' THEN RAISE EXCEPTION 'missing submit claim attempt' USING ERRCODE='23514'; END IF;
 ELSIF op.status='RECONCILING' THEN SELECT * INTO a FROM platform_operation_attempts WHERE operation_uuid=op.operation_uuid AND attempt_kind='RECONCILE' AND attempt_number=op.reconciliation_count; IF a.status IS DISTINCT FROM 'STARTED' THEN RAISE EXCEPTION 'missing reconcile claim attempt' USING ERRCODE='23514'; END IF;
 ELSIF op.status<>'CREATED' THEN
   IF op.reconciliation_count>0 AND op.status IN ('SUCCEEDED','FAILED_TERMINAL','UNKNOWN_OUTCOME') THEN SELECT * INTO a FROM platform_operation_attempts WHERE operation_uuid=op.operation_uuid AND attempt_kind='RECONCILE' AND attempt_number=op.reconciliation_count;
   ELSE SELECT * INTO a FROM platform_operation_attempts WHERE operation_uuid=op.operation_uuid AND attempt_kind='SUBMIT' AND attempt_number=op.attempt_count; END IF;
   IF a.operation_attempt_uuid IS NULL OR (op.status='FAILED_RETRYABLE' AND a.status<>'FAILED_RETRYABLE') OR (op.status='SUCCEEDED' AND a.status<>'SUCCEEDED') OR (op.status='FAILED_TERMINAL' AND a.status<>'FAILED_TERMINAL') OR (op.status='UNKNOWN_OUTCOME' AND a.status NOT IN ('UNKNOWN_OUTCOME','NOT_FOUND')) OR a.normalized_error_code IS DISTINCT FROM op.normalized_error_code OR a.safe_provider_trace_id IS DISTINCT FROM op.safe_provider_trace_id OR a.evidence IS DISTINCT FROM op.outcome_evidence THEN RAISE EXCEPTION 'operation attempt result mismatch' USING ERRCODE='23514'; END IF;
   IF a.attempt_kind='SUBMIT' AND a.evidence->>'attemptKind'<>'SUBMIT' OR a.attempt_kind='RECONCILE' AND a.evidence->>'attemptKind'<>'RECONCILE' THEN RAISE EXCEPTION 'attempt evidence kind mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_operation_attempt_coherence AFTER INSERT OR UPDATE OF status,attempt_count,reconciliation_count ON platform_operations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_attempt_coherence();
CREATE CONSTRAINT TRIGGER trg_platform_attempt_operation_coherence AFTER INSERT OR UPDATE OF status ON platform_operation_attempts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_attempt_coherence();

CREATE FUNCTION verify_platform_budget_operation_coherence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE op platform_operations%ROWTYPE; adset platform_ad_sets%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='platform_ad_sets' THEN
   IF TG_OP='INSERT' AND NEW.last_budget_operation_uuid IS NOT NULL THEN RAISE EXCEPTION 'initial budget provenance must be null' USING ERRCODE='23514'; END IF;
   IF TG_OP='UPDATE' AND (NEW.budget_amount IS DISTINCT FROM OLD.budget_amount OR NEW.last_budget_operation_uuid IS DISTINCT FROM OLD.last_budget_operation_uuid) THEN
     IF NEW.budget_amount IS NOT DISTINCT FROM OLD.budget_amount OR NEW.last_budget_operation_uuid IS NULL OR NEW.last_budget_operation_uuid IS NOT DISTINCT FROM OLD.last_budget_operation_uuid OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'budget amount/provenance/version must change together' USING ERRCODE='23514'; END IF;
     SELECT * INTO op FROM platform_operations WHERE operation_uuid=NEW.last_budget_operation_uuid;
     IF op.status IS DISTINCT FROM 'SUCCEEDED' OR op.operation_type IS DISTINCT FROM 'UPDATE_BUDGET' OR op.platform_account_uuid IS DISTINCT FROM NEW.platform_account_uuid OR op.platform_ad_set_uuid IS DISTINCT FROM NEW.platform_ad_set_uuid OR (op.request_payload->>'previousBudgetAmount')::numeric IS DISTINCT FROM OLD.budget_amount OR (op.request_payload->>'newBudgetAmount')::numeric IS DISTINCT FROM NEW.budget_amount OR (op.request_payload->>'expectedEntityVersion')::bigint IS DISTINCT FROM OLD.version OR op.request_payload->>'budgetType' IS DISTINCT FROM NEW.budget_type OR op.request_payload->>'currency' IS DISTINCT FROM NEW.currency THEN RAISE EXCEPTION 'successful budget operation is incoherent' USING ERRCODE='23514'; END IF;
   END IF;
 ELSE
   IF NEW.operation_type='UPDATE_BUDGET' AND NEW.status='SUCCEEDED' THEN SELECT * INTO adset FROM platform_ad_sets WHERE platform_ad_set_uuid=NEW.platform_ad_set_uuid; IF adset.last_budget_operation_uuid IS DISTINCT FROM NEW.operation_uuid OR adset.budget_amount IS DISTINCT FROM (NEW.request_payload->>'newBudgetAmount')::numeric THEN RAISE EXCEPTION 'succeeded budget operation was not applied' USING ERRCODE='23514'; END IF; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_ad_set_budget_coherence AFTER INSERT OR UPDATE OF budget_amount,last_budget_operation_uuid,version ON platform_ad_sets DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_budget_operation_coherence();
CREATE CONSTRAINT TRIGGER trg_platform_operation_budget_coherence AFTER UPDATE OF status ON platform_operations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_budget_operation_coherence();

CREATE FUNCTION verify_platform_entity_operation_coherence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE op platform_operations%ROWTYPE; entity_row JSONB; entity_id UUID; observed_before TEXT; observed_after TEXT;
BEGIN
 IF TG_TABLE_NAME<>'platform_operations' THEN
   entity_id:=(to_jsonb(NEW)->>CASE TG_TABLE_NAME WHEN 'platform_campaigns' THEN 'platform_campaign_uuid' WHEN 'platform_ad_sets' THEN 'platform_ad_set_uuid' ELSE 'platform_ad_uuid' END)::uuid;
   SELECT * INTO op FROM platform_operations WHERE status='SUCCEEDED' AND completed_at=NEW.updated_at
     AND COALESCE(platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid)=entity_id;
   IF op.operation_uuid IS NULL THEN RAISE EXCEPTION 'entity result requires a matching successful operation' USING ERRCODE='23514'; END IF;
   observed_before:=OLD.observed_state; observed_after:=NEW.observed_state;
   IF op.outcome_evidence?'observedState' AND op.outcome_evidence->>'observedState' IS DISTINCT FROM observed_after THEN RAISE EXCEPTION 'entity observation differs from operation evidence' USING ERRCODE='23514'; END IF;
   IF NOT op.outcome_evidence?'observedState' AND observed_after IS DISTINCT FROM observed_before THEN RAISE EXCEPTION 'entity observation changed without operation evidence' USING ERRCODE='23514'; END IF;
   IF op.operation_type LIKE 'CREATE_%' THEN
     IF OLD.external_id IS NOT NULL OR NEW.external_id IS NULL OR op.external_id IS DISTINCT FROM NEW.external_id OR op.outcome_evidence->>'externalIdFingerprint' IS DISTINCT FROM encode(sha256(convert_to(NEW.external_id,'UTF8')),'hex') THEN RAISE EXCEPTION 'create result ID/fingerprint is incoherent' USING ERRCODE='23514'; END IF;
   ELSIF op.operation_type IN ('PAUSE','RESUME') THEN
     IF NEW.external_id IS DISTINCT FROM OLD.external_id OR op.external_id IS NOT NULL OR (op.request_payload->>'expectedEntityVersion')::bigint IS DISTINCT FROM OLD.version OR op.request_payload->>'targetDesiredState' IS DISTINCT FROM NEW.desired_state OR NEW.desired_state IS NOT DISTINCT FROM OLD.desired_state OR op.outcome_evidence?'externalIdFingerprint' THEN RAISE EXCEPTION 'state mutation result is incoherent' USING ERRCODE='23514'; END IF;
   ELSIF op.operation_type<>'UPDATE_BUDGET' THEN RAISE EXCEPTION 'unsupported entity result operation' USING ERRCODE='23514';
   END IF;
 ELSE
   IF NEW.status<>'SUCCEEDED' THEN RETURN NULL; END IF;
   IF NEW.entity_type='CAMPAIGN' THEN SELECT to_jsonb(e) INTO entity_row FROM platform_campaigns e WHERE e.platform_campaign_uuid=NEW.platform_campaign_uuid;
   ELSIF NEW.entity_type='AD_SET' THEN SELECT to_jsonb(e) INTO entity_row FROM platform_ad_sets e WHERE e.platform_ad_set_uuid=NEW.platform_ad_set_uuid;
   ELSE SELECT to_jsonb(e) INTO entity_row FROM platform_ads e WHERE e.platform_ad_uuid=NEW.platform_ad_uuid; END IF;
   IF entity_row IS NULL OR (entity_row->>'updated_at')::timestamptz IS DISTINCT FROM NEW.completed_at THEN RAISE EXCEPTION 'successful operation has no atomic entity result' USING ERRCODE='23514'; END IF;
   IF NEW.operation_type LIKE 'CREATE_%' THEN
     IF entity_row->>'external_id' IS DISTINCT FROM NEW.external_id OR NEW.outcome_evidence->>'externalIdFingerprint' IS DISTINCT FROM encode(sha256(convert_to(NEW.external_id,'UTF8')),'hex') THEN RAISE EXCEPTION 'successful create entity ID/fingerprint mismatch' USING ERRCODE='23514'; END IF;
   ELSIF NEW.operation_type IN ('PAUSE','RESUME') THEN
     IF NEW.external_id IS NOT NULL OR entity_row->>'desired_state' IS DISTINCT FROM NEW.request_payload->>'targetDesiredState' OR (entity_row->>'version')::bigint IS DISTINCT FROM (NEW.request_payload->>'expectedEntityVersion')::bigint+1 OR NEW.outcome_evidence?'externalIdFingerprint' THEN RAISE EXCEPTION 'successful state mutation was not applied' USING ERRCODE='23514'; END IF;
   END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_campaign_operation_coherence AFTER UPDATE OF external_id,desired_state,observed_state,version ON platform_campaigns DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_entity_operation_coherence();
CREATE CONSTRAINT TRIGGER trg_platform_ad_set_operation_coherence AFTER UPDATE OF external_id,desired_state,observed_state,budget_amount,version ON platform_ad_sets DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_entity_operation_coherence();
CREATE CONSTRAINT TRIGGER trg_platform_ad_operation_coherence AFTER UPDATE OF external_id,desired_state,observed_state,version ON platform_ads DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_entity_operation_coherence();
CREATE CONSTRAINT TRIGGER trg_platform_success_entity_coherence AFTER UPDATE OF status ON platform_operations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_entity_operation_coherence();

CREATE FUNCTION verify_platform_metric_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE expected_revision INTEGER; last_fetch TIMESTAMPTZ;
BEGIN
 SELECT COALESCE(MAX(revision_number),0)+1,MAX(fetched_at) INTO expected_revision,last_fetch FROM platform_metric_snapshots WHERE platform_account_uuid=NEW.platform_account_uuid AND entity_type=NEW.entity_type AND platform_campaign_uuid IS NOT DISTINCT FROM NEW.platform_campaign_uuid AND platform_ad_set_uuid IS NOT DISTINCT FROM NEW.platform_ad_set_uuid AND platform_ad_uuid IS NOT DISTINCT FROM NEW.platform_ad_uuid AND window_start=NEW.window_start AND window_end=NEW.window_end AND timezone=NEW.timezone AND attribution_click_days=NEW.attribution_click_days AND attribution_view_days=NEW.attribution_view_days AND currency=NEW.currency;
 IF NEW.revision_number<>expected_revision OR (last_fetch IS NOT NULL AND NEW.fetched_at<=last_fetch) THEN RAISE EXCEPTION 'metric revision/fetch time is not contiguous' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_metrics_revision BEFORE INSERT ON platform_metric_snapshots FOR EACH ROW EXECUTE FUNCTION verify_platform_metric_insert();
CREATE FUNCTION verify_platform_metric_account_coherence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE acct platform_accounts%ROWTYPE;
BEGIN SELECT * INTO acct FROM platform_accounts WHERE platform_account_uuid=NEW.platform_account_uuid FOR SHARE; IF acct.lifecycle_status IS DISTINCT FROM 'ACTIVE' OR acct.currency IS DISTINCT FROM NEW.currency OR acct.timezone IS DISTINCT FROM NEW.timezone THEN RAISE EXCEPTION 'metric account is incoherent' USING ERRCODE='23514'; END IF; RETURN NULL; END $$;
CREATE CONSTRAINT TRIGGER trg_platform_metrics_account_coherence AFTER INSERT ON platform_metric_snapshots DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_metric_account_coherence();
CREATE TRIGGER trg_platform_metrics_append_only BEFORE UPDATE ON platform_metric_snapshots FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
