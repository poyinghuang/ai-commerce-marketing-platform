# Milestone 2D-2 — Recalculation, API, Audit, and Aggregate

## Gate status

- Stage：2D-2
- Branch：`codex/2d-2-recalculation-api`
- Base Commit：`3b047d99eb99fddfd03ea0861ab32bff146a8267`
- Implementation：Complete
- Local Verification：Passed
- Implementation Commit：`f3cdb3386584fe182ea8c3f2dabc3ffdb07ac44f`
- Remote CI：Passed — implementation Push Run `31207893627`; implementation PR Run `31207911328`; approval-record Push Run `31208548446`; approval-record PR Run `31208547888`
- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Commit：`f3cdb3386584fe182ea8c3f2dabc3ffdb07ac44f`
- Approved CI Run：Push `31208548446`; Pull Request `31208547888`
- Human Review Required：No
- Merge：Passed — PR #25, Squash Merge Commit `84d7131524e734de0354dc1468bdd1e001aa6509`
- Post-merge CI：Passed — main Run `31208990817`
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
- npm production audit：Passed — 0 vulnerabilities.
- Gitleaks history and worktree scans：Passed — no leaks found.
- actionlint 1.7.7：Passed.
- `git diff --check`：Passed.

## Manager review

- Review date：2026-08-08（Asia/Taipei）
- Repository：`poyinghuang/ai-commerce-marketing-platform`
- Pull Request：#25
- Base／Head：`3b047d99eb99fddfd03ea0861ab32bff146a8267` → `f3cdb3386584fe182ea8c3f2dabc3ffdb07ac44f`
- Scope reviewed：41 approved Runtime, test, configuration, and documentation files; no unexpected or forbidden file.
- Migration reviewed：No Migration change. V1–V5 Git blobs are identical to the verified base and no merged Migration was modified.
- Domain／Transaction／Audit boundary：Source mutation, source Audit, deterministic projection, projection Audit, blockers, and workflow share the caller transaction. Product then Quality locking and rollback coverage passed.
- API／Aggregate contract：The fixed Quality GET and manual-adjustment Merge Patch endpoints implement ETag／If-Match, allowlisted fields, range／reason validation, archived protection, and additive Aggregate `quality`; existing contracts remain backward compatible.
- Security impact：No new secret, credential, external network call, Browser proxy, authentication, RBAC, permission, or production deployment. Startup repair is disabled by default and unavailable under `production`.
- Data impact：No schema or System-of-Record source-row change. Quality and workflow projections remain reproducible; mutations are transactional and idempotent.
- Completion report compared：Matches the exact Head diff, local reports, runtime smoke, and Remote CI jobs.
- Findings：None.
- Decision：APPROVE.
- Human approval required：No.
- Merge allowed：Completed.
- Next Stage allowed：Yes — 2D-3 may start from verified `main` Commit `84d7131524e734de0354dc1468bdd1e001aa6509` after this delivery record merges.

## Commands and evidence

- `git status --short`
- `git diff --check origin/main...HEAD`
- `git log --oneline --decorate -10`
- `git diff --exit-code origin/main...HEAD -- backend/src/main/resources/db/migration .github/workflows`
- `backend\\mvnw.cmd test` — 175 tests passed on the implementation Head.
- `docker compose config --quiet`; cold Compose build/start and health smoke passed.
- pinned Playwright Chromium regression — 4 tests passed.
- pinned Node.js production audit, Gitleaks 8.28.0, and actionlint 1.7.7 passed.
- Remote Push Run `31207893627` and PR Run `31207911328`: `quality-and-compose` and `secret-scan` passed; Backend, Frontend, Compose, Browser E2E, smoke, and Gitleaks steps actually executed.
- Approval-record Push Run `31208548446` and PR Run `31208547888` passed at Head `f23e0dcc1cf8a527f50155962f2bcc3d0e7de0b5`.
- Post-merge main Run `31208990817` passed at Merge Commit `84d7131524e734de0354dc1468bdd1e001aa6509`.

## Known limitations

- 2D-3 must add fixed same-origin BFF routes and Quality UI before Browser users can use the new API.
- 2D-4 must add Quality-specific real-browser progression and stale-adjustment scenarios.
- Startup repair is deterministic and idempotent but runs serially. Production repair is intentionally fail closed and requires a separately approved mechanism.
- Byte Buddy dynamic Java-agent future-deprecation warning remains non-blocking.
