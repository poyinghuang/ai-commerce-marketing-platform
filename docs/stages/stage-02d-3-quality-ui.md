# Milestone 2D-3 — Quality UI

## Gate status

- Stage：2D-3
- Branch：`codex/2d-3-quality-ui`
- Base Commit：`24c6d74044ac87a41c4b195e62c4554415774494`
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Pending
- Manager Review：Pending
- Manager Decision：Pending
- Human Review Required：No
- Merge：Pending
- Milestone 2D-4：Not started

## Scope delivered

- Fixed same-origin BFF routes for Quality GET and manual-adjustment PATCH.
- Strict Backend path allowlist, empty query allowlist, 64 KiB body limit, server-only Backend origin, sanitized request ID, and no Cookie／Authorization／Browser actor forwarding.
- Product detail `?tab=quality` navigation.
- Deterministic component breakdown, system score, adjustment, final score, readiness status, calculation metadata, and complete blocker list.
- Manual adjustment form with `-20..20`, required non-zero reason, reset-to-zero, current ETag, and archived Product read-only behavior.
- Loading, unavailable, validation, 409 archived, 412 stale version, and 428 missing token recovery states.
- Responsive desktop／mobile layout with accessible labels and progress elements.

## Explicitly not included

- No Backend, Flyway Migration, scoring rule, Audit, Aggregate, or domain change.
- No new Quality-specific committed Playwright suite; formal progression／blocker／clamp／archive scenarios remain 2D-4 scope.
- No authentication, RBAC, AI scoring, blocker override, Google Connector, external call, Dashboard, Decision Engine, or Stage 03+ behavior.

## BFF and security boundary

- Browser routes are exactly `/api/products/{uuid}/quality` and `/api/products/{uuid}/quality/manual-adjustment`.
- Browser path, query, header, or body cannot select an upstream origin or arbitrary Backend path.
- Only `If-Match` and a sanitized `X-Request-ID` may be forwarded from the Browser.
- Only allowlisted response metadata is exposed; Backend error status and body are preserved.
- Timeout and network failures use the existing sanitized `BACKEND_UNAVAILABLE` response.

## Local verification

- Frontend lint：Passed.
- Frontend typecheck：Passed.
- Frontend Vitest：Passed — 19 files, 119 tests.
- Frontend production build：Passed; both Quality Route Handlers are present.
- Backend regression：Passed — 175 tests, 0 failures, 0 errors, 0 skipped.
- Flyway／Hibernate regression：Passed through the Backend suite; no Migration changed.
- Docker Compose config and cold build/start：Passed; PostgreSQL, Backend, and Frontend healthy.
- Runtime BFF smoke：Passed — Product created, initial Quality `35` at `W/"0"`, adjustment persisted with trusted `local-admin` actor.
- Real Chromium interaction：Passed — adjustment updated final score, blocker list remained visible, and no internal Backend hostname was exposed.
- Stale ETag interaction：Passed — concurrent update caused 412 UI recovery; reload showed the current score and adjustment.
- Responsive layout：Passed at 390px and 1440px — no horizontal overflow, 6 tabs, 5 component progress bars, and no console errors.
- Existing Playwright regression：Passed — 4 tests against the real Compose stack.
- npm production audit：Passed — 0 vulnerabilities.
- Gitleaks 8.28.0 history and worktree scans：Passed — 48 commits and the current uncommitted worktree scanned; no leaks found.
- `git diff --check`：Passed.
- Local actionlint：Not verified — actionlint is not installed locally and no Workflow file changed; Remote CI remains required evidence.

## Known limitations

- Formal committed Quality Playwright scenarios remain required in 2D-4.
- The in-app browser plugin could not initialize in this local session because its runtime attempted to redefine a protected JavaScript global; Repository-pinned standalone Playwright Chromium provided the equivalent localhost interaction and layout evidence.
- Next.js may cancel speculative RSC prefetch requests when the page closes; required API and navigation requests completed successfully.
- Byte Buddy dynamic Java-agent future-deprecation warning remains non-blocking in the unchanged Backend suite.
- Windows LF／CRLF conversion notices remain informational; `git diff --check` passes.

## Acceptance checklist

- [x] Fixed Quality BFF routes preserve ETag, If-Match, request ID, status, and safe response headers.
- [x] Arbitrary target, query, credentials, Browser actor, and oversized body are rejected or stripped.
- [x] Quality tab renders all five components, totals, readiness, blockers, and metadata.
- [x] Adjustment, reason, reset, archived read-only, 409, 412, and 428 behaviors are implemented and tested.
- [x] Desktop and mobile layouts are usable without horizontal overflow.
- [x] Frontend and Backend regression suites, Compose build/start, and runtime smoke pass locally.
- [ ] Push and Pull Request Remote CI pass at the committed Head.
- [ ] Exact-head Manager Review records one of the three allowed Gate decisions.
- [ ] Merge and post-merge `main` verification complete before 2D-4 starts.
