CREATE TABLE decision_recommendations (
    recommendation_uuid UUID PRIMARY KEY,
    platform_account_uuid UUID NOT NULL REFERENCES platform_accounts(platform_account_uuid) ON DELETE RESTRICT,
    platform_campaign_uuid UUID NOT NULL,
    campaign_uuid UUID NOT NULL REFERENCES campaign_plans(campaign_uuid) ON DELETE RESTRICT,
    recommendation_type VARCHAR(32) NOT NULL CHECK (recommendation_type IN (
        'INCREASE_BUDGET', 'DECREASE_BUDGET', 'PAUSE', 'SWAP_CREATIVE',
        'REGENERATE_CREATIVE', 'AUDIENCE_FATIGUE', 'CREATIVE_FATIGUE')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(64) NOT NULL CHECK (timezone = 'Asia/Taipei'),
    attribution_click_days SMALLINT NOT NULL CHECK (attribution_click_days = 7),
    attribution_view_days SMALLINT NOT NULL CHECK (attribution_view_days = 1),
    currency CHAR(3) NOT NULL CHECK (currency = 'TWD'),
    desired_state VARCHAR(16) NOT NULL CHECK (desired_state IN ('DRAFT', 'PAUSED', 'ACTIVE', 'ARCHIVED')),
    reason_summary VARCHAR(500) NOT NULL,
    risk_summary VARCHAR(500) NOT NULL,
    impressions BIGINT CHECK (impressions IS NULL OR impressions >= 0),
    reach BIGINT CHECK (reach IS NULL OR reach >= 0),
    clicks BIGINT CHECK (clicks IS NULL OR clicks >= 0),
    conversions BIGINT CHECK (conversions IS NULL OR conversions >= 0),
    spend NUMERIC(19, 6) CHECK (spend IS NULL OR spend >= 0),
    revenue NUMERIC(19, 6) CHECK (revenue IS NULL OR revenue >= 0),
    ctr NUMERIC(19, 6) CHECK (ctr IS NULL OR ctr >= 0),
    cpc NUMERIC(19, 6) CHECK (cpc IS NULL OR cpc >= 0),
    cpm NUMERIC(19, 6) CHECK (cpm IS NULL OR cpm >= 0),
    cpa NUMERIC(19, 6) CHECK (cpa IS NULL OR cpa >= 0),
    cvr NUMERIC(19, 6) CHECK (cvr IS NULL OR cvr >= 0),
    roas NUMERIC(19, 6) CHECK (roas IS NULL OR roas >= 0),
    rule_set_key VARCHAR(32) NOT NULL CHECK (rule_set_key = 'RULE_SET_V1'),
    evidence_fingerprint CHAR(64) NOT NULL CHECK (evidence_fingerprint ~ '^[0-9a-f]{64}$'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_decision_recommendations_campaign
        FOREIGN KEY (platform_campaign_uuid, platform_account_uuid)
        REFERENCES platform_campaigns (platform_campaign_uuid, platform_account_uuid) ON DELETE RESTRICT,
    CONSTRAINT uq_decision_recommendations_identity UNIQUE (
        platform_account_uuid, platform_campaign_uuid, recommendation_type,
        window_start, window_end, timezone, attribution_click_days, attribution_view_days, currency),
    CONSTRAINT ck_decision_recommendations_window CHECK (window_end > window_start)
);

CREATE INDEX idx_decision_recommendations_list
    ON decision_recommendations (platform_account_uuid, status, updated_at DESC, recommendation_uuid DESC);

CREATE TABLE decision_recommendation_decisions (
    recommendation_decision_uuid UUID PRIMARY KEY,
    recommendation_uuid UUID NOT NULL UNIQUE REFERENCES decision_recommendations(recommendation_uuid) ON DELETE RESTRICT,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(2000),
    reviewer_type VARCHAR(32) NOT NULL CHECK (reviewer_type IN ('LOCAL_ADMIN', 'TRUSTED_ACTOR')),
    reviewer_id VARCHAR(128) NOT NULL CHECK (BTRIM(reviewer_id) <> ''),
    request_id VARCHAR(128) NOT NULL CHECK (request_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    reviewed_recommendation_version BIGINT NOT NULL CHECK (reviewed_recommendation_version >= 0),
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_decision_recommendation_reason CHECK (
        (decision = 'APPROVED' AND reason IS NULL)
        OR (decision = 'REJECTED' AND reason IS NOT NULL AND BTRIM(reason) <> '')
    )
);

CREATE FUNCTION reject_decision_recommendation_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Decision recommendation decisions are append-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_decision_recommendation_decisions_no_update
    BEFORE UPDATE ON decision_recommendation_decisions
    FOR EACH ROW EXECUTE FUNCTION reject_decision_recommendation_update();
CREATE TRIGGER trg_decision_recommendation_decisions_no_delete
    BEFORE DELETE ON decision_recommendation_decisions
    FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();
CREATE TRIGGER trg_decision_recommendations_no_delete
    BEFORE DELETE ON decision_recommendations
    FOR EACH ROW EXECUTE FUNCTION reject_platform_hard_delete();

CREATE FUNCTION protect_decision_recommendation_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.recommendation_uuid IS DISTINCT FROM OLD.recommendation_uuid
        OR NEW.platform_account_uuid IS DISTINCT FROM OLD.platform_account_uuid
        OR NEW.platform_campaign_uuid IS DISTINCT FROM OLD.platform_campaign_uuid
        OR NEW.campaign_uuid IS DISTINCT FROM OLD.campaign_uuid
        OR NEW.recommendation_type IS DISTINCT FROM OLD.recommendation_type
        OR NEW.window_start IS DISTINCT FROM OLD.window_start
        OR NEW.window_end IS DISTINCT FROM OLD.window_end
        OR NEW.timezone IS DISTINCT FROM OLD.timezone
        OR NEW.attribution_click_days IS DISTINCT FROM OLD.attribution_click_days
        OR NEW.attribution_view_days IS DISTINCT FROM OLD.attribution_view_days
        OR NEW.currency IS DISTINCT FROM OLD.currency
        OR NEW.rule_set_key IS DISTINCT FROM OLD.rule_set_key
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'Decision recommendation identity fields cannot change' USING ERRCODE = '23514';
    END IF;
    IF OLD.status IN ('APPROVED', 'REJECTED') THEN
        RAISE EXCEPTION 'Terminal decision recommendation cannot change' USING ERRCODE = '23514';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF NOT (OLD.status = 'PENDING' AND NEW.status IN ('APPROVED', 'REJECTED')) THEN
            RAISE EXCEPTION 'Decision recommendation status transition is invalid' USING ERRCODE = '23514';
        END IF;
        IF NEW.impressions IS DISTINCT FROM OLD.impressions
            OR NEW.reach IS DISTINCT FROM OLD.reach
            OR NEW.clicks IS DISTINCT FROM OLD.clicks
            OR NEW.conversions IS DISTINCT FROM OLD.conversions
            OR NEW.spend IS DISTINCT FROM OLD.spend
            OR NEW.revenue IS DISTINCT FROM OLD.revenue
            OR NEW.ctr IS DISTINCT FROM OLD.ctr
            OR NEW.cpc IS DISTINCT FROM OLD.cpc
            OR NEW.cpm IS DISTINCT FROM OLD.cpm
            OR NEW.cpa IS DISTINCT FROM OLD.cpa
            OR NEW.cvr IS DISTINCT FROM OLD.cvr
            OR NEW.roas IS DISTINCT FROM OLD.roas
            OR NEW.desired_state IS DISTINCT FROM OLD.desired_state
            OR NEW.reason_summary IS DISTINCT FROM OLD.reason_summary
            OR NEW.risk_summary IS DISTINCT FROM OLD.risk_summary
            OR NEW.evidence_fingerprint IS DISTINCT FROM OLD.evidence_fingerprint THEN
            RAISE EXCEPTION 'Terminal decision recommendation cannot change evidence' USING ERRCODE = '23514';
        END IF;
        IF NEW.version <> OLD.version + 1 THEN
            RAISE EXCEPTION 'Decision recommendation status transition must increment version once' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NEW.version <> OLD.version + 1 THEN
            RAISE EXCEPTION 'Pending decision recommendation evidence update must increment version once' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_decision_recommendations_protect
    BEFORE UPDATE ON decision_recommendations
    FOR EACH ROW EXECUTE FUNCTION protect_decision_recommendation_mutation();

CREATE FUNCTION verify_decision_recommendation_coherence()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    rec_id UUID := COALESCE(NEW.recommendation_uuid, OLD.recommendation_uuid);
    rec_status VARCHAR(16);
    rec_version BIGINT;
    stored_decision VARCHAR(16);
    stored_version BIGINT;
BEGIN
    SELECT status, version INTO rec_status, rec_version
      FROM decision_recommendations WHERE recommendation_uuid = rec_id;
    SELECT decision, reviewed_recommendation_version INTO stored_decision, stored_version
      FROM decision_recommendation_decisions WHERE recommendation_uuid = rec_id;

    IF rec_status = 'PENDING' THEN
        IF stored_decision IS NOT NULL THEN
            RAISE EXCEPTION 'Pending recommendation cannot have a decision' USING ERRCODE = '23514';
        END IF;
    ELSIF stored_decision IS NULL OR stored_decision <> rec_status OR rec_version <> stored_version + 1 THEN
        RAISE EXCEPTION 'Terminal recommendation and decision are incoherent' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_decision_recommendations_coherence
    AFTER INSERT OR UPDATE OF status, version ON decision_recommendations
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_decision_recommendation_coherence();

CREATE CONSTRAINT TRIGGER trg_decision_recommendation_decisions_coherence
    AFTER INSERT ON decision_recommendation_decisions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_decision_recommendation_coherence();
