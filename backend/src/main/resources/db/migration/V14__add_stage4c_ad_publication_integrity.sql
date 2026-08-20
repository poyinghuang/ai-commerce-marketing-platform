-- Stage 04C is additive. V1-V13 remain immutable. No new business table and no backfill.
CREATE OR REPLACE FUNCTION is_valid_platform_request(value JSONB,op TEXT,entity TEXT,entity_id UUID) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
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
     AND value->>'platformAdUuid'=lower(entity_id::text) AND value->>'platformAdSetUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND value->>'productUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' AND value->>'assetUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND value->>'generationOutputUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' AND value->>'reviewDecisionUuid'~'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
     AND value->>'approvedChecksumSha256'~'^[0-9a-f]{64}$' AND jsonb_typeof(value->'creativeMappingKey')='string' AND btrim(value->>'creativeMappingKey')<>'' AND value->>'desiredState'='PAUSED'
     AND (
       (NOT value?'expectedParentVersion'
         AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','platformAdUuid','platformAdSetUuid','productUuid','assetUuid','generationOutputUuid','reviewDecisionUuid','approvedChecksumSha256','creativeMappingKey','desiredState'])))
       OR
       (value?'expectedParentVersion'
         AND jsonb_typeof(value->'expectedParentVersion')='number'
         AND (value->>'expectedParentVersion') ~ '^(0|[1-9][0-9]*)$'
         AND (value->>'expectedParentVersion')::numeric BETWEEN 0 AND 9223372036854775807
         AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','platformAdUuid','platformAdSetUuid','expectedParentVersion','productUuid','assetUuid','generationOutputUuid','reviewDecisionUuid','approvedChecksumSha256','creativeMappingKey','desiredState'])))
     )
   WHEN 'PAUSE' THEN jsonb_typeof(value->'expectedEntityVersion')='number' AND value->>'expectedEntityVersion'~'^(0|[1-9][0-9]*)$' AND value->>'targetDesiredState'='PAUSED' AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','expectedEntityVersion','targetDesiredState']))
   WHEN 'RESUME' THEN jsonb_typeof(value->'expectedEntityVersion')='number' AND value->>'expectedEntityVersion'~'^(0|[1-9][0-9]*)$' AND value->>'targetDesiredState'='ACTIVE' AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','expectedEntityVersion','targetDesiredState']))
   WHEN 'UPDATE_BUDGET' THEN entity='AD_SET' AND value?&ARRAY['platformAdSetUuid','expectedEntityVersion','budgetType','currency','previousBudgetAmount','newBudgetAmount'] AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(value) k WHERE k<>ALL(ARRAY['schemaVersion','operationType','entityType','entityUuid','platformAdSetUuid','expectedEntityVersion','budgetType','currency','previousBudgetAmount','newBudgetAmount']))
     AND value->>'platformAdSetUuid'=lower(entity_id::text) AND jsonb_typeof(value->'expectedEntityVersion')='number' AND value->>'expectedEntityVersion'~'^(0|[1-9][0-9]*)$' AND value->>'currency'='TWD'
     AND jsonb_typeof(value->'previousBudgetAmount')='number' AND jsonb_typeof(value->'newBudgetAmount')='number' AND value->>'previousBudgetAmount'~'^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$' AND value->>'newBudgetAmount'~'^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$'
     AND (value->>'previousBudgetAmount')::numeric>0 AND (value->>'newBudgetAmount')::numeric<>(value->>'previousBudgetAmount')::numeric
     AND ((value->>'budgetType'='DAILY' AND (value->>'newBudgetAmount')::numeric<=100) OR (value->>'budgetType'='LIFETIME' AND (value->>'newBudgetAmount')::numeric<=300))
   ELSE FALSE END;
$$;

CREATE FUNCTION is_stage4c_new_create_ad(value JSONB) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
 SELECT value IS NOT NULL AND jsonb_typeof(value)='object' AND value?'expectedParentVersion'
  AND is_valid_platform_request(value,'CREATE_AD','AD',(value->>'platformAdUuid')::uuid);
$$;

CREATE FUNCTION is_approved_stage4c_account(account_uuid UUID) RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
 SELECT EXISTS (
  SELECT 1 FROM platform_accounts a
  WHERE a.platform_account_uuid=account_uuid
    AND a.provider_key='FAKE' AND a.currency='TWD' AND a.timezone='Asia/Taipei'
    AND (
      (a.platform_account_uuid='00000000-0000-4000-8000-00000000004b'::uuid
        AND a.account_reference='stage4b-local' AND a.environment='LOCAL'
        AND a.external_account_fingerprint='4f1eee978e5efed2d42ac62995484b642870cda74dea26cd2d2f63653d51cf36')
      OR
      (a.platform_account_uuid='00000000-0000-4000-8000-00000000005b'::uuid
        AND a.account_reference='stage4b-test' AND a.environment='TEST'
        AND a.external_account_fingerprint='9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2')
    )
 );
$$;

CREATE FUNCTION is_stage4c_owned_operation(p_operation_uuid UUID) RETURNS BOOLEAN LANGUAGE plpgsql STABLE AS $$
DECLARE op platform_operations%ROWTYPE;
BEGIN
 SELECT * INTO op FROM platform_operations WHERE operation_uuid=p_operation_uuid;
 IF NOT FOUND OR op.entity_type<>'AD' OR NOT is_approved_stage4c_account(op.platform_account_uuid) THEN RETURN FALSE; END IF;
 IF op.operation_type='CREATE_AD' THEN RETURN is_stage4c_new_create_ad(op.request_payload); END IF;
 IF op.operation_type IN ('PAUSE','RESUME')
    AND op.request_payload->>'entityUuid'=lower(op.platform_ad_uuid::text)
    AND (SELECT count(*) FROM platform_operations c
         WHERE c.platform_account_uuid=op.platform_account_uuid
           AND c.status='SUCCEEDED' AND c.operation_type='CREATE_AD' AND c.entity_type='AD'
           AND c.platform_ad_uuid=op.platform_ad_uuid
           AND c.request_payload->>'entityUuid'=lower(op.platform_ad_uuid::text)
           AND c.request_payload->>'platformAdUuid'=lower(op.platform_ad_uuid::text)
           AND is_stage4c_new_create_ad(c.request_payload))=1
 THEN RETURN TRUE; END IF;
 RETURN FALSE;
END $$;

CREATE FUNCTION protect_platform_create_ad_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.operation_type<>'CREATE_AD' THEN RETURN NEW; END IF;
 IF NOT is_stage4c_new_create_ad(NEW.request_payload) THEN
   RAISE EXCEPTION 'CREATE_AD inserted after V14 must use the new expectedParentVersion key set' USING ERRCODE='23514';
 END IF;
 IF NEW.request_payload->>'creativeMappingKey' IS DISTINCT FROM 'APPROVED_IMAGE_ASSET_V1' THEN
   RAISE EXCEPTION 'CREATE_AD inserted after V14 must use APPROVED_IMAGE_ASSET_V1' USING ERRCODE='23514';
 END IF;
 IF NEW.request_sha256 IS NULL OR NEW.request_sha256 !~ '^[0-9a-f]{64}$' THEN
   RAISE EXCEPTION 'CREATE_AD request_sha256 is incoherent' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_create_ad_new_shape BEFORE INSERT ON platform_operations FOR EACH ROW EXECUTE FUNCTION protect_platform_create_ad_insert();

CREATE FUNCTION verify_platform_ad_submit_claim() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
 camp_id UUID; aset_id UUID; product_id UUID; asset_id UUID; output_id UUID; review_id UUID;
 acc platform_accounts%ROWTYPE;
 camp platform_campaigns%ROWTYPE;
 aset platform_ad_sets%ROWTYPE;
 ad platform_ads%ROWTYPE;
 ok BOOLEAN;
 parent_version BIGINT;
BEGIN
 IF NOT (OLD.status IN ('CREATED','FAILED_RETRYABLE') AND NEW.status='SUBMITTING' AND NEW.entity_type='AD') THEN RETURN NEW; END IF;
 IF NEW.operation_type='CREATE_AD' AND is_stage4c_new_create_ad(NEW.request_payload) THEN
   IF NOT is_approved_stage4c_account(NEW.platform_account_uuid) THEN
     RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514';
   END IF;
   SELECT * INTO acc FROM platform_accounts WHERE platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   aset_id:=(NEW.request_payload->>'platformAdSetUuid')::uuid;
   SELECT platform_campaign_uuid INTO camp_id FROM platform_ad_sets WHERE platform_ad_set_uuid=aset_id AND platform_account_uuid=NEW.platform_account_uuid;
   IF camp_id IS NULL THEN RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514'; END IF;
   SELECT * INTO camp FROM platform_campaigns WHERE platform_campaign_uuid=camp_id AND platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   SELECT * INTO aset FROM platform_ad_sets WHERE platform_ad_set_uuid=aset_id AND platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   IF aset.platform_campaign_uuid IS DISTINCT FROM camp.platform_campaign_uuid THEN RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514'; END IF;
   SELECT * INTO ad FROM platform_ads WHERE platform_ad_uuid=NEW.platform_ad_uuid AND platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   parent_version:=(NEW.request_payload->>'expectedParentVersion')::bigint;
   IF camp.desired_state<>'PAUSED' OR aset.desired_state<>'PAUSED' OR camp.external_id IS NULL OR btrim(camp.external_id)='' OR aset.external_id IS NULL OR btrim(aset.external_id)='' OR aset.version IS DISTINCT FROM parent_version THEN
     RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514';
   END IF;
   SELECT TRUE INTO ok FROM products p
     JOIN assets a ON a.asset_uuid=ad.asset_uuid AND a.product_uuid=p.product_uuid
     JOIN ai_generation_outputs o ON o.generation_output_uuid=ad.generation_output_uuid AND o.product_uuid=p.product_uuid
     JOIN ai_review_decisions d ON d.review_decision_uuid=ad.review_decision_uuid AND d.generation_output_uuid=o.generation_output_uuid
    WHERE p.product_uuid=ad.product_uuid AND p.lifecycle_status='ACTIVE' AND a.asset_type='IMAGE' AND a.lifecycle_status='ACTIVE'
      AND a.checksum_sha256 IS NOT NULL AND a.checksum_sha256 ~ '^[0-9a-f]{64}$'
      AND o.generation_type='IMAGE' AND o.generated_asset_uuid=a.asset_uuid AND o.review_status='APPROVED' AND o.preservation_status='PASSED'
      AND o.output_checksum_sha256=a.checksum_sha256 AND o.output_checksum_sha256=ad.approved_checksum_sha256 AND d.decision='APPROVED'
      AND ad.product_uuid=(NEW.request_payload->>'productUuid')::uuid AND ad.asset_uuid=(NEW.request_payload->>'assetUuid')::uuid
      AND ad.generation_output_uuid=(NEW.request_payload->>'generationOutputUuid')::uuid AND ad.review_decision_uuid=(NEW.request_payload->>'reviewDecisionUuid')::uuid
      AND ad.approved_checksum_sha256=NEW.request_payload->>'approvedChecksumSha256'
      AND ad.creative_mapping_key='APPROVED_IMAGE_ASSET_V1' AND NEW.request_payload->>'creativeMappingKey'='APPROVED_IMAGE_ASSET_V1'
    FOR UPDATE OF p,a,o,d;
   IF ok IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514'; END IF;
 ELSIF NEW.operation_type='RESUME' AND is_stage4c_owned_operation(NEW.operation_uuid) THEN
   IF NOT is_approved_stage4c_account(NEW.platform_account_uuid) THEN
     RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514';
   END IF;
   SELECT * INTO acc FROM platform_accounts WHERE platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   SELECT platform_ad_set_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid INTO aset_id,product_id,asset_id,output_id,review_id FROM platform_ads WHERE platform_ad_uuid=NEW.platform_ad_uuid AND platform_account_uuid=NEW.platform_account_uuid;
   SELECT platform_campaign_uuid INTO camp_id FROM platform_ad_sets WHERE platform_ad_set_uuid=aset_id AND platform_account_uuid=NEW.platform_account_uuid;
   SELECT * INTO camp FROM platform_campaigns WHERE platform_campaign_uuid=camp_id AND platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   SELECT * INTO aset FROM platform_ad_sets WHERE platform_ad_set_uuid=aset_id AND platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   SELECT * INTO ad FROM platform_ads WHERE platform_ad_uuid=NEW.platform_ad_uuid AND platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   IF camp.desired_state<>'ACTIVE' OR aset.desired_state<>'ACTIVE' OR camp.external_id IS NULL OR aset.external_id IS NULL OR ad.external_id IS NULL
      OR ad.desired_state<>'PAUSED' OR ad.version IS DISTINCT FROM (NEW.request_payload->>'expectedEntityVersion')::bigint
      OR NEW.request_payload->>'targetDesiredState'<>'ACTIVE' THEN
     RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514';
   END IF;
   SELECT TRUE INTO ok FROM products p
     JOIN assets a ON a.asset_uuid=ad.asset_uuid AND a.product_uuid=p.product_uuid
     JOIN ai_generation_outputs o ON o.generation_output_uuid=ad.generation_output_uuid AND o.product_uuid=p.product_uuid
     JOIN ai_review_decisions d ON d.review_decision_uuid=ad.review_decision_uuid AND d.generation_output_uuid=o.generation_output_uuid
    WHERE p.product_uuid=ad.product_uuid AND p.lifecycle_status='ACTIVE' AND a.asset_type='IMAGE' AND a.lifecycle_status='ACTIVE'
      AND a.checksum_sha256=ad.approved_checksum_sha256 AND o.generation_type='IMAGE' AND o.generated_asset_uuid=a.asset_uuid
      AND o.review_status='APPROVED' AND o.preservation_status='PASSED' AND o.output_checksum_sha256=a.checksum_sha256 AND d.decision='APPROVED'
    FOR UPDATE OF p,a,o,d;
   IF ok IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514'; END IF;
 ELSIF NEW.operation_type='PAUSE' AND is_stage4c_owned_operation(NEW.operation_uuid) THEN
   IF NOT is_approved_stage4c_account(NEW.platform_account_uuid) THEN
     RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514';
   END IF;
   SELECT * INTO acc FROM platform_accounts WHERE platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   SELECT * INTO ad FROM platform_ads WHERE platform_ad_uuid=NEW.platform_ad_uuid AND platform_account_uuid=NEW.platform_account_uuid FOR UPDATE;
   IF ad.external_id IS NULL OR btrim(ad.external_id)='' OR ad.desired_state<>'ACTIVE'
      OR ad.version IS DISTINCT FROM (NEW.request_payload->>'expectedEntityVersion')::bigint
      OR NEW.request_payload->>'targetDesiredState'<>'PAUSED' THEN
     RAISE EXCEPTION 'ct_platform_ad_submit_claim_evidence' USING ERRCODE='23514';
   END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER ct_platform_ad_submit_claim_evidence AFTER UPDATE OF status ON platform_operations DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION verify_platform_ad_submit_claim();

CREATE FUNCTION verify_platform_ad_dispatch_evidence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
 op platform_operations%ROWTYPE;
 ad platform_ads%ROWTYPE;
 aset platform_ad_sets%ROWTYPE;
 camp platform_campaigns%ROWTYPE;
 attempt platform_operation_attempts%ROWTYPE;
 camp_id UUID; aset_id UUID;
 ok BOOLEAN;
 new_shape BOOLEAN;
 claim_kind TEXT;
BEGIN
 IF TG_TABLE_NAME='platform_operations' THEN
   IF NEW.status<>'SUCCEEDED' OR NEW.entity_type<>'AD' THEN RETURN NULL; END IF;
   IF NOT (NEW.operation_type='CREATE_AD' OR is_stage4c_owned_operation(NEW.operation_uuid)) THEN RETURN NULL; END IF;
   IF OLD.status='SUBMITTING' THEN claim_kind:='SUBMIT'; ELSIF OLD.status='RECONCILING' THEN claim_kind:='RECONCILE'; ELSE RETURN NULL; END IF;
   op:=NEW;
 ELSE
   IF TG_OP<>'UPDATE' THEN RETURN NULL; END IF;
   SELECT * INTO op FROM platform_operations
    WHERE status='SUCCEEDED' AND entity_type='AD' AND platform_ad_uuid=NEW.platform_ad_uuid
      AND platform_account_uuid=NEW.platform_account_uuid
      AND (
        (operation_type='CREATE_AD' AND OLD.external_id IS NULL AND NEW.external_id IS NOT NULL)
        OR (operation_type IN ('PAUSE','RESUME') AND OLD.desired_state IS DISTINCT FROM NEW.desired_state
            AND (request_payload->>'expectedEntityVersion')::bigint=OLD.version)
      )
    ORDER BY updated_at DESC LIMIT 1;
   IF op.operation_uuid IS NULL THEN RETURN NULL; END IF;
   IF NOT (op.operation_type='CREATE_AD' OR is_stage4c_owned_operation(op.operation_uuid)) THEN RETURN NULL; END IF;
   IF op.reconciliation_count>0 THEN claim_kind:='RECONCILE'; ELSE claim_kind:='SUBMIT'; END IF;
 END IF;
 SELECT platform_ad_set_uuid INTO aset_id FROM platform_ads WHERE platform_ad_uuid=op.platform_ad_uuid AND platform_account_uuid=op.platform_account_uuid;
 SELECT platform_campaign_uuid INTO camp_id FROM platform_ad_sets WHERE platform_ad_set_uuid=aset_id AND platform_account_uuid=op.platform_account_uuid;
 PERFORM 1 FROM platform_accounts WHERE platform_account_uuid=op.platform_account_uuid FOR UPDATE;
 SELECT * INTO camp FROM platform_campaigns WHERE platform_campaign_uuid=camp_id AND platform_account_uuid=op.platform_account_uuid FOR UPDATE;
 SELECT * INTO aset FROM platform_ad_sets WHERE platform_ad_set_uuid=aset_id AND platform_account_uuid=op.platform_account_uuid FOR UPDATE;
 SELECT * INTO ad FROM platform_ads WHERE platform_ad_uuid=op.platform_ad_uuid AND platform_account_uuid=op.platform_account_uuid FOR UPDATE;
 IF claim_kind='SUBMIT' THEN
   SELECT * INTO attempt FROM platform_operation_attempts WHERE operation_uuid=op.operation_uuid AND attempt_kind='SUBMIT' AND attempt_number=op.attempt_count FOR UPDATE;
 ELSE
   SELECT * INTO attempt FROM platform_operation_attempts WHERE operation_uuid=op.operation_uuid AND attempt_kind='RECONCILE' AND attempt_number=op.reconciliation_count FOR UPDATE;
 END IF;
 IF attempt.status IS DISTINCT FROM 'SUCCEEDED' THEN RAISE EXCEPTION 'ct_platform_ad_dispatch_result' USING ERRCODE='23514'; END IF;
 IF attempt.normalized_error_code IS DISTINCT FROM op.normalized_error_code OR attempt.safe_provider_trace_id IS DISTINCT FROM op.safe_provider_trace_id OR attempt.evidence IS DISTINCT FROM op.outcome_evidence THEN
   RAISE EXCEPTION 'ct_platform_ad_dispatch_result' USING ERRCODE='23514';
 END IF;
 new_shape:=is_stage4c_new_create_ad(op.request_payload);
 IF op.operation_type='CREATE_AD' THEN
   IF ad.desired_state<>'PAUSED' OR ad.external_id IS NULL OR op.external_id IS DISTINCT FROM ad.external_id
      OR camp.desired_state<>'PAUSED' OR aset.desired_state<>'PAUSED' OR camp.external_id IS NULL OR aset.external_id IS NULL
      OR (new_shape AND aset.version IS DISTINCT FROM (op.request_payload->>'expectedParentVersion')::bigint) THEN
     RAISE EXCEPTION 'ct_platform_ad_dispatch_result' USING ERRCODE='23514';
   END IF;
   SELECT TRUE INTO ok FROM products p
     JOIN assets a ON a.asset_uuid=ad.asset_uuid AND a.product_uuid=p.product_uuid
     JOIN ai_generation_outputs o ON o.generation_output_uuid=ad.generation_output_uuid AND o.product_uuid=p.product_uuid
     JOIN ai_review_decisions d ON d.review_decision_uuid=ad.review_decision_uuid AND d.generation_output_uuid=o.generation_output_uuid
    WHERE p.product_uuid=ad.product_uuid AND p.lifecycle_status='ACTIVE' AND a.asset_type='IMAGE' AND a.lifecycle_status='ACTIVE'
      AND o.generation_type='IMAGE' AND o.generated_asset_uuid=a.asset_uuid AND o.review_status='APPROVED' AND o.preservation_status='PASSED'
      AND o.output_checksum_sha256=a.checksum_sha256 AND o.output_checksum_sha256=ad.approved_checksum_sha256 AND d.decision='APPROVED'
    FOR UPDATE OF p,a,o,d;
   IF ok IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'ct_platform_ad_dispatch_result' USING ERRCODE='23514'; END IF;
 ELSIF op.operation_type='RESUME' THEN
   IF ad.desired_state<>'ACTIVE' OR ad.external_id IS NULL OR camp.desired_state<>'ACTIVE' OR aset.desired_state<>'ACTIVE'
      OR camp.external_id IS NULL OR aset.external_id IS NULL OR op.external_id IS NOT NULL THEN
     RAISE EXCEPTION 'ct_platform_ad_dispatch_result' USING ERRCODE='23514';
   END IF;
   SELECT TRUE INTO ok FROM products p
     JOIN assets a ON a.asset_uuid=ad.asset_uuid AND a.product_uuid=p.product_uuid
     JOIN ai_generation_outputs o ON o.generation_output_uuid=ad.generation_output_uuid AND o.product_uuid=p.product_uuid
     JOIN ai_review_decisions d ON d.review_decision_uuid=ad.review_decision_uuid AND d.generation_output_uuid=o.generation_output_uuid
    WHERE p.product_uuid=ad.product_uuid AND p.lifecycle_status='ACTIVE' AND a.checksum_sha256=ad.approved_checksum_sha256
      AND o.review_status='APPROVED' AND o.preservation_status='PASSED' AND d.decision='APPROVED'
    FOR UPDATE OF p,a,o,d;
   IF ok IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'ct_platform_ad_dispatch_result' USING ERRCODE='23514'; END IF;
 ELSIF op.operation_type='PAUSE' THEN
   IF ad.desired_state<>'PAUSED' OR ad.external_id IS NULL OR op.external_id IS NOT NULL THEN
     RAISE EXCEPTION 'ct_platform_ad_dispatch_result' USING ERRCODE='23514';
   END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ct_platform_ad_dispatch_result AFTER UPDATE OF status ON platform_operations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_ad_dispatch_evidence();
CREATE CONSTRAINT TRIGGER ct_platform_ad_dispatch_result_entity AFTER UPDATE ON platform_ads DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_platform_ad_dispatch_evidence();
