# Stage Gate Review — Milestone 2E-2 Sheets Preview and Execute

## Review identity

- Review date：2026-08-09
- Reviewer：Codex Project Manager／Stage Gate Owner
- Repository：`poyinghuang/ai-commerce-marketing-platform`
- Branch：`codex/2e-2-sheets-preview-execute`
- Base Commit：`5131d4696f7b9a39839a2ef43d2b60d53f9481a4`
- Reviewed Head Commit：`4391456af314a894bc3db0c70a2ca29af3393534`
- Pull Request：#36

## Status before review

- Implementation：Complete
- Local Verification：Passed
- Remote CI：Passed
- Human Review Required：No
- Merge：Pending

## Scope reviewed

- Approved scope：additive V6.1 header-presence persistence, bounded Google Sheets read adapter, Preview／Execute APIs, UUID-first/Product-ID-second upsert planning, independent row transactions, Product Audit, Quality recalculation, and recovery.
- Explicit out of scope：Drive/V7, Connector UI/BFF, live credentials, polling, write-back, bidirectional sync, production deployment, and Stage 03.
- Files reviewed：all 39 PR files, both commits, migration, Domain/Application/Web/provider code, persistence mappings, tests, and Stage documents.
- Forbidden or unexpected files：None. V1–V6 have no Git diff; V6.1 is the only migration addition.
- Completion report compared：Matched actual implementation and verification evidence after the bounded-range correction.

## Architecture and contracts

- Architecture documents reviewed：`README.md`, `docs/Architecture.md`, `docs/Data-Model.md`, `docs/Development-Rules.md`, and the 2E specification/delivery documents.
- Migration reviewed：V6.1 backfills existing rows to `8191`, adds required-bit/range constraints, removes the temporary default, and rejects direct-SQL mask mutation.
- Domain／Transaction／Audit boundary：Preview provider I/O occurs before its database transaction. Preview job/rows/Audit are atomic. Each executable row uses `REQUIRES_NEW`; Product mutation, Product Audit, Quality/Workflow recalculation, and row success share that transaction. Job lifecycle/finalization is separately locked and audited.
- API contract changes：adds fixed Sheets template, Preview, query, and Execute endpoints with standard errors and weak ETag/If-Match semantics.
- Frontend／BFF contract changes：None in 2E-2; fixed BFF and UI remain owned by 2E-4.
- Backward compatibility：V1–V6 are unchanged. Existing V6 previews receive all-header semantics; new previews explicitly persist the presence mask.
- Rollback／forward recovery：Runtime rollback leaves the additive V6.1 column/trigger unused. Interrupted `EXECUTING` jobs resume only pending rows through a bounded SYSTEM operation. A merged migration must be corrected only by a later additive migration.

## Impact

- Security impact：Google origin is fixed, ADC and readonly scope are server-side only, range/identity/input sizes are bounded, provider errors are sanitized, and production/default profiles fail closed. No Browser actor, credential, token, or arbitrary upstream URL is accepted.
- Data impact：additive immutable integer metadata on Sheet import jobs plus Product writes explicitly requested through Execute. No destructive migration or hard delete.
- Production impact：No deployment or live credential provisioning is included.
- External service／cost impact：Google Sheets reads are bounded to at most 1,001 rows and 13 columns with two attempts. Tests and CI use deterministic stubs; no live Google call is made.

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status --short` | Passed | Clean at reviewed Head |
| Diff check | `git diff --check origin/main...HEAD` | Passed | No whitespace errors |
| Commit history | `git log --oneline --decorate -10` | Passed | Base and two scoped implementation commits verified |
| Backend tests | `backend/mvnw.cmd --batch-mode test` | Passed | 206 tests; rerun after bounded-range fix |
| Migration tests | Focused Testcontainers migration/schema tests | Passed | V6.1 cold/backfill/repeat, explicit mask, constraints, trigger, V1–V6 checksums |
| Hibernate validation | Testcontainers and Compose startup | Passed | Flyway 6.1 with `ddl-auto=validate` |
| Frontend lint | `npm run lint` on Node 24.18.0/npm 11.16.0 | Passed | Existing regression |
| Frontend typecheck | `npm run typecheck` | Passed | Existing regression |
| Frontend tests | `npm test` | Passed | 119 tests |
| Production build | `npm run build` | Passed | Next.js production build |
| Docker Compose config | `docker compose ... config --quiet` | Passed | Isolated project |
| Docker Compose cold start | isolated `up --detach --build --wait` | Passed | All services healthy in 222 seconds |
| Smoke tests | Actuator, BFF health, Preview／Execute/Product/Quality/Audit | Passed | Stub import completed with expected persisted effects |
| Playwright E2E | `npm run test:e2e` | Passed | 7 existing regression tests |
| Gitleaks | local pinned 8.28.0 and Remote Runs | Passed | History and working directory; no leaks |
| Dependency audit | `npm audit --omit=dev` | Passed | 0 vulnerabilities |
| actionlint | local checksummed 1.7.7 and Remote Runs | Passed | Workflow valid |

## Remote CI

- Push Run `31293906611`：Head `4391456af314a894bc3db0c70a2ca29af3393534`; `quality-and-compose` and `secret-scan` passed.
- PR Run `31293908307`：same Head; `quality-and-compose` and `secret-scan` passed.
- Backend/Testcontainers, Frontend verification, Compose validation/cold stack, Browser E2E, Smoke, actionlint, and Gitleaks executed. No required step was skipped; failure-artifact upload was correctly skipped because Playwright passed.
- Non-blocking warnings：existing Byte Buddy dynamic-agent warning, GitHub Actions compatibility annotation, and npm allow-scripts informational notice.

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| M2E2-001 | MAJOR — Resolved | `SheetSource.java`; reviewed initial Commit `ef2225e` | The default/custom A1 range could omit an explicit end row or request an excessive row count, allowing provider reads beyond the approved 1,000-row boundary before application validation. | Fixed in `4391456` by requiring the declared sheet, header row 1, explicit end row at most 1001, ordered width at most 13, and injection-safe syntax. Added quoted-name, wrong-sheet, start-row, row-limit, width, and injection tests; focused and full Backend suites passed. |

No open CRITICAL, BLOCKING, or necessary MAJOR finding remains at the reviewed Head.

## Known limitations

- Connector UI and fixed same-origin Next.js Route Handlers remain intentionally deferred to 2E-4.
- Production Google credentials and live API access are not provisioned or exercised.
- Existing Byte Buddy dynamic-agent and GitHub Actions compatibility warnings remain non-blocking.
- npm reports an informational allow-scripts review notice for `unrs-resolver@1.12.2`; audit reports zero vulnerabilities.
- Windows cold Compose startup remains comparatively slow.

## Stage Gate decision

- Decision：`APPROVE`
- Decision rationale：The approved additive scope is implemented, V1–V6 remain unchanged, exact-head local and Remote CI evidence passed, the security/transaction/data boundaries match the specification, and the only MAJOR review finding was fixed and regression-tested.
- Required next action：Commit this approval record, pass Push and PR CI at the resulting documentation Head, mark PR #36 Ready, merge, and verify exact post-merge `main` CI before 2E-3.
- Human approval required：`No`
- Human approval reason／evidence：The user pre-approved V6.1 and authorized in-scope 2E-2 completion; no new architecture, schema, security, production-access, destructive-data, or scope escalation occurred.

## Approval record

- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Commit：`4391456af314a894bc3db0c70a2ca29af3393534`
- Approved CI Run：Push `31293906611`; PR `31293908307`
- Commands actually executed：Git status/diff/log; full Backend and focused Testcontainers suites; exact-runtime Frontend lint/typecheck/tests/build/audit; Compose config/cold start/smoke; Playwright; Gitleaks; actionlint; exact-head GitHub CI inspection.
- Merge allowed：Yes, after the approval-record Head passes required CI.
- Next Stage allowed：Only after merge and post-merge verification.

## Delivery completion

- PR #36：Merged
- Squash Commit：`7dd925af3c4f79abd26c522d646a45c2ee7649f3`
- Post-merge main Run `31294372079`：`quality-and-compose` and `secret-scan` passed
- Milestone 2E-2：Completed after this documentation closeout is merged and verified
- Milestone 2E-3：Allowed only after the documentation closeout post-merge main CI passes
