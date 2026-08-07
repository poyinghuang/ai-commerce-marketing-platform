# Milestone 2D — Quality and Workflow

## Gate status

- Status：2D-1, 2D-2, and 2D-3 completed; 2D-4 not started
- Branch：`codex/2d-3-finalize`
- Base Commit：`6ba92bdc48a50f61448ee347b89939f961bdb5e4`
- Specification Merge：Passed — `6ba92bdc48a50f61448ee347b89939f961bdb5e4`
- Specification Post-merge CI：Passed — Run `31198925437`
- Implementation：2D-1 and 2D-2 completed; 2D-3 complete locally; 2D-4 not started
- Migration：V5 committed in `558262f1474618e58a4d7b8cce76d838dc46822a`
- Local Verification：2D-1 Passed
- Remote CI：2D-1 Passed — Push Run `31202242425`; PR Run `31202259584`; 2D-2 Passed — Push Run `31207893627`; PR Run `31207911328`; 2D-3 Passed — Push Run `31213276000`; PR Run `31213280058`
- Manager Review：2D-1 Passed; 2D-2 Passed; 2D-3 Passed
- Manager Decision：2D-1 APPROVE; 2D-2 APPROVE at `f3cdb3386584fe182ea8c3f2dabc3ffdb07ac44f`; 2D-3 APPROVE at `66cd92a48a10b16acb49d10bf183563d208a1d1d`
- Approved Specification Commit：`fd1813653113cef26361c2aa815f2e303f5c6bc2`
- Human Review Required：No
- 2D-1 Merge：Passed — PR #23, `c897bb6f6f3847e62fea9b6d334400349c87e3b0`
- 2D-1 Post-merge CI：Passed — main Run `31203178454`
- 2D-1 Finalization Merge：Passed — `3b047d99eb99fddfd03ea0861ab32bff146a8267`; main Run `31204042828` passed
- 2D-2 Merge：Passed — PR #25, `84d7131524e734de0354dc1468bdd1e001aa6509`
- 2D-2 Post-merge CI：Passed — main Run `31208990817`
- 2D-2 delivery record：[Milestone 2D-2 — Recalculation, API, Audit, and Aggregate](stage-02d-2-recalculation-api.md)
- 2D-3 Merge：Passed — PR #27, `4f4aace65a737305f15bf3c9b34633dfc66366ba`
- 2D-3 Corrective Merge：Passed — PR #28, `131274be0fff0230ce9cdd7ef1ca53d1d09cbcb7`
- 2D-3 Final Post-merge CI：Passed — main Run `31215080850`
- 2D-3 delivery record：[Milestone 2D-3 — Quality UI](stage-02d-3-quality-ui.md)
- Milestone 2E：Not started

## Objective

Add a deterministic, explainable Product quality projection and readiness workflow on top of the completed Product Center. PostgreSQL remains the only System of Record. Stage 2D does not call an LLM and does not permit a manual adjustment to hide blocking reasons.

## Included

- Additive V5 Flyway migration for `quality_scores`, `quality_score_blockers`, and `workflow_status`.
- Deterministic five-component score with an inspectable breakdown.
- Automatic recalculation after relevant Product, Knowledge, Creative Plan, Campaign Product／Campaign, and Asset mutations.
- Manual adjustment constrained to `-20..20`, with a mandatory reason and trusted actor for non-zero values.
- Derived readiness state: `DRAFT`, `NEEDS_REVIEW`, or `READY`.
- Quality and workflow REST API, same-origin BFF, and Product Quality UI.
- Transactional Audit for manual adjustment and effective automatic projection changes.
- Migration, Backend, Frontend, Compose, Playwright, security, and regression tests.

## Explicitly out of scope

- AI suggested score calculation; the nullable field is reserved but is never written by Stage 2D.
- Manual removal, suppression, acknowledgement, or override of blockers.
- User-defined score weights or rules.
- Approval roles, authentication, RBAC, Tenant, assignee, queue, retry engine, Temporal, or n8n.
- Google Sheets, Google Drive, `StorageProvider`, file upload, credentials, or external calls.
- Asset binary review, campaign execution, Meta Ads, Dashboard, Decision Engine, or Stage 03+ behavior.

## V5 migration contract

Migration：`V5__create_quality_and_workflow.sql`. V1–V4 are immutable.

### `quality_scores`

- `quality_score_uuid UUID PRIMARY KEY`
- `product_uuid UUID NOT NULL UNIQUE REFERENCES products(product_uuid) ON DELETE RESTRICT`
- `product_master_score INTEGER NOT NULL CHECK (0..35)`
- `product_knowledge_score INTEGER NOT NULL CHECK (0..25)`
- `creative_plan_score INTEGER NOT NULL CHECK (0..25)`
- `asset_metadata_score INTEGER NOT NULL CHECK (0..10)`
- `campaign_readiness_score INTEGER NOT NULL CHECK (0..5)`
- `system_score INTEGER NOT NULL CHECK (0..100)`
- `ai_suggested_score INTEGER NULL CHECK (0..100)`; Stage 2D must leave it null
- `manual_adjustment INTEGER NOT NULL DEFAULT 0 CHECK (-20..20)`
- `manual_adjustment_reason VARCHAR(1000) NULL`
- `manual_adjusted_by VARCHAR(128) NULL`
- `manual_adjusted_at TIMESTAMP WITH TIME ZONE NULL`
- `final_score INTEGER NOT NULL CHECK (0..100)`
- `calculated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `created_at`, `updated_at`, and `version BIGINT NOT NULL DEFAULT 0`

Database consistency constraints require non-zero adjustment to have nonblank reason, actor, and time; zero adjustment requires all three metadata fields null. PostgreSQL triggers prevent reassignment of `quality_score_uuid` or `product_uuid`.

### `quality_score_blockers`

- `quality_score_blocker_uuid UUID PRIMARY KEY`
- `quality_score_uuid UUID NOT NULL REFERENCES quality_scores(quality_score_uuid) ON DELETE RESTRICT`
- `blocker_code VARCHAR(64) NOT NULL`
- `field_path VARCHAR(256) NULL`
- `message VARCHAR(512) NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `UNIQUE (quality_score_uuid, blocker_code)`

Allowed blocker codes are string-constrained: `PRODUCT_ARCHIVED`, `PRODUCT_NAME_MISSING`, `SALE_PRICE_MISSING`, `CURRENCY_MISSING`, `KNOWLEDGE_MISSING`, `CREATIVE_PLAN_MISSING`, `IMAGE_ASSET_MISSING`. Blockers are system projections: no public CRUD API and no manual override.

### `workflow_status`

- `workflow_status_uuid UUID PRIMARY KEY`
- `product_uuid UUID NOT NULL UNIQUE REFERENCES products(product_uuid) ON DELETE RESTRICT`
- `stage VARCHAR(32) NOT NULL CHECK (stage = 'PRODUCT_READINESS')`
- `status VARCHAR(32) NOT NULL CHECK (DRAFT, NEEDS_REVIEW, READY)`
- `status_reason VARCHAR(512) NOT NULL`
- `evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `created_at`, `updated_at`, and `version BIGINT NOT NULL DEFAULT 0`

Triggers prevent identity or Product reassignment. There is no public endpoint to force a workflow state.

## Deterministic score rules

The score is calculated from ACTIVE records only. Text is complete only after trim; archived Product data can still be inspected but produces `PRODUCT_ARCHIVED` and cannot be READY.

### Product Master — 35

- Product name: 8
- Brand: 4
- Category: 4
- Short description: 5
- Sale price plus currency: 5
- Cost plus currency: 3
- Stock: 3
- Safe `http`／`https` Product URL: 3

### Product Knowledge — 25

- At least one active entry: 5
- Active `FEATURE`: 5
- Active `BENEFIT`: 5
- Active `AUDIENCE`: 5
- Active `PAIN_POINT`, `FAQ`, or `PROOF`: 5

### Creative Plan — 25

Evaluate the most complete active plan; do not combine fields across plans.

- Active plan exists: 5
- Primary audience: 5
- Pain point and core benefit: 5
- Creative angle: 5
- Brand tone, visual style, and CTA: 5

### Asset Metadata — 10

- Active asset exists: 2
- Active IMAGE asset exists: 3
- At least one active asset has either `fileUrl` or both `storageProvider` and `providerFileId`: 3
- At least one active asset has `mediaType` and `originalFilename`: 2

### Campaign Readiness — 5

Evaluate active associations to active Campaigns only.

- Active association exists: 2
- Associated Campaign has objective: 1
- Associated Campaign has landing page: 1
- Associated Campaign has a valid currency-backed budget: 1

`system_score` is the sum of the five components. `final_score = clamp(system_score + manual_adjustment, 0, 100)`. Manual adjustment does not change components or blockers.

## Workflow state machine

- `DRAFT`: final score 0–69, or Product is archived.
- `NEEDS_REVIEW`: final score 70–89, or final score 90–100 with one or more blockers.
- `READY`: final score 90–100 and no blockers.

Recalculation is idempotent. If component scores, system score, final score, blockers, and workflow state are unchanged, it updates neither version nor timestamps and writes no Audit event.

## Recalculation and transaction boundary

- Create a `ProductQualityRecalculationService` and a read-only `ProductQualityQueryService`.
- Product creation initializes the projection in the same transaction.
- Relevant successful mutations recalculate after domain validation and before transaction commit.
- A Product mutation and all resulting quality／workflow projection changes commit or roll back together.
- HTTP-triggered recalculation retains the operation request ID and trusted actor provenance; non-HTTP repair/bootstrap flow uses explicit `SYSTEM` actor and a server-generated request ID.
- Recalculation locks the per-Product quality row before updating; the public resource ETag is derived from `quality_scores.version`.
- The additive migration backfills one score and workflow row per existing Product using deterministic SQL defaults, after which application startup repair recalculates actual values. No existing Product or 2C row is modified.

## REST API

- `GET /api/products/{productUuid}/quality`
- `PATCH /api/products/{productUuid}/quality/manual-adjustment`

GET returns component scores, system score, null `aiSuggestedScore`, manual adjustment metadata, final score, blockers, readiness status, calculation time, and version with `ETag: W/"<version>"`.

Manual adjustment uses `application/merge-patch+json`, requires `If-Match`, and accepts only `manualAdjustment` and `reason`. Range is `-20..20`. Non-zero requires a trimmed reason; setting zero clears reason／actor／time. Empty or semantic no-op Patch does not increment version or create Audit. Missing, malformed, and stale preconditions return 428, 400, and 412 respectively. Archived Product adjustment returns 409.

Quality is included additively in the Product Aggregate as `quality`; existing Product and 2C member contracts remain unchanged.

## Audit

- Entity types: `QUALITY_SCORE`, `WORKFLOW_STATUS`.
- Manual adjustment records actual changes to adjustment, reason, actor, final score, and readiness status using the trusted HTTP actor.
- Automatic effective score／blocker／status changes use the same operation context and request ID; bootstrap／repair uses `SYSTEM`.
- Blocker details are represented as a redacted, bounded stable summary, never arbitrary request content.
- Failure, stale precondition, blocked archived mutation, and no-op recalculation create no Audit.

## Frontend and BFF

- Add `?tab=quality` to `/products/[productUuid]`.
- Render component breakdown, system score, adjustment, final score, readiness status, and every blocking reason.
- Provide trusted same-origin adjustment form with current ETag, reason validation, reset-to-zero, loading／empty／error, 409 archived, 412 reload, and 428 recovery states.
- Extend only fixed Product quality BFF routes; preserve server-only Backend origin, route/header allowlists, body limit, timeout, request-ID sanitization, and no Cookie／Authorization forwarding.

## Delivery slices

1. **2D-1 Schema and scoring domain** — V5, entities, repositories, rule engine, migration／constraint tests.
2. **2D-2 Recalculation and API** — transactional hooks, query and adjustment API, Audit, Aggregate extension.
3. **2D-3 Quality UI** — BFF routes, Product Quality tab, conflict and blocker UX.
4. **2D-4 E2E and acceptance** — real Compose flows, Manager Gate, merge, post-merge CI, and `milestone-2d-complete` tag.

## Verification and acceptance

- [ ] V1–V4 blobs unchanged; empty and populated V4 databases upgrade to V5; repeat migration and Hibernate validation pass.
- [ ] Direct JDBC constraints and immutable trigger tests pass.
- [ ] All five component totals, boundaries, clamp behavior, blockers, and state transitions have table-driven tests.
- [ ] Multiple records, archived records, cross-Product relations, and campaign lifecycle are calculated correctly.
- [ ] Manual adjustment enforces range, reason, trusted actor, ETag, idempotency, and blocker invariance.
- [ ] Relevant mutations recalculate in the same transaction; rollback, stale, blocked, and no-op behavior leave no partial projection or Audit.
- [ ] Existing Products receive deterministic projections without changing Product／2C data.
- [ ] API, Aggregate, BFF, and UI contracts pass unit and integration tests.
- [ ] Playwright covers score progression, blocker preventing READY, adjustment clamp／reason, stale adjustment conflict, archive／restore, and persisted reload.
- [ ] Backend, Frontend, Testcontainers, Compose, smoke, Playwright, npm audit, Gitleaks, and actionlint pass locally and remotely.
- [ ] No AI score calculation, manual blocker override, Google connector, external workflow engine, RBAC, Ads, Dashboard, Decision Engine, or Stage 03 scope is introduced.
- [ ] Each slice receives exact-head Manager `APPROVE`, merge, and post-merge main verification before its dependent slice starts.

## Rollback and recovery

- V5 is transactional and additive. Application rollback to the 2C Runtime leaves the new tables unused.
- V5 is immutable after merge; corrections use V6+ forward migration.
- Projection rows are reproducible from System-of-Record data plus persisted manual adjustment. A SYSTEM repair command may recalculate them without deleting source data.
- Dropping tables, resetting manual adjustments, or mass production recalculation requires separate human approval.
