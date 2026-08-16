CREATE TABLE platform_accounts (
    platform_account_uuid UUID PRIMARY KEY,
    provider_key VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    account_reference VARCHAR(128) NOT NULL,
    external_account_fingerprint CHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_accounts_reference UNIQUE (provider_key, environment, account_reference),
    CONSTRAINT uq_platform_accounts_fingerprint UNIQUE (provider_key, environment, external_account_fingerprint),
    CONSTRAINT uq_platform_accounts_uuid_provider UNIQUE (platform_account_uuid, provider_key),
    CONSTRAINT ck_platform_accounts_provider CHECK (provider_key IN ('FAKE', 'META')),
    CONSTRAINT ck_platform_accounts_environment CHECK (environment IN ('LOCAL', 'TEST', 'PRODUCTION')),
    CONSTRAINT ck_platform_accounts_reference CHECK (BTRIM(account_reference) <> ''),
    CONSTRAINT ck_platform_accounts_fingerprint CHECK (external_account_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_platform_accounts_timezone CHECK (BTRIM(timezone) <> ''),
    CONSTRAINT ck_platform_accounts_lifecycle CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_platform_accounts_archive CHECK ((lifecycle_status='ACTIVE' AND archived_at IS NULL) OR (lifecycle_status='ARCHIVED' AND archived_at IS NOT NULL)),
    CONSTRAINT ck_platform_accounts_version CHECK (version >= 0)
);

CREATE TABLE platform_campaigns (
    platform_campaign_uuid UUID PRIMARY KEY,
    campaign_uuid UUID NOT NULL REFERENCES campaign_plans(campaign_uuid) ON DELETE RESTRICT,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    objective VARCHAR(32) NOT NULL,
    desired_state VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
    observed_state VARCHAR(64),
    schedule_start TIMESTAMP WITH TIME ZONE,
    schedule_end TIMESTAMP WITH TIME ZONE,
    account_timezone VARCHAR(64) NOT NULL,
    external_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_campaigns_uuid_account UNIQUE (platform_campaign_uuid, platform_account_uuid),
    CONSTRAINT uq_platform_campaigns_plan_account UNIQUE (campaign_uuid, platform_account_uuid),
    CONSTRAINT ck_platform_campaigns_objective CHECK (objective='OUTCOME_SALES'),
    CONSTRAINT ck_platform_campaigns_desired CHECK (desired_state IN ('DRAFT','PAUSED','ACTIVE','ARCHIVED')),
    CONSTRAINT ck_platform_campaigns_schedule CHECK (schedule_start IS NULL OR schedule_end IS NULL OR schedule_end > schedule_start),
    CONSTRAINT ck_platform_campaigns_timezone CHECK (BTRIM(account_timezone) <> ''),
    CONSTRAINT ck_platform_campaigns_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_platform_campaigns_external ON platform_campaigns(platform_account_uuid, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_platform_campaigns_plan ON platform_campaigns(campaign_uuid);
CREATE INDEX idx_platform_campaigns_account ON platform_campaigns(platform_account_uuid);

CREATE TABLE platform_ad_sets (
    platform_ad_set_uuid UUID PRIMARY KEY,
    platform_campaign_uuid UUID NOT NULL,
    platform_account_uuid UUID NOT NULL,
    budget_type VARCHAR(16) NOT NULL,
    budget_amount NUMERIC(19,6) NOT NULL,
    currency CHAR(3) NOT NULL,
    schedule_start TIMESTAMP WITH TIME ZONE,
    schedule_end TIMESTAMP WITH TIME ZONE,
    account_timezone VARCHAR(64) NOT NULL,
    optimization_goal VARCHAR(64) NOT NULL,
    targeting_profile_key VARCHAR(128) NOT NULL,
    placement_profile_key VARCHAR(128) NOT NULL,
    desired_state VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
    observed_state VARCHAR(64),
    external_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_platform_ad_sets_campaign FOREIGN KEY (platform_campaign_uuid,platform_account_uuid) REFERENCES platform_campaigns(platform_campaign_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_platform_ad_sets_uuid_account UNIQUE (platform_ad_set_uuid,platform_account_uuid),
    CONSTRAINT ck_platform_ad_sets_budget_type CHECK (budget_type IN ('DAILY','LIFETIME')),
    CONSTRAINT ck_platform_ad_sets_budget CHECK (budget_amount > 0),
    CONSTRAINT ck_platform_ad_sets_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_platform_ad_sets_schedule CHECK (schedule_start IS NULL OR schedule_end IS NULL OR schedule_end > schedule_start),
    CONSTRAINT ck_platform_ad_sets_text CHECK (BTRIM(account_timezone)<>'' AND BTRIM(optimization_goal)<>'' AND BTRIM(targeting_profile_key)<>'' AND BTRIM(placement_profile_key)<>''),
    CONSTRAINT ck_platform_ad_sets_desired CHECK (desired_state IN ('DRAFT','PAUSED','ACTIVE','ARCHIVED')),
    CONSTRAINT ck_platform_ad_sets_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_platform_ad_sets_external ON platform_ad_sets(platform_account_uuid,external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_platform_ad_sets_campaign_account ON platform_ad_sets(platform_campaign_uuid,platform_account_uuid);

ALTER TABLE ai_review_decisions ADD CONSTRAINT uq_ai_review_decisions_uuid_output UNIQUE (review_decision_uuid,generation_output_uuid);

CREATE TABLE platform_ads (
    platform_ad_uuid UUID PRIMARY KEY,
    platform_ad_set_uuid UUID NOT NULL,
    platform_account_uuid UUID NOT NULL,
    product_uuid UUID NOT NULL,
    asset_uuid UUID NOT NULL,
    generation_output_uuid UUID NOT NULL,
    review_decision_uuid UUID NOT NULL,
    approved_checksum_sha256 CHAR(64) NOT NULL,
    creative_mapping_key VARCHAR(128) NOT NULL,
    desired_state VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
    observed_state VARCHAR(64),
    external_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_platform_ads_ad_set FOREIGN KEY(platform_ad_set_uuid,platform_account_uuid) REFERENCES platform_ad_sets(platform_ad_set_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ads_asset FOREIGN KEY(asset_uuid,product_uuid) REFERENCES assets(asset_uuid,product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ads_output FOREIGN KEY(generation_output_uuid,product_uuid) REFERENCES ai_generation_outputs(generation_output_uuid,product_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ads_review FOREIGN KEY(review_decision_uuid,generation_output_uuid) REFERENCES ai_review_decisions(review_decision_uuid,generation_output_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_platform_ads_uuid_account UNIQUE(platform_ad_uuid,platform_account_uuid),
    CONSTRAINT ck_platform_ads_checksum CHECK(approved_checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_ads_mapping CHECK(BTRIM(creative_mapping_key)<>''),
    CONSTRAINT ck_platform_ads_desired CHECK(desired_state IN ('DRAFT','PAUSED','ACTIVE','ARCHIVED')),
    CONSTRAINT ck_platform_ads_version CHECK(version>=0)
);
CREATE UNIQUE INDEX uq_platform_ads_external ON platform_ads(platform_account_uuid,external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_platform_ads_ad_set_account ON platform_ads(platform_ad_set_uuid,platform_account_uuid);
CREATE INDEX idx_platform_ads_asset_product ON platform_ads(asset_uuid,product_uuid);
CREATE INDEX idx_platform_ads_output_product ON platform_ads(generation_output_uuid,product_uuid);
CREATE INDEX idx_platform_ads_review_output ON platform_ads(review_decision_uuid,generation_output_uuid);

CREATE TABLE platform_operations (
    operation_uuid UUID PRIMARY KEY,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    operation_type VARCHAR(32) NOT NULL,
    entity_type VARCHAR(16) NOT NULL,
    platform_campaign_uuid UUID,
    platform_ad_set_uuid UUID,
    platform_ad_uuid UUID,
    client_request_uuid UUID NOT NULL,
    idempotency_key CHAR(64) NOT NULL,
    request_payload JSONB NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    requested_actor_type VARCHAR(32) NOT NULL,
    requested_actor_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    external_id VARCHAR(128),
    normalized_error_code VARCHAR(64),
    safe_provider_trace_id VARCHAR(128),
    outcome_evidence JSONB,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    claimed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_platform_operations_campaign FOREIGN KEY(platform_campaign_uuid,platform_account_uuid) REFERENCES platform_campaigns(platform_campaign_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_operations_ad_set FOREIGN KEY(platform_ad_set_uuid,platform_account_uuid) REFERENCES platform_ad_sets(platform_ad_set_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_operations_ad FOREIGN KEY(platform_ad_uuid,platform_account_uuid) REFERENCES platform_ads(platform_ad_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_platform_operations_request UNIQUE(platform_account_uuid,requested_actor_type,requested_actor_id,client_request_uuid),
    CONSTRAINT uq_platform_operations_idempotency UNIQUE(platform_account_uuid,idempotency_key),
    CONSTRAINT ck_platform_operations_type CHECK(operation_type IN ('CREATE_CAMPAIGN','CREATE_AD_SET','CREATE_AD','PAUSE','RESUME','UPDATE_BUDGET')),
    CONSTRAINT ck_platform_operations_entity CHECK((entity_type='CAMPAIGN' AND platform_campaign_uuid IS NOT NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD_SET' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NOT NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NOT NULL)),
    CONSTRAINT ck_platform_operations_hashes CHECK(idempotency_key ~ '^[0-9a-f]{64}$' AND request_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_operations_payload CHECK(JSONB_TYPEOF(request_payload)='object' AND OCTET_LENGTH(request_payload::text)<=16384),
    CONSTRAINT ck_platform_operations_actor CHECK(requested_actor_type IN ('LOCAL_ADMIN','TRUSTED_ACTOR') AND BTRIM(requested_actor_id)<>''),
    CONSTRAINT ck_platform_operations_request_id CHECK(request_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    CONSTRAINT ck_platform_operations_status CHECK(status IN ('CREATED','SUBMITTING','SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL','UNKNOWN_OUTCOME')),
    CONSTRAINT ck_platform_operations_attempts CHECK(attempt_count>=0 AND max_attempts BETWEEN 1 AND 10 AND attempt_count<=max_attempts),
    CONSTRAINT ck_platform_operations_evidence CHECK(outcome_evidence IS NULL OR (JSONB_TYPEOF(outcome_evidence)='object' AND OCTET_LENGTH(outcome_evidence::text)<=8192)),
    CONSTRAINT ck_platform_operations_version CHECK(version>=0)
);
CREATE INDEX idx_platform_operations_account_status ON platform_operations(platform_account_uuid,status,next_attempt_at);
CREATE INDEX idx_platform_operations_campaign_account ON platform_operations(platform_campaign_uuid,platform_account_uuid) WHERE platform_campaign_uuid IS NOT NULL;
CREATE INDEX idx_platform_operations_ad_set_account ON platform_operations(platform_ad_set_uuid,platform_account_uuid) WHERE platform_ad_set_uuid IS NOT NULL;
CREATE INDEX idx_platform_operations_ad_account ON platform_operations(platform_ad_uuid,platform_account_uuid) WHERE platform_ad_uuid IS NOT NULL;

CREATE TABLE platform_metric_snapshots (
    metric_snapshot_uuid UUID PRIMARY KEY,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    entity_type VARCHAR(16) NOT NULL,
    platform_campaign_uuid UUID,
    platform_ad_set_uuid UUID,
    platform_ad_uuid UUID,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    attribution_click_days SMALLINT NOT NULL DEFAULT 7,
    attribution_view_days SMALLINT NOT NULL DEFAULT 1,
    currency CHAR(3) NOT NULL,
    impressions BIGINT, reach BIGINT, clicks BIGINT, conversions BIGINT,
    spend NUMERIC(19,6), revenue NUMERIC(19,6),
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    freshness_status VARCHAR(16) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_metrics_campaign FOREIGN KEY(platform_campaign_uuid,platform_account_uuid) REFERENCES platform_campaigns(platform_campaign_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_metrics_ad_set FOREIGN KEY(platform_ad_set_uuid,platform_account_uuid) REFERENCES platform_ad_sets(platform_ad_set_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_metrics_ad FOREIGN KEY(platform_ad_uuid,platform_account_uuid) REFERENCES platform_ads(platform_ad_uuid,platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_metrics_entity CHECK((entity_type='CAMPAIGN' AND platform_campaign_uuid IS NOT NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD_SET' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NOT NULL AND platform_ad_uuid IS NULL) OR (entity_type='AD' AND platform_campaign_uuid IS NULL AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NOT NULL)),
    CONSTRAINT ck_platform_metrics_window CHECK(window_end>window_start),
    CONSTRAINT ck_platform_metrics_attribution CHECK(attribution_click_days=7 AND attribution_view_days=1),
    CONSTRAINT ck_platform_metrics_currency CHECK(currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_platform_metrics_counts CHECK((impressions IS NULL OR impressions>=0) AND (reach IS NULL OR reach>=0) AND (clicks IS NULL OR clicks>=0) AND (conversions IS NULL OR conversions>=0)),
    CONSTRAINT ck_platform_metrics_money CHECK((spend IS NULL OR spend>=0) AND (revenue IS NULL OR revenue>=0)),
    CONSTRAINT ck_platform_metrics_freshness CHECK(freshness_status IN ('FRESH','DELAYED','UNAVAILABLE')),
    CONSTRAINT ck_platform_metrics_fingerprint CHECK(source_fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX uq_platform_metrics_campaign_window ON platform_metric_snapshots(platform_account_uuid,platform_campaign_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency) WHERE platform_campaign_uuid IS NOT NULL;
CREATE UNIQUE INDEX uq_platform_metrics_ad_set_window ON platform_metric_snapshots(platform_account_uuid,platform_ad_set_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency) WHERE platform_ad_set_uuid IS NOT NULL;
CREATE UNIQUE INDEX uq_platform_metrics_ad_window ON platform_metric_snapshots(platform_account_uuid,platform_ad_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency) WHERE platform_ad_uuid IS NOT NULL;
CREATE INDEX idx_platform_metrics_campaign_account ON platform_metric_snapshots(platform_campaign_uuid,platform_account_uuid) WHERE platform_campaign_uuid IS NOT NULL;
CREATE INDEX idx_platform_metrics_ad_set_account ON platform_metric_snapshots(platform_ad_set_uuid,platform_account_uuid) WHERE platform_ad_set_uuid IS NOT NULL;
CREATE INDEX idx_platform_metrics_ad_account ON platform_metric_snapshots(platform_ad_uuid,platform_account_uuid) WHERE platform_ad_uuid IS NOT NULL;

CREATE FUNCTION protect_platform_foundation_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION '% cannot be hard deleted',TG_TABLE_NAME USING ERRCODE='23514'; END IF;
  IF TG_TABLE_NAME='platform_accounts' THEN
    IF (NEW.platform_account_uuid,NEW.provider_key,NEW.environment,NEW.account_reference,NEW.external_account_fingerprint,NEW.currency,NEW.timezone) IS DISTINCT FROM (OLD.platform_account_uuid,OLD.provider_key,OLD.environment,OLD.account_reference,OLD.external_account_fingerprint,OLD.currency,OLD.timezone) THEN RAISE EXCEPTION 'platform account identity is immutable' USING ERRCODE='23514'; END IF;
  ELSIF TG_TABLE_NAME='platform_campaigns' THEN
    IF (NEW.platform_campaign_uuid,NEW.campaign_uuid,NEW.platform_account_uuid,NEW.objective,NEW.schedule_start,NEW.schedule_end,NEW.account_timezone) IS DISTINCT FROM (OLD.platform_campaign_uuid,OLD.campaign_uuid,OLD.platform_account_uuid,OLD.objective,OLD.schedule_start,OLD.schedule_end,OLD.account_timezone) OR (OLD.external_id IS NOT NULL AND NEW.external_id IS DISTINCT FROM OLD.external_id) THEN RAISE EXCEPTION 'platform campaign identity is immutable' USING ERRCODE='23514'; END IF;
  ELSIF TG_TABLE_NAME='platform_ad_sets' THEN
    IF (NEW.platform_ad_set_uuid,NEW.platform_campaign_uuid,NEW.platform_account_uuid,NEW.budget_type,NEW.currency,NEW.schedule_start,NEW.schedule_end,NEW.account_timezone,NEW.optimization_goal,NEW.targeting_profile_key,NEW.placement_profile_key) IS DISTINCT FROM (OLD.platform_ad_set_uuid,OLD.platform_campaign_uuid,OLD.platform_account_uuid,OLD.budget_type,OLD.currency,OLD.schedule_start,OLD.schedule_end,OLD.account_timezone,OLD.optimization_goal,OLD.targeting_profile_key,OLD.placement_profile_key) OR (OLD.external_id IS NOT NULL AND NEW.external_id IS DISTINCT FROM OLD.external_id) THEN RAISE EXCEPTION 'platform ad set identity is immutable' USING ERRCODE='23514'; END IF;
  ELSIF TG_TABLE_NAME='platform_ads' THEN
    IF (NEW.platform_ad_uuid,NEW.platform_ad_set_uuid,NEW.platform_account_uuid,NEW.product_uuid,NEW.asset_uuid,NEW.generation_output_uuid,NEW.review_decision_uuid,NEW.approved_checksum_sha256,NEW.creative_mapping_key) IS DISTINCT FROM (OLD.platform_ad_uuid,OLD.platform_ad_set_uuid,OLD.platform_account_uuid,OLD.product_uuid,OLD.asset_uuid,OLD.generation_output_uuid,OLD.review_decision_uuid,OLD.approved_checksum_sha256,OLD.creative_mapping_key) OR (OLD.external_id IS NOT NULL AND NEW.external_id IS DISTINCT FROM OLD.external_id) THEN RAISE EXCEPTION 'platform ad evidence is immutable' USING ERRCODE='23514'; END IF;
  ELSIF TG_TABLE_NAME='platform_operations' THEN
    IF (NEW.operation_uuid,NEW.platform_account_uuid,NEW.operation_type,NEW.entity_type,NEW.platform_campaign_uuid,NEW.platform_ad_set_uuid,NEW.platform_ad_uuid,NEW.client_request_uuid,NEW.idempotency_key,NEW.request_payload,NEW.request_sha256,NEW.requested_actor_type,NEW.requested_actor_id,NEW.request_id,NEW.max_attempts) IS DISTINCT FROM (OLD.operation_uuid,OLD.platform_account_uuid,OLD.operation_type,OLD.entity_type,OLD.platform_campaign_uuid,OLD.platform_ad_set_uuid,OLD.platform_ad_uuid,OLD.client_request_uuid,OLD.idempotency_key,OLD.request_payload,OLD.request_sha256,OLD.requested_actor_type,OLD.requested_actor_id,OLD.request_id,OLD.max_attempts) THEN RAISE EXCEPTION 'platform operation input is immutable' USING ERRCODE='23514'; END IF;
    IF OLD.status IN ('SUCCEEDED','FAILED_TERMINAL') AND NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'terminal platform operation is immutable' USING ERRCODE='23514'; END IF;
    IF NEW.status<>OLD.status AND NOT ((OLD.status IN ('CREATED','FAILED_RETRYABLE') AND NEW.status='SUBMITTING') OR (OLD.status='SUBMITTING' AND NEW.status IN ('SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL','UNKNOWN_OUTCOME')) OR (OLD.status='UNKNOWN_OUTCOME' AND NEW.status IN ('SUCCEEDED','FAILED_TERMINAL'))) THEN RAISE EXCEPTION 'invalid platform operation transition' USING ERRCODE='23514'; END IF;
    IF NEW.status='SUBMITTING' AND (NEW.attempt_count<>OLD.attempt_count+1 OR NEW.claimed_at IS NULL) THEN RAISE EXCEPTION 'claim must increment attempt count' USING ERRCODE='23514'; END IF;
    IF NEW.status<>'SUBMITTING' AND NEW.attempt_count<>OLD.attempt_count THEN RAISE EXCEPTION 'attempt count may change only on claim' USING ERRCODE='23514'; END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER trg_platform_accounts_protect BEFORE UPDATE OR DELETE ON platform_accounts FOR EACH ROW EXECUTE FUNCTION protect_platform_foundation_mutation();
CREATE TRIGGER trg_platform_campaigns_protect BEFORE UPDATE OR DELETE ON platform_campaigns FOR EACH ROW EXECUTE FUNCTION protect_platform_foundation_mutation();
CREATE TRIGGER trg_platform_ad_sets_protect BEFORE UPDATE OR DELETE ON platform_ad_sets FOR EACH ROW EXECUTE FUNCTION protect_platform_foundation_mutation();
CREATE TRIGGER trg_platform_ads_protect BEFORE UPDATE OR DELETE ON platform_ads FOR EACH ROW EXECUTE FUNCTION protect_platform_foundation_mutation();
CREATE TRIGGER trg_platform_operations_protect BEFORE UPDATE OR DELETE ON platform_operations FOR EACH ROW EXECUTE FUNCTION protect_platform_foundation_mutation();
CREATE TRIGGER trg_platform_metrics_append_only BEFORE UPDATE OR DELETE ON platform_metric_snapshots FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
