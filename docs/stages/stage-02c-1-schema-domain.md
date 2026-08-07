# Milestone 2C-1 — Schema and Domain Foundation

## Gate status

- Status：Manager approved；pending merge
- Branch：`codex/2c-1-schema-domain`
- Base Commit：`5cb6131594eddc029d48880a0174bed573e33837`
- Implementation：Complete
- Local Verification：Passed
- Remote CI：Passed — Push Run `31141729630`；PR Run `31141749841`
- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Implementation Head：`6756d10b92c8509ad702f4b51ae015f1ef275c6a`
- Human Review Required：No — the additive V4 model and audit constraint expansion were approved in the parent Milestone 2C specification
- Merge：Pending
- Dependent Stage：2C-2 and 2C-3 remain locked until merge and post-merge verification

## Developer verification evidence

- `backend\\mvnw.cmd test`：Passed — 74 tests，0 failures，0 errors，0 skipped。
- `backend\\mvnw.cmd -Dtest=Milestone2CSchemaIntegrationTest test`：Passed — 8 tests；補強每張 V4 table 的 lifecycle enum／archive consistency direct JDBC coverage 後重跑通過。
- `backend\\mvnw.cmd -Dtest=PersistenceFoundationIntegrationTest test`：Passed — 17 tests；確認新增 Audit value types 經 JPA writer 寫入。
- Flyway cold migration：Passed — 空庫依序套用 V1、V2、V3、V4；repeat migration 無 pending work。
- Populated 2B upgrade：Passed — ACTIVE／ARCHIVED Product、Audit row 與既有 version 保持不變。
- Migration atomicity：Passed — 隔離 schema 中故意造成 V4 衝突後，PostgreSQL 回滾所有部分 V4 objects。
- Hibernate `ddl-auto=validate`：Passed。
- Direct PostgreSQL constraints／FK／unique／immutable trigger tests：Passed。
- Archive-only repository contract：Passed — five 2C repositories expose save／saveAndFlush／findById without any hard-delete method; Spring context and lifecycle persistence remain valid。
- Docker Compose config：Passed。
- Isolated Docker Compose cold start：Passed — PostgreSQL、Backend、Frontend healthy；使用 project `aimcp2c1`，驗證後已移除其 containers、network 與 volume。
- Frontend pinned-container regression：Passed — lint、typecheck、12 tests、production build。
- Health and Product 2B smoke：Passed — Backend health `UP`、same-origin health `UP`、Product create／get 與 `ETag: W/\"0\"`。
- `npm audit --omit=dev`：Passed — 0 vulnerabilities。
- Gitleaks history and working-tree scans：Passed — no leaks found。
- actionlint 1.7.7：Passed。
- `git diff --check`：Passed。

Known non-blocking warnings：Mockito／Byte Buddy dynamic Java agent future deprecation remains present. The host Node.js 24.14.0／npm 11.9.0 does not match the repository pin, so Frontend verification used the pinned Node.js 24.18.0／npm 11.16.0 Docker build instead of weakening the engine requirement.

## Manager review evidence

- Initial review of `cf676ec844c657dbf21074e76b984617eb9a06ce` returned `REQUEST_CHANGES` because the five repositories exposed hard-delete APIs and `archive(null)` could partially mutate lifecycle state.
- Fix Commit `6756d10b92c8509ad702f4b51ae015f1ef275c6a` introduced an archive-only repository contract and made archive validation atomic, with regression tests.
- Independent migration review found no CRITICAL, BLOCKING, or MAJOR finding; V1–V3 are unchanged and V4 matches the approved additive five-table contract.
- Independent domain review confirmed the two findings are closed; enum mapping, ETag, patch presence, JPA mappings, scope boundaries, and archive-only persistence contract conform to the approved specification.
- Required Remote CI jobs ran without skipped verification steps. The existing GitHub Actions Node.js compatibility annotation remains non-blocking technical debt.

## Objective

Establish the persistence and domain foundation shared by every Milestone 2C vertical slice. This delivery creates the approved additive V4 schema, persistence entities, enums, repositories, and reusable lifecycle／merge-patch／resource-ETag primitives without exposing new REST endpoints or Frontend behavior.

The authoritative field definitions, constraints, indexes, ownership rules, audit types, rollback strategy, and architecture boundaries remain those in [Milestone 2C — Knowledge, Plans, Campaigns and Assets](stage-02c-knowledge-plans-campaigns-assets.md).

## Included

- New additive Flyway migration `V4__create_knowledge_plans_campaigns_assets.sql`.
- Tables `product_knowledge`, `creative_plans`, `campaign_plans`, `campaign_products`, and `assets`.
- Approved foreign keys, composite ownership keys, check constraints, indexes, lifecycle columns, timestamps, optimistic-lock versions, and immutable identity triggers.
- Expansion of `audit_log_changes.value_type` to retain `STRING`, `UUID`, `ENUM`, `TIMESTAMP` and add `DECIMAL`, `INTEGER`, `DATE`.
- JPA entities, enums, repositories, and explicit persistence mappings for the five resources.
- Reusable domain primitives for ACTIVE／ARCHIVED lifecycle transitions.
- Reusable field-presence-safe merge-patch primitives; no resource-specific HTTP parser or controller.
- Reusable weak resource ETag parsing／formatting based only on the resource `version`.
- V3 canonical checksum protection while preserving V1 and V2 canonical checksums.
- PostgreSQL Testcontainers migration, constraint, trigger, compatibility, Hibernate validation, and transaction tests.
- Stage documentation and verification evidence.

## Explicitly out of scope

- Product Knowledge, Creative Plan, Campaign, Campaign Product, Asset, or Aggregate controllers and application command/query services.
- Any new public REST endpoint or change to the existing Product API contract.
- Next.js BFF routes, Product Tabs, Campaign pages, or other Frontend behavior.
- Resource-specific audit event creation; only audit value-type support required by later slices is included.
- Google Drive, Google Sheets, `StorageProvider`, upload/download, remote URL fetch, credentials, or provider calls.
- Quality Score, Workflow, AI, Meta Ads, Dashboard, Decision Engine, or Stage 03+ work.
- Authentication, RBAC, Tenant, production actor-provider, workflow-permission, or branch-protection changes.
- Playwright; it remains required in 2C-7.

## Migration rules

1. V1, V2, and V3 are immutable and must not change byte-for-byte except that checksum tests normalize CRLF to LF before hashing.
2. V4 is additive and executes transactionally. It must not drop tables, columns, data, or existing indexes.
3. The already-approved `audit_log_changes.value_type` constraint expansion may replace only that check constraint while retaining every existing allowed value and row.
4. Every foreign key uses `ON DELETE RESTRICT`.
5. Every resource identity UUID and immutable owner identity is protected against direct SQL UPDATE by PostgreSQL trigger, not only by JPA configuration.
6. Lifecycle consistency requires ACTIVE with `archived_at IS NULL` and ARCHIVED with non-null `archived_at`.
7. Application rollback to the 2B runtime remains possible because V4 does not modify Product columns or existing Product endpoints.
8. After V4 is merged, it becomes immutable; corrections require V5 or later.

## Domain and persistence boundaries

- Domain objects expose controlled lifecycle transitions and never accept HTTP DTOs.
- Persistence entities must not be returned from a Web layer.
- Repositories provide only the persistence operations required by later command/query services; this stage does not invent workflow behavior.
- Ownership identifiers (`product_uuid`, `campaign_uuid`, and immutable creative-plan ownership) cannot be reassigned after creation.
- Resource ETags use the strict weak format `W/"<non-negative-version>"`; malformed, strong, wildcard, negative, list, or padded variants are rejected by the shared parser.
- Merge-patch presence distinguishes an absent field from an explicit `null`; allowlists and field validation remain resource-specific responsibilities of later slices.
- Shared lifecycle no-op semantics do not increment version. Audit creation is deferred to transactional command services in later slices.

## Required database verification

- Empty PostgreSQL executes V1 → V2 → V3 → V4 and Hibernate validation succeeds.
- A populated 2B database containing active／archived Products and audit rows upgrades to V4 without changing existing data or versions.
- A repeat migration has no pending versions.
- Canonical SHA-256 checksums protect V1, V2, and V3 with CRLF→LF normalization only.
- `spring.flyway.clean-disabled=true` remains effective.
- Direct JDBC tests cover every enum-like check, lifecycle check, date rule, budget rule, non-negative value, checksum format, JSON object rule, and 16 KiB metadata boundary that is enforceable in PostgreSQL.
- Direct JDBC tests cover all foreign keys, the unique Campaign／Product pair, Creative Plan ownership, and Campaign Product ownership.
- Direct JDBC UPDATE attempts against every immutable UUID／owner identity are rejected.
- Existing Product and Audit append-only trigger protections continue to pass regression tests.
- Migration failure is atomic and does not leave a partial V4 schema.

## Required unit and mapping verification

- Each enum maps by string, never ordinal.
- Each entity maps table, columns, lengths, precision, timestamps, lifecycle, and `@Version` correctly.
- Repository context loads under Hibernate `ddl-auto=validate`.
- Shared lifecycle transitions cover archive, restore, and no-op behavior.
- Shared merge-patch presence covers absent, explicit null, and present value.
- Shared ETag tests cover format, valid parse, and rejection of malformed inputs.
- Audit value types map and persist all seven allowed string values.

## Regression verification

- Backend full test suite.
- Frontend lint, typecheck, tests, and production build even though Frontend is unchanged.
- `docker compose config`.
- Docker Compose cold start with PostgreSQL, Backend, and Frontend healthy.
- Existing health and Product vertical-slice smoke tests.
- Gitleaks history and working-tree scan.
- `npm audit --omit=dev`.
- actionlint.
- `git diff --check` and clean worktree after commit.

## Acceptance checklist

- [x] V1–V3 files are unchanged and their canonical checksums pass.
- [x] V4 creates exactly the five approved tables and audit value-type expansion.
- [x] No 2C table or field outside the approved parent specification is introduced.
- [x] Empty and populated 2B databases upgrade safely; repeat migration has no pending work.
- [x] Hibernate schema validation succeeds.
- [x] Every database check, FK, unique rule, lifecycle rule, and immutable identity trigger has direct PostgreSQL coverage.
- [x] Existing Product identity and Audit append-only protections regress successfully.
- [x] Entities, enums, repositories, lifecycle, patch presence, and ETag primitives have automated tests.
- [x] No Controller, public API, BFF, Frontend, external Provider, Quality, Workflow, or later-stage behavior is implemented.
- [x] Backend, Frontend, Compose, smoke, Gitleaks, dependency audit, and actionlint pass locally and remotely.
- [x] Draft PR contains the implementation report and exact migration／test evidence.
- [x] Independent Manager Review returns `APPROVE` before merge.
- [ ] Post-merge main CI passes before 2C-2 or 2C-3 starts.

## Mandatory escalation conditions

Stop and return `ESCALATE_TO_HUMAN` if implementation requires modifying V1–V3, destructive DDL or data changes, a model outside the approved five-table contract, a breaking Product API change, authentication／RBAC／Tenant changes, production credentials, or lowered CI／migration standards.
