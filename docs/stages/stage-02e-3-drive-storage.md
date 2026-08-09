# Milestone 2E-3 — Google Drive StorageProvider

## Delivery status

- Branch：`codex/2e-3-drive-storage`
- Base Commit：`bd9a6472c3463a42cd56ca7e186b5393e10fd5ac`
- Implementation：Complete
- Local Verification：Passed
- Commit／Push／Remote CI：Passed
- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Implementation Commit：`7016ab76236f16033dda3df240208cdaa6874590`
- Approved CI Runs：Push `31302252213`; PR `31302254329`
- Human Review Required：No
- Merge：Passed — Squash Commit `6e0efaabd3d1595e17899b9f9ff459f4298896d2`
- Post-merge CI：Passed — main Run `31303057300`
- Milestone 2E-3：Completed
- 2E-4：Allowed; not started

## Approved scope

- Additive V7 with `product_storage_folders` and the exact six `product_storage_subfolders` roles.
- `StorageProvider` port with deterministic test/local Stub and fail-closed Google Drive adapter.
- Required `GOOGLE_DRIVE_ROOT_FOLDER_ID` and optional `GOOGLE_SHARED_DRIVE_ID`; Shared Drive requests use the approved all-drives parameters.
- Search-before-create using fixed parent plus provider `appProperties` (`product_uuid`, `folder_role`) so retries reuse external folders.
- Idempotent GET/POST Product storage-folder API, active-Product boundary, trusted Audit actor, one database transaction for folder tree and Audit.
- Migration, Hibernate, direct-JDBC constraint/immutability/delete, provider/profile, transaction, API, and regression tests.

## Explicitly out of scope

- No modification to V1–V6.1, no later migration, and no destructive schema operation.
- No Connector UI/BFF; those belong to 2E-4.
- No file upload/download, asset bytes, folder delete/move/rename, Product archive folder move, or live export.
- No real credential, Shared Drive access, production deployment, RBAC, billing, or Stage 03 functionality.

## V7 persistence contract

`product_storage_folders` stores immutable Product association, provider, configured root/shared-drive scope, and Product folder ID. `provider_metadata` is bounded provider-only JSONB. Product UUID and Product folder ID are unique. The Product FK uses `ON DELETE RESTRICT`.

`product_storage_subfolders` stores exactly one immutable provider ID per `ORIGINAL`, `IMAGES`, `VIDEOS`, `DOCUMENTS`, `CAMPAIGNS`, and `ARCHIVE` role. Role and provider IDs are unique within the approved constraints. Direct SQL UPDATE of identity/subfolders and DELETE of either table are rejected by triggers.

## Provider and transaction boundary

- The Google origin is fixed to `https://www.googleapis.com`; Browser input cannot choose URL, parent, credential, Drive ID, name, or query syntax.
- Root/shared-drive IDs are server configuration and validated before use. ADC uses only the Drive file scope. Tokens and provider error bodies never enter API errors, logs, Audit, or persistence.
- Connect/read timeouts are 5/15 seconds. Only transient transport, 429, and 5xx failures receive one retry.
- Every folder is searched by fixed parent and deterministic appProperties before creation. Shared Drive search uses `supportsAllDrives`, `includeItemsFromAllDrives`, `corpora=drive`, and configured `driveId`; create uses `supportsAllDrives`.
- External folder creation occurs before the database transaction. Persistence rechecks Product lifecycle; folder, six children, and Audit commit or roll back together. A database race rereads the winner instead of producing a duplicate.
- Repeated ensure returns the stored tree with 200, creates no external/database duplicate, changes no version, and emits no additional Audit. First successful ensure returns 201.

## REST contract

- `GET /api/products/{productUuid}/storage-folder`
- `POST /api/products/{productUuid}/storage-folder`

GET returns 404 `STORAGE_FOLDER_NOT_FOUND` when absent. POST returns 201 plus `Location` on first persistence and 200 on an idempotent repeat. Archived Products return the existing 409 `PRODUCT_ARCHIVED`. Provider configuration/auth/permission/rate/unavailable errors remain sanitized and use the approved standard error envelope.

## Acceptance checklist

- [x] Empty V1→V7 and populated V6.1→V7 migration, repeat migration, Hibernate validation, and V1–V6.1 canonical checksum protection pass.
- [x] Direct JDBC proves provider/role/FK/unique/JSON constraints, identity immutability, subfolder immutability, and DELETE rejection.
- [x] Local/test selects Stub; default/production selects fail-closed Google provider, including mixed production profiles.
- [x] My Drive and Shared Drive request semantics, fixed origin, ADC scope, timeout/retry, appProperties search-before-create, duplicate detection, and sanitized failures pass provider tests.
- [x] Active Product creates one Product folder plus exact six subfolders; repeat ensure reuses IDs and emits no additional Audit.
- [x] Archived/missing Product, Audit rollback, database race recovery, and partial external completion recovery are covered.
- [x] GET/POST status, Location, ETag, standard errors, Request ID, trusted actor, and no Browser credential/URL control pass.
- [x] Full Backend, Frontend, Compose, Smoke, existing Playwright, dependency audit, Gitleaks, and actionlint pass locally/remotely.
- [x] Exact-head Manager Decision is `APPROVE`; merge and post-merge main CI passed before 2E-4.

## Known limitations

- CI and local tests use Stub/Mock providers; no live Google Drive or production credential is exercised.
- External Drive side effects cannot share a PostgreSQL transaction; deterministic search-before-create is the recovery mechanism.
- Connector UI and BFF remain in 2E-4; full Stub-backed Drive browser flow remains in 2E-5.
- Windows Docker Desktop name lookup was inconsistent inside the Playwright worker; local E2E therefore passed the resolved PostgreSQL Container ID. Linux CI retains the Compose service path.

## Local verification evidence

- Backend: 225 tests passed, including V1→V7 cold migration, populated V6.1→V7 upgrade, repeat migration, Hibernate validation, direct JDBC trigger/constraint checks, provider/profile tests, transactional rollback, and API tests.
- Frontend: lint, typecheck, 119 tests, and production build passed with Node 24.18.0 and npm 11.6.0; production dependency audit reported 0 vulnerabilities.
- Compose: isolated `aimcp2e3` build and cold start completed healthy in 149 seconds; Backend and same-origin health proxy returned `UP`.
- Smoke: first folder ensure returned 201, repeat returned 200, GET returned 200, and PostgreSQL contained one Product folder, six subfolders, one storage Audit, and Flyway version 7.
- Playwright: all seven existing Chromium scenarios passed against the isolated Compose stack.
- Exact-head Manager Gate, approval-record CI, Squash merge, and post-merge `main` verification passed.

## Delivery completion

- PR #38：Merged
- Squash Commit：`6e0efaabd3d1595e17899b9f9ff459f4298896d2`
- Approval-record Push Run：`31302808821` — Passed
- Approval-record PR Run：`31302810904` — Passed
- Post-merge main Run：`31303057300` — `quality-and-compose` and `secret-scan` Passed
- Milestone 2E-3：Completed
- Milestone 2E-4：Allowed after this closeout is merged and its post-merge CI passes
