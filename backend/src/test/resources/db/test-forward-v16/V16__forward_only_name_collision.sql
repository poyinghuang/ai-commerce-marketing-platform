-- Test-only forward collision. Not shipped as a real V16 on the main-bound branch.
CREATE INDEX idx_platform_metrics_campaign_as_of
  ON platform_metric_snapshots (platform_campaign_uuid);
