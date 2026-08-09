# Milestone 2E-2 — Sheets Preview and Execute

## Delivery status

- Branch: `codex/2e-2-sheets-preview-execute`
- Base Commit: `5131d4696f7b9a39839a2ef43d2b60d53f9481a4`
- Implementation: Complete
- Local Verification: Passed
- Commit: Passed — implementation `ef2225e696f03211ca84124713f3770a4a4caa3f`; bounded-range fix `4391456af314a894bc3db0c70a2ca29af3393534`
- Push: Passed
- Remote CI: Passed — Push Run `31293906611`; PR Run `31293908307`
- Manager Review: Passed
- Manager Decision: `APPROVE`
- Approved Implementation Commit: `4391456af314a894bc3db0c70a2ca29af3393534`
- Human Review Required: No
- Merge: Passed — PR #36, Squash Commit `7dd925af3c4f79abd26c522d646a45c2ee7649f3`
- Post-merge CI: Passed — main Run `31294372079`
- 2E-3: Allowed after this documentation closeout is merged and its post-merge main CI passes

## Approved scope delivered

- Additive V6.1 header-presence persistence with legacy backfill `8191`, required-bit/range constraints, removed permanent default, and direct-SQL immutability protection.
- Google Sheets `values.get` adapter using fixed Google origin, Application Default Credentials, readonly scope, bounded connect/read timeouts, two-attempt transient retry, and sanitized provider errors.
- Deterministic local/test Stub provider and production/default fail-closed provider profiles.
- Preview API, immutable normalized snapshot, explicit header mask, per-row validation, UUID-first/Product-ID-second matching, duplicate-target rejection, and job Audit.
- Execute API with weak ETag/If-Match, one independent transaction per valid row, Product create/update, Product Audit, Quality recalculation, partial failure reporting, idempotent completed response, and SYSTEM interruption recovery.
- Template and import-result query endpoints using the existing standard error and Request ID contracts.

## Explicitly out of scope

- No changes to V1–V6 and no V7 or Google Drive schema.
- No Google Drive `StorageProvider`, folder creation, asset bytes, or production credential provisioning.
- No Next.js BFF or Connector UI; those remain in 2E-4.
- No automatic polling, webhook, Sheet write-back, bidirectional synchronization, or Browser-supplied actor.
- No Stage 03 functionality.

## V6.1 compatibility and PATCH semantics

- Existing V6 rows are backfilled to `header_presence_mask=8191` before the temporary default is removed.
- New Preview jobs must explicitly persist a computed mask; database insertion without it fails.
- Required bits are `product_uuid`, `product_id`, and `product_name` (`11`); valid mask range is `0..8191`.
- Header presence is immutable through JPA and direct SQL.
- Omitted optional headers preserve current Product values. Present blank optional cells resolve to explicit null.
- Canonical migration checksums protect V1–V6 byte content independently of CRLF/LF line endings.

## Transaction and recovery contract

- External Sheet reads occur before the Preview database transaction.
- Preview job, rows, and `SHEET_IMPORT_JOB` CREATE Audit commit or roll back together.
- Execute first moves the job from `PREVIEWED` to `EXECUTING` under a pessimistic lock and current job ETag.
- Every pending valid row uses `REQUIRES_NEW`; Product mutation, Product Audit, Quality/Workflow recalculation, and row success commit or roll back together.
- Invalid rows stay `SKIPPED`. A stale Product fails only its row with `STALE_PRODUCT`; successful rows remain committed.
- Final counts and terminal status are computed after no PENDING row remains.
- A completed job with its current ETag is idempotent and creates no Product or Audit duplication.
- Recovery accepts only an `EXECUTING` job, processes only PENDING rows, and uses one explicit SYSTEM actor, operation UUID, and server-generated nonempty Request ID.

## Verification status

| Verification | Result | Evidence |
| --- | --- | --- |
| V6.1 migration/backfill/default removal | Passed | Focused Testcontainers migration tests |
| Header mask constraints and direct-SQL immutability | Passed | PostgreSQL constraint/trigger tests |
| Hibernate schema validation | Passed | Spring Boot Testcontainers startup at Flyway 6.1 |
| Preview/execute integration | Passed | Mixed create/update/invalid, partial stale failure, idempotency, SYSTEM recovery |
| Transaction rollback | Passed | Preview Audit rollback and Product/Audit/Quality/row rollback tests |
| Provider security/profile tests | Passed | Fixed origin/path, bounded retry, sanitized errors, fail-closed profile matrix |
| REST contract tests | Passed | 201/Location/ETag, template/query, 428/400/412 |
| Full Backend suite | Passed | 206 tests, 0 failures/errors/skips |
| Frontend regression | Passed | lint, typecheck, 119 tests, and production build on Node 24.18.0/npm 11.16.0 |
| Dependency audit | Passed | `npm audit --omit=dev`: 0 vulnerabilities |
| Docker Compose and smoke | Passed | config valid; cold build/start healthy in 222 seconds; Flyway 6.1; Preview/Execute/Product/Quality/Audit smoke passed |
| Runtime users | Passed | Backend `app`; Frontend `node` |
| Playwright regression | Passed | 7 tests, 0 failures |
| actionlint | Passed | Verified with checksummed actionlint 1.7.7 Windows release |
| Gitleaks | Passed | 57-commit history and working directory scans; no leaks found |
| `git diff --check` | Passed | No whitespace errors |
| V1–V6 content | Passed | No migration diff; canonical V1–V6 checksum test passed |
| Remote CI | Passed | Push Run `31293906611`; PR Run `31293908307`; both required jobs passed at exact implementation Head |

## Known limitations

- Connector UI and same-origin Next.js Route Handlers are deliberately deferred to 2E-4.
- Production Google credentials and live API access are not provisioned or exercised.
- Existing Byte Buddy dynamic-agent and GitHub Actions compatibility warnings remain non-blocking unless their behavior changes.
- npm 11.16.0 reports the existing informational `allow-scripts` review notice for `unrs-resolver@1.12.2`; audit remains at zero vulnerabilities.
- Windows cold Compose build/start took 222 seconds, dominated by the Maven dependency cache layer.

## Manager Gate

Exact-head local verification and both Push/PR CI runs passed. Manager Decision is `APPROVE`; the resolved bounded-range MAJOR finding and full evidence are recorded in [the Manager Review report](../management/reviews/stage-02e-2-manager-review.md). PR #36 was squash-merged as `7dd925af3c4f79abd26c522d646a45c2ee7649f3`, and post-merge main Run `31294372079` passed all required jobs. Milestone 2E-2 is complete after this documentation closeout is merged and verified on `main`.
