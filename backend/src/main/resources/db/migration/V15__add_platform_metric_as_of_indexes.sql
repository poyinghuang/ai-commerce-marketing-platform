-- Stage 4D as-of indexes. V12 entity UPDATE rules required a SUCCEEDED platform_operations row,
-- but delivery sync persists observed state without creating an operation. The function replacements
-- below add that observation-only shape. V1-V14 files remain byte-identical.

CREATE INDEX idx_platform_metrics_campaign_as_of
  ON platform_metric_snapshots (
    platform_campaign_uuid, window_start, window_end, timezone,
    attribution_click_days, attribution_view_days, currency,
    fetched_at DESC, revision_number DESC)
  WHERE platform_campaign_uuid IS NOT NULL;

CREATE INDEX idx_platform_metrics_ad_set_as_of
  ON platform_metric_snapshots (
    platform_ad_set_uuid, window_start, window_end, timezone,
    attribution_click_days, attribution_view_days, currency,
    fetched_at DESC, revision_number DESC)
  WHERE platform_ad_set_uuid IS NOT NULL;

CREATE INDEX idx_platform_metrics_ad_as_of
  ON platform_metric_snapshots (
    platform_ad_uuid, window_start, window_end, timezone,
    attribution_click_days, attribution_view_days, currency,
    fetched_at DESC, revision_number DESC)
  WHERE platform_ad_uuid IS NOT NULL;

CREATE OR REPLACE FUNCTION protect_platform_entity_update() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
     OR (NEW.external_id IS NOT DISTINCT FROM OLD.external_id AND NEW.desired_state=OLD.desired_state AND NEW.budget_amount IS DISTINCT FROM OLD.budget_amount AND NEW.last_budget_operation_uuid IS DISTINCT FROM OLD.last_budget_operation_uuid)
     OR (NEW.external_id IS NOT DISTINCT FROM OLD.external_id AND NEW.desired_state=OLD.desired_state AND NEW.budget_amount=OLD.budget_amount AND NEW.last_budget_operation_uuid IS NOT DISTINCT FROM OLD.last_budget_operation_uuid AND NEW.observed_state IS DISTINCT FROM OLD.observed_state);
 ELSE
   allowed_shape:=(OLD.external_id IS NULL AND NEW.external_id IS NOT NULL AND NEW.desired_state=OLD.desired_state)
     OR (NEW.external_id IS NOT DISTINCT FROM OLD.external_id AND NEW.desired_state IS DISTINCT FROM OLD.desired_state)
     OR (NEW.external_id IS NOT DISTINCT FROM OLD.external_id AND NEW.desired_state=OLD.desired_state AND NEW.observed_state IS DISTINCT FROM OLD.observed_state);
 END IF;
 IF NOT allowed_shape THEN RAISE EXCEPTION 'entity update must apply exactly one operation result' USING ERRCODE='23514'; END IF;
 IF NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'entity version must increment once' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION verify_platform_entity_operation_coherence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE op platform_operations%ROWTYPE; entity_row JSONB; entity_id UUID; observed_before TEXT; observed_after TEXT;
BEGIN
 IF TG_TABLE_NAME<>'platform_operations' THEN
   entity_id:=(to_jsonb(NEW)->>CASE TG_TABLE_NAME WHEN 'platform_campaigns' THEN 'platform_campaign_uuid' WHEN 'platform_ad_sets' THEN 'platform_ad_set_uuid' ELSE 'platform_ad_uuid' END)::uuid;
    IF NEW.external_id IS DISTINCT FROM OLD.external_id THEN
      SELECT * INTO op FROM platform_operations WHERE status='SUCCEEDED' AND operation_type LIKE 'CREATE_%'
        AND COALESCE(platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid)=entity_id
        AND external_id=NEW.external_id;
    ELSIF NEW.desired_state IS DISTINCT FROM OLD.desired_state THEN
      SELECT * INTO op FROM platform_operations WHERE status='SUCCEEDED' AND operation_type IN ('PAUSE','RESUME')
        AND COALESCE(platform_campaign_uuid,platform_ad_set_uuid,platform_ad_uuid)=entity_id
        AND (request_payload->>'expectedEntityVersion')::bigint=OLD.version
        AND request_payload->>'targetDesiredState'=NEW.desired_state;
    ELSIF TG_TABLE_NAME='platform_ad_sets' THEN
      IF NEW.budget_amount IS DISTINCT FROM OLD.budget_amount THEN
        SELECT * INTO op FROM platform_operations WHERE operation_uuid=NEW.last_budget_operation_uuid AND status='SUCCEEDED';
      ELSIF NEW.observed_state IS DISTINCT FROM OLD.observed_state
            AND NEW.external_id IS NOT DISTINCT FROM OLD.external_id
            AND NEW.desired_state IS NOT DISTINCT FROM OLD.desired_state THEN
        RETURN NULL;
      ELSE
        RAISE EXCEPTION 'entity result has no effective correlated mutation' USING ERRCODE='23514';
      END IF;
    ELSIF NEW.observed_state IS DISTINCT FROM OLD.observed_state
          AND NEW.external_id IS NOT DISTINCT FROM OLD.external_id
          AND NEW.desired_state IS NOT DISTINCT FROM OLD.desired_state THEN
      RETURN NULL;
    ELSE
      RAISE EXCEPTION 'entity result has no effective correlated mutation' USING ERRCODE='23514';
    END IF;
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
    IF entity_row IS NULL THEN RAISE EXCEPTION 'successful operation has no atomic entity result' USING ERRCODE='23514'; END IF;
   IF NEW.operation_type LIKE 'CREATE_%' THEN
      IF entity_row->>'external_id' IS DISTINCT FROM NEW.external_id OR (entity_row->>'version')::bigint<>1 OR NEW.outcome_evidence->>'externalIdFingerprint' IS DISTINCT FROM encode(sha256(convert_to(NEW.external_id,'UTF8')),'hex') THEN RAISE EXCEPTION 'successful create entity ID/fingerprint mismatch' USING ERRCODE='23514'; END IF;
   ELSIF NEW.operation_type IN ('PAUSE','RESUME') THEN
     IF NEW.external_id IS NOT NULL OR entity_row->>'desired_state' IS DISTINCT FROM NEW.request_payload->>'targetDesiredState' OR (entity_row->>'version')::bigint IS DISTINCT FROM (NEW.request_payload->>'expectedEntityVersion')::bigint+1 OR NEW.outcome_evidence?'externalIdFingerprint' THEN RAISE EXCEPTION 'successful state mutation was not applied' USING ERRCODE='23514'; END IF;
   END IF;
 END IF;
 RETURN NULL;
END $$;
