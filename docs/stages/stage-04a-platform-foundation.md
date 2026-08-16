# Stage 04 Milestone 4A — Platform Persistence and Operation Foundation

## Gate status

- Status: Technical specification drafted; implementation not started
- Branch: `codex/stage-04a-platform-foundation-specification`
- Base Commit: `a90f6e0bb20d23da10edb66712d85261dafe14e8`
- Prerequisite: Stage 04 specification merged by PR #56; post-merge main CI Run `31921389709` passed
- Specification: Pending Independent Manager Review
- Implementation: Not started
- Migration: Not started; the approved implementation may add only `V12__create_platform_operation_foundation.sql`
- Local Verification: Passed — documentation scope/whitespace review, `git diff --check`, and Gitleaks history/worktree scans
- Remote CI: Pending
- Manager Review: Pending
- Manager Decision: Pending
- Human Review Required: No for the deterministic, local/test-only foundation described here; mandatory before any separately gated security or external-access scope
- Merge: Pending
- Milestone 4B and later: Locked

## Purpose

Milestone 4A establishes the provider-neutral PostgreSQL model and application seams needed by later Stage 04 vertical slices. It makes desired platform state, immutable command identity, attempts, reconciliation state, and normalized evidence durable before any adapter call. It also defines a deterministic fake adapter for local and normal CI verification.

This milestone is an internal foundation only. It exposes no REST or BFF route, renders no UI, performs no external network call, stores no credential, and cannot create spend. PostgreSQL remains the sole System of Record.

## Evidence and compatibility baseline

This specification is grounded in the repository state at base `a90f6e0bb20d23da10edb66712d85261dafe14e8`:

- Flyway V1–V11 are the immutable migration baseline.
- V2 supplies append-only `audit_logs` and `audit_log_changes`; their existing `CREATE` and `UPDATE` actions are sufficient for this milestone.
- V4 supplies `campaign_plans`, `assets`, and composite Product ownership relationships.
- V8–V11 supply provider-neutral AI jobs, approved output evidence, `ai_review_decisions`, optimistic versions, delete protection, and direct-SQL trigger patterns.
- `MutableEntity` supplies `created_at`, `updated_at`, and JPA `@Version` mapping conventions.
- Existing local/test adapters are Spring profile-restricted deterministic components, while production profiles fail closed.
- Existing migration tests execute cold and upgrade paths and pin merged migration content hashes.

No statement in this document treats generated `backend/target` output as source-of-truth. Only tracked source and merged migrations define the implementation contract.

## Independently approvable scope

Milestone 4A implementation may contain only:

1. One additive Flyway V12 migration implementing the exact tables, constraints, indexes, and triggers below.
2. Provider-neutral domain models and repositories for accounts, Campaigns, Ad Sets, Ads, operations, operation attempts, and metric snapshots.
3. Provider-neutral command and reconciliation ports plus bounded result/error value objects.
4. An internal application service proving persistence-before-call, optimistic worker claim, idempotency, retry, and reconciliation behavior without a web entry point.
5. A deterministic fake adapter active only in `local` and `test`, with no network client or provider SDK.
6. Audit integration that records effective local mutations with bounded, redacted values.
7. Migration, Hibernate, direct-SQL, domain, transaction, concurrency, port-contract, fake-adapter, Audit, and regression tests.

The implementation may add a package such as `com.aicommerce.platform.delivery`; it must not make `ai`, Decision Engine, frontend, or a scheduled job depend on a platform write port.

## Explicit exclusions

- No REST controller, API contract, BFF route, frontend component, Playwright journey, or Browser input.
- No Meta SDK, Graph URL, HTTP client, DNS lookup, socket, webhook, provider cursor, or external network.
- No authentication, authorization, RBAC, Tenant model, account ownership model, role implementation, or trust-boundary change.
- No token, app secret, credential provider implementation, secret-manager integration, production access, Meta test-account access, or real account identifier.
- No paid provider, budget authorization, delivery, spend, billing, payment, production deployment, or production traffic.
- No AI-initiated or Decision-Engine-initiated platform write, automation, scheduling, or unattended retry.
- No changes to Stage 03 review semantics or existing Product, Campaign Plan, Asset, generation output, or review decision data.
- No edits to V1–V11, destructive migration, backfill, data rewrite, or System of Record change.
- No Stage 4B API/UI behavior, Stage 4C publication behavior, Stage 4D polling behavior, Stage 4E real-account proof, Dashboard, Decision Engine, or Stage 05 work.

Schema support for later entities and metrics is inert in 4A. Having a table or enum value does not authorize a later command, provider call, account, or environment.

## V12 migration contract

The only allowed migration filename is:

`backend/src/main/resources/db/migration/V12__create_platform_operation_foundation.sql`

It creates seven tables in dependency order:

1. `platform_accounts`
2. `platform_campaigns`
3. `platform_ad_sets`
4. `platform_ads`
5. `platform_operations`
6. `platform_operation_attempts`
7. `platform_metric_snapshots`

It may add one composite uniqueness constraint to `ai_review_decisions` so the Ad evidence FK can prove that the decision belongs to the referenced output. It must not modify or drop existing data, columns, constraints, tables, or migration files.

### `platform_accounts`

| Column | PostgreSQL type | Null/default | Contract |
| --- | --- | --- | --- |
| `platform_account_uuid` | `UUID` | NOT NULL | Primary key; immutable |
| `provider_key` | `VARCHAR(32)` | NOT NULL | `FAKE` or `META`; immutable |
| `environment` | `VARCHAR(16)` | NOT NULL | 4A permits `LOCAL` or `TEST` only; immutable |
| `account_reference` | `VARCHAR(128)` | NOT NULL | Non-secret server-owned logical reference; immutable, nonblank |
| `external_account_fingerprint` | `CHAR(64)` | NOT NULL | Lowercase SHA-256 fingerprint, never the raw account ID; immutable |
| `currency` | `CHAR(3)` | NOT NULL | Uppercase ISO format; immutable |
| `timezone` | `VARCHAR(64)` | NOT NULL | IANA name, 4A domain permits `Asia/Taipei`; immutable |
| `lifecycle_status` | `VARCHAR(16)` | NOT NULL DEFAULT `ACTIVE` | `ACTIVE` or terminal `ARCHIVED` |
| `archived_at` | `TIMESTAMPTZ` | NULL | Required exactly when archived |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `CURRENT_TIMESTAMP` | Immutable |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `CURRENT_TIMESTAMP` | Changes with effective mutation |
| `version` | `BIGINT` | NOT NULL DEFAULT `0` | Nonnegative optimistic version |

Constraints and indexes:

- PK `platform_account_uuid`.
- Unique `(provider_key, environment, account_reference)`.
- Unique `(provider_key, environment, external_account_fingerprint)`.
- Unique `(platform_account_uuid, provider_key)` for provider-coherent composite references.
- Checks: allowed provider/environment/lifecycle, nonblank reference/timezone, SHA-256 format, ISO currency, archive consistency, nonnegative version.
- Index `(lifecycle_status, updated_at DESC)`.
- There is no credential, token, secret, raw account ID, tenant ID, page ID, or Instagram actor ID column.

`META` is only a normalized provider discriminator. Because 4A permits only `LOCAL`/`TEST`, supplies no credential port implementation, and registers only the fake adapter, it cannot activate Meta access. Adding `PRODUCTION` later is a separately reviewed migration and human escalation.

### `platform_campaigns`

| Column | PostgreSQL type | Null/default | Contract |
| --- | --- | --- | --- |
| `platform_campaign_uuid` | `UUID` | NOT NULL | Primary key; immutable |
| `campaign_uuid` | `UUID` | NOT NULL | FK to `campaign_plans`; immutable |
| `platform_account_uuid` | `UUID` | NOT NULL | FK to account; immutable |
| `objective` | `VARCHAR(32)` | NOT NULL | Exactly `OUTCOME_SALES`; immutable |
| `desired_state` | `VARCHAR(16)` | NOT NULL DEFAULT `PAUSED` | Provider-neutral desired state |
| `observed_state` | `VARCHAR(24)` | NULL | Normalized observation; initially NULL |
| `schedule_start` | `TIMESTAMPTZ` | NULL | Immutable normalized input |
| `schedule_end` | `TIMESTAMPTZ` | NULL | Immutable; strictly after start when both set |
| `account_timezone` | `VARCHAR(64)` | NOT NULL | Immutable snapshot; `Asia/Taipei` in 4A |
| `external_id` | `VARCHAR(128)` | NULL | May change only NULL to one nonblank value |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | NOT NULL, current timestamp | Standard audit timestamps |
| `version` | `BIGINT` | NOT NULL DEFAULT `0` | Optimistic version |

Constraints and indexes:

- FK `campaign_uuid -> campaign_plans(campaign_uuid) ON DELETE RESTRICT`.
- FK `platform_account_uuid -> platform_accounts ON DELETE RESTRICT`.
- Unique `(platform_campaign_uuid, platform_account_uuid)` and `(campaign_uuid, platform_account_uuid)`.
- Partial unique `(platform_account_uuid, external_id) WHERE external_id IS NOT NULL`.
- Checks for objective, states, schedule, timezone, external ID nonblank when present, version.
- Indexes on `campaign_uuid` and `platform_account_uuid`.

### `platform_ad_sets`

| Column | PostgreSQL type | Null/default | Contract |
| --- | --- | --- | --- |
| `platform_ad_set_uuid` | `UUID` | NOT NULL | Primary key; immutable |
| `platform_campaign_uuid` | `UUID` | NOT NULL | Parent Campaign; immutable |
| `platform_account_uuid` | `UUID` | NOT NULL | Same account as parent; immutable |
| `budget_type` | `VARCHAR(16)` | NOT NULL | `DAILY` or `LIFETIME`; immutable after creation |
| `budget_amount` | `NUMERIC(19,6)` | NOT NULL | Positive; mutable only through a later approved budget command |
| `currency` | `CHAR(3)` | NOT NULL | Account currency; immutable |
| `schedule_start`, `schedule_end` | `TIMESTAMPTZ` | NULL | Immutable; valid increasing range |
| `account_timezone` | `VARCHAR(64)` | NOT NULL | Immutable; `Asia/Taipei` in 4A |
| `optimization_goal` | `VARCHAR(64)` | NOT NULL | Server-owned, nonblank normalized value |
| `targeting_profile_key` | `VARCHAR(128)` | NOT NULL | Exactly `TW_BROAD_FEEDS_V1` in 4A |
| `placement_profile_key` | `VARCHAR(128)` | NOT NULL | Exactly `TW_BROAD_FEEDS_V1` in 4A |
| `desired_state` | `VARCHAR(16)` | NOT NULL DEFAULT `PAUSED` | Desired lifecycle |
| `observed_state` | `VARCHAR(24)` | NULL | Normalized observation |
| `external_id` | `VARCHAR(128)` | NULL | Write-once external evidence |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | NOT NULL, current timestamp | Standard timestamps |
| `version` | `BIGINT` | NOT NULL DEFAULT `0` | Optimistic version |

Constraints and indexes:

- Composite FK `(platform_campaign_uuid, platform_account_uuid)` to Campaign, `ON DELETE RESTRICT`.
- Unique `(platform_ad_set_uuid, platform_account_uuid)`.
- Partial unique `(platform_account_uuid, external_id)` where non-null.
- Checks for budget type/positive amount, currency, schedule, exact profile keys, nonblank goal/timezone/external ID, states, version.
- Index `(platform_campaign_uuid, platform_account_uuid)`.

No raw targeting, placement JSON, audience identifier, or Browser-supplied provider field is stored.

### `platform_ads`

| Column | PostgreSQL type | Null/default | Contract |
| --- | --- | --- | --- |
| `platform_ad_uuid` | `UUID` | NOT NULL | Primary key; immutable |
| `platform_ad_set_uuid` | `UUID` | NOT NULL | Parent Ad Set; immutable |
| `platform_account_uuid` | `UUID` | NOT NULL | Same account as parent; immutable |
| `product_uuid` | `UUID` | NOT NULL | Evidence owner; immutable |
| `asset_uuid` | `UUID` | NOT NULL | Approved generated Asset; immutable |
| `generation_output_uuid` | `UUID` | NOT NULL | Stage 03 output; immutable |
| `review_decision_uuid` | `UUID` | NOT NULL | APPROVED decision; immutable |
| `approved_checksum_sha256` | `CHAR(64)` | NOT NULL | Exact immutable generated Asset/output checksum snapshot |
| `creative_mapping_key` | `VARCHAR(128)` | NOT NULL | Server-owned provider-neutral mapping key; immutable |
| `desired_state` | `VARCHAR(16)` | NOT NULL DEFAULT `PAUSED` | Desired lifecycle |
| `observed_state` | `VARCHAR(24)` | NULL | Normalized observation |
| `external_id` | `VARCHAR(128)` | NULL | Write-once external evidence |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | NOT NULL, current timestamp | Standard timestamps |
| `version` | `BIGINT` | NOT NULL DEFAULT `0` | Optimistic version |

Constraints and indexes:

- Composite FK `(platform_ad_set_uuid, platform_account_uuid)` to Ad Set.
- Composite FK `(asset_uuid, product_uuid)` to V10 unique Asset ownership.
- Composite FK `(generation_output_uuid, product_uuid)` to V11 unique output ownership.
- Add unique `(review_decision_uuid, generation_output_uuid)` on `ai_review_decisions`, then use it as the Ad composite FK.
- Unique `(platform_ad_uuid, platform_account_uuid)`.
- Partial unique `(platform_account_uuid, external_id)` where non-null.
- SHA-256 and nonblank mapping/external ID checks; state and version checks.
- Indexes on parent/account and each evidence FK pair.

Application validation in the same transaction must also prove: Product and Asset are active; output `review_status = APPROVED`; decision is `APPROVED`; generated Asset matches the output; preservation is `PASSED`; and current Asset checksum equals `approved_checksum_sha256`. The immutable FK/checksum snapshot prevents later evidence substitution. Direct-SQL integration tests must cover each rejection.

### `platform_operations`

| Column | PostgreSQL type | Null/default | Contract |
| --- | --- | --- | --- |
| `operation_uuid` | `UUID` | NOT NULL | Primary key and durable adapter idempotency identity |
| `platform_account_uuid` | `UUID` | NOT NULL | Account FK; immutable |
| `operation_type` | `VARCHAR(32)` | NOT NULL | Allowlisted command; immutable |
| `entity_type` | `VARCHAR(16)` | NOT NULL | `CAMPAIGN`, `AD_SET`, or `AD`; immutable |
| `platform_campaign_uuid` | `UUID` | NULL | Exactly one typed entity FK is present |
| `platform_ad_set_uuid` | `UUID` | NULL | Exactly one typed entity FK is present |
| `platform_ad_uuid` | `UUID` | NULL | Exactly one typed entity FK is present |
| `client_request_uuid` | `UUID` | NOT NULL | Trusted caller retry identity; immutable |
| `idempotency_key` | `CHAR(64)` | NOT NULL | SHA-256 of versioned canonical identity; immutable |
| `request_payload` | `JSONB` | NOT NULL | Provider-neutral canonical input only, max 16 KiB; immutable |
| `request_sha256` | `CHAR(64)` | NOT NULL | SHA-256 of exact canonical payload; immutable |
| `requested_actor_type` | `VARCHAR(32)` | NOT NULL | 4A internal fixture uses `LOCAL_ADMIN` or `SYSTEM`; immutable |
| `requested_actor_id` | `VARCHAR(128)` | NOT NULL | Trusted server-derived nonblank actor; immutable |
| `request_id` | `VARCHAR(128)` | NOT NULL | Existing safe request-ID format; immutable |
| `status` | `VARCHAR(24)` | NOT NULL DEFAULT `CREATED` | Operation state machine |
| `attempt_count` | `INTEGER` | NOT NULL DEFAULT `0` | Incremented exactly once per submit claim |
| `reconciliation_count` | `INTEGER` | NOT NULL DEFAULT `0` | Incremented exactly once per reconcile claim |
| `max_attempts` | `INTEGER` | NOT NULL | `1..10`; immutable |
| `external_id` | `VARCHAR(128)` | NULL | Bounded normalized outcome; write-once |
| `normalized_error_code` | `VARCHAR(64)` | NULL | Stable allowlisted code, not raw provider text |
| `safe_provider_trace_id` | `VARCHAR(128)` | NULL | Bounded non-secret trace token |
| `outcome_evidence` | `JSONB` | NULL | Allowlisted normalized object, max 8 KiB |
| `next_attempt_at` | `TIMESTAMPTZ` | NULL | Server-controlled retry time |
| `claimed_at` | `TIMESTAMPTZ` | NULL | Latest claim timestamp |
| `completed_at` | `TIMESTAMPTZ` | NULL | Required for terminal status |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | NOT NULL, current timestamp | Standard timestamps |
| `version` | `BIGINT` | NOT NULL DEFAULT `0` | Optimistic worker/ETag version |

Allowed `operation_type` values are `CREATE_CAMPAIGN`, `CREATE_AD_SET`, `CREATE_AD`, `PAUSE`, `RESUME`, and `UPDATE_BUDGET`. Only foundation tests may create them in 4A; no public command surface exists.

Constraints and indexes:

- Typed composite FKs to the one referenced entity, all `ON DELETE RESTRICT`.
- Unique request identity `(platform_account_uuid, requested_actor_type, requested_actor_id, client_request_uuid)`.
- Unique `(platform_account_uuid, idempotency_key)`.
- Exact-one-entity coherence check matching `entity_type`.
- Operation/entity coherence: `CREATE_CAMPAIGN` requires `CAMPAIGN`, `CREATE_AD_SET` requires `AD_SET`, `CREATE_AD` requires `AD`, `UPDATE_BUDGET` requires `AD_SET`, and `PAUSE`/`RESUME` permit any one typed entity.
- SHA-256, JSON type/size, actor/request ID, attempt bounds, evidence type/size, error/trace/external-ID nonblank-when-present, state/timestamp coherence, and nonnegative version checks.
- Claim queue index `(platform_account_uuid, status, next_attempt_at, created_at)`.
- Partial entity/account indexes for each entity FK.

`request_payload` is a versioned canonical application DTO, never a provider payload. Allowed keys are selected by `operation_type`, strings are Unicode-normalized, object keys are lexically sorted, numbers use plain decimal without insignificant zeroes, and absent optional values are omitted. Arrays preserve declared semantic order. Unknown keys, URLs, credentials, raw account IDs, provider fields, and secret-marker keys are rejected before persistence.

`idempotency_key = SHA-256("platform-operation-v1\n" + platformAccountUuid + "\n" + actorType + "\n" + actorId + "\n" + clientRequestUuid)`. A repeated request identity returns the existing operation only when `request_sha256` and typed entity match; otherwise it fails with `PLATFORM_IDEMPOTENCY_CONFLICT` and performs no mutation or adapter call.

### `platform_operation_attempts`

| Column | PostgreSQL type | Null/default | Contract |
| --- | --- | --- | --- |
| `operation_attempt_uuid` | `UUID` | NOT NULL | Primary key; immutable |
| `operation_uuid` | `UUID` | NOT NULL | Operation FK; immutable |
| `attempt_kind` | `VARCHAR(16)` | NOT NULL | `SUBMIT` or `RECONCILE`; immutable |
| `attempt_number` | `INTEGER` | NOT NULL | Positive sequence within kind; immutable |
| `status` | `VARCHAR(24)` | NOT NULL DEFAULT `STARTED` | One-way attempt lifecycle |
| `safe_provider_trace_id` | `VARCHAR(128)` | NULL | Bounded safe evidence |
| `normalized_error_code` | `VARCHAR(64)` | NULL | Stable code only |
| `evidence` | `JSONB` | NULL | Allowlisted object, max 8 KiB |
| `started_at` | `TIMESTAMPTZ` | NOT NULL | Persisted before adapter invocation; immutable |
| `completed_at` | `TIMESTAMPTZ` | NULL | Required after terminal result |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `CURRENT_TIMESTAMP` | Immutable |
| `version` | `BIGINT` | NOT NULL DEFAULT `0` | Must be 0 at insert and exactly 1 after finalization |

Constraints and indexes:

- FK to operation `ON DELETE RESTRICT`.
- Unique `(operation_uuid, attempt_kind, attempt_number)`.
- Status values `STARTED`, `SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, `NOT_FOUND`.
- `STARTED` requires no completion/evidence result; terminal attempt status requires `completed_at`.
- `SUBMIT` cannot finish `NOT_FOUND`; `RECONCILE` may use `NOT_FOUND`.
- Trigger allows exactly one update from `STARTED` to a terminal attempt state, with version `0 -> 1`; all identity/start fields are immutable; terminal rows and all deletes are rejected.
- Index `(operation_uuid, attempt_kind, attempt_number DESC)`.

This table is required because a mutable aggregate `attempt_count` alone is not an auditable attempt history. Claim and STARTED attempt insertion commit atomically before the adapter call.

### `platform_metric_snapshots`

| Column | PostgreSQL type | Null/default | Contract |
| --- | --- | --- | --- |
| `metric_snapshot_uuid` | `UUID` | NOT NULL | Primary key |
| `platform_account_uuid` | `UUID` | NOT NULL | Account FK |
| `entity_type` | `VARCHAR(16)` | NOT NULL | Typed entity discriminator |
| `platform_campaign_uuid` | `UUID` | NULL | Present only for a Campaign snapshot |
| `platform_ad_set_uuid` | `UUID` | NULL | Present only for an Ad Set snapshot |
| `platform_ad_uuid` | `UUID` | NULL | Present only for an Ad snapshot |
| `window_start`, `window_end` | `TIMESTAMPTZ` | NOT NULL | End strictly after start |
| `timezone` | `VARCHAR(64)` | NOT NULL | `Asia/Taipei` in initial contract |
| `attribution_click_days` | `SMALLINT` | NOT NULL DEFAULT `7` | Exactly 7 |
| `attribution_view_days` | `SMALLINT` | NOT NULL DEFAULT `1` | Exactly 1 |
| `currency` | `CHAR(3)` | NOT NULL | Uppercase ISO format |
| `impressions`, `reach`, `clicks`, `conversions` | `BIGINT` | NULL | Nonnegative; missing remains NULL |
| `spend`, `revenue` | `NUMERIC(19,6)` | NULL | Nonnegative; missing remains NULL |
| `fetched_at` | `TIMESTAMPTZ` | NOT NULL | Source observation time |
| `freshness_status` | `VARCHAR(16)` | NOT NULL | `FRESH`, `DELAYED`, or `UNAVAILABLE` |
| `source_fingerprint` | `CHAR(64)` | NOT NULL | SHA-256 duplicate/source evidence |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `CURRENT_TIMESTAMP` | Immutable |

Constraints/indexes:

- Typed entity/account composite FKs and exact-one-entity check.
- Nonnegative nullable metrics; currency, attribution, window, timezone, freshness, and fingerprint checks.
- Three partial unique indexes by account, typed entity, UTC window, timezone, attribution, and currency.
- Three partial entity/account lookup indexes.
- Entire row is append-only; update and delete triggers reject mutation.

The table is intentionally inert in 4A; there is no polling service or read port implementation. CTR/CPC/CPM/CPA/CVR/ROAS are derived later from stored base values only when denominators are valid; they are not persisted in V12.

## Database trigger contract

V12 creates narrowly named trigger functions and per-table triggers; it must not overload V1–V11 functions.

- Every new table rejects hard delete with SQLSTATE `23514`.
- Accounts: identity/config fields immutable; only `ACTIVE -> ARCHIVED` is allowed; effective transition increments version exactly once.
- Campaign/Ad Set/Ad: identity and parent/evidence/schedule/policy fields immutable; external ID is write-once; observed state may follow normalized observation transitions; desired-state transitions follow the exact machine below; version increments exactly once for any effective update.
- Operations: immutable input and idempotency fields; exact state transitions; claim counter/timestamp coherence; terminal rows reject any update; external ID is write-once.
- Attempts: one finalization only; then immutable.
- Metrics: append-only.
- Audit: existing V2 append-only triggers remain authoritative.

JPA/domain validation is not a substitute for these direct-SQL constraints. Conversely, triggers do not authorize a command; application policy and trusted entry points remain mandatory in later milestones.

## State machines

### Desired entity state

All Campaigns, Ad Sets, and Ads are constructed as `PAUSED`. `DRAFT` is retained only for a future server-owned preparation flow and is not emitted by 4A factories.

Allowed transitions:

- `DRAFT -> PAUSED`
- `PAUSED -> ACTIVE`
- `ACTIVE -> PAUSED`
- `PAUSED -> ARCHIVED`

`ARCHIVED` is terminal. `ACTIVE -> ARCHIVED`, restore, self-transition updates, and all other edges are rejected. Later activation, pause, and archive commands require their own approved policy and audit; schema capability is not authorization.

Observed states are `UNKNOWN`, `PENDING`, `PAUSED`, `ACTIVE`, `COMPLETED`, `REJECTED`, `ERROR`, and `DELETED`. NULL means never observed. Only adapter normalization may supply these values; raw provider strings are not persisted. Observed state never mutates desired state.

The exact observed-state database machine permits NULL to any normalized value and permits any non-`DELETED` normalized value to any normalized value, including itself when another field changes. `DELETED` is terminal. An update that changes neither normalized state nor another permitted field is a no-op and must not increment version.

### Platform operation

- `CREATED -> SUBMITTING`
- `FAILED_RETRYABLE -> SUBMITTING`, only while `attempt_count < max_attempts` and `next_attempt_at <= now`
- `SUBMITTING -> SUCCEEDED | FAILED_RETRYABLE | FAILED_TERMINAL | UNKNOWN_OUTCOME`
- `UNKNOWN_OUTCOME -> RECONCILING`
- `RECONCILING -> SUCCEEDED | FAILED_TERMINAL | UNKNOWN_OUTCOME`

`SUCCEEDED` and `FAILED_TERMINAL` are terminal and immutable. `UNKNOWN_OUTCOME` never transitions to `SUBMITTING`. A stale `SUBMITTING` claim after process failure is treated as ambiguous: recovery records `UNKNOWN_OUTCOME` and then reconciles; it never assumes the write did not happen. A stale `RECONCILING` claim returns to `UNKNOWN_OUTCOME`, not to submission.

Status/timestamp coherence is exact: `CREATED` has zero counters and no claim/completion; `SUBMITTING`/`RECONCILING` require `claimed_at` and no `completed_at`; `FAILED_RETRYABLE` requires `next_attempt_at`, no `completed_at`, and a normalized error; `UNKNOWN_OUTCOME` requires no `completed_at` and a normalized ambiguity code; terminal statuses require `completed_at` and no `next_attempt_at`. `SUCCEEDED` requires an external ID for create operations. Failure states cannot persist raw error text.

Each `SUBMITTING` claim increments `attempt_count` exactly once and atomically inserts the matching `SUBMIT/STARTED` attempt. Each `RECONCILING` claim increments `reconciliation_count` exactly once and inserts the matching `RECONCILE/STARTED` attempt. Optimistic-lock failure means the loser performs no call.

### Attempt

`STARTED` transitions exactly once to one result. A SUBMIT attempt may end `SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, or `UNKNOWN_OUTCOME`. A RECONCILE attempt may end `SUCCEEDED`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, or `NOT_FOUND`. `NOT_FOUND` means no matching external entity was proven; it is not authority to resubmit the write. The containing operation stays `UNKNOWN_OUTCOME` pending an explicit human-safe resolution policy in a later milestone.

## Optimistic locking and transaction boundaries

All mutable aggregates use `@Version`. Worker claim uses a version-qualified update or a JPA flush that guarantees exactly one claimant. No pessimistic database lock is held across an adapter call.

Required sequence:

1. Transaction A validates canonical input and local references; inserts the paused entity if a create command requires it; inserts `platform_operations` in `CREATED`; writes same-operation Audit; commits.
2. Transaction B claims the exact operation version, moves it to `SUBMITTING`, increments `attempt_count`, inserts a `SUBMIT/STARTED` attempt, writes Audit, and commits.
3. The application invokes the adapter after Transaction B. `TransactionSynchronizationManager.isActualTransactionActive()` must be false inside the adapter.
4. Transaction C locks by optimistic version, finalizes the attempt, transitions the operation, applies bounded normalized entity evidence, writes Audit, and commits atomically.
5. If response receipt is ambiguous or Transaction C cannot prove the result, persist/recover `UNKNOWN_OUTCOME`; do not resubmit.

Reconciliation uses the analogous claim/call/finalize sequence with `RECONCILING` and a RECONCILE attempt. Adapter exceptions are normalized outside persistence; raw exception text is not written to the database or Audit.

## Provider-neutral ports

Application/domain packages may define these interfaces without SDK types:

- `PlatformCampaignPort.submitCampaign(PlatformCampaignCommand)`
- `PlatformAdSetPort.submitAdSet(PlatformAdSetCommand)`
- `PlatformAdPort.submitAd(PlatformAdCommand)`
- state-mutation methods on the matching typed port for pause/resume and bounded budget change
- `PlatformOperationReconciliationPort.reconcile(PlatformReconciliationQuery)`
- `PlatformDeliveryReadPort` and `PlatformMetricsReadPort` as contracts only; no 4A implementation or scheduled caller
- `PlatformAccountPolicyProvider` and `PlatformBudgetPolicyProvider` as fail-closed contracts; 4A deterministic fixtures are server-owned
- `PlatformCredentialProvider` as a marker contract only, with no bean, method that returns a secret, or implementation in 4A

Command records contain only local UUIDs, normalized enums/values, canonical checksum/evidence, operation UUID, and idempotency key. Results are a closed sum of success, retryable failure, terminal failure, and ambiguous outcome with bounded stable codes. They contain no access token, Graph URL, raw body, raw provider error, SDK object, arbitrary map, or HTTP response.

Port calls are reachable only from the internal 4A test/application orchestration service. There is no controller, scheduler, event listener, AI dependency, or Decision Engine dependency.

## Deterministic fake adapter contract

- Registered only under `(local | test) & !production` and an explicit fake-platform profile/property.
- Uses no HTTP/library SDK, filesystem credential, environment secret, random timing, DNS, socket, or paid service.
- Validates the same normalized request limits as the application port contract.
- Derives stable external IDs as a documented prefix plus a truncated lowercase SHA-256 of operation UUID/idempotency identity.
- Repeated submit with the same identity returns the same external ID; a different payload for the same identity returns an idempotency conflict.
- Server-owned fixtures cover success, retryable rate limit, terminal validation, terminal permission, malformed-result normalization, ambiguous timeout, and reconciliation success/not-found/still-unknown.
- Fixture selection is constructor/test configuration, not persisted arbitrary input and never a Browser/provider-origin field.
- Records invocation count and whether a Spring transaction was active so tests prove single submission and transaction separation.
- Default and production profiles expose no usable write adapter; startup or operation dispatch fails closed with `PLATFORM_ADAPTER_UNAVAILABLE`.

Contract tests must run the same test suite against every implementation of a write or reconciliation port. A future Meta adapter cannot weaken the fake contract.

## Audit, logging, and redaction boundary

Use existing `AuditService`, `AuditOperationContext`, and `AuditValueSanitizer` patterns. Effective mutations write Audit in the same transaction; stale optimistic attempts, validation failures, duplicate replays, and failed idempotency conflicts write no false state-change Audit.

Allowed Audit content:

- operation/entity UUID, normalized entity/operation/status, request ID, actor type/ID, attempt number, normalized error code, safe trace ID, timestamps, and bounded checksum/fingerprint;
- `CREATE` for newly persisted foundation records and `UPDATE` for effective transitions.

Forbidden everywhere, including logs, Audit, exceptions, JSON evidence, test snapshots, and completion reports:

- tokens, secrets, credentials, cookies, Authorization headers, raw external account IDs, raw provider request/response bodies, Graph URLs, arbitrary targeting, SDK objects, stack traces returned to a caller, or unbounded exception messages.

The existing sanitizer marker list is the minimum. Platform canonicalizers reject keys containing `authorization`, `cookie`, `credential`, `password`, `secret`, or `token`, case-insensitively, before persistence. Tests use sentinel secrets and capture logs/Audit/database rows to prove absence. Safe trace IDs are length/character bounded and never trusted as URLs.

## Verification matrix

| Area | Required evidence |
| --- | --- |
| Migration cold path | Empty PostgreSQL migrates V1 through V12; Flyway latest is 12; all seven tables, columns, constraints, indexes, functions, and triggers match this specification |
| Upgrade path | A populated V11 fixture containing Product, Campaign Plan, Asset, generated output, and approved decision migrates to V12 without data/version/checksum change |
| Migration immutability | Canonical SHA-256 assertions cover V1–V11 byte-for-byte; V12 is new only; no pending migration remains |
| Migration atomicity | A deliberate V12 object-name collision causes the migration to roll back with none of the other V12 objects left behind |
| Hibernate | `ddl-auto=validate` passes against V12; all enum lengths, JSONB, `CHAR`, money precision/scale, nullability, composite relationships, timestamps, and `@Version` mappings match |
| Direct SQL identity | Attempts to mutate every account/entity/operation identity, Ad approval/checksum evidence, write-once external ID, canonical payload/hash, or attempt identity fail with SQLSTATE `23514` |
| Direct SQL lifecycle | Invalid desired/observed/operation/attempt transitions, attempt counter drift, terminal updates, account restore, and version jumps fail |
| Delete protection | DELETE from each of seven tables fails; referenced V1–V11 records remain protected by `ON DELETE RESTRICT`; Audit remains append-only |
| Metric append-only | Valid null metrics insert; duplicate window fails; missing values remain NULL; negative/base invalid values, update, and delete fail |
| Domain unit | All valid and invalid state-machine edges, value bounds, canonicalization, checksum, timezone/currency, exact profiles, and normalized result/error mappings |
| Port contract | Success, rate limit, validation, permission, malformed response, ambiguous timeout, idempotent replay, reconcile found/not-found/unknown, and no provider types escaping |
| Persistence before call | A separate connection can read the operation and STARTED attempt inside fake invocation; adapter observes no active Spring transaction |
| Concurrency | Concurrent duplicate create returns one operation/entity and one submit call; concurrent claim has one winner; stale versions fail; max attempts cannot be exceeded |
| Retry | Retry reuses operation UUID, canonical payload, idempotency key, and entity; creates the next attempt row only; no replacement entity/operation |
| Ambiguous recovery | Timeout/crash produces UNKNOWN; submit count remains one; only reconcile is called; restart recovery never blindly submits; NOT_FOUND remains unresolved |
| Audit | Effective create/claim/result/reconcile transitions have same-transaction Audit; rollback leaves neither state nor Audit; sentinel secret is absent from Audit/log/database |
| Profile/security | Local/test fake is deterministic; default/production has no usable adapter; no network-capable dependency or credential implementation is introduced |
| Regression | Full Backend Testcontainers suite; Frontend lint/typecheck/tests/build/audit even though unchanged; Compose config/cold health; Smoke; Playwright; actionlint; Gitleaks history/worktree; `git diff --check` |

Normal Remote Push and Pull Request CI must execute `quality-and-compose` and `secret-scan` without required-step skips.

## Rollback and forward recovery

- Before merge, discard/revert the unmerged implementation branch; never edit a merged migration.
- After V12 is merged or applied, rollback is forward-only. Do not run Flyway clean, drop V12 objects, or delete foundation data.
- If application deployment fails after V12 succeeds, roll back application binaries to the prior compatible version; unused additive tables remain inert.
- Repair a V12 defect with V13 or later after a new approved specification; do not alter V12.
- Persisted `CREATED`/`FAILED_RETRYABLE` work remains inert until an approved internal runner claims it. Persisted `SUBMITTING` after a crash is ambiguous and must move to reconciliation, not retry.
- `UNKNOWN_OUTCOME` and `NOT_FOUND` are retained as evidence. A human-safe resolution or provider-specific lookup strategy requires a later approved milestone; no row is rewritten or deleted.
- Metrics are append-only. Attempt rows remain immutable except for their one allowed STARTED-to-result finalization, after which their history cannot be updated or deleted. Incorrect normalized observations are superseded by later records where the future contract permits; they are never overwritten.

## Warnings and known limitations

- The V12 model includes inert Campaign, Ad Set, Ad, and metric structures so later milestones do not redesign operation identity. 4A does not expose or schedule those capabilities.
- Database constraints cannot prove an IANA timezone name or inspect every semantic property of canonical JSON; domain validation and contract tests are mandatory in addition to SQL checks.
- PostgreSQL uniqueness arbitrates duplicate operation creation, but application code must load and compare the winning row after a constraint race.
- A provider may ignore an idempotency key or return an ambiguous result. Therefore `UNKNOWN_OUTCOME` is retained and blind resubmission is forbidden.
- Optimistic locking prevents concurrent application writers but does not replace later authorization, account isolation, rate limiting, or emergency kill-switch design.
- No real provider behavior, credential rotation, Meta version compatibility, external delivery, or spend can be inferred from fake-adapter tests.
- Repository manual Manager Gate remains necessary because an automated `manager-gate` required check is not enabled.

## Escalation boundaries

Immediately stop and `ESCALATE_TO_HUMAN` before any:

- authentication, authorization, RBAC, Tenant, account ownership/isolation, trusted-actor, or security-model implementation;
- credential/token/secret manager, raw Meta account/page/Instagram ID, external network, Meta SDK/Graph API, protected environment, test account, or production access;
- budget authority, paid spend, delivery, billing, payment, production deployment/traffic, or live provider call;
- destructive migration, edit to V1–V11, data backfill/rewrite, System of Record change, or forward-recovery uncertainty affecting existing data;
- new product objective, broader targeting/placement, arbitrary provider JSON/proxy, autonomous retry/publication, AI/Decision Engine write path, or Stage 4B+ behavior;
- ambiguity requiring an operator to decide whether a real external write is safe to repeat.

## Acceptance checklist

- [ ] This technical specification receives exact-head Independent Manager `APPROVE`, merge, and post-merge main CI before implementation begins.
- [ ] Implementation diff stays inside the approved 4A scope and contains exactly one additive V12 migration; V1–V11 remain byte-for-byte unchanged.
- [ ] PostgreSQL is authoritative for all normalized desired state, operation identity, attempts, reconciliation, and evidence.
- [ ] Identity, approval evidence, external IDs, terminal rows, attempts, metrics, and hard-delete rules are enforced by direct SQL as specified.
- [ ] Duplicate and concurrent commands cannot create a second operation/entity or second provider call.
- [ ] Every adapter call follows a committed operation and STARTED attempt and runs outside a database transaction.
- [ ] Ambiguous submission can only reconcile; it cannot blindly submit again.
- [ ] Deterministic fake adapter contract and all migration/Hibernate/direct-SQL/concurrency/Audit/security tests pass.
- [ ] No REST/UI/network/credential/auth/RBAC/tenant/production/spend or Stage 4B+ implementation exists.
- [ ] Full local and Remote CI pass on the exact implementation Head.
- [ ] Independent Manager Review records `APPROVE`, the implementation PR merges, and post-merge main CI passes before Milestone 4B unlocks.
