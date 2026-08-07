# Milestone 2D-1 — Schema and Scoring Domain

## Gate status

- Stage：2D-1 — Schema and Scoring Domain
- Branch：`codex/2d-1-schema-scoring`
- Base Commit：`6ba92bdc48a50f61448ee347b89939f961bdb5e4`
- Implementation：Complete
- Local Verification：Passed
- Commit／Push／Pull Request：Pending
- Remote CI：Pending
- Manager Review：Pending
- Manager Decision：Pending
- Human Review Required：No
- Merge：Pending
- Milestone 2D-2：Not started

## Scope delivered

- Additive `V5__create_quality_and_workflow.sql` migration.
- `quality_scores`, `quality_score_blockers`, and `workflow_status` projection tables.
- Database range, sum, clamp, enum, adjustment metadata, uniqueness, foreign-key, and immutable-identity constraints.
- Deterministic five-component rule engine, blocker projection, and readiness thresholds.
- JPA entities and restricted repositories for score, blocker, and workflow projections.
- Empty database V1→V5, populated V4→V5, repeat migration, canonical V1–V4 checksum, and Hibernate validation coverage.
- Direct JDBC constraint and immutable-trigger coverage.

## Explicitly not included

- Recalculation hooks, startup repair, public Quality API, Aggregate extension, or Audit events.
- Manual-adjustment HTTP mutation or ETag handling.
- BFF, Quality UI, or new browser scenarios.
- Google Connectors, AI scoring, Dashboard, Decision Engine, or Stage 03+ work.

## Local verification

- Backend Maven test suite：Passed — 166 tests, 0 failures, 0 errors, 0 skipped.
- Flyway cold migration and populated V4 upgrade：Passed.
- Flyway repeat migration and Hibernate `ddl-auto=validate`：Passed.
- Direct JDBC constraints and immutable identity triggers：Passed.
- Docker Compose config：Passed.
- Docker Compose cold build/start：Passed — PostgreSQL, Backend, and Frontend healthy.
- Backend Actuator and Frontend same-origin health chain：Passed.
- Frontend pinned Docker build：Passed — lint, typecheck, tests, and production build.
- Playwright regression：Passed — 4 tests.
- Production dependency audit：Passed — 0 vulnerabilities.
- Gitleaks history and worktree scans：Passed — no leaks found.
- actionlint 1.7.7：Passed.
- `git diff --check`：Passed.

## Migration and data impact

- V5 is additive; V1–V4 are unchanged and remain canonical.
- One zero-valued score projection and one `DRAFT` workflow projection are backfilled for each existing Product.
- Deterministic blocker rows are backfilled from existing Product Center data.
- Existing Product, Knowledge, Creative Plan, Campaign, Campaign Product, and Asset rows are not changed.
- Projection recalculation and startup repair remain 2D-2 scope.

## Known limitations

- Remote CI and exact-head Manager Review remain pending until commit and push.
- The first local Playwright invocation intentionally failed closed because `PLAYWRIGHT_AUDIT_DB_ASSERTION` was absent; the full suite passed after running with the same guarded environment as CI.
- Byte Buddy dynamic Java-agent future-deprecation warning remains non-blocking.
- No 2D public API or UI exists in this slice by design.
