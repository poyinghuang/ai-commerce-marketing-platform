# Milestone 2C-6 — Product Aggregate and Integration

## Gate status

- Status：Manager approved, pending merge
- Branch：`codex/2c-6-aggregate-integration`
- Base Commit：`cf738e9284ab7bb4a66a19610cad528ebc6c1948`
- Prerequisite 2C-5 Main CI：Run `31178905522` Passed
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Passed
- Manager Review：Passed
- Manager Decision：APPROVE
- Human Review Required：No
- Approved Commit：`a7a5b971f89e7401e1c01a02d2286ad887ce0282`
- Push CI：Run `31185455853` Passed
- Pull Request：`#16`
- Pull Request CI：Run `31185475601` Passed
- Merge：Pending
- Milestone 2C-7：Locked

## Objective

Deliver the approved read-only Product Aggregate contract and prove that Product Master, Knowledge, Creative Plans, Campaign associations, and Asset Metadata integrate as one deterministic view without changing their independent mutation contracts. PostgreSQL remains the only System of Record.

## Included

- `GET /api/products/{productUuid}/aggregate?includeArchived=false`.
- A read-only `ProductAggregateQueryService` and immutable application-layer view records; JPA entities do not cross into the Web layer.
- A deterministic Product Aggregate response containing `product`, `knowledge`, `creativePlans`, `campaigns`, and `assets`.
- Campaign members include the Product's non-null Campaign Product association metadata.
- Constant-query integration with explicit N+1 regression evidence.
- Fixed-path same-origin Next.js Aggregate Route Handler.
- A read-only Product Detail aggregate summary with loading, error, retry, empty, populated, and include-archived states.
- Full Backend／Frontend regression, Compose integration smoke, security checks, and Stage documentation.

## Explicitly out of scope

- Any Flyway migration or modification to V1–V4.
- Any Create／Patch／Archive／Restore endpoint or change to existing mutation, ETag, Audit, lifecycle, or error contracts.
- Aggregate mutation ETag, snapshot persistence, denormalized aggregate table, cache, search index, materialized view, or background refresh.
- Pagination, user-selectable include expressions, arbitrary relationship expansion, GraphQL, bulk export, or response field selection.
- Playwright or Browser runtime changes; those remain mandatory in 2C-7.
- Quality, Workflow, Google Sheets, Google Drive, binary storage, StorageProvider calls, AI, Meta Ads, Dashboard, Decision Engine, Authentication, RBAC, Tenant, credentials, production deployment, or Stage 03 functionality.

## Aggregate REST contract

Endpoint：

`GET /api/products/{productUuid}/aggregate?includeArchived=false`

Response fields：

- `product`：the existing Product response shape, including its independent `version` and lifecycle fields.
- `knowledge[]`：existing Knowledge response members.
- `creativePlans[]`：existing Creative Plan response members.
- `campaigns[]`：existing Campaign response members with a non-null `association` for the path Product.
- `assets[]`：existing Asset response members, including provider-neutral metadata.

Rules：

- The Product is returned whether ACTIVE or ARCHIVED; missing Product returns the existing `404 PRODUCT_NOT_FOUND` contract.
- Omitted `includeArchived` is exactly `false`.
- Only one `includeArchived` parameter is allowed and its value must be exactly `true` or `false`; invalid or duplicated values return `400 VALIDATION_ERROR` with an `includeArchived` field error.
- `includeArchived=false` returns ACTIVE Knowledge, Creative Plans, and Assets. Campaign members are returned only when both Campaign and the Product's association are ACTIVE.
- `includeArchived=true` returns ACTIVE and ARCHIVED child resources and Campaign associations, including archived Campaign or association members.
- Child lifecycle is never changed or inferred from the Product lifecycle. An archived Product remains readable with the same inclusion rules.
- Knowledge, Creative Plans, and Assets sort by `updatedAt DESC`, then their UUID `ASC`. Campaigns sort by Campaign `updatedAt DESC`, then `campaignUuid ASC`.
- Every member retains its own `version`; clients must use the target member's individual endpoint and ETag for mutation.
- Aggregate returns no `ETag` and is never accepted as a concurrency token.
- Response uses `Cache-Control: no-store`; errors retain the existing `ApiError` contract and never contain stack traces.

## Query and consistency design

- `ProductAggregateQueryService` runs in one read-only PostgreSQL `REPEATABLE_READ` transaction so all member queries observe one database snapshot.
- The implementation executes at most five SQL statements regardless of child cardinality：one Product query, one Knowledge query, one Creative Plan query, one Campaign／association join query, and one Asset query.
- Campaign and association data must be fetched by one bounded join/projection query; per-row Campaign or association lookups are forbidden.
- Repository queries apply lifecycle filters and deterministic ordering in PostgreSQL. The Web layer does not re-query or lazily traverse relationships.
- Hibernate statistics or equivalent test instrumentation must prove the SQL statement count remains constant when the number of members increases.
- Aggregate is intentionally unpaginated because the parent 2C contract defines complete arrays. Large-response pagination or caps require a future additive contract decision and are recorded as technical debt rather than silently truncating data.

## Application and Web boundaries

- Application code returns immutable aggregate view records containing scalar／value data only.
- Web response mapping may reuse the established field shapes but must not expose JPA entities or persistence proxies.
- Existing `ProductResponse`, `KnowledgeResponse`, `CreativePlanResponse`, `CampaignResponse`, `CampaignProductResponse`, and `AssetResponse` contracts remain backward compatible.
- Aggregate is read-only：it creates no `AuditOperationContext`, Audit Log, Domain mutation, version increment, or lifecycle transition.
- No Domain repository gains a hard-delete method or generic arbitrary-query API.

## Frontend and BFF

- Add only `/api/products/[productUuid]/aggregate` as a fixed Route Handler.
- Backend origin remains server-only `BACKEND_INTERNAL_URL`; Browser input cannot select origin or path.
- Only GET is exported. The only accepted query is one exact `includeArchived=true|false` value.
- BFF preserves Backend status, body, `Content-Type`, and `X-Request-ID`; it does not forward Cookie, Authorization, `X-Actor-ID`, hop-by-hop headers, or arbitrary query parameters.
- The Product Master tab renders a read-only Aggregate summary with counts and compact member labels for Knowledge, Creative Plans, Campaigns, and Assets.
- The summary supports loading, empty, populated, error／retry, and include-archived toggle states. It does not add mutation controls or replace the existing independent tabs.
- Archived Product remains able to load the summary; the existing Product mutation read-only rule remains unchanged.

## Verification requirements

### Backend

- Controller tests：default false, exact true／false, duplicate／invalid query, archived Product read, missing Product error, no Aggregate ETag, and `Cache-Control: no-store`.
- PostgreSQL integration：one active and one archived member for every collection; active／archived Campaign and association combinations; correct nested association metadata; deterministic ordering; archived Product readability.
- Query-performance test：at most five statements and constant statement count after increasing every collection's cardinality; no lazy-load query after service return.
- Transaction test：the service is read-only and `REPEATABLE_READ`; no Audit row or version changes are produced.
- Existing V1–V4 checksum, Flyway repeat migration, Hibernate validation, Product, Knowledge, Creative Plan, Campaign, Asset, Audit, lifecycle, and concurrency tests remain green.

### Frontend／BFF

- Fixed origin and path, GET-only route, exact boolean query, duplicate／arbitrary query rejection or stripping according to the approved boundary, and credential／actor isolation.
- Backend status／body plus approved response-header preservation; sanitized timeout／network failure.
- Aggregate summary loading, empty, populated member rendering, archived toggle, error／retry, and archived Product states.
- Existing Product Detail tabs and mutation UI regressions remain green.

### Compose／security

- Compose config and cold build／healthy start.
- Smoke creates Product, Knowledge, Creative Plan, Campaign association, and Asset Metadata, then proves Backend and same-origin BFF Aggregate contain all members.
- Smoke archives at least one child, proves default exclusion and `includeArchived=true` inclusion, and confirms an attacker-controlled target query is not forwarded.
- Full Backend tests; Frontend lint／typecheck／tests／production build; npm production audit; actionlint; pinned Gitleaks history and working-tree scans; `git diff --check`.

## Acceptance checklist

- [x] Aggregate response exactly matches the approved Product／Knowledge／Creative Plan／Campaign association／Asset shapes.
- [x] Default and include-archived lifecycle semantics are proven for every child type.
- [x] Archived Product remains readable and missing Product uses the existing error contract.
- [x] Each member retains independent lifecycle and version data; Aggregate has no mutation ETag.
- [x] Deterministic ordering and a constant maximum of five SQL statements are proven in PostgreSQL.
- [x] Aggregate executes in one read-only repeatable-read transaction and creates no Audit or version changes.
- [x] BFF remains fixed-origin／fixed-path, GET-only, exact-query bounded, and credential／actor safe.
- [x] Product Detail aggregate summary covers loading, empty, populated, archived, error／retry, and include-archived states.
- [x] V1–V4 and all existing Product／Knowledge／Creative Plan／Campaign／Asset contracts remain unchanged and green.
- [x] Local and Remote CI verification pass with no required step skipped.
- [x] Independent Manager Decision is `APPROVE` before merge.
- [ ] Post-merge `main` CI passes before 2C-7 begins.

## Known limitations

- The complete-array parent contract is unpaginated. This is acceptable for the current Product Center dataset but must be revisited before high-cardinality production use.
- The summary is read-only and does not replace individual tabs or their resource ETags.
- Playwright is intentionally deferred to the already approved 2C-7 Browser E2E gate.
- Existing Byte Buddy, Maven Surefire shutdown-after-success, GitHub Actions Node.js compatibility, and Windows LF／CRLF warnings remain non-blocking technical debt.

## Mandatory escalation

Stop and escalate if implementation requires modifying V1–V4, adding a migration, silently truncating Aggregate data, changing an existing response or mutation contract, introducing a cache／materialized view／external index, weakening transaction consistency or BFF boundaries, adding credentials／authentication／RBAC／Tenant, or extending scope beyond read-only Aggregate and integration verification.

## Developer delivery record

- Backend：150 tests across 36 test classes passed with zero failure, error, or skip. The focused PostgreSQL suite proves deterministic lifecycle ordering, nested Campaign association metadata, immutable application views, archived Product readability, no Audit／version side effects, and exactly five prepared SQL statements before and after increasing every collection's cardinality.
- Transaction boundary：`ProductAggregateQueryService#get` is read-only with `REPEATABLE_READ`; repository queries apply lifecycle filters and ordering in PostgreSQL, and Campaign／association uses one bounded join query.
- Frontend：lint, typecheck, 16 test files／107 tests, production build, and production dependency audit passed. Aggregate BFF tests additionally prove fixed path／origin, GET-only export, exact boolean query, credential／actor isolation, sanitized upstream failure, approved response headers, and suppression of any upstream Aggregate ETag.
- Docker Compose：config validation and cold pinned-image build passed; PostgreSQL, Backend, and Frontend became healthy. Backend and same-origin BFF smoke returned Product, Knowledge, Creative Plan, Campaign association, and Asset; default excluded an archived child, `includeArchived=true` included it, both responses were `no-store` without ETag, and attacker-controlled `target` query was rejected with 400.
- Security：pinned Gitleaks history scan (35 commits) and working-directory scan found no leaks. V1–V4, dependency manifests, Runtime, Docker, CI workflow, credentials, and permissions were not changed.
- Local actionlint：not installed; no workflow file changed. Required actionlint evidence remains Pending Remote CI.
- Initial verification findings：the first PostgreSQL focused run exposed an ambiguous `Instant` type only in test seed SQL and was corrected with explicit `Timestamp`; an ordering-test expectation was corrected from Java signed-`long` UUID natural order to PostgreSQL canonical UUID order. One multi-file Vitest focused run had a worker-start timeout, and one parallel full run timed out an existing Knowledge test under combined Docker／JVM load; the affected tests passed independently and the final non-parallel full Frontend suite passed 107／107.
- Delivery state：Implementation, local verification, Commit, Push, Draft PR, Remote CI, and independent Manager Review are complete. Merge remains Pending.

## Manager review record

- Stage：Milestone 2C-6 — Product Aggregate and Integration
- Branch：`codex/2c-6-aggregate-integration`
- Base Commit：`cf738e9284ab7bb4a66a19610cad528ebc6c1948`
- Head Commit：`a7a5b971f89e7401e1c01a02d2286ad887ce0282`
- Pull Request：`#16`
- Scope reviewed：Read-only aggregate Backend service and API, bounded persistence queries, same-origin BFF, Product Detail aggregate summary, tests, smoke coverage, and Stage documentation.
- Files reviewed：All 20 changed files in `origin/main...HEAD`; no unapproved Runtime, dependency, Docker, CI workflow, or Flyway migration changes were present.
- Migration reviewed：V1 through V4 were unchanged; no V5 or other migration was introduced.
- Domain and transaction boundary：One read-only `REPEATABLE_READ` application transaction; immutable application view records; five bounded repository queries; no Audit event or entity version mutation.
- API and Frontend contract：Fixed GET aggregate endpoint, exact `includeArchived` validation, deterministic lifecycle and ordering semantics, no Aggregate ETag, `Cache-Control: no-store`, fixed-origin BFF, and read-only Product Detail summary.
- Tests executed：`git status --short`; `git diff --check origin/main...HEAD`; full Maven test suite (150/150); Frontend lint, typecheck, tests (107/107), production build, and production audit; Docker Compose config and cold build/healthy start; Backend and BFF aggregate smoke; pinned Gitleaks history and working-tree scans.
- Remote CI：Push Run `31185455853` and Pull Request Run `31185475601` passed `quality-and-compose` and `secret-scan`; actionlint, Backend Testcontainers, Frontend verification, Compose validation/start/smoke, and Gitleaks executed without required-step skips.
- Findings：No CRITICAL, BLOCKING, or required MAJOR findings. No unresolved code finding remains.
- Contract changes：One additive read-only Aggregate API and one fixed same-origin BFF route. Existing mutation, lifecycle, ETag, error, and resource contracts remain unchanged.
- Security impact：No credential, authentication, RBAC, Tenant, permission, or production-access changes. BFF origin/path/query/header boundaries were verified.
- Data impact：Read-only only. No migration, destructive SQL, persistence mutation, data loss, or System of Record change.
- Known limitations：Aggregate arrays are unpaginated; Playwright remains required in 2C-7; existing Byte Buddy, test-shutdown, Actions Node.js compatibility, and Windows line-ending warnings are non-blocking.
- Decision：`APPROVE`
- Human approval required：No
- Required next action：Commit and push this approval record, require both Push and Pull Request CI to pass again, then mark PR `#16` Ready, squash merge without starting 2C-7, and verify post-merge `main` CI.
