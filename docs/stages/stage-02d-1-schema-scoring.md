# Milestone 2D-1 — Schema and Scoring Domain

## Gate status

- Stage：2D-1 — Schema and Scoring Domain
- Branch：`codex/2d-1-schema-scoring`
- Base Commit：`6ba92bdc48a50f61448ee347b89939f961bdb5e4`
- Implementation：Complete
- Local Verification：Passed
- Implementation Commit：`558262f1474618e58a4d7b8cce76d838dc46822a`
- Push／Pull Request：Passed — Draft PR #23
- Remote CI：Passed — Push Run `31202242425`; PR Run `31202259584`
- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Implementation Commit：`558262f1474618e58a4d7b8cce76d838dc46822a`
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

## Manager review

- Review date：2026-08-08（Asia/Taipei）
- Repository：`poyinghuang/ai-commerce-marketing-platform`
- Pull Request：#23
- Files reviewed：23 approved 2D-1 Runtime, V5, test, and documentation files; no unexpected file.
- Migration reviewed：V5 is additive; V1–V4 Git blobs and canonical checksums are unchanged. Cold, populated V4 upgrade, repeat migration, direct JDBC constraints, immutable triggers, and Hibernate validation passed.
- Domain boundary：The slice contains deterministic scoring and projection persistence only. Recalculation transactions, Audit, API, BFF, and UI remain out of scope.
- Contract changes：No public API, Frontend, BFF, authentication, RBAC, external service, or production contract change.
- Security impact：No new secret, credential, request input, network call, or elevated permission. Gitleaks passed locally and remotely.
- Data impact：Existing System-of-Record rows are unchanged. V5 backfills reproducible projection rows only and supports forward recovery.
- Findings：None.
- Decision：APPROVE.
- Human approval required：No.
- Merge allowed：Yes, after the approval-record-only Head passes both Push and Pull Request CI.
- Next Stage allowed：Only after PR #23 merge and post-merge `main` verification.

## Commands and evidence

- `git status --short`, `git diff --check`, `git log --oneline --decorate -10`
- `backend\\mvnw.cmd test`
- `docker compose config --quiet`
- `docker compose down --volumes --remove-orphans`
- `docker compose up --build --detach --wait`
- guarded `npm run test:e2e`
- pinned Node.js 24.18.0 `npm audit --omit=dev`
- pinned Gitleaks 8.28.0 history and worktree scans
- checksum-verified actionlint 1.7.7
- Remote Push Run `31202242425` and PR Run `31202259584`: `quality-and-compose` and `secret-scan` passed with required steps executed.

## Known limitations

- The first local Playwright invocation intentionally failed closed because `PLAYWRIGHT_AUDIT_DB_ASSERTION` was absent; the full suite passed after running with the same guarded environment as CI.
- Byte Buddy dynamic Java-agent future-deprecation warning remains non-blocking.
- GitHub Actions reports the upstream Node.js 20 compatibility annotation for pinned `actions/checkout`; the Runner forced Node.js 24 and all Jobs passed.
- No 2D public API or UI exists in this slice by design.
