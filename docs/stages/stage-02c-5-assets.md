# Milestone 2C-5 — Asset Metadata Vertical Slice

## Gate status

- Status：Completed
- Branch：`codex/2c-5-assets`
- Base Commit：`c051d26c45e3d9769bce2cdfb4cb05502f1d7d18`
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Passed
- Manager Review：Passed
- Manager Decision：APPROVE
- Human Review Required：No
- Approved Commit：`b9b17b82d217028068ce6113bf311231f0464716`
- Approved Push CI：Run `31177169477` — `quality-and-compose` and `secret-scan` Passed
- Approved PR CI：Run `31177191719` — `quality-and-compose` and `secret-scan` Passed
- Approval-document Push CI：Run `31177560976` — `quality-and-compose` and `secret-scan` Passed
- Approval-document PR CI：Run `31177563762` — `quality-and-compose` and `secret-scan` Passed
- Final documentation Push CI：Run `31178281222` — `quality-and-compose` and `secret-scan` Passed
- Final documentation PR CI：Run `31178299650` — `quality-and-compose` and `secret-scan` Passed
- Pull Request：[#14](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/14)
- Merge：Passed — Squash Commit `b4c16808faf70d331f8e1955c2b7649201e624c2`
- Post-merge Main CI：Run `31177940031` — `quality-and-compose` and `secret-scan` Passed
- Milestone 2C-6：Ready after this finalization record reaches `main`

## Objective

Deliver provider-neutral Asset Metadata management on the approved V4 schema. Assets belong to one permanent `product_uuid` and may optionally reference a Creative Plan owned by that Product and a Campaign with an existing Campaign Product association. PostgreSQL remains the only System of Record; this slice stores metadata only and never uploads, downloads, fetches, signs, or synchronizes files.

## Included

- Asset create, list, single read, field-presence-safe merge patch, archive, and restore.
- Product Detail `?tab=assets` with loading, empty, error, create, edit, archive, restore, filters, pagination, read-only, and version-conflict recovery states.
- Independent Asset ETag／If-Match concurrency and archive-only lifecycle.
- Transactional trusted-actor Audit containing only actual changed fields.
- Product, Creative Plan, Campaign, and Campaign Product ownership／active-state validation.
- Recursive provider metadata validation, sensitive-key rejection, JSON object and 16 KiB limits.
- Fixed-path Next.js Asset Route Handlers and endpoint-specific query allowlists.
- Backend unit／PostgreSQL integration tests and Frontend／BFF component tests.

## Explicitly out of scope

- A new Flyway migration or any modification to V1–V4.
- Multipart upload, binary storage, remote URL fetch, signed URL, thumbnail generation, media processing, checksum calculation from file content, or file download.
- Google Drive folders, Drive IDs with special semantics, Google APIs, Service Accounts, OAuth, StorageProvider calls, or provider credentials.
- Aggregate API, Quality, Workflow, Playwright, AI, Meta Ads, Dashboard, Decision Engine, or Stage 03 functionality.
- Authentication, RBAC, Tenant, production actor-provider, GitHub permission, Runtime, dependency, Docker image, or CI workflow changes.

## Approved schema and identity

2C-5 uses the existing V4 `assets` table exactly as merged. V1–V4 are immutable and no V5 is required.

- `asset_uuid`：Application-generated permanent UUID and REST identity.
- `product_uuid`：required permanent owner; immutable after create.
- `creative_plan_uuid`：optional create-time reference; immutable after create.
- `campaign_uuid`：optional create-time reference; immutable after create.
- `asset_type`：`IMAGE | VIDEO | DOCUMENT | OTHER`; required and patchable.
- `purpose`：optional, trimmed, max 256.
- `storage_provider`：optional opaque provider key, trimmed, max 64; no Provider call.
- `provider_file_id`：optional opaque identifier, trimmed, max 512; not globally unique.
- `file_url`：optional absolute HTTP／HTTPS metadata URL, max 2048; Backend never connects to it.
- `media_type`：optional MIME-type metadata, trimmed, max 255.
- `original_filename`：optional display metadata, trimmed, max 512.
- `size_bytes`：optional integer, `>= 0`.
- `checksum_sha256`：optional lower-case 64-character hexadecimal SHA-256 supplied as metadata.
- `provider_metadata`：optional JSON object, serialized UTF-8 representation at most 16 KiB.
- Standard lifecycle, archived timestamp, created／updated timestamps, and `version` come from the approved V4 foundation.

Client input may never set `assetUuid`, `productUuid` from the body, association identities during Patch, lifecycle, archive timestamp, version, or timestamps. PostgreSQL V4 constraints, composite foreign keys, and the identity trigger remain defense in depth.

## Provider metadata security contract

- `providerMetadata` accepts only a JSON object or explicit `null`; arrays or scalars at the root are rejected.
- Validation recursively visits keys in nested objects, including objects inside arrays.
- A key is rejected case-insensitively when its name contains `token`, `secret`, `password`, `authorization`, `cookie`, or `credential`.
- The Application enforces the 16 KiB serialized-size limit before persistence; PostgreSQL retains its JSON-object and size constraints.
- Values are stored as provider-neutral JSON and are never interpreted as credentials, URLs to fetch, commands, HTML, or executable configuration.
- Raw `providerMetadata` and its values are never written to Audit or error logs. Audit records a non-reversible SHA-256 fingerprint marker for old／new metadata so an actual metadata change remains traceable without disclosing content.
- Error responses identify the invalid field but do not echo the rejected metadata, sensitive key, or full request body.

## Relationship and lifecycle rules

- Product must exist and be ACTIVE for Create, Patch, Archive, and Restore. An archived Product returns `409 PRODUCT_ARCHIVED`; reads remain available.
- When `creativePlanUuid` is supplied, the Creative Plan must belong to the path Product and be ACTIVE for Create. Ownership mismatch or missing reference returns `409 RELATIONSHIP_CONFLICT` without disclosing another Product's data.
- When `campaignUuid` is supplied, Campaign and the unique Campaign Product association for the path Product must both exist and be ACTIVE for Create. Missing, archived, or mismatched relationships return `409 RELATIONSHIP_CONFLICT`.
- Asset references are immutable after Create; Patch cannot silently re-parent an Asset.
- Patch and Restore require all referenced Product／Creative Plan／Campaign／association resources to remain ACTIVE. Archive is allowed when the Product is ACTIVE even if an optional referenced Plan or Campaign was later archived, so historical Asset metadata can be closed without changing ownership.
- Archiving Product, Creative Plan, Campaign, or Campaign Product never cascades to Asset rows. Assets remain readable with their historical references.
- Repeated Archive／Restore with the current ETag is an idempotent no-op with no version increment and no Audit event.
- An archived Asset rejects ordinary Patch with `409 RESOURCE_ARCHIVED`.

## REST API contract

- `POST /api/products/{productUuid}/assets`
- `GET /api/products/{productUuid}/assets`
- `GET /api/products/{productUuid}/assets/{assetUuid}`
- `PATCH /api/products/{productUuid}/assets/{assetUuid}`
- `DELETE /api/products/{productUuid}/assets/{assetUuid}`
- `POST /api/products/{productUuid}/assets/{assetUuid}/restore`

Create body fields：`creativePlanUuid`, `campaignUuid`, `assetType`, `purpose`, `storageProvider`, `providerFileId`, `fileUrl`, `mediaType`, `originalFilename`, `sizeBytes`, `checksumSha256`, `providerMetadata`.

Patchable fields：`assetType`, `purpose`, `storageProvider`, `providerFileId`, `fileUrl`, `mediaType`, `originalFilename`, `sizeBytes`, `checksumSha256`, `providerMetadata`. Association identities are create-only.

- Create returns `201 Created`, `Location`, body, and `ETag: W/"0"`.
- Single read and successful Patch／Restore return the current Asset ETag; Archive returns `204` plus ETag.
- Patch consumes only `application/merge-patch+json`; missing fields remain unchanged, explicit `null` clears optional fields, unknown／immutable fields fail with `INVALID_MERGE_PATCH`, and `assetType` cannot be null.
- Patch／Archive／Restore require strict `If-Match`: missing `428`, malformed `400`, stale `412`.
- Path ownership mismatch returns `404 ASSET_NOT_FOUND`.
- Collection supports `status=ACTIVE|ARCHIVED|ALL`, optional `assetType`, `creativePlanUuid`, `campaignUuid`, and `storageProvider`, plus `page`, `size <= 100`, and sort allowlist `updatedAt`, `createdAt`, `assetType`, `originalFilename`, `sizeBytes`.
- Collection ordering always appends `assetUuid` as a deterministic secondary key. Default is `status=ACTIVE`, `page=0`, `size=20`, `sort=updatedAt,desc`.
- Error bodies retain the unified `ApiError` contract and never return stack traces or metadata payloads.

## Transaction and Audit boundary

- `AssetCommandService` creates exactly one trusted `AuditOperationContext` per command.
- Product and optional reference checks, pessimistic mutation lookup, version validation, Asset mutation, and Audit append occur in the same transaction.
- Audit uses `entity_type=ASSET`, `entity_uuid=assetUuid`, and the owning `product_uuid`.
- CREATE／UPDATE／ARCHIVE／RESTORE record actor, request ID, actual fields, and continuous `change_order` only.
- `size_bytes` uses `INTEGER`; lifecycle and asset type use `ENUM`; UUIDs use `UUID`; timestamp uses `TIMESTAMP`; remaining scalar metadata and the provider metadata fingerprint use `STRING`.
- Failed, stale, blocked, validation, ownership-conflict, empty-patch, and idempotent operations leave no Audit.
- AuditWriter failure rolls back the Asset mutation; Asset persistence failure leaves no Audit.

## Frontend and BFF

- Product Detail adds allowlisted `?tab=assets`; unknown tabs continue to fall back to Product Master.
- Assets Tab supports status and asset-type filters, stable pagination, create, edit, archive, restore, and safe external file links.
- Archived Product renders existing Assets read-only with a clear reason and disables mutation controls.
- 409／412／428 states show a reload action and do not overwrite the user's view silently.
- External `fileUrl` links use only the Backend-validated HTTP／HTTPS value, open safely, and include `rel="noopener noreferrer"` when a new browsing context is used.
- Next.js Route Handlers construct only the approved Asset paths. Browser input cannot select an upstream origin or arbitrary path.
- Asset collection query, methods, request headers, response headers, timeout, and 64 KiB body limit are separately allowlisted.
- BFF preserves Backend status/body and `Content-Type`, `ETag`, `Location`, `X-Request-ID`; it never forwards Cookie, Authorization, `X-Actor-ID`, hop-by-hop headers, or arbitrary query keys.

## Verification requirements

- Domain and request validation covers every writable field, boundary length／numeric cases, URL, checksum, root JSON type, 16 KiB boundary, and recursively forbidden metadata keys.
- Every endpoint proves happy path, ownership mismatch, missing／malformed／stale ETag, archived Asset／Product, invalid merge patch, filters, sort allowlist, size limit, and stable pagination.
- PostgreSQL integration proves V4 constraints, Product／Creative Plan／Campaign Product composite ownership, ACTIVE／ARCHIVED／ALL queries, immutable identity trigger, no cascade, and Hibernate validation.
- Create／Update／Archive／Restore Audit proves trusted actor, request ID, typed actual changes, continuous ordering, metadata fingerprint confidentiality, no-Audit paths, and transaction rollback.
- BFF proves fixed origin/path, collection-only queries, credential／actor/header isolation, payload limit, timeout, sanitized network failure, and status/body/header preservation.
- Assets Tab proves loading, empty, error, create, edit, archive, restore, filters, pagination, archived-Product read-only state, safe link attributes, and 409／412／428 recovery.
- Full Backend suite; Frontend lint／typecheck／tests／production build; `npm audit`; Compose config／cold start; Product, Knowledge, Creative Plan, Campaign, and Asset smoke; Gitleaks; actionlint; `git diff --check`.
- V1–V4 blobs and checksums remain unchanged. No Asset binary, Google API, credential, Provider call, Aggregate, Quality, Workflow, AI, Meta Ads, Dashboard, Decision Engine, or Stage 03 scope is introduced.

## Acceptance checklist

- [x] Asset API and Product Assets Tab conform to the approved metadata, validation, lifecycle, and concurrency contract.
- [x] Product, Creative Plan, Campaign, and Campaign Product ownership／active-state boundaries are proven in PostgreSQL.
- [x] Provider metadata recursively rejects sensitive keys, respects 16 KiB, remains absent from logs／Audit, and stores only a non-reversible Audit fingerprint.
- [x] Asset Audit is trusted, typed, actual-change-only, and transactionally rolls back with Domain mutation.
- [x] Asset identity is immutable; archive never hard-deletes or cascades.
- [x] Asset BFF preserves SSRF, query, header, credential, timeout, and body-size boundaries.
- [x] Product, Knowledge, Creative Plan, and Campaign regressions remain green; V1–V4 remain unchanged.
- [x] Local and Remote CI verification pass.
- [x] Independent Manager Decision is `APPROVE` before merge.
- [x] Post-merge `main` CI passes before 2C-6 begins.

## Mandatory escalation

Stop and escalate if implementation requires changing V1–V4, destructive data changes, binary storage or remote file fetching, provider credentials or Google APIs, weakening metadata redaction or BFF boundaries, adding authentication／RBAC／Tenant, changing PostgreSQL as System of Record, breaking an approved API contract, or extending scope beyond Asset Metadata.

## Developer delivery record

- Manager remediation verification: Backend 141 tests passed; Frontend lint, typecheck, 96 tests, production build, and production dependency audit passed.
- Lifecycle mutations now send the version displayed to the user; 409/412/428 recovery clears stale selection, form state, and ETag before reloading.
- Product, Creative Plan, Campaign, and Campaign Product active-state validation uses transaction-scoped pessimistic locks; PostgreSQL lock contention evidence passed.

- Backend：133 tests passed, including PostgreSQL ownership, lifecycle, immutable identity, Audit confidentiality, and rollback evidence.
- Frontend：lint, typecheck, 89 tests, production build, and production dependency audit passed.
- Docker Compose：pinned images built; PostgreSQL, Backend, and Frontend became healthy; Backend and same-origin BFF Asset smoke passed.
- Security：Gitleaks history and working-directory scans found no leaks. V1–V4 were not modified.
- Cross-platform test fix：the existing Backend health component assertion retains its behavior and uses a narrowly scoped five-second `waitFor` timeout for constrained container scheduling.
- Local limitation：`actionlint` was not installed locally and no workflow changed; Remote CI remains responsible for the required actionlint evidence.
- Implementation, Remote CI, Manager Review, approval-document CI, Merge, and post-merge Main CI：Passed.

## Manager review record

- Review date：2026-08-07
- Reviewer：Codex Project Manager and Stage Gate Owner
- Base／Head reviewed：`c051d26c45e3d9769bce2cdfb4cb05502f1d7d18` → `b9b17b82d217028068ce6113bf311231f0464716`
- Scope／files reviewed：Asset domain, persistence, command/query transactions, REST and merge-patch contracts, error mapping, metadata security, Audit changes, PostgreSQL ownership／lock／rollback evidence, fixed-path BFF, Product Assets Tab, and Backend／Frontend tests.
- Migration reviewed：V1–V4 unchanged; no V5, destructive schema operation, dependency, Runtime, Docker, CI workflow, credential, or permission change.
- Local verification：`git status --short`; `git diff --check origin/main...HEAD`; `git log --oneline --decorate -10`; `.\mvnw.cmd test` (141 passed); `npm run lint`; `npm run typecheck`; `npm test` (14 files／96 passed); `npm run build`; `npm audit --omit=dev` (0 vulnerabilities); `docker compose config --quiet`; `docker compose up --build -d --wait`; Backend and same-origin BFF Asset create／list／archive／restore smoke; pinned Gitleaks history and working-tree scans.
- Remote verification：Push Run `31177169477` and PR Run `31177191719`; actionlint, Backend Testcontainers, Frontend verification, Compose validation／healthy start／smoke, and Gitleaks all executed and Passed with no required step skipped.
- Findings：Initial `REQUEST_CHANGES` covered lifecycle optimistic-concurrency bypass, stale recovery state, active-reference race protection, and missing required acceptance tests. Commit `b9b17b82d217028068ce6113bf311231f0464716` resolved all findings. No open `CRITICAL`, `BLOCKING`, or required `MAJOR` finding remains.
- Contract impact：Additive Asset Metadata API／UI only; independent ETag／If-Match, archive-only lifecycle, fixed paths, and existing Product／Knowledge／Creative Plan／Campaign contracts remain compatible.
- Security impact：Provider metadata rejects recursively sensitive keys, Audit stores only SHA-256 fingerprints, and the BFF exposes no user-selected origin／path, credentials, actor, or arbitrary headers.
- Data impact：Only additive use of the approved V4 `assets` table; PostgreSQL remains the System of Record, identity is immutable, lifecycle is archive-only, and no binary or external Provider data operation occurs.
- Known limitations：Playwright remains assigned to 2C-7. Byte Buddy dynamic-agent, Maven Surefire fork shutdown-after-success, GitHub Actions Node.js compatibility, and Windows LF／CRLF warnings remain non-blocking technical debt. Local actionlint was unavailable; both Remote CI Runs supplied the required passing actionlint evidence.
- Decision：`APPROVE`
- Required next action：Merge this Markdown-only finalization record, verify `main`, then begin Milestone 2C-6 from the resulting clean base.
- Human approval required：No
