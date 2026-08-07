# Milestone 2D-3 — Quality UI

## Gate status

- Stage：2D-3
- Branch：`codex/2d-3-quality-ui`
- Base Commit：`24c6d74044ac87a41c4b195e62c4554415774494`
- Implementation：Complete
- Local Verification：Passed
- Implementation Commit：`66cd92a48a10b16acb49d10bf183563d208a1d1d`
- Remote CI：Passed — Push Run `31213276000`; Pull Request Run `31213280058`
- Manager Review：Passed at implementation Head `66cd92a48a10b16acb49d10bf183563d208a1d1d`
- Manager Decision：APPROVE
- Review Findings：None
- Human Review Required：No
- Merge：Passed — implementation PR #27, `4f4aace65a737305f15bf3c9b34633dfc66366ba`
- Initial Post-merge CI：Failed — main Run `31214177537`; asynchronous Aggregate assertion raced its response.
- Corrective Commit：`aeef9757388ab38b98023ca2b5b1bb093e44192c`
- Corrective Remote CI：Passed — Push Run `31214636761`; Pull Request Run `31214655119`
- Corrective Manager Decision：APPROVE; no remaining findings; Human approval not required.
- Corrective Merge：Passed — PR #28, `131274be0fff0230ce9cdd7ef1ca53d1d09cbcb7`
- Final Post-merge CI：Passed — main Run `31215080850`
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
- [x] Push and Pull Request Remote CI pass at the committed Head.
- [x] Exact-head Manager Review records one of the three allowed Gate decisions.
- [x] Merge and post-merge `main` verification complete before 2D-4 starts.

## Manager review record

- Decision：`APPROVE`
- Scope reviewed：17-file additive Quality UI vertical slice; no Backend, Migration, Workflow, Runtime dependency, or security-model change.
- Contract changes：additive fixed Quality BFF routes and Product detail Quality tab only.
- Security impact：no new credential or actor flow; Backend origin remains server-only, with fixed path/query/header/body and response-header allowlists.
- Data impact：none; no Migration or persistence change.
- Tests executed locally：Frontend lint, typecheck, 119 Vitest tests, production build, 175 Backend tests, Compose config/cold start, runtime Quality smoke, Chromium desktop/mobile interaction, stale ETag and archive recovery, existing 4-test Playwright regression, npm audit, Gitleaks, and `git diff --check`.
- Remote evidence：Push Run `31213276000` and Pull Request Run `31213280058`; both `quality-and-compose` and `secret-scan` passed, including Backend, Frontend, Compose cold start, Browser E2E, smoke, actionlint, and Gitleaks.
- Known non-blocking warnings：GitHub Actions Node.js compatibility annotation and Byte Buddy dynamic-agent future deprecation.
- Human approval required：No.

## Post-merge corrective record

- Main Run `31214177537` correctly blocked completion after exposing a nondeterministic synchronous assertion in `product-detail-view.test.tsx`.
- The correction changed only the assertion from synchronous `getAllByText` to awaited `findAllByText`; production behavior and contracts were unchanged.
- The target test passed 10 consecutive local runs before the complete Frontend Gate passed.
- Exact-head Push Run `31214636761`, PR Run `31214655119`, and corrective post-merge main Run `31215080850` passed.
- 2D-3 is completed; 2D-4 may start only after this finalization record is merged and its post-merge main CI passes.
