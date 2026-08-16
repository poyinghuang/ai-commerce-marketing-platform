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

- Base: `a90f6e0bb20d23da10edb66712d85261dafe14e8`
- Branch: `codex/stage-04a-platform-foundation`
- Stable-Audit fix implementation Head: `0fbc92f0853f42d8d0a4661495c4cd77414758f1`
- Implementation: Complete; all Platform Operation Audit contexts are explicitly rebound to the stable platform operation UUID while preserving trusted actor, request ID, and source
- Local verification: PASS — Backend 313 tests; V1–V11 checksum, Flyway V12, Hibernate validation; Frontend lint, typecheck, 134 tests, production build, and production audit; isolated Compose config/cold health/smoke; Playwright 14 tests; actionlint 1.7.7; Gitleaks 8.28.0; `git diff --check`
- Remote Push CI: PASS — Run `31950682960` for implementation Head `0fbc92f0853f42d8d0a4661495c4cd77414758f1`
- Remote PR CI: PASS — Run `31950684584` for implementation Head `0fbc92f0853f42d8d0a4661495c4cd77414758f1`
- Manager review: Re-review required for the follow-up exact Head
- Manager decision: `REQUEST_CHANGES` remains in force until independent re-review
- Human review required: No new escalation boundary was crossed
- Merge: Blocked pending independent Manager `APPROVE`
- Stage 4B: Locked pending Stage 4A merge and post-merge `main` CI

The Remote CI failure-artifact upload step was conditionally skipped because Playwright passed. GitHub emitted a non-blocking Node 20 action-runtime deprecation annotation; no workflow or runtime dependency was changed in Stage 4A.
