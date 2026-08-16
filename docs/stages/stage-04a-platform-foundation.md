# Stage 4A — Platform Persistence and Operation Foundation

## Scope

Stage 4A adds the provider-neutral persistence and operation boundary approved by the Stage 04 specification. It contains no REST, UI, Meta SDK, Graph request, credential, production access, advertising spend, or external network behavior.

## Architecture decisions

- PostgreSQL remains authoritative for desired entity state, immutable operation input, idempotency identity, outcomes, reconciliation state, and normalized metric snapshots.
- The `delivery` bounded context is separate from Campaign Planning. Its application ports contain no provider SDK types.
- A platform operation is committed in `CREATED` before execution. Claim and outcome persistence use short transactions; provider work occurs between them.
- Retries reuse the same operation UUID, canonical request, and idempotency key. `UNKNOWN_OUTCOME` can only be resolved through reconciliation and cannot be submitted again.
- `SUCCEEDED` and `FAILED_TERMINAL` are terminal. Database triggers independently enforce transition and immutable-input rules.
- Normal CI and local verification use only a deterministic fake adapter. Real credentials or writes remain a mandatory human escalation.
- The metric snapshot table is included as the approved additive persistence foundation; delivery synchronization and metric reads remain Stage 4D behavior.

## Data impact

Migration V12 adds `platform_accounts`, `platform_campaigns`, `platform_ad_sets`, `platform_ads`, `platform_operations`, and `platform_metric_snapshots`. Foreign keys restrict deletion; platform resources reject hard deletion; metric snapshots are append-only. Money uses `NUMERIC(19,6)`. Migrations V1–V11 are unchanged.

## Gate status

- Base: `a90f6e0`
- Branch: `codex/stage-04a-platform-foundation`
- Implementation: Complete
- Local verification: PASS (backend 309 tests; frontend lint, typecheck, 134 tests, build, and audit; isolated Compose health; Playwright 14 tests)
- Remote CI: Not started
- Manager review: Not started
- Manager decision: Pending
- Merge: Not started
