# Milestone 2C-7 — Browser E2E

## Gate status

- Status：Approved for implementation
- Branch：`codex/2c-7-browser-e2e`
- Base Commit：`f9c591911bb9d4b2352b7676aea8c335680f0c96`
- Prerequisite 2C-6 Main CI：Run `31187589632` Passed
- Specification Review：Passed
- Manager Decision：APPROVE
- Human Review Required：No
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Passed
- Manager Review：Passed
- Approved Commit：`94cedeaec548ed7aa65d9ce3b64849436bc39563`
- Pull Request：`#18`
- Push CI：Run `31190967331` Passed
- Pull Request CI：Run `31190970855` Passed
- Merge：Not started
- Milestone 2C-8：Locked

## Objective

Prove the four approved Stage 02 browser journeys against the real Docker Compose Frontend, Backend, and PostgreSQL stack. Playwright exercises only the same-origin UI and BFF for Domain behavior; PostgreSQL is read directly only for the explicit append-only Audit count assertion.

## Included

- Pin `@playwright/test` to `1.62.1` in `frontend/package.json` and `package-lock.json`.
- Install the Chromium revision pinned by that Playwright release; no floating Browser channel is allowed.
- Add a Chromium-only Playwright configuration and four independent browser scenarios.
- Add `npm run test:e2e` and a documented local Compose E2E command.
- Extend the existing CI job to install pinned Chromium, run E2E after the stack is healthy, and upload non-sensitive failure artifacts with `actions/upload-artifact` pinned to commit `ea165f8d65b6e75b540449e92b4886f43607fa02` (`v7.0.1`).
- Preserve existing Backend, Frontend, Compose smoke, actionlint, dependency audit, and Gitleaks gates.

## Explicitly out of scope

- No Flyway migration or modification to V1–V4.
- No Backend Domain, API, transaction, Audit, lifecycle, ETag, or error-contract change.
- No Product Center feature or visual redesign solely to accommodate tests.
- No authentication, RBAC, Tenant, credential, production access, deployment, Google Connector, Quality, Workflow, AI, Ads, Dashboard, Decision Engine, or Stage 03 work.
- No arbitrary proxy, public test hook, test-only Backend endpoint, external paid browser service, or new container image.
- No Safari/WebKit, Firefox, mobile, visual-regression baseline, load, accessibility, or cross-browser matrix in this milestone.

## Runtime and artifact policy

- Node remains pinned to `24.18.0` and npm to `11.16.0`.
- Playwright package is exactly `1.62.1`; `npm ci` and the lockfile are authoritative.
- CI runs `npx playwright install --with-deps chromium` from the locked package. Tests select the bundled `chromium` project and never use an unpinned installed Chrome channel.
- Base URL defaults to `http://127.0.0.1:3000` and may only be overridden by the server-side `PLAYWRIGHT_BASE_URL` environment variable.
- CI uses one worker and one retry; local runs use one worker and no retry. Serial execution keeps shared Compose data deterministic.
- Trace uses `retain-on-failure`, screenshot uses `only-on-failure`, and video is disabled. Tests use synthetic non-sensitive values and must not attach response bodies, cookies, environment dumps, or credentials.
- `frontend/test-results` and `frontend/playwright-report` are excluded from Git and Docker build context. Failure artifacts are retained for seven days and uploaded only when the E2E step fails.

## Required scenarios

### 1. Complete Product graph

- Create a Product through `/products/new`.
- Add Product Knowledge and a Creative Plan through Product Detail tabs.
- Create a Campaign through `/campaigns/new`, capture its UUID from the fixed application route, and associate the Product through the Product Campaign tab.
- Add provider-neutral Asset Metadata through the Asset tab.
- Verify the Aggregate summary and individual tabs show the created Knowledge, Creative Plan, Campaign association, and Asset.
- All Browser network traffic stays on the same-origin Next.js routes.

### 2. Stale Knowledge ETag

- Open the same Product Knowledge record in two isolated browser pages before either mutates it.
- The first page successfully saves an update.
- The second page submits its stale version and receives the existing `412 PRECONDITION_FAILED` path through the UI.
- Verify the conflict alert is actionable, choose Reload, and verify the latest saved value replaces the stale view.

### 3. Archived Product boundary

- Archive a Product from Product Detail with its current ETag.
- Verify Product Knowledge, Creative Plans, Campaigns, and Assets are readable but their mutation controls are absent or disabled with a clear archived reason.
- Use the same-origin BFF from the browser request context to attempt a child mutation and prove `409 PRODUCT_ARCHIVED`.
- Restore the Product through the Product Detail UI and prove mutation controls return.

### 4. Child lifecycle and Audit no-op

- Create a Knowledge child, archive it, and prove the default ACTIVE collection hides it.
- Select ARCHIVED, restore it, and prove the default collection shows it again.
- Repeat Archive and Restore with the current ETag through the fixed same-origin BFF and prove the resource version does not increment on each target-state no-op.
- Query the ephemeral Compose PostgreSQL container with fixed SQL and prove exactly one CREATE, one ARCHIVE, and one RESTORE Audit event for the child; no duplicate no-op event may exist.

## Test isolation and helpers

- Every test creates uniquely named synthetic data; tests never depend on execution order or seed data.
- Page helpers may wrap user-visible navigation and form interaction, but must not bypass the UI for happy-path creation or mutation.
- Direct request-context calls are limited to the approved negative `409` assertion and target-state no-op verification that has no user control.
- The Audit helper invokes `docker compose -f ../docker-compose.yml exec -T postgres psql` with fixed command arguments and validates a UUID before interpolation. It may execute only when `PLAYWRIGHT_AUDIT_DB_ASSERTION=1` is set by the local/CI Compose E2E command.
- Compose teardown uses `docker compose down --volumes`; no persistent or production database may be targeted.

## CI design

The existing `quality-and-compose` job remains the only quality job:

1. actionlint, Backend Testcontainers, Frontend lint/typecheck/Vitest/build/audit.
2. Install the package-pinned Chromium runtime and OS dependencies.
3. Validate and start Docker Compose with health checks.
4. Run the four Playwright scenarios against `127.0.0.1:3000` with the Audit DB assertion enabled.
5. On failure, upload only Playwright trace/screenshot/report paths for seven days.
6. Run the existing API smoke when E2E succeeds.
7. Always stop Compose and remove volumes.

The separate pinned Gitleaks job remains unchanged. Workflow permissions remain `contents: read`; artifact upload uses the job-scoped Actions capability and does not receive Repository Secrets.

## Verification requirements

- `git status --short`, `git diff --check`, and forbidden-scope diff review.
- `npm ci`, lint, typecheck, Vitest, production build, and production dependency audit.
- `npx playwright install chromium` and all four local E2E scenarios on a cold Compose stack.
- Confirm failure configuration creates trace/screenshot paths without sensitive values; successful runs leave no committed artifact.
- Backend full Testcontainers suite and Hibernate/Flyway regression remain green.
- Docker Compose config, cold build/healthy start, existing API smoke, and teardown pass.
- actionlint validates the changed workflow.
- Pinned Gitleaks history and working-tree scans find no leak.
- Push and Pull Request Remote CI both execute, rather than skip, Playwright and all prior required gates.

## Acceptance checklist

- [x] Exact Playwright and artifact-action versions are pinned; no `latest`, floating Browser channel, or floating Action ref exists.
- [x] Complete Product graph is created through the UI and visible in Aggregate and tabs.
- [x] Two-page stale Knowledge mutation surfaces 412 and Reload shows the latest value.
- [x] Archived Product renders all child tabs read-only, rejects a child mutation with 409, and is restorable.
- [x] Knowledge Archive/Restore visibility, version-stable no-op, and exact Audit action counts are proven.
- [x] Tests use the real same-origin BFF, Backend, and PostgreSQL without mocked Domain APIs or a generic proxy.
- [x] Failure artifacts are non-sensitive, bounded, ignored by Git/Docker, and retained for seven days.
- [x] Backend, Frontend, Compose smoke, actionlint, npm audit, and Gitleaks regressions pass.
- [x] Push and Pull Request Remote CI pass with the E2E step actually executed.
- [x] Independent Manager Review records one of the three allowed decisions.
- [ ] Post-merge `main` CI passes before 2C-8 begins.

## Risks and mitigations

- Browser installation increases CI time and network dependency. The package and browser revision are pinned, while npm and Playwright caches may be optimized later without weakening verification.
- UI copy currently mixes English and Chinese, so selectors prefer roles, labels, routes, and stable created values instead of styling or positional selectors.
- Shared Compose state can make parallel E2E flaky. Unique data, one worker, isolated contexts, and volume teardown keep runs deterministic.
- Failure traces can capture entered data. Only synthetic values are used, video is disabled, uploads occur only on failure, and retention is limited.
- Direct database inspection is intentionally narrow. It targets only the ephemeral Compose service and fixed Audit count query; broader DB access from browser tests is forbidden.

## Mandatory escalation

Stop and escalate if implementation requires a migration, production credential or access, authentication/RBAC/Tenant changes, a public test hook, lowering an existing gate, changing an approved API or lifecycle contract, destructive persistent data handling, a paid browser service, or scope outside the four approved scenarios.

## Developer delivery record

- Dependency and runtime：`@playwright/test` is pinned to `1.62.1`; the installed Chromium revision is `1234` (Chrome for Testing `151.0.7922.34`). Node, npm, Next.js, Docker images, and all existing dependencies remain pinned; no floating Browser channel was introduced.
- Browser E2E：all four scenarios passed against the real cold Compose stack in 46.8 seconds with one Chromium worker. No Domain API was mocked.
- Frontend regression：lint, typecheck, 16 Vitest files／108 tests, production build, Playwright test discovery, and production dependency audit with zero vulnerabilities passed under the Repository-pinned Node image.
- Backend regression：150 tests passed with zero failure, error, or skip; Flyway V1–V4 and Hibernate validation remained green.
- Compose：config validation, cold build, PostgreSQL／Backend／Frontend health checks, Browser E2E, Backend health, and same-origin health proxy passed.
- Audit proof：the fourth Browser scenario used a UUID-validated fixed `psql` query against only the ephemeral Compose PostgreSQL service and observed exactly one CREATE, one ARCHIVE, and one RESTORE event after target-state no-ops.
- Security and workflow：pinned Gitleaks history and working-tree scans found no leaks; actionlint `1.7.7` validated the changed workflow; failure screenshot／trace output was generated during negative test development, remained ignored by Git and Docker, contained only synthetic inputs, and is configured for seven-day CI retention.
- Finding and fix：Browser verification found that Knowledge Archive did not retain the response ETag, so a same-view Restore sent the stale pre-archive token and received 412. `KnowledgeTab` now stores the successful lifecycle response ETag, with a component regression proving `W/"1"` is used for Restore. No Backend or concurrency contract was changed.
- Known warnings：the pre-existing Byte Buddy dynamic-agent warning, npm allow-scripts notice for `unrs-resolver`, Windows Node engine mismatch for the local Browser runner, GitHub Actions Node.js compatibility annotation, and LF／CRLF informational warnings remain non-blocking. Exact Node verification passed inside the pinned image and remains mandatory in Remote CI.
- Delivery state：Implementation, local verification, Commit, Draft PR, Remote CI, and independent Manager Review are complete. Merge and post-merge Main CI remain pending.

## Manager review record

- Stage：Milestone 2C-7 — Browser E2E
- Branch：`codex/2c-7-browser-e2e`
- Base Commit：`f9c591911bb9d4b2352b7676aea8c335680f0c96`
- Head Commit：`94cedeaec548ed7aa65d9ce3b64849436bc39563`
- Pull Request：`#18`
- Scope reviewed：Playwright dependency and configuration, four Browser journeys, CI Browser installation／execution／failure artifacts, ignore boundaries, local instructions, Knowledge lifecycle ETag fix, and regression test.
- Files reviewed：All 13 changed files in `origin/main...HEAD`; only the approved E2E, CI, documentation, ignore, lockfile, and minimal Knowledge UI regression scope is present.
- Migration reviewed：V1–V4 are byte-for-byte outside the Diff; no migration was added or modified.
- API and transaction contract：No Backend Domain, API, transaction, Audit writer, lifecycle, or error contract changed. The UI now retains the successful lifecycle response ETag required by the existing concurrency contract.
- Security boundary：Browser traffic uses fixed same-origin routes. No credential, Browser actor header, arbitrary upstream URL, public test hook, new permission, authentication, RBAC, Tenant, or production access was introduced. Artifacts contain synthetic data only, upload only on failure, and expire after seven days.
- Data impact：Tests create only synthetic data in the ephemeral Compose PostgreSQL volume and remove it on teardown. The fixed Audit query validates UUID format and reads only `audit_logs`; no destructive SQL is used.
- Tests executed：`git status --short`; `git diff --check`; full Backend Testcontainers suite (150/150); exact-Node Frontend lint, typecheck, Vitest (108/108), production build, npm production audit; Playwright discovery and four local Chromium scenarios; Compose config/cold build/health; actionlint 1.7.7; pinned Gitleaks history and working-tree scans.
- Remote CI：Push Run `31190967331` and Pull Request Run `31190970855` passed. Both executed actionlint, Backend Testcontainers, Frontend verification, package-pinned Chromium install, Compose cold start, all four Browser E2E scenarios, existing API smoke, teardown, and Gitleaks. Browser results were 4/4 in 10.8s and 11.7s with no retry.
- Findings：One BLOCKING finding was discovered locally and resolved before the approved Head: Knowledge Archive did not persist its response ETag, causing same-view Restore to send a stale token. The minimal fix and component regression passed locally and remotely. No CRITICAL, BLOCKING, or required MAJOR finding remains.
- Known limitations：Chromium-only coverage, no visual baseline, and the existing Byte Buddy, npm allow-scripts, Actions Node.js compatibility, and Windows line-ending warnings remain non-blocking technical debt.
- Contract changes：Additive test and CI contract only; successful Knowledge lifecycle operations now correctly carry their returned ETag into the next UI operation.
- Decision：`APPROVE`
- Human approval required：No
- Required next action：Commit and push this Manager record, require the new Push and Pull Request Head CI to pass, then mark PR `#18` Ready, squash merge, and verify post-merge `main` before 2C-8.
