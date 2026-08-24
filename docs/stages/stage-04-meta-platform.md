# Stage 04 — Meta Ads Adapter

## Gate status

- Status: Stage 04 FAKE LOCAL/TEST closed on `main`; annotated tag `stage-04-complete`
- Branch: merged (4E runtime `codex/stage-04e-deterministic-acceptance-runtime`; 4E spec `codex/stage-04e-deterministic-acceptance-specification`)
- Base Commit: `031d6575a2d6102af5dd1574ca2c2d74799310f4` (PR #67 squash merge)
- Stage 03 prerequisite: Passed — tag `stage-03-complete` at `4eaddaff95c0d3ce9739c2ca2628908a41b69c31`
- Specification: Human-approved on 2026-08-15; independent Manager Review passed on 2026-08-16; 4E specification squash-merged as PR [#67](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/67)
- Implementation: 4A–4E merged
- Migration: V12–V15 additive on `main`; V1–V15 must remain byte-for-byte unchanged
- Latest closed slice: Stage 4E specification PR #67 at `031d657`; 4E runtime PR #68 at `42515e2`; post-merge main CI Run `32754399607` passed
- Human Review Required: Defaults approved on 2026-08-15; new approval remains mandatory before real credentials, spend, production access, or live delivery
- Merge: 4A–4E merged; tag `stage-04-complete` peels to `031d657`
- Stage 05: Specification merged (PR #69 at `3bbbc69`); runtime is the current gate; Stage 06 remains locked

## Objective

Deliver the first `PlatformAdapter` vertical slice for Meta Ads while preserving PostgreSQL as the System of Record and keeping every external write behind explicit trusted-human authorization. The slice covers deterministic local/test behavior and a Meta test-account boundary; it does not authorize production credentials, production traffic, or autonomous advertising spend.

## Non-negotiable decisions

1. AI generation and the Decision Engine never call Meta write APIs. Only an authenticated, authorized human command may request a platform mutation after Stage 04's security model is separately approved.
2. PostgreSQL remains authoritative for desired state, command history, idempotency identity, normalized delivery state, metric snapshots, and Audit. Meta identifiers and responses are external evidence, not the System of Record.
3. Provider SDK types, access tokens, account IDs, Graph URLs, cursors, and raw responses never cross the Meta adapter boundary.
4. Every external mutation is persisted before submission, uses a stable operation UUID/idempotency identity, and is safe to retry after timeout or process restart.
5. Local development and normal CI use a deterministic fake adapter only. A real Meta test account requires a separate human-approved credential and access record.
6. Production mode fails closed when trusted actor, authorization, account allowlist, budget policy, credential provider, or adapter configuration is absent.
7. Browser requests cannot supply Graph API origins, tokens, app secrets, arbitrary account IDs, raw targeting JSON, arbitrary fields, or provider URLs.
8. Stage 04 publishes only human-approved Stage 03 assets. Pending, rejected, blocked, archived, missing, or checksum-mismatched assets cannot be submitted.

## Approved functional scope

- Create and read a normalized Meta Campaign.
- Create and read a normalized Meta Ad Set beneath a known Campaign.
- Create and read a normalized Meta Ad using an approved existing Asset reference.
- Pause and resume Campaign, Ad Set, and Ad through explicit state-transition commands.
- Update bounded daily or lifetime budget through an explicit human command and server-side policy.
- Read normalized delivery status and bounded performance metrics.
- Persist attempts, provider identifiers, normalized outcomes, retry state, request IDs, trusted actor, and append-only Audit.
- Same-origin BFF and UI for preview, explicit confirmation, operation status, retry eligibility, and normalized metrics.
- Deterministic fake adapter, contract tests, migration compatibility, Compose smoke, and browser acceptance.
- Optional Meta test-account acceptance only after human escalation approves credentials and access outside the repository.

## Explicitly out of scope

- Production credentials/deployment/ad-account access/traffic, billing, payment methods, or account funding.
- Automatic publication, unattended budget changes, AI-initiated writes, or blind retry after an ambiguous outcome.
- Authentication, RBAC, Tenant, authorization, or trusted-actor implementation until separately designed and human-approved.
- Audience expansion, custom/lookalike audiences, pixels, Conversions API, catalogs, shops, Advantage+, experiments, or dynamic creative.
- Arbitrary targeting JSON, arbitrary provider fields, generic Graph proxying, or Browser-selected provider origins/accounts.
- Asset upload/delete in the first write slice; only approved server-side asset mappings may be referenced.
- Google Ads, LINE Ads, TikTok Ads, Dashboard, Decision Engine, or Stage 05+ behavior.
- Raw provider response storage, secrets in logs/Audit/database, destructive schema changes, or edits to merged migrations.

## Architecture boundaries

### Core ports

Application code depends only on provider-neutral contracts:

- `PlatformCampaignPort`
- `PlatformAdSetPort`
- `PlatformAdPort`
- `PlatformDeliveryReadPort`
- `PlatformMetricsReadPort`
- `PlatformCredentialProvider`
- `PlatformBudgetPolicyProvider`
- `PlatformAccountPolicyProvider`

The Meta adapter implements these ports. Domain/application code must not import Meta SDK or Graph API classes.

### Command lifecycle

1. Validate trusted actor, authorization, Product/Campaign/Asset lifecycle, approval, account allowlist, objective, currency, schedule, targeting, placement, and budget policy.
2. Persist a platform operation in `CREATED` with immutable normalized input and same-operation Audit.
3. Commit before any provider call.
4. Claim by optimistic lock, move to `SUBMITTING`, and call the adapter outside the database transaction.
5. Persist `SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, or `UNKNOWN_OUTCOME` with bounded evidence and Audit.
6. Retry only the same immutable operation identity; never silently create a replacement.
7. After an ambiguous timeout, reconcile by stable external reference before another write. `UNKNOWN_OUTCOME` cannot be blindly resubmitted.

### Read synchronization

- Delivery and metrics are explicit or bounded scheduled pulls; frequency is server-controlled.
- Provider cursors remain opaque adapter state and are never accepted from the Browser.
- Metric snapshots are append-only by account/entity/time window with documented attribution/timezone.
- Missing/delayed provider data uses explicit freshness metadata and is never silently converted to zero.
- Reads cannot mutate desired state or trigger writes.

### Credential and network boundary

- Tokens and app secrets come only from a secret-backed `PlatformCredentialProvider`; domain tables never store them.
- Meta Graph origin and API version are fixed server configuration; cross-origin redirects are rejected.
- Account, page, Instagram actor, pixel, and asset mapping IDs come from server-side allowlists.
- Logs contain operation UUID, request ID, normalized error, safe provider trace ID, and timing; never credentials, raw bodies, or raw responses.

## Persistence plan

Only additive migrations are allowed. Exact columns and constraints require Milestone 4A approval.

- `platform_accounts`: provider-neutral account, environment, currency/timezone, lifecycle, external-account fingerprint, version; no credentials.
- `platform_campaigns`: local UUID, Campaign Plan, account, objective/status/schedule, optional external ID, desired/observed state, version.
- `platform_ad_sets`: parent Campaign, bounded budget/schedule/optimization/placement/targeting profile, optional external ID, desired/observed state, version.
- `platform_ads`: parent Ad Set, approved Asset/output and immutable checksum/review evidence, creative mapping key, optional external ID, desired/observed state, version.
- `platform_operations`: immutable type/entity/account/request/idempotency, lifecycle, attempts, bounded evidence/error, timestamps, version.
- `platform_metric_snapshots`: append-only normalized entity/window/attribution/freshness/metrics with duplicate-window protection.

Database enforcement must guarantee:

- Provider/account/entity associations, operation input, and idempotency identity are immutable.
- External IDs are unique within provider account and entity type.
- Terminal operations cannot return to nonterminal states.
- Ad review decision, Asset/output UUID, and checksum cannot be changed.
- Money uses `NUMERIC(19,6)` and uppercase ISO currency; floating point is prohibited.
- Hard delete is rejected; foreign keys use `ON DELETE RESTRICT` and cannot erase Audit/evidence.
- V1–V11 checksums remain protected by migration compatibility tests.

## Normalized contracts

### Campaign

- Objective is server-allowlisted; the approved initial value is `OUTCOME_SALES` only.
- Desired state: `DRAFT`, `PAUSED`, `ACTIVE`, `ARCHIVED`; observed provider state is separate.
- Creation begins paused. Activation is a separate explicit command.

### Ad Set

- Budget is exactly one of daily or lifetime and currency matches account/Campaign Plan.
- UTC schedule retains account timezone for display and metrics.
- Targeting uses an approved server profile key; arbitrary JSON is rejected.
- Placements use a fixed allowlist; automatic expansion is deferred.

### Ad

- References one approved Stage 03 output/Asset snapshot and checksum.
- Creative mappings are server-owned and provider-neutral.
- Creation begins paused; activation is separate.
- A regenerated/replaced Asset creates a new mapping; approved evidence is never overwritten.

### Metrics

Initial normalized metrics are impressions, reach, clicks, spend, conversions, revenue/value when available, and derived CTR/CPC/CPM/CPA/CVR/ROAS only with valid inputs. Every snapshot records timezone, UTC window, attribution, currency, fetched-at, and freshness. Undefined values remain null, not zero.

## API and BFF plan

All routes are additive and require the future approved authorization boundary. Browser traffic uses same-origin BFF only.

- `POST /api/platforms/meta/campaigns`
- `GET /api/platforms/meta/campaigns/{platformCampaignUuid}`
- `POST /api/platforms/meta/campaigns/{platformCampaignUuid}/pause|resume`
- `POST /api/platforms/meta/campaigns/{platformCampaignUuid}/ad-sets`
- `POST /api/platforms/meta/ad-sets/{platformAdSetUuid}/budget`
- `POST /api/platforms/meta/ad-sets/{platformAdSetUuid}/pause|resume`
- `POST /api/platforms/meta/ad-sets/{platformAdSetUuid}/ads`
- `POST /api/platforms/meta/ads/{platformAdUuid}/pause|resume`
- `GET /api/platform-operations/{operationUuid}`
- `POST /api/platform-operations/{operationUuid}/retry`
- `GET /api/platform-entities/{entityType}/{entityUuid}/metrics`

Mutations use `If-Match` where local state exists. Creation accepts a validated client request UUID scoped with trusted actor/account; repeats return the existing operation. Provider errors map to bounded stable codes. Responses never expose tokens, raw Meta errors, arbitrary URLs, stack traces, or secret configuration.

## Mandatory human/security gate

The current local trusted-actor fallback is insufficient for real advertising writes. Before enabling a real adapter or test-account write, human approval must define:

- authentication, authorization roles, tenant/account ownership, and cross-account isolation;
- who may preview, create, activate, pause, resume, retry, and change budget;
- approval provenance and any creator/reviewer/operator separation;
- daily/lifetime ceilings, currency authority, and emergency kill switch;
- credential owner, rotation, revocation, environment, and incident handling;
- test account/page/Instagram actor allowlist and production promotion process.

Until approved, implementation may use only a deterministic fake adapter with no external network or spend. Any real credential, test-account access, paid budget, or production action requires `ESCALATE_TO_HUMAN`.

## Milestones

Each milestone requires exact-head Manager `APPROVE`, merge, and post-merge `main` CI before its dependent milestone starts.

### 4A — Platform persistence and operation foundation

- Additive schema and provider-neutral domain/ports.
- State machines, optimistic locking, Audit, idempotency, reconciliation, deterministic fake adapter.
- No REST/UI or external network.

### 4B — Campaign and Ad Set vertical slice

- Create/read/pause/resume and bounded budget updates.
- Server policies for objective, targeting, placement, account, currency, schedule, and budget.
- Same-origin preview/confirmation UI; fake adapter unless separately approved.

### 4C — Ad creative publication slice

- Create paused Ads from approved Stage 03 assets with immutable review/checksum evidence.
- No unreviewed asset, asset deletion, Product redraw, or automatic activation.

### 4D — Delivery and metrics read slice

- Normalized observed status and metric snapshots with bounded cursor/freshness/attribution behavior.
- Dashboard remains out of scope; only entity-level operational views.

### 4E — Acceptance and optional Meta test-account proof

- Full Backend/migration/Hibernate/Frontend/Compose/Playwright/Audit/dependency/workflow/secret regression.
- Deterministic acceptance covers idempotency, ambiguous reconciliation, stale ETag, budget rejection, approval enforcement, pause/resume, metrics freshness, and no AI direct write.
- Optional test-account smoke is separately approved, manually triggered, protected, and credential-safe.
- Final Manager Gate, post-merge verification, and completion tags.

## Verification baseline

- Cold/upgrade migration plus V1–V11 checksum compatibility.
- Direct-SQL tests for immutable identity, terminal states, append-only metrics/Audit, and delete prevention.
- Transaction tests proving persistence before provider call and no external call inside a long transaction.
- Concurrency tests for duplicate request, retry, stale ETag, and worker claim behavior.
- Adapter contract tests for success, rate limit, validation/permission failure, ambiguous timeout, malformed response, and reconciliation.
- Full Backend regression, Frontend lint/typecheck/tests/build, dependency audit, Compose cold start/health, browser E2E, Gitleaks, actionlint, and `git diff --check`.
- Secret tests proving token/app-secret/account data is rejected or redacted from persistence, responses, Audit, and logs.
- Remote Push/PR CI runs all required jobs without required-step skips.

## Acceptance criteria

- [ ] Specification receives exact-head Manager `APPROVE` before 4A starts.
- [ ] Human/security decisions are recorded before any real credential or external write.
- [ ] V1–V11 remain unchanged; schema changes are additive.
- [ ] AI and Decision Engine have no path to a platform write port.
- [ ] Duplicate/ambiguous submission cannot duplicate entities or spend.
- [ ] Only active, approved, checksum-matching Stage 03 assets are usable.
- [ ] Campaigns, Ad Sets, and Ads start paused; activation is separate.
- [ ] Budget, currency, targeting, placement, account, and objective are server-policy constrained.
- [ ] Metrics preserve attribution, timezone, currency, window, and freshness.
- [ ] Credentials/raw payloads never appear in repository, database, Browser, Audit, or logs.
- [ ] Full local/remote verification and exact-head Manager Gate pass before each merge.
- [ ] Stage 05 stays locked through Stage 04 final acceptance, post-merge CI, and tags.

## Human-approved specification decisions

The repository owner approved these defaults through the Codex task on 2026-08-15. This fixes the specification but does not supply credentials or authorize paid/production delivery.

1. Initial objective is `OUTCOME_SALES` only.
2. Campaign, Ad Set, and Ad are created `PAUSED`; activation/resume is a separate human-approved operation.
3. MVP budget mutation applies only at Ad Set level. Real test entities use `lifetime_budget`. Fake-adapter ceilings are TWD 100 per Ad Set daily, TWD 300 per Ad Set lifetime, TWD 300 per operation batch, and TWD 1,000 per account day.
4. Real Meta smoke has a zero-spend ceiling and may create/read/reconcile/pause entities only. Any delivery proof requires new approval; the suggested TWD 1,000 first-delivery ceiling is not currently authorized.
5. Server-owned `TW_BROAD_FEEDS_V1` uses country TW, age 18–65, Facebook Feed and Instagram Feed only; detailed/custom/lookalike audiences, Audience Network, automatic placements/expansion, and special-ad-category use are prohibited.
6. Roles are `PLATFORM_VIEWER`, `PLATFORM_OPERATOR`, `PLATFORM_APPROVER`, `PLATFORM_ADMIN`, and repository-external `CREDENTIAL_CUSTODIAN`. Operator/approver separation is required for activation and budget changes; one Meta Ad Account belongs to exactly one tenant. Authentication/RBAC implementation remains separately gated.
7. Stage 4E may include `META_TEST_READ_WRITE_PAUSED` after separate credential/access approval. `META_TEST_DELIVERY` remains disabled and unauthorized.
8. Business timezone is `Asia/Taipei`; attribution windows are `7d_click` and `1d_view`; snapshots are daily, currency comes from the allowlisted Meta account, and undefined metrics remain null.

## Escalation boundaries

Immediately `ESCALATE_TO_HUMAN` for authentication/RBAC/tenant changes, credentials, paid spend, budget-policy authority, production access, destructive migration/data work, System of Record changes, generic Graph proxying, automatic publication, AI writes, broader targeting/objectives/account scope, or Stage 05+ work.
