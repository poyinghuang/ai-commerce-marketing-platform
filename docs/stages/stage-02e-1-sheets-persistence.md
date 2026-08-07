# Milestone 2E-1 — Sheets Persistence and Ports

## Delivery status

- Branch：`codex/2e-1-sheets-persistence`
- Base Commit：`d0e9516befcf00a1519c4c2c5867ea0418de46b8`
- Implementation：Complete
- Local Verification：Passed
- Commit：Pending
- Push：Pending
- Remote CI：Passed — Push Run `31227689476`; PR Run `31227703935`
- Manager Review：Passed
- Manager Decision：`APPROVE`
- Approved Implementation Commit：`6b96f03e2f3824824ca7313caa508d8848d44953`
- Human Review Required：No
- Merge：Pending
- 2E-2：Not started

## Scope delivered

- Additive `V6__create_sheet_import_foundation.sql` with immutable preview source data, bounded execution state, coherent result Product UUID／Product ID identity, database constraints, foreign keys, indexes, and delete protection.
- `SheetImportJob` and `SheetImportRow` JPA state models with optimistic versions and explicit state transitions.
- `SheetValuesProvider` port, normalized values snapshot, canonical Product mapping, deterministic SHA-256 fingerprint, and bounded source value objects.
- Canonical Google Sheets Product import mapping document and CSV template.
- Testcontainers coverage for cold/repeat migration, populated V5 upgrade, Hibernate validation, constraints, foreign keys, JSON shape, immutable triggers, delete rejection, JPA persistence, and optimistic state updates.
- V1–V5 canonical checksum protection remains active; no merged migration was modified.

## Explicitly out of scope

- No REST API, Next.js BFF, or Connector UI.
- No Google SDK, credential, live Google request, provider adapter, or external network call.
- No Preview／Execute application service or Product mutation.
- No Google Drive schema, `StorageProvider`, V7, folder creation, or asset upload.
- No Stage 03 functionality.

## Local verification

| Verification | Result | Evidence |
| --- | --- | --- |
| Backend full test suite | Passed | 185 tests, 0 failures |
| 2E-1 focused Domain／Migration／Schema tests | Passed | 15 tests, 0 failures after final boundary hardening |
| Empty database migration | Passed | V1→V6 applied; repeat migration has no pending work |
| Populated database migration | Passed | Existing V5 Product and Quality data survived V6 |
| Hibernate schema validation | Passed | JPA mappings validated against PostgreSQL 17.6 |
| Direct JDBC constraints／triggers | Passed | Enum, range, FK, JSON, immutability, and DELETE rejection covered |
| Frontend lint | Passed | Existing Frontend regression |
| Frontend typecheck | Passed | Existing Frontend regression |
| Frontend tests | Passed | 119 tests across 19 files |
| Frontend production build | Passed | Next.js production build |
| npm production audit | Passed | 0 vulnerabilities |
| Docker Compose config | Passed | `docker compose -p aicmp2e1 config --quiet` |
| Docker Compose cold start | Passed | PostgreSQL, Backend, Frontend healthy in 91 seconds |
| Migration smoke | Passed | Flyway latest version `6`; both Sheet tables present |
| Health smoke | Passed | Backend and same-origin Frontend health returned `UP` |
| Playwright E2E | Passed | 7 tests, including Product graph, concurrency, lifecycle, Audit, and Quality |
| `git diff --check` | Passed | No whitespace errors |
| V1–V5 diff | Passed | No changes |
| actionlint | Not verified locally | Tool is not installed; Remote CI required |
| Gitleaks | Not verified locally | Tool is not installed; Remote CI required |

The first isolated Playwright attempt used the default Compose project for its direct Audit assertion and produced one harness-location failure after six tests passed. The final run injected the actual isolated Compose project name from its Docker label and passed all seven tests. No Runtime change was required.

## Architecture, security, and data impact

- PostgreSQL remains the only System of Record.
- The application layer depends only on `SheetValuesProvider`; no Google SDK type enters Domain or Application code.
- V6 is additive. Runtime rollback leaves the two unused Connector tables in place; the migration must not be edited after merge.
- Import source identity and row preview snapshots are immutable even through direct SQL. Import history cannot be deleted.
- Row counts, source strings, validation JSON, result values, and errors are bounded by both application and database rules.
- No Secret, token, credential, Browser actor, arbitrary URL, or production access is introduced.

## Known limitations

- Provider adapter, preview validation, execute/upsert, transactional Product Audit, and Quality recalculation belong to 2E-2.
- Local actionlint and Gitleaks are pending Remote CI evidence.
- The existing Byte Buddy dynamic Java-agent future-deprecation warning remains non-blocking.
- Windows Docker cold startup remains comparatively slow.

## Manager Gate

Exact-head implementation review found no blocking findings. Merge remains prohibited until this approval-record documentation Commit receives successful Push and PR Remote CI. See the [Manager Review report](../management/reviews/stage-02e-1-manager-review.md).
