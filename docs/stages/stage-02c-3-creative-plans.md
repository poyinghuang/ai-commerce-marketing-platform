# Milestone 2C-3 — Creative Plan Vertical Slice

## Gate status

- Status：Manager approved, pending merge
- Branch：`codex/2c-3-creative-plans`
- Base Commit：`9c2b678a7329c8a8c9519e05cc6bc4eec12d5e61`
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Passed
- Manager Review：Passed
- Manager Decision：APPROVE
- Human Review Required：No
- Merge：Pending
- Milestone 2C-4：Locked

## Objective

Deliver the Creative Plan Backend, same-origin BFF, and Product Detail Creative Plans Tab on the approved V4 foundation. The slice uses independent resource ETags, archive-only lifecycle, Product ownership, and transactional audit without introducing AI generation or approval workflow.

## Included

- Creative Plan Create, list, single read, merge-patch, archive, and restore.
- Endpoints exactly as defined by the parent 2C specification under `/api/products/{productUuid}/creative-plans`.
- Writable V4 business fields：`planName`, `primaryAudience`, `secondaryAudience`, `painPoint`, `coreBenefit`, `creativeAngle`, `emotionalDirection`, `brandTone`, `visualStyle`, `mainColor`, `characterSetting`, `cta`.
- `201 + Location + ETag` on create; resource `ETag` on single reads and successful mutations.
- Strict `If-Match` with 428／400／412 behavior.
- ACTIVE／ARCHIVED／ALL filtering, pagination, size ≤ 100, sort allowlist, and UUID secondary ordering.
- Archived Product remains readable but rejects Creative Plan mutation with `PRODUCT_ARCHIVED`.
- Archived Plan rejects ordinary patch; archive/restore no-op does not increment version or create audit.
- `CreativePlanCommandService` transaction boundary with trusted `AuditActorProvider`; audit records only actual changes.
- Next.js endpoint-specific same-origin Creative Plan Route Handlers.
- Product Detail `creative-plans` Tab with loading, empty, error, create, edit, archive, restore, archived-product read-only, and stale-version reload UX.
- Backend integration/unit tests and Frontend/BFF/component tests.

## Explicitly out of scope

- Product Knowledge, Campaign, Campaign Product, Asset, or Aggregate implementation.
- V1–V4 migration changes or a new migration.
- AI-generated fields, prompts, LLM calls, approval actor/time, Quality, or Workflow.
- Google, Meta Ads, Dashboard, Decision Engine, or Playwright.
- Authentication／RBAC／Tenant or production actor-provider changes.
- Generic proxy, Cookie／Authorization forwarding, or Browser-controlled Backend origin.

## Required contracts

- Existing Product endpoints and responses remain unchanged.
- Merge patch accepts only `application/merge-patch+json`; missing fields are unchanged, explicit null clears optional fields but cannot clear `planName`, unknown/immutable fields fail, and empty/no-change patches produce no audit/version change.
- Error responses use existing `ApiError` and request ID contracts.
- Path Product ownership mismatch returns not found without disclosing another Product relationship.
- Mutation and audit share one transaction.
- Audit uses approved value types, redaction, truncation, and actual-change ordering; no sensitive request body is logged.
- BFF uses fixed endpoint/query/header allowlists, preserves Backend status/body/ETag/Location/request ID, enforces timeout/body size, and never forwards Cookie or Authorization.

## Verification requirements

- Backend happy paths and validation for all six endpoints and every writable field.
- 428, malformed ETag, stale 412, archived Plan 409, archived Product 409, owner mismatch 404, empty patch, and rollback tests.
- Audit CREATE／UPDATE／ARCHIVE／RESTORE actor/request ID/actual-change tests; failed/stale/blocked/no-op requests leave no audit.
- Repository queries exclude archived by default and apply stable pagination/sort.
- BFF arbitrary path/URL rejection, query/header/cookie boundary, timeout/body size, and response forwarding tests.
- Creative Plans Tab loading/empty/error/CRUD/archive/restore/409/412/428 tests.
- Full Backend suite; Frontend lint/typecheck/tests/build; Compose config/cold start; existing Product smoke plus Creative Plan vertical-slice smoke; Gitleaks; npm audit; actionlint.

## Local verification evidence

- Backend full suite after Product Knowledge integration：104 tests passed across 23 reports; 0 failures, errors, or skips. PostgreSQL Testcontainers executed V1→V4, V3→V4 upgrade, repeat migration with no pending work, and Hibernate validation.
- Creative Plan persistence／audit integration：passed for ACTIVE／ARCHIVED／ALL queries, stable pagination, Product ownership, CREATE／UPDATE／ARCHIVE／RESTORE audit, and stale／blocked／failed／no-op audit exclusion.
- Audit rollback integration：passed; a failing Audit Writer rolls back the Creative Plan mutation.
- Controller／parser／command targeted suites：passed, including 428, malformed ETag, stale 412, archived Resource／Product 409, owner mismatch 404, field presence, pagination, status, and sort allowlist.
- Pinned Frontend Docker verification (`node:24.18.0`, npm `11.16.0`) after Product Knowledge integration：lint, typecheck, 39 tests across 7 files, and production build passed. Host Node.js 24.14.0／npm 11.9.0 was correctly rejected by repository engines and was not used to weaken the pin.
- Docker Compose cold build／start：PostgreSQL, Backend, and Frontend healthy. Direct Backend and same-origin BFF Creative Plan smoke passed; lifecycle ETag advanced `W/"0"` → `W/"3"`.
- `docker compose config --quiet`、`npm audit --omit=dev` (0 vulnerabilities)、actionlint 1.7.7 checksum-verified run、Gitleaks history and working-tree scans、`git diff --check`：passed.
- V1–V4 Git blob identities match Base Commit `9c2b678a7329c8a8c9519e05cc6bc4eec12d5e61`; no Migration or Workflow file changed.

## Product Knowledge integration evidence

- Merged finalized Milestone 2C-2 main commit `c88aebea72b3eb44fac43637241c8304ee90a0ca`; the final documentation merge was conflict-free.
- Shared conflict resolution preserves both approved slices in `GlobalExceptionHandler`, Product Detail product／knowledge／creative-plans tabs, endpoint-specific Knowledge／Creative Plan BFF paths, and both test suites.
- Knowledge and Creative Plan collection queries are allowlisted independently; detail and restore endpoints do not forward collection-only query parameters.
- Targeted Backend Knowledge／Creative Plan controller and merge-patch suites passed before the full Backend run.
- Targeted pinned Frontend integration verification passed 34 tests plus typecheck before the full 39-test Docker build.
- Compose cold start was not repeated after the documentation-only final merge. Both slices had already passed their local Compose smoke; Remote CI remains responsible for the integrated Compose build and smoke gate.
- Final `git diff --check` passed and `git diff main...HEAD -- backend/src/main/resources/db/migration` is empty, confirming V1–V4 are unchanged.

Known non-blocking warnings：Mockito／Byte Buddy dynamic Java-agent future deprecation and npm's informational newer-major-version notice.

## Acceptance checklist

- [x] Creative Plan API and UI conform to the approved contract.
- [x] Product ownership, archive, concurrency, idempotency, and audit boundaries are proven.
- [x] No hard-delete API or repository operation is introduced.
- [x] BFF security boundary is preserved.
- [x] V1–V4 remain unchanged.
- [x] No AI, approval workflow, or other out-of-scope behavior is implemented.
- [x] Local and Remote CI verification pass.
- [x] Independent Manager Decision is `APPROVE` before merge.
- [ ] Post-merge main CI passes before Campaign or Asset depends on this slice.

## Mandatory escalation

Stop and escalate if implementation requires changing V1–V4, breaking the Product contract, weakening BFF/audit boundaries, adding AI/authentication/production credentials, destructive data changes, or extending scope beyond Creative Plans.

## Manager verification record

- Manager-reviewed Head: `fba8bb2f7c4d6a56ab4340cc22e44c1c6cae518e`.
- Remote CI: Push Run `31148770170` and Pull Request Run `31148772646` both completed `quality-and-compose` and `secret-scan` successfully on the integrated Head.
- Review commands and evidence: `git status --short`; `git log --oneline --decorate -8`; `git diff --check main...HEAD`; `git diff --stat main...HEAD`; `git diff --name-status main...HEAD`; direct V1–V4 and Workflow diff; fresh Surefire XML aggregation (`23` reports, `104` tests, `0` failures, `0` errors, `0` skipped).
- Contract review: verified all six Creative Plan endpoints, the twelve approved writable fields, strict resource ETag/If-Match, field-presence-safe merge patch, archive/restore idempotency, stable collection queries, Product ownership, and fixed-path BFF behavior.
- Integration review: verified Product, Knowledge, and Creative Plan tabs coexist; Knowledge and Creative Plan retain separate collection-only query allowlists and do not accept Browser-controlled origins, paths, credentials, or actor headers.
- Finding resolution: required explicit CREATE/UPDATE/ARCHIVE/RESTORE audit actor, request ID, actual-field, and `change_order` assertions; corrected in `fba8bb2f7c4d6a56ab4340cc22e44c1c6cae518e` and the targeted PostgreSQL Testcontainers test passed.
- Security and data impact: no Secret, credential, authentication/RBAC, production-access, destructive-data, System-of-Record, V1–V4 migration, CI Workflow, or dependency impact.
- Decision: `APPROVE`. No blocking or critical findings remain; no human escalation is required. Merge and post-merge `main` verification remain pending, and 2C-4 stays locked until they pass.
