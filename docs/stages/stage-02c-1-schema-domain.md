# Milestone 2C-1 — Schema and Domain Foundation

## Gate status

- Status：Approved for implementation
- Branch：`codex/2c-1-schema-domain`
- Base Commit：`5cb6131594eddc029d48880a0174bed573e33837`
- Implementation：Not started
- Local Verification：Not started
- Remote CI：Not started
- Manager Review：Not started
- Manager Decision：Pending
- Human Review Required：No — the additive V4 model and audit constraint expansion were approved in the parent Milestone 2C specification
- Merge：Not started
- Dependent Stage：2C-2 and 2C-3 remain locked until merge and post-merge verification

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

- [ ] V1–V3 files are unchanged and their canonical checksums pass.
- [ ] V4 creates exactly the five approved tables and audit value-type expansion.
- [ ] No 2C table or field outside the approved parent specification is introduced.
- [ ] Empty and populated 2B databases upgrade safely; repeat migration has no pending work.
- [ ] Hibernate schema validation succeeds.
- [ ] Every database check, FK, unique rule, lifecycle rule, and immutable identity trigger has direct PostgreSQL coverage.
- [ ] Existing Product identity and Audit append-only protections regress successfully.
- [ ] Entities, enums, repositories, lifecycle, patch presence, and ETag primitives have automated tests.
- [ ] No Controller, public API, BFF, Frontend, external Provider, Quality, Workflow, or later-stage behavior is implemented.
- [ ] Backend, Frontend, Compose, smoke, Gitleaks, dependency audit, and actionlint pass locally and remotely.
- [ ] Draft PR contains the implementation report and exact migration／test evidence.
- [ ] Independent Manager Review returns `APPROVE` before merge.
- [ ] Post-merge main CI passes before 2C-2 or 2C-3 starts.

## Mandatory escalation conditions

Stop and return `ESCALATE_TO_HUMAN` if implementation requires modifying V1–V3, destructive DDL or data changes, a model outside the approved five-table contract, a breaking Product API change, authentication／RBAC／Tenant changes, production credentials, or lowered CI／migration standards.
