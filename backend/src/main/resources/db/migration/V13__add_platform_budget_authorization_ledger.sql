CREATE FUNCTION platform_taipei_business_date(anchor TIMESTAMPTZ)
RETURNS DATE LANGUAGE sql IMMUTABLE STRICT
RETURN (anchor AT TIME ZONE 'Asia/Taipei')::date;

CREATE TABLE platform_operation_batches (
    operation_batch_uuid UUID PRIMARY KEY,
    operation_uuid UUID NOT NULL UNIQUE,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    client_request_uuid UUID NOT NULL,
    requested_actor_type VARCHAR(32) NOT NULL,
    requested_actor_id VARCHAR(128) NOT NULL,
    expected_entity_version BIGINT CHECK (expected_entity_version >= 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'TWD'),
    business_date DATE NOT NULL,
    reserved_amount NUMERIC(19,6) NOT NULL CHECK (reserved_amount >= 0 AND reserved_amount <= 300.000000),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK (version = 0),
    CONSTRAINT uq_platform_operation_batch_request UNIQUE
      (platform_account_uuid, requested_actor_type, requested_actor_id, client_request_uuid),
    CONSTRAINT fk_platform_operation_batch_operation FOREIGN KEY (operation_uuid)
      REFERENCES platform_operations(operation_uuid) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE platform_account_budget_days (
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    business_date DATE NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency = 'TWD'),
    account_budget_day_uuid UUID NOT NULL UNIQUE,
    reserved_amount NUMERIC(19,6) NOT NULL CHECK (reserved_amount >= 0 AND reserved_amount <= 1000.000000),
    ceiling_amount NUMERIC(19,6) NOT NULL CHECK (ceiling_amount = 1000.000000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    PRIMARY KEY (platform_account_uuid, business_date, currency),
    CONSTRAINT uq_platform_account_budget_day_identity UNIQUE
      (account_budget_day_uuid, platform_account_uuid, business_date, currency)
);

CREATE TABLE platform_budget_reservations (
    budget_reservation_uuid UUID PRIMARY KEY,
    operation_batch_uuid UUID NOT NULL UNIQUE REFERENCES platform_operation_batches(operation_batch_uuid) ON DELETE RESTRICT,
    operation_uuid UUID NOT NULL UNIQUE REFERENCES platform_operations(operation_uuid) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    account_budget_day_uuid UUID NOT NULL,
    platform_ad_set_uuid UUID NOT NULL REFERENCES platform_ad_sets(platform_ad_set_uuid) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    reservation_kind VARCHAR(32) NOT NULL CHECK (reservation_kind IN ('INITIAL','INCREASE','DECREASE_NO_RELEASE')),
    previous_budget_amount NUMERIC(19,6),
    new_budget_amount NUMERIC(19,6) NOT NULL CHECK (new_budget_amount > 0),
    reserved_amount NUMERIC(19,6) NOT NULL CHECK (reserved_amount >= 0 AND reserved_amount <= 300.000000),
    currency CHAR(3) NOT NULL CHECK (currency = 'TWD'),
    business_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_platform_budget_reservation_day FOREIGN KEY
      (account_budget_day_uuid, platform_account_uuid, business_date, currency)
      REFERENCES platform_account_budget_days(account_budget_day_uuid, platform_account_uuid, business_date, currency)
      ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_platform_budget_reservation_shape CHECK (
      (reservation_kind = 'INITIAL' AND previous_budget_amount IS NULL AND reserved_amount = new_budget_amount)
      OR (reservation_kind = 'INCREASE' AND previous_budget_amount > 0 AND new_budget_amount > previous_budget_amount
          AND reserved_amount = new_budget_amount - previous_budget_amount)
      OR (reservation_kind = 'DECREASE_NO_RELEASE' AND previous_budget_amount > new_budget_amount AND reserved_amount = 0)
    )
);

-- Stage 4B audit events need a logical, immutable order.  The column remains
-- nullable for every pre-Stage-4B and Stage-4A audit row; rows owned by an
-- operation batch receive their ordinal inside the same database transaction.
ALTER TABLE audit_logs ADD COLUMN stage4b_operation_ordinal SMALLINT;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_stage4b_ordinal
  CHECK (stage4b_operation_ordinal IS NULL OR stage4b_operation_ordinal >= 0);
ALTER TABLE audit_logs ADD CONSTRAINT uq_audit_logs_stage4b_operation_ordinal
  UNIQUE (operation_uuid, stage4b_operation_ordinal) DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION platform_assign_stage4b_audit_ordinal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owns_stage4b_operation BOOLEAN; expected_ordinal SMALLINT;
BEGIN
  -- Serialize writers for one logical operation even when callers use direct SQL.
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.operation_uuid::text, 40402));
  SELECT EXISTS (SELECT 1 FROM platform_operation_batches b WHERE b.operation_uuid=NEW.operation_uuid)
    INTO owns_stage4b_operation;
  IF owns_stage4b_operation THEN
    IF NEW.stage4b_operation_ordinal IS NOT NULL THEN
      RAISE EXCEPTION 'stage4b audit ordinal is database owned' USING ERRCODE='23514';
    END IF;
    SELECT COALESCE(MAX(stage4b_operation_ordinal),-1)+1 INTO expected_ordinal
      FROM audit_logs WHERE operation_uuid=NEW.operation_uuid;
    NEW.stage4b_operation_ordinal:=expected_ordinal;
  ELSIF NEW.stage4b_operation_ordinal IS NOT NULL THEN
    RAISE EXCEPTION 'stage4b audit ordinal has no operation batch owner' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_assign_stage4b_audit_ordinal BEFORE INSERT ON audit_logs
FOR EACH ROW EXECUTE FUNCTION platform_assign_stage4b_audit_ordinal();

CREATE FUNCTION platform_validate_stage4b_audit_ownership() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE operation_id UUID; owns_stage4b_operation BOOLEAN;
BEGIN
  operation_id:=NEW.operation_uuid;
  SELECT EXISTS (SELECT 1 FROM platform_operation_batches b WHERE b.operation_uuid=operation_id)
    INTO owns_stage4b_operation;
  IF owns_stage4b_operation AND EXISTS (
      SELECT 1 FROM audit_logs a
      WHERE a.operation_uuid=operation_id AND a.stage4b_operation_ordinal IS NULL
  ) THEN
    RAISE EXCEPTION 'stage4b audit ordinal is required for operation batch owner' USING ERRCODE='23514';
  ELSIF NOT owns_stage4b_operation AND EXISTS (
      SELECT 1 FROM audit_logs a
      WHERE a.operation_uuid=operation_id AND a.stage4b_operation_ordinal IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'stage4b audit ordinal has no operation batch owner' USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_audit_ownership_from_audit AFTER INSERT ON audit_logs
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION platform_validate_stage4b_audit_ownership();
CREATE CONSTRAINT TRIGGER trg_platform_audit_ownership_from_batch AFTER INSERT ON platform_operation_batches
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION platform_validate_stage4b_audit_ownership();

CREATE INDEX idx_platform_budget_reservations_account_day
  ON platform_budget_reservations(platform_account_uuid, business_date, currency);

CREATE FUNCTION platform_anchor_budget_day() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.reserved_amount := 0;
  NEW.ceiling_amount := 1000.000000;
  NEW.created_at := statement_timestamp();
  NEW.updated_at := NEW.created_at;
  NEW.version := 0;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_anchor_budget_day BEFORE INSERT ON platform_account_budget_days
FOR EACH ROW EXECUTE FUNCTION platform_anchor_budget_day();

CREATE FUNCTION platform_anchor_batch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.created_at := statement_timestamp();
  NEW.business_date := platform_taipei_business_date(NEW.created_at);
  NEW.currency := 'TWD';
  NEW.version := 0;
  IF EXISTS (SELECT 1 FROM platform_operations WHERE operation_uuid = NEW.operation_uuid) THEN
    RAISE EXCEPTION 'operation batch must be inserted before operation' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_anchor_batch BEFORE INSERT ON platform_operation_batches
FOR EACH ROW EXECUTE FUNCTION platform_anchor_batch();

CREATE FUNCTION platform_anchor_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b platform_operation_batches%ROWTYPE;
BEGIN
  SELECT * INTO STRICT b FROM platform_operation_batches WHERE operation_batch_uuid=NEW.operation_batch_uuid;
  NEW.created_at:=b.created_at; NEW.business_date:=b.business_date; NEW.currency:=b.currency;
  IF NEW.operation_uuid<>b.operation_uuid OR NEW.platform_account_uuid<>b.platform_account_uuid THEN
    RAISE EXCEPTION 'reservation batch identity mismatch' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
EXCEPTION WHEN no_data_found THEN
  RAISE EXCEPTION 'reservation batch provenance missing' USING ERRCODE='23514';
END $$;
CREATE TRIGGER trg_platform_anchor_reservation BEFORE INSERT ON platform_budget_reservations
FOR EACH ROW EXECUTE FUNCTION platform_anchor_reservation();

CREATE FUNCTION platform_immutable_row() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION '% is immutable', TG_TABLE_NAME USING ERRCODE='23514'; END $$;
CREATE TRIGGER trg_platform_batches_immutable BEFORE UPDATE OR DELETE ON platform_operation_batches
FOR EACH ROW EXECUTE FUNCTION platform_immutable_row();
CREATE TRIGGER trg_platform_reservations_immutable BEFORE UPDATE OR DELETE ON platform_budget_reservations
FOR EACH ROW EXECUTE FUNCTION platform_immutable_row();

CREATE FUNCTION platform_protect_budget_day() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'platform_account_budget_days is immutable' USING ERRCODE='23514'; END IF;
  IF OLD.account_budget_day_uuid<>NEW.account_budget_day_uuid OR OLD.platform_account_uuid<>NEW.platform_account_uuid
     OR OLD.business_date<>NEW.business_date OR OLD.currency<>NEW.currency OR OLD.ceiling_amount<>NEW.ceiling_amount
     OR OLD.created_at<>NEW.created_at OR NEW.reserved_amount<=OLD.reserved_amount OR NEW.version<>OLD.version+1
     OR NEW.updated_at<OLD.updated_at THEN
    RAISE EXCEPTION 'invalid account budget day update' USING ERRCODE='23514';
  END IF;
  NEW.updated_at:=statement_timestamp();
  RETURN NEW;
END $$;
CREATE TRIGGER trg_platform_budget_day_protect BEFORE UPDATE OR DELETE ON platform_account_budget_days
FOR EACH ROW EXECUTE FUNCTION platform_protect_budget_day();

CREATE FUNCTION platform_validate_batch_coherence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o platform_operations%ROWTYPE; ledger NUMERIC(19,6);
BEGIN
  SELECT * INTO STRICT o FROM platform_operations WHERE operation_uuid=NEW.operation_uuid;
  IF o.operation_type NOT IN ('CREATE_CAMPAIGN','CREATE_AD_SET','PAUSE','RESUME','UPDATE_BUDGET')
     OR o.entity_type NOT IN ('CAMPAIGN','AD_SET') THEN
    RAISE EXCEPTION 'operation batch type is outside stage4b' USING ERRCODE='23514';
  END IF;
  IF o.platform_account_uuid<>NEW.platform_account_uuid OR o.client_request_uuid<>NEW.client_request_uuid
     OR o.requested_actor_type<>NEW.requested_actor_type OR o.requested_actor_id<>NEW.requested_actor_id THEN
    RAISE EXCEPTION 'operation batch identity mismatch' USING ERRCODE='23514';
  END IF;
  SELECT COALESCE(SUM(reserved_amount),0) INTO ledger FROM platform_budget_reservations
    WHERE operation_batch_uuid=NEW.operation_batch_uuid;
  IF ledger<>NEW.reserved_amount THEN RAISE EXCEPTION 'operation batch reservation sum mismatch' USING ERRCODE='23514'; END IF;
  RETURN NULL;
EXCEPTION WHEN no_data_found THEN
  RAISE EXCEPTION 'operation batch operation provenance missing' USING ERRCODE='23514';
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_batch_coherence AFTER INSERT ON platform_operation_batches
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION platform_validate_batch_coherence();

CREATE FUNCTION platform_validate_stage4b_operation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b platform_operation_batches%ROWTYPE; r platform_budget_reservations%ROWTYPE;
BEGIN
  IF NEW.operation_type NOT IN ('CREATE_CAMPAIGN','CREATE_AD_SET','PAUSE','RESUME','UPDATE_BUDGET')
     OR NEW.entity_type NOT IN ('CAMPAIGN','AD_SET') THEN
    RETURN NULL;
  END IF;
  SELECT * INTO STRICT b FROM platform_operation_batches WHERE operation_uuid=NEW.operation_uuid;
  IF b.platform_account_uuid<>NEW.platform_account_uuid OR b.client_request_uuid<>NEW.client_request_uuid
     OR b.requested_actor_type<>NEW.requested_actor_type OR b.requested_actor_id<>NEW.requested_actor_id THEN
    RAISE EXCEPTION 'operation batch identity mismatch' USING ERRCODE='23514';
  END IF;
  IF (NEW.operation_type='CREATE_CAMPAIGN' AND b.expected_entity_version IS NOT NULL)
     OR (NEW.operation_type IN ('CREATE_AD_SET','PAUSE','RESUME','UPDATE_BUDGET')
         AND b.expected_entity_version IS DISTINCT FROM
             CASE WHEN NEW.operation_type='CREATE_AD_SET' THEN b.expected_entity_version
                  ELSE (NEW.request_payload->>'expectedEntityVersion')::bigint END) THEN
    RAISE EXCEPTION 'operation batch expected version mismatch' USING ERRCODE='23514';
  END IF;
  IF NEW.operation_type='CREATE_AD_SET' AND b.expected_entity_version IS NULL THEN
    RAISE EXCEPTION 'ad set parent version provenance missing' USING ERRCODE='23514';
  END IF;
  IF NEW.operation_type IN ('CREATE_AD_SET','UPDATE_BUDGET') THEN
    SELECT * INTO STRICT r FROM platform_budget_reservations WHERE operation_uuid=NEW.operation_uuid;
    IF r.operation_batch_uuid<>b.operation_batch_uuid OR r.platform_account_uuid<>NEW.platform_account_uuid
       OR r.platform_ad_set_uuid<>NEW.platform_ad_set_uuid
       OR (NEW.operation_type='CREATE_AD_SET' AND r.reservation_kind<>'INITIAL')
       OR (NEW.operation_type='UPDATE_BUDGET' AND r.reservation_kind NOT IN ('INCREASE','DECREASE_NO_RELEASE'))
       OR NEW.request_payload->>'currency' IS DISTINCT FROM 'TWD'
       OR (NEW.operation_type='CREATE_AD_SET' AND
           (NEW.request_payload->>'budgetAmount')::numeric IS DISTINCT FROM r.new_budget_amount)
       OR (NEW.operation_type='UPDATE_BUDGET' AND
           ((NEW.request_payload->>'newBudgetAmount')::numeric IS DISTINCT FROM r.new_budget_amount
            OR (NEW.request_payload->>'previousBudgetAmount')::numeric IS DISTINCT FROM r.previous_budget_amount)) THEN
      RAISE EXCEPTION 'operation reservation payload mismatch' USING ERRCODE='23514';
    END IF;
  ELSIF EXISTS (SELECT 1 FROM platform_budget_reservations WHERE operation_uuid=NEW.operation_uuid) THEN
    RAISE EXCEPTION 'non-budget operation has reservation' USING ERRCODE='23514';
  END IF;
  RETURN NULL;
EXCEPTION WHEN no_data_found THEN
  RAISE EXCEPTION 'stage4b operation provenance missing' USING ERRCODE='23514';
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_stage4b_operation_coherence AFTER INSERT ON platform_operations
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION platform_validate_stage4b_operation();

CREATE FUNCTION platform_validate_day_coherence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE ledger NUMERIC(19,6); actual NUMERIC(19,6); positive_count BIGINT; actual_version BIGINT; actual_created TIMESTAMPTZ; actual_updated TIMESTAMPTZ; expected_updated TIMESTAMPTZ;
BEGIN
  SELECT COALESCE(SUM(reserved_amount),0) INTO ledger FROM platform_budget_reservations
    WHERE platform_account_uuid=NEW.platform_account_uuid AND business_date=NEW.business_date AND currency=NEW.currency;
  SELECT COUNT(*) FILTER (WHERE reserved_amount>0) INTO positive_count FROM platform_budget_reservations
    WHERE platform_account_uuid=NEW.platform_account_uuid AND business_date=NEW.business_date AND currency=NEW.currency;
  SELECT MAX(created_at) FILTER (WHERE reserved_amount>0) INTO expected_updated FROM platform_budget_reservations
    WHERE platform_account_uuid=NEW.platform_account_uuid AND business_date=NEW.business_date AND currency=NEW.currency;
  SELECT reserved_amount,version,created_at,updated_at INTO STRICT actual,actual_version,actual_created,actual_updated FROM platform_account_budget_days
    WHERE platform_account_uuid=NEW.platform_account_uuid AND business_date=NEW.business_date AND currency=NEW.currency;
  IF ledger<>actual OR actual_version<>positive_count
     OR (positive_count=0 AND actual_updated<>actual_created)
     OR (positive_count>0 AND (actual_updated<expected_updated OR actual_updated<=actual_created))
     OR NOT EXISTS (SELECT 1 FROM platform_budget_reservations
       WHERE platform_account_uuid=NEW.platform_account_uuid AND business_date=NEW.business_date AND currency=NEW.currency) THEN
    RAISE EXCEPTION 'account budget day ledger sum mismatch' USING ERRCODE='23514';
  END IF;
  RETURN NULL;
EXCEPTION WHEN no_data_found THEN
  RAISE EXCEPTION 'account budget day provenance missing' USING ERRCODE='23514';
END $$;
CREATE CONSTRAINT TRIGGER trg_platform_day_coherence AFTER INSERT OR UPDATE ON platform_account_budget_days
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION platform_validate_day_coherence();
CREATE CONSTRAINT TRIGGER trg_platform_reservation_day_coherence AFTER INSERT ON platform_budget_reservations
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION platform_validate_day_coherence();
