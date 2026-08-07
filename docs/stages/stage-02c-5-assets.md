# Milestone 2C-5 — Asset Metadata Vertical Slice

## Gate status

- Status：Approved for implementation
- Branch：`codex/2c-5-assets`
- Base Commit：`c051d26c45e3d9769bce2cdfb4cb05502f1d7d18`
- Implementation：Not started
- Local Verification：Not started
- Remote CI：Not started
- Manager Review：Not started
- Manager Decision：Pending
- Human Review Required：No
- Merge：Not started
- Milestone 2C-6：Locked

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

- [ ] Asset API and Product Assets Tab conform to the approved metadata, validation, lifecycle, and concurrency contract.
- [ ] Product, Creative Plan, Campaign, and Campaign Product ownership／active-state boundaries are proven in PostgreSQL.
- [ ] Provider metadata recursively rejects sensitive keys, respects 16 KiB, remains absent from logs／Audit, and stores only a non-reversible Audit fingerprint.
- [ ] Asset Audit is trusted, typed, actual-change-only, and transactionally rolls back with Domain mutation.
- [ ] Asset identity is immutable; archive never hard-deletes or cascades.
- [ ] Asset BFF preserves SSRF, query, header, credential, timeout, and body-size boundaries.
- [ ] Product, Knowledge, Creative Plan, and Campaign regressions remain green; V1–V4 remain unchanged.
- [ ] Local and Remote CI verification pass.
- [ ] Independent Manager Decision is `APPROVE` before merge.
- [ ] Post-merge `main` CI passes before 2C-6 begins.

## Mandatory escalation

Stop and escalate if implementation requires changing V1–V4, destructive data changes, binary storage or remote file fetching, provider credentials or Google APIs, weakening metadata redaction or BFF boundaries, adding authentication／RBAC／Tenant, changing PostgreSQL as System of Record, breaking an approved API contract, or extending scope beyond Asset Metadata.
