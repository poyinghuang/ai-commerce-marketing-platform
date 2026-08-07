# Milestone 2C-2 — Product Knowledge Vertical Slice

## Gate status

- Status：Manager approved, pending merge
- Branch：`codex/2c-2-product-knowledge`
- Base Commit：`9c2b678a7329c8a8c9519e05cc6bc4eec12d5e61`
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Passed
- Manager Review：Passed
- Manager Decision：APPROVE
- Human Review Required：No
- Merge：Not started
- Milestone 2C-4：Locked

## Objective

Deliver the Product Knowledge Backend, same-origin BFF, and Product Detail Knowledge Tab on the approved V4 foundation. All mutations must use independent resource concurrency, trusted audit actors, archive-only lifecycle, and the existing Product ownership boundary.

## Included

- Knowledge Create, list, single read, merge-patch, archive, and restore.
- Endpoints exactly as defined by the parent 2C specification under `/api/products/{productUuid}/knowledge`.
- Writable fields only：`knowledgeType`, `title`, `content`, `source`.
- `201 + Location + ETag` on create; resource `ETag` on single reads and successful mutations.
- Strict `If-Match` for patch/archive/restore with 428／400／412 behavior.
- ACTIVE／ARCHIVED／ALL collection filtering, pagination, size ≤ 100, sort allowlist, and UUID secondary ordering.
- Archived Product remains readable but rejects Knowledge mutation with `PRODUCT_ARCHIVED`.
- Archived Knowledge rejects ordinary patch; archive/restore no-op does not increment version or create audit.
- `KnowledgeCommandService` transaction boundary with trusted `AuditActorProvider`; audit records only actual changes.
- Next.js endpoint-specific same-origin Knowledge Route Handlers; no arbitrary URL/path proxy.
- Product Detail `knowledge` Tab with loading, empty, error, create, edit, archive, restore, archived-product read-only, and stale-version reload UX.
- Backend integration/unit tests and Frontend/BFF/component tests.

## Explicitly out of scope

- Creative Plan, Campaign, Campaign Product, Asset, or Aggregate implementation.
- V1–V4 migration changes or a new migration.
- Google, Quality, Workflow, AI, Meta Ads, Dashboard, Decision Engine, or Playwright.
- Authentication／RBAC／Tenant or production actor-provider changes.
- Generic proxy, Cookie／Authorization forwarding, or Browser-controlled Backend origin.

## Required contracts

- Existing Product endpoints and responses remain unchanged.
- Merge patch accepts only `application/merge-patch+json`; missing fields are unchanged, explicit null follows field rules, unknown/immutable fields return `INVALID_MERGE_PATCH`, and empty/no-change patches create no version or audit change.
- Error responses use the existing `ApiError` contract and request ID.
- Path Product ownership mismatch is indistinguishable from not found.
- Mutation and audit commit or roll back together.
- Large content audit values use existing redaction and 4096-character truncation; secrets are never logged.
- BFF forwards only approved query/header/response fields, preserves Backend status/body/ETag/Location/request ID, enforces timeout/body limit, and never forwards Cookie or Authorization.

## Verification requirements

- Backend happy paths and validation for all six endpoints.
- 428, malformed ETag, stale 412, archived resource 409, archived Product 409, owner mismatch 404, empty patch, and rollback tests.
- Audit CREATE／UPDATE／ARCHIVE／RESTORE actor/request ID/actual-change tests; failed/stale/blocked/no-op requests leave no audit.
- Repository queries exclude archived by default and apply stable pagination/sort.
- BFF allowlists, no arbitrary path/URL, header/cookie boundary, timeout/body size, and status/header forwarding tests.
- Knowledge Tab loading/empty/error/CRUD/archive/restore/409/412/428 tests.
- Full Backend suite; Frontend lint/typecheck/tests/build; Compose config/cold start; existing Product smoke plus Knowledge vertical-slice smoke; Gitleaks; npm audit; actionlint.

## Acceptance checklist

- [x] Knowledge API and UI are implemented against the approved contract; verification remains in progress.
- [x] Product ownership, archive, concurrency, idempotency, and transactional audit boundaries are implemented with automated coverage.
- [x] No hard-delete API or repository operation is introduced.
- [x] BFF uses endpoint-specific routes, a server-owned origin, path/query allowlists, and credential-header isolation.
- [x] V1–V4 remain unchanged.
- [x] No out-of-scope resource or later-stage behavior is implemented.
- [x] Local and Remote CI verification pass.
- [x] Independent Manager Decision is `APPROVE` before merge.
- [ ] Post-merge main CI passes before downstream integration depends on this slice.

## Mandatory escalation

Stop and escalate if implementation requires changing V1–V4, breaking the Product contract, weakening the BFF or audit actor boundary, adding authentication／RBAC／production credentials, destructive data changes, or extending scope beyond Product Knowledge.

## Developer verification record

- Backend targeted Web MVC/parser/audit tests: Passed — 16 tests, 0 failures, 0 errors, 0 skipped. The coverage includes explicit audit action/actor/request-ID/changed-field assertions, stable multi-record pagination, and controller error mapping.
- Backend full suite with PostgreSQL Testcontainers: Passed — 90 tests, 0 failures, 0 errors, 0 skipped. This includes Flyway/Hibernate regressions, Knowledge persistence/lifecycle/audit coverage, and audit-failure transaction rollback.
- Frontend pinned Node.js 24.18.0 / npm 11.16.0 container verification: Passed — lint, typecheck, 26 tests, and production build. The coverage includes Knowledge Tab edit/restore/states/pagination/sort behavior plus BFF payload-limit, unavailable-upstream, status/body, and response-header forwarding boundaries. The host Node.js 24.14.0 / npm 11.9.0 was correctly rejected by `engine-strict` and was not used to weaken the repository pin.
- `npm audit --omit=dev`: Passed — 0 vulnerabilities.
- Docker Compose config, image build, cold start, and service health: Passed — PostgreSQL, Backend, and Frontend reported healthy.
- HTTP smoke through the running Compose network and same-origin Frontend BFF: Passed — Backend health `UP`, same-origin health `UP`, Product create `201`, Knowledge create `201` with Location/ETag, list `200`, single read `200` with ETag, patch `200`, stale patch `412`, archive `204`, archived patch `409`, restore `200`, and archived-Product mutation `409`.
- Compose cleanup: Passed — `docker compose -p aimcp2c2 down --volumes` removed all containers, network, and volume; `docker compose -p aimcp2c2 ps --all --quiet` was empty.
- Gitleaks 8.28.0: Passed — 17-commit repository history scan and current 2C-2 working-directory scan found no leaks. The worktree `.git` indirection cannot be resolved inside the scan container, so history was intentionally scanned from the primary repository while uncommitted 2C-2 content was scanned with `gitleaks dir`.
- actionlint 1.7.7: Passed using the checksum-verified Windows amd64 release asset.
- `git diff --check`: Passed. V1–V4 have no working-tree differences.
- Known non-blocking warning: Mockito / Byte Buddy dynamic Java-agent future deprecation.
- Remote CI: Passed. Push Run `31147012431` and Pull Request Run `31147026416` both completed `quality-and-compose` and `secret-scan` successfully.
- Manager Review: Passed after the required acceptance-coverage corrections in `8f3d4c3`.
- Manager Decision: `APPROVE`; no blocking or critical findings remain and no human escalation is required.
- Manager-reviewed implementation Head: `f5e27be0b6f2c173b0c5e93b2a2a519d11f92bd5`.
- Merge: Pending. Post-merge `main` verification remains required before 2C-4 may start.

## Manager verification record

- Scope and history: `git status --short`; `git log --oneline --decorate -5`; `git diff --stat 9c2b678..HEAD`; `git diff --name-status 9c2b678..HEAD`.
- Integrity: `git diff --check 9c2b678..HEAD`; direct V1–V4 migration diff; no migration changes found.
- Test evidence: inspected the fresh Surefire XML reports (`18` reports, `90` tests, `0` failures, `0` errors, `0` skipped), the Knowledge PostgreSQL/audit/rollback tests, Web MVC error-contract tests, BFF boundary tests, and Knowledge Tab state/lifecycle/concurrency tests.
- Contract review: verified the six fixed Product Knowledge endpoints, resource ETag/If-Match behavior, merge-patch allowlist, archive-only lifecycle, Product ownership boundary, transactional audit, stable pagination, and same-origin BFF restrictions.
- Security and data impact: no Secret, credential, authentication/RBAC, production-access, destructive-data, System-of-Record, or V1–V4 migration impact.
