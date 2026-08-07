# Milestone 2D-4 — E2E and Acceptance

## Gate status

- Stage：2D-4
- Branch：`codex/2d-4-e2e-acceptance`
- Base Commit：`8c729131f6c0097bec8be9eea2734a10eb3f347f`
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Pending
- Manager Review：Pending
- Manager Decision：Pending
- Human Review Required：No
- Merge：Pending
- Completion Tag：Pending
- Milestone 2E：Not started

## Scope delivered

- Add committed real-Chromium Quality workflow scenarios to the existing Compose-backed Playwright suite.
- Verify exact deterministic score progression from Product Master through Knowledge, Creative Plan, Campaign, and Asset metadata.
- Verify a blocking reason prevents `READY` even when manual adjustment clamps final score at 100.
- Verify non-zero adjustment reason validation, persisted adjustment reload, stale ETag 412 recovery, Product archive 409/read-only behavior, and restore recovery.
- Run every request through the Next.js same-origin BFF; Browser tests never address the Docker Backend hostname.
- Preserve all existing Product Center Playwright scenarios and database-backed Audit assertion.

## Explicitly not included

- No Flyway Migration, Backend Runtime, scoring rule, API contract, UI behavior, Docker, CI workflow, dependency, security model, or data-model change.
- No AI scoring, blocker override, authentication, RBAC, Google Connector, Dashboard, Decision Engine, or Stage 03+ behavior.
- No Milestone 2E implementation.

## Quality Playwright scenarios

1. A complete Product Master begins at 35, complete Knowledge raises the score to 60, a complete Creative Plan to 85, and a ready Campaign association to 90.
2. With no active Image, `IMAGE_ASSET_MISSING` keeps readiness at `NEEDS_REVIEW`; a `+20` adjustment clamps final score to 100 but does not remove the blocker.
3. Reload preserves adjustment value and reason; creating complete Image metadata raises the system score to 100, removes the blocker, and permits `READY`.
4. Two Browser pages using the same Quality ETag produce one successful adjustment and one 412; reload shows the persisted winner.
5. Archiving the Product makes adjustment return 409 and renders `PRODUCT_ARCHIVED` read-only state; restore removes the blocker and returns the form.

## Local verification

- Frontend lint：Passed.
- Frontend typecheck：Passed.
- Frontend Vitest：Passed — 19 files, 119 tests.
- Quality Playwright：Passed — 3 tests against the real Compose stack.
- Complete Playwright regression：Passed — 7 tests, including the existing database-backed Audit assertion.
- Docker Compose config/build/start：Passed; PostgreSQL, Backend, and Frontend healthy.
- Frontend production build：Passed.
- Backend/Testcontainers regression：Passed — 175 tests, 0 failures, 0 errors, 0 skipped; Flyway and Hibernate validation included.
- Runtime health smoke：Passed — Backend Actuator and Frontend same-origin health both returned `UP`.
- npm production audit：Passed — 0 vulnerabilities.
- Gitleaks 8.28.0 history/worktree scans：Passed — 55 commits scanned; no leaks found.
- `git diff --check`：Passed.
- Local actionlint：Not verified — no Workflow changed; pinned actionlint remains a required Remote CI step.

## Acceptance checklist

- [x] Score progression and exact component totals are exercised against real services.
- [x] Blocking reason invariance and final-score clamp are exercised through UI and API boundaries.
- [x] Adjustment reason, persisted reload, stale ETag, archive, and restore behaviors are covered.
- [x] Existing Product Center E2E and Audit coverage remains green.
- [x] Applicable Backend, Frontend, build, Testcontainers, Compose, smoke, Playwright, audit, npm audit, Gitleaks, and diff checks pass locally.
- [ ] Remote actionlint, Backend, Frontend, Compose, smoke, Playwright, npm audit, and Gitleaks pass at the committed Head.
- [ ] Exact-head Manager Review records one of the three allowed decisions.
- [ ] Merge and post-merge `main` CI pass.
- [ ] `milestone-2d-complete` tag is created only after verified completion.
- [ ] Milestone 2E remains unstarted until all 2D Gates pass.

## Known limitations

- GitHub Actions Node.js compatibility annotations remain non-blocking technical debt.
- Byte Buddy dynamic-agent future-deprecation warning remains non-blocking.
- Windows LF/CRLF notices remain informational when `git diff --check` passes.
