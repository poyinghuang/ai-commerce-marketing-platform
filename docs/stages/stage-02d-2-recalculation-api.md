# Milestone 2D-2 — Recalculation, API, Audit, and Aggregate

## Gate status

- Stage：2D-2
- Branch：`codex/2d-2-recalculation-api`
- Base Commit：`3b047d99eb99fddfd03ea0861ab32bff146a8267`
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Pending
- Manager Review：Pending
- Manager Decision：Pending
- Human Review Required：No
- Merge：Pending
- Milestone 2D-3：Not started

## Scope delivered

- Transactional deterministic recalculation after effective Product, Knowledge, Creative Plan, Campaign, Campaign Product, and Asset mutations.
- Product-first then Quality-row pessimistic lock order and version-derived Quality ETag.
- Fail-closed local／test startup repair using explicit `SYSTEM` actor, generated request ID, and one operation context per Product.
- `GET /api/products/{productUuid}/quality`.
- `PATCH /api/products/{productUuid}/quality/manual-adjustment` with Merge Patch, `If-Match`, `-20..20`, trusted actor, reason, reset, idempotency, and archived Product protection.
- Transactional `QUALITY_SCORE` and `WORKFLOW_STATUS` Audit events that include only effective projection changes.
- Additive `quality` member in the Product Aggregate response.

## Explicitly not included

- No Flyway migration; V1–V5 are unchanged.
- No BFF route, Frontend Quality tab, or Quality form.
- No new Playwright Quality scenarios; these remain 2D-3／2D-4 scope.
- No AI scoring, blocker override, RBAC, external service, Google Connector, Dashboard, Decision Engine, or Stage 03+ behavior.

## Transaction and concurrency contract

- Source mutation, source Audit, projection recalculation, projection Audit, blocker replacement, and workflow update share one transaction.
- Product row is read-locked before the per-Product Quality row is write-locked, including manual adjustment, preventing adjustment from racing an archive operation.
- Blocker or workflow changes advance the Quality resource version even when numeric components are unchanged.
- Semantic no-op source changes and empty／identical adjustment patches do not change Quality version, timestamps, or Audit.
- Failure and outer rollback remove source, projection, blocker, workflow, and Audit changes together.
- Startup repair requires the server-side `app.quality.startup-repair-enabled=true` property and is unavailable whenever the `production` profile is active.

## Local verification

- Backend full suite：Passed — 175 tests, 0 failures, 0 errors, 0 skipped.
- Dedicated 2D-2 tests：Passed — 9 tests cover all source domains progressing to 100／READY; ETag, 428, 400, 412, 409, trusted actor, reset, no-op, SYSTEM repair, production fail-closed configuration, and rollback.
- Flyway V1→V5 and Hibernate validation regression：Passed; no migration changed.
- Docker Compose config and cold build/start：Passed; PostgreSQL, Backend, and Frontend healthy.
- Runtime API smoke：Passed — Product initial Quality `35` at `W/"0"`; adjustment produced `45` at `W/"1"`; Aggregate returned Quality `45`.
- Backend Actuator and Frontend same-origin health chain：Passed.
- Frontend pinned Docker lint, typecheck, tests, and production build：Passed.
- Existing Playwright regression：Passed — 4 tests.
- `git diff --check`：Passed.

## Known limitations

- 2D-3 must add fixed same-origin BFF routes and Quality UI before Browser users can use the new API.
- 2D-4 must add Quality-specific real-browser progression and stale-adjustment scenarios.
- Startup repair is deterministic and idempotent but runs serially. Production repair is intentionally fail closed and requires a separately approved mechanism.
- Byte Buddy dynamic Java-agent future-deprecation warning remains non-blocking.
