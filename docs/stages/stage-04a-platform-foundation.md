# Stage 04 Milestone 4A — Platform Persistence and Operation Foundation

## Gate status

- Status: Technical specification revised after `REQUEST_CHANGES`; implementation not started
- Branch: `codex/stage-04a-platform-foundation-specification`
- Base Commit: `a90f6e0bb20d23da10edb66712d85261dafe14e8`
- Prerequisite: Stage 04 specification merged by PR #56; post-merge main CI Run `31921389709` passed
- Specification: `REQUEST_CHANGES` findings resolved by specification revision; pending new exact-head Independent Manager re-review
- Implementation: Not started
- Migration: Not started; the approved implementation may add only `V12__create_platform_operation_foundation.sql`
- Local Verification: Passed — specification-only scope, Markdown table sanity, `git diff --check`, and Gitleaks 8.28.0 history/worktree scans
- Remote CI: Pending
- Manager Review: Re-review required on the revised exact Head
- Manager Decision: `REQUEST_CHANGES` on reviewed Head `d129df447f670a9f5b1faab230a06a64c8240438`; revised Head pending decision
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
| `budget_type` | `VARCHAR(16)` | NOT NULL | `DAILY` or `LIFETIME`; immutable policy identity after creation |
| `budget_amount` | `NUMERIC(19,6)` | NOT NULL | Positive mutable value; may change only through the exact bounded `UPDATE_BUDGET` operation below |
| `currency` | `CHAR(3)` | NOT NULL | Account currency; immutable |
| `schedule_start`, `schedule_end` | `TIMESTAMPTZ` | NULL | Immutable; valid increasing range |
| `account_timezone` | `VARCHAR(64)` | NOT NULL | Immutable; `Asia/Taipei` in 4A |
| `optimization_goal` | `VARCHAR(64)` | NOT NULL | Server-owned, nonblank normalized value |
| `targeting_profile_key` | `VARCHAR(128)` | NOT NULL | Exactly `TW_BROAD_FEEDS_V1` in 4A |
| `placement_profile_key` | `VARCHAR(128)` | NOT NULL | Exactly `TW_BROAD_FEEDS_V1` in 4A |
| `desired_state` | `VARCHAR(16)` | NOT NULL DEFAULT `PAUSED` | Desired lifecycle |
| `observed_state` | `VARCHAR(24)` | NULL | Normalized observation |
| `external_id` | `VARCHAR(128)` | NULL | Write-once external evidence |
| `last_budget_operation_uuid` | `UUID` | NULL | Latest successful `UPDATE_BUDGET` provenance; NULL at creation and changes only with an effective budget revision |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | NOT NULL, current timestamp | Standard timestamps |
| `version` | `BIGINT` | NOT NULL DEFAULT `0` | Optimistic version |

Constraints and indexes:

- Composite FK `(platform_campaign_uuid, platform_account_uuid)` to Campaign, `ON DELETE RESTRICT`.
- Unique `(platform_ad_set_uuid, platform_account_uuid)`.
- Partial unique `(platform_account_uuid, external_id)` where non-null.
- Checks for budget type/positive amount, currency, schedule, exact profile keys, nonblank goal/timezone/external ID, states, version.
- After `platform_operations` exists, V12 adds a deferred FK from `last_budget_operation_uuid` to `platform_operations(operation_uuid) ON DELETE RESTRICT`; no other operation may be referenced.
- Index `(platform_campaign_uuid, platform_account_uuid)`.

No raw targeting, placement JSON, audience identifier, or Browser-supplied provider field is stored.

`budget_type`, currency, schedule, timezone, optimization goal, targeting profile, placement profile, parent, and account form immutable policy identity/configuration. `budget_amount` is not policy identity: it is the one mutable budget value. An effective change must atomically set a different `last_budget_operation_uuid`, increment the Ad Set version exactly once, and write same-transaction Audit. V12 defines `verify_platform_budget_operation_coherence()` and two `DEFERRABLE INITIALLY DEFERRED` constraint triggers: one after UPDATE OF `budget_amount`, `last_budget_operation_uuid`, or `version` on `platform_ad_sets`, and the reciprocal trigger after UPDATE OF `status` on an `UPDATE_BUDGET` operation. At commit they enforce both directions: a changed amount references the same account/Ad Set operation in `SUCCEEDED`, and every newly succeeded budget operation is the Ad Set's new provenance pointer with its new amount applied. The canonical payload must contain the OLD amount, NEW amount, immutable budget type/currency, and exactly `OLD.version`; `NEW.version` must equal `OLD.version + 1`. Initial creation must have `last_budget_operation_uuid IS NULL`; changing the pointer without changing the amount, reusing an earlier operation, marking the operation successful without the entity change, changing the amount without a newly successful operation, or changing any immutable policy field fails with SQLSTATE `23514`.

The internal 4A fixture is fail-closed and TWD-only: `DAILY` permits `0 < newBudgetAmount <= 100.000000`; `LIFETIME` permits `0 < newBudgetAmount <= 300.000000`; one operation batch may not exceed TWD `300.000000`; and accepted operations attributed to one account/business date may not exceed TWD `1000.000000`. These are server-owned validation ceilings inherited from the approved Stage 04 specification, not Browser input or authority to spend. A different currency, missing policy, stale expected version, ceiling violation, or inactive account fails before adapter dispatch with a stable local error and produces no budget mutation. A real account, external write, delivery, or change in budget authority remains a mandatory later human gate.

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

V12 must enforce the full evidence chain in PostgreSQL, not only in application validation. It creates a narrowly named `verify_platform_ad_evidence_coherence()` function and a `DEFERRABLE INITIALLY DEFERRED` constraint trigger after INSERT or UPDATE OF `product_uuid`, `asset_uuid`, `generation_output_uuid`, `review_decision_uuid`, or `approved_checksum_sha256` on `platform_ads`. At deferred execution the function locks the referenced Product, Asset, generation output, and decision rows with `FOR SHARE` and rejects unless all of the following are true at the transaction boundary:

1. The Product exists and `lifecycle_status = 'ACTIVE'`.
2. The Asset exists, belongs to that Product, has `asset_type = 'IMAGE'`, has `lifecycle_status = 'ACTIVE'`, and has a non-null lowercase SHA-256 checksum.
3. The generation output exists, belongs to that Product, has `generation_type = 'IMAGE'`, references exactly that Asset as `generated_asset_uuid`, has `review_status = 'APPROVED'`, has `preservation_status = 'PASSED'`, and has `output_checksum_sha256` equal to both the current Asset checksum and `platform_ads.approved_checksum_sha256`.
4. The review decision exists for exactly that generation output and has `decision = 'APPROVED'`. V11's existing deferred review-coherence trigger remains authoritative for decision/output version coherence.
5. The immutable Ad evidence columns and checksum snapshot cannot be updated after insert.

The trigger raises SQLSTATE `23514` for semantic evidence incoherence; missing/mismatched composite references continue to fail with SQLSTATE `23503`. Application validation repeats the same checks in Transaction A for early errors, but it is defense in depth and never replaces database enforcement. Later archival of already-referenced Product/Asset evidence may remain allowed only when it occurs in a separate committed transaction; it does not rewrite the immutable Ad evidence snapshot and it blocks any new Ad creation. Direct-SQL tests must independently reject: a mismatched Asset/output pair, a TEXT output, PENDING_REVIEW and REJECTED output/decision combinations, BLOCKED preservation, inactive Product, inactive Asset, null/different Asset checksum, different output checksum, different Ad snapshot checksum, and any post-insert evidence substitution.

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
- SHA-256, JSON type/size, actor/request ID, attempt bounds, evidence type/size/exact-key schema, stable error allowlist, error/outcome/status coherence, trace/external-ID nonblank-when-present, state/timestamp coherence, and nonnegative version checks.
- Claim queue index `(platform_account_uuid, status, next_attempt_at, created_at)`.
- Partial entity/account indexes for each entity FK.

`request_payload` is a versioned canonical application DTO, never a provider payload. Every payload is a JSON object with exactly the required keys below and only the stated optional keys. UUIDs are lowercase canonical strings; enums are uppercase; Instants are UTC RFC 3339 with zero to six fractional digits and no insignificant trailing zero; strings are Unicode NFC normalized; decimals are JSON numbers in plain notation with scale at most six and no insignificant trailing zero; optional absence is represented by an omitted key, never JSON null. Object keys are lexically sorted by Unicode code point before UTF-8 serialization. Unknown keys, arrays, URLs, credentials, raw account IDs, provider fields, non-finite numbers, and secret-marker keys are rejected. The exact UTF-8 bytes must not exceed 16 KiB.

| `operation_type` | Required payload keys in addition to `schemaVersion`, `operationType`, `entityType`, `entityUuid` | Optional keys | Exact value rules |
| --- | --- | --- | --- |
| `CREATE_CAMPAIGN` | `platformCampaignUuid`, `campaignUuid`, `objective`, `desiredState`, `accountTimezone` | `scheduleStart`, `scheduleEnd` | `entityType=CAMPAIGN`; entity UUID equals `platformCampaignUuid`; objective `OUTCOME_SALES`; desired state `PAUSED`; optional schedule pair follows Campaign constraints |
| `CREATE_AD_SET` | `platformAdSetUuid`, `platformCampaignUuid`, `budgetType`, `budgetAmount`, `currency`, `accountTimezone`, `optimizationGoal`, `targetingProfileKey`, `placementProfileKey`, `desiredState` | `scheduleStart`, `scheduleEnd` | `entityType=AD_SET`; entity UUID equals `platformAdSetUuid`; profile keys `TW_BROAD_FEEDS_V1`; desired state `PAUSED`; budget and schedule follow stored policy |
| `CREATE_AD` | `platformAdUuid`, `platformAdSetUuid`, `productUuid`, `assetUuid`, `generationOutputUuid`, `reviewDecisionUuid`, `approvedChecksumSha256`, `creativeMappingKey`, `desiredState` | none | `entityType=AD`; entity UUID equals `platformAdUuid`; desired state `PAUSED`; evidence values equal the database-enforced Ad evidence chain |
| `PAUSE` | `expectedEntityVersion`, `targetDesiredState` | none | Any exact typed entity; target `PAUSED`; stored pre-change version equals expected version; only `ACTIVE -> PAUSED` |
| `RESUME` | `expectedEntityVersion`, `targetDesiredState` | none | Any exact typed entity; target `ACTIVE`; stored pre-change version equals expected version; only `PAUSED -> ACTIVE`; schema capability is inert until later human/security policy approval |
| `UPDATE_BUDGET` | `platformAdSetUuid`, `expectedEntityVersion`, `budgetType`, `currency`, `previousBudgetAmount`, `newBudgetAmount` | none | `entityType=AD_SET`; entity UUID equals `platformAdSetUuid`; type/currency/current amount/version must equal the stored pre-change row; new amount differs and passes the server-owned ceilings |

For every row, `schemaVersion` is JSON number `1`, `operationType` equals the column value, and the generic `entityUuid` equals the one non-null typed entity FK. `request_sha256` is lowercase `SHA-256(exact canonical request_payload UTF-8 bytes)`.

The durable request identity and adapter idempotency key are exact:

```text
identity = "platform-operation-v1\n"
         + lowercase(platformAccountUuid) + "\n"
         + requestedActorType + "\n"
         + NFC(requestedActorId) + "\n"
         + lowercase(clientRequestUuid)
idempotency_key = lowercaseHex(SHA-256(UTF-8(identity)))
```

`operation_uuid` is generated once by the server before Transaction A and is reused forever for that request identity. A repeated request identity loads the existing row and returns it without Audit or adapter invocation only when account, actor, client request UUID, operation type, entity type/UUID, canonical payload bytes, `request_sha256`, and `idempotency_key` all match. Any mismatch fails locally with `PLATFORM_IDEMPOTENCY_CONFLICT`; it neither inserts/replaces a row nor calls a port.

### Normalized result, error, and evidence contract

Write ports return exactly one closed `PlatformWriteOutcome` variant; reconciliation returns exactly one closed `PlatformReconciliationOutcome` variant. They never throw provider-specific checked exceptions or return null. The orchestration boundary catches unexpected adapter exceptions and maps them by the table below before persistence.

| Outcome record | Required fields | Optional fields | Operation transition |
| --- | --- | --- | --- |
| `WriteSucceeded` | `externalId` for create, `evidence` | `safeProviderTraceId`, `observedState`; `externalId` for non-create | `SUBMITTING -> SUCCEEDED` |
| `WriteRetryableFailure` | `errorCode`, `retryAfterSeconds`, `evidence` | `safeProviderTraceId` | `SUBMITTING -> FAILED_RETRYABLE` only when another attempt remains; otherwise `FAILED_TERMINAL` with `PLATFORM_MAX_ATTEMPTS_EXCEEDED` |
| `WriteTerminalFailure` | `errorCode`, `evidence` | `safeProviderTraceId` | `SUBMITTING -> FAILED_TERMINAL` |
| `WriteUnknownOutcome` | `errorCode`, `evidence` | `safeProviderTraceId` | `SUBMITTING -> UNKNOWN_OUTCOME` |
| `ReconciliationFound` | `externalId`, `evidence` | `safeProviderTraceId`, `observedState` | `RECONCILING -> SUCCEEDED` |
| `ReconciliationNotFound` | `errorCode=PLATFORM_RECONCILIATION_NOT_FOUND`, `evidence` | `safeProviderTraceId` | attempt `NOT_FOUND`; operation `RECONCILING -> UNKNOWN_OUTCOME` |
| `ReconciliationStillUnknown` | `errorCode=PLATFORM_RECONCILIATION_INCONCLUSIVE`, `evidence` | `safeProviderTraceId` | `RECONCILING -> UNKNOWN_OUTCOME` |
| `ReconciliationTerminalFailure` | `errorCode`, `evidence` | `safeProviderTraceId` | `RECONCILING -> FAILED_TERMINAL` |

`externalId` is 1–128 characters matching `^[A-Za-z0-9._:-]+$`. `safeProviderTraceId`, when present, uses the same safe character class and length. `observedState`, when present, is one of the normalized observed states. `retryAfterSeconds` is integer `1..3600`; the application computes `next_attempt_at = claimCompletionTime + retryAfterSeconds` and never accepts a provider timestamp.

`NormalizedPlatformEvidence` serializes to a JSON object with required keys `schemaVersion` (number `1`), `providerKey` (`FAKE` or future approved provider), `attemptKind` (`SUBMIT` or `RECONCILE`), and `resultKind` (`SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, `FOUND`, `NOT_FOUND`). Optional keys are only `externalIdFingerprint` (lowercase SHA-256 of external ID), `observedState`, and `retryAfterSeconds`; their presence must agree with the outcome table. No other key or nested object is allowed. The exact object is at most 8 KiB and is copied to the finalized attempt; `platform_operations.outcome_evidence` stores the latest finalized normalized evidence. Raw response/body/message/status/header/URL/account identifier is never retained.

Stable error mapping is exhaustive for 4A:

| Source condition | Stable code | Classification |
| --- | --- | --- |
| Fake/provider rate limit or explicit retry-after | `PLATFORM_RATE_LIMITED` | Retryable |
| Confirmed temporary unavailability before any ambiguous write receipt | `PLATFORM_TEMPORARILY_UNAVAILABLE` | Retryable |
| Normalized request rejected | `PLATFORM_VALIDATION_FAILED` | Terminal |
| Provider permission denial | `PLATFORM_PERMISSION_DENIED` | Terminal |
| Inactive/archived account | `PLATFORM_ACCOUNT_INACTIVE` | Local terminal before dispatch |
| Server policy or budget ceiling rejection | `PLATFORM_POLICY_REJECTED` | Local terminal before dispatch |
| Missing usable adapter | `PLATFORM_ADAPTER_UNAVAILABLE` | Local terminal before dispatch |
| Stale entity/operation version | `PLATFORM_STALE_VERSION` | Local conflict before dispatch |
| Repeated identity with different contract | `PLATFORM_IDEMPOTENCY_CONFLICT` | Local conflict before dispatch |
| Retry requested after limit | `PLATFORM_MAX_ATTEMPTS_EXCEEDED` | Terminal |
| Timeout, connection interruption after dispatch, malformed success, null/unknown result, or persistence uncertainty | `PLATFORM_RESPONSE_AMBIGUOUS` | Unknown outcome; reconcile only |
| Reconciliation proves no entity | `PLATFORM_RECONCILIATION_NOT_FOUND` | Not found; remains unknown |
| Reconciliation cannot prove found/not-found | `PLATFORM_RECONCILIATION_INCONCLUSIVE` | Still unknown |

No arbitrary code can be persisted: V12 checks the complete stable-code allowlist and the exact evidence top-level keys/types, while domain constructors enforce the same contract before persistence. Local validation, policy, adapter-availability, stale-version, and idempotency rejection occurs before Transaction A inserts a new operation, creates no STARTED attempt, and performs no entity mutation. Retry against an existing ineligible operation returns the stable local error without changing that operation. Once dispatch begins, any condition that cannot prove the write was not applied maps to unknown, never retryable.

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
| `revision_number` | `INTEGER` | NOT NULL | Append-only revision identity for the exact entity/window; starts at 1 and increases contiguously |
| `fetched_at` | `TIMESTAMPTZ` | NOT NULL | Source observation time |
| `freshness_status` | `VARCHAR(16)` | NOT NULL | `FRESH`, `DELAYED`, or `UNAVAILABLE` |
| `source_fingerprint` | `CHAR(64)` | NOT NULL | SHA-256 of the canonical normalized observation, excluding revision/fetch/persistence timestamps |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `CURRENT_TIMESTAMP` | Immutable |

Constraints/indexes:

- Typed entity/account composite FKs and exact-one-entity check.
- Nonnegative nullable metrics; currency, attribution, window, timezone, freshness, and fingerprint checks.
- For each entity type, one partial unique revision index over account, the typed entity UUID, UTC window, timezone, attribution, currency, and `revision_number`.
- For each entity type, one partial unique duplicate-evidence index over the same window identity plus `source_fingerprint`.
- Three partial entity/account lookup indexes.
- Entire row is append-only; update and delete triggers reject mutation.

The model is exactly append-only revisions; it is not one canonical mutable row per window. The canonical window identity is `(platform_account_uuid, typed entity UUID, window_start, window_end, timezone, attribution_click_days, attribution_view_days, currency)`. A V12 insert trigger requires the first revision for an identity to be `1`; every later insert must use `MAX(revision_number) + 1` and a strictly later `fetched_at`. PostgreSQL uniqueness arbitrates concurrent attempts. `source_fingerprint` is lowercase SHA-256 of canonical JSON containing only `entityType`, the typed entity UUID, UTC window, timezone, attribution, currency, nullable base metrics, and freshness; JSON null is retained, object keys are lexically sorted, and `revisionNumber`, `fetchedAt`, `createdAt`, and derived metrics are excluded. Therefore a delayed/corrected observation with changed normalized values creates the next revision, while an exact duplicate fails regardless of a different fetch time.

The latest snapshot for a window is the row with greatest `revision_number`; an as-of read selects the greatest revision whose `fetched_at <= asOf`. No revision is updated, deleted, or silently replaced. The table is intentionally inert in 4A; no poller or metrics read port is defined or implemented until 4D. CTR/CPC/CPM/CPA/CVR/ROAS are derived later from the selected revision's stored base values only when denominators are valid; they are not persisted in V12.

## Database trigger contract

V12 creates narrowly named trigger functions and per-table triggers; it must not overload V1–V11 functions.

- Every new table rejects hard delete with SQLSTATE `23514`.
- Accounts: identity/config fields immutable; only `ACTIVE -> ARCHIVED` is allowed; effective transition increments version exactly once.
- Campaign/Ad/Ad Set policy identity and configuration fields are immutable; Ad Set `budget_amount` and `last_budget_operation_uuid` are the sole budget-mutation pair and must satisfy the deferred successful-operation coherence rule. External ID is write-once; observed state may follow normalized observation transitions; desired-state transitions follow the exact machine below; version increments exactly once for any effective update.
- Operations: immutable input and idempotency fields; exact state transitions; claim counter/timestamp coherence; terminal rows reject any update; external ID is write-once.
- Attempts: one finalization only; then immutable.
- Metrics: append-only numbered revisions; the first/next revision, monotonic fetch time, and exact-duplicate fingerprint rules are database-enforced.
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

Exact field mutation by edge:

- Claim from `CREATED` or eligible `FAILED_RETRYABLE`: set `status=SUBMITTING`, increment `attempt_count`, set `claimed_at=now`, clear `next_attempt_at`, clear the operation's prior normalized error/trace/outcome evidence, increment version, and insert `SUBMIT/STARTED` with `attempt_number=attempt_count` in the same transaction.
- Submit success: set `status=SUCCEEDED`, set `completed_at`, persist normalized external/trace/evidence, clear error/retry fields, increment version, and apply matching bounded entity evidence. `UPDATE_BUDGET` also changes `budget_amount` and `last_budget_operation_uuid` in this same transaction using the payload's expected entity version.
- Retryable result with attempts remaining: set `status=FAILED_RETRYABLE`, persist retryable code/trace/evidence, set server-computed `next_attempt_at`, keep `completed_at` null, and increment version. When no attempt remains, use `FAILED_TERMINAL` and `PLATFORM_MAX_ATTEMPTS_EXCEEDED` instead.
- Terminal result: set `status=FAILED_TERMINAL`, persist terminal code/trace/evidence and `completed_at`, clear retry time, and increment version.
- Ambiguous result: set `status=UNKNOWN_OUTCOME`, persist ambiguity code/trace/evidence, keep completion/retry time null, and increment version. No submit edge exists from this state.
- Reconcile claim: from `UNKNOWN_OUTCOME`, set `RECONCILING`, increment `reconciliation_count`, set `claimed_at=now`, clear the prior normalized error/trace/outcome evidence, increment version, and atomically insert `RECONCILE/STARTED` numbered from the reconciliation count.
- Reconcile found/terminal/still-unknown/not-found finalizes the attempt and operation exactly as the normalized outcome table specifies. NOT_FOUND never changes desired state, budget, or external ID and never creates submit eligibility.

### Attempt

`STARTED` transitions exactly once to one result. A SUBMIT attempt may end `SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, or `UNKNOWN_OUTCOME`. A RECONCILE attempt may end `SUCCEEDED`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, or `NOT_FOUND`. `NOT_FOUND` means no matching external entity was proven; it is not authority to resubmit the write. The containing operation stays `UNKNOWN_OUTCOME` pending an explicit human-safe resolution policy in a later milestone.

## Optimistic locking and transaction boundaries

All mutable aggregates use `@Version`. Worker claim uses a version-qualified update or a JPA flush that guarantees exactly one claimant. No pessimistic database lock is held across an adapter call.

Required sequence:

1. Transaction A validates canonical input, trusted actor, account state, exact local references, immutable policy, budget ceiling, and expected entity version; inserts the paused entity if a create command requires it; inserts `platform_operations` in `CREATED`; writes same-operation Audit; commits. Duplicate identity resolution happens here and never dispatches twice.
2. Transaction B claims the exact operation version, moves it to `SUBMITTING`, increments `attempt_count`, inserts a `SUBMIT/STARTED` attempt, writes Audit, and commits.
3. The application invokes the adapter after Transaction B. `TransactionSynchronizationManager.isActualTransactionActive()` must be false inside the adapter.
4. Transaction C locks the operation and entity by the exact expected optimistic versions, finalizes the attempt, transitions the operation, applies bounded normalized entity evidence (including the budget/provenance pair for successful `UPDATE_BUDGET`), writes Audit, and commits atomically. A stale entity version cannot partially finalize a supposedly successful mutation; if the adapter may already have written, recovery records `UNKNOWN_OUTCOME` and reconciles.
5. If response receipt is ambiguous or Transaction C cannot prove the result, persist/recover `UNKNOWN_OUTCOME`; do not resubmit.

Reconciliation uses the analogous claim/call/finalize sequence with `RECONCILING` and a RECONCILE attempt. Adapter exceptions are normalized outside persistence; raw exception text is not written to the database or Audit.

## Provider-neutral ports

The following are the only provider-facing interfaces approved for 4A. Method names, parameter records, and return families are exact; implementations may not add an arbitrary context/map or leak SDK types:

```java
interface PlatformCampaignPort {
    PlatformWriteOutcome submitCampaign(PlatformCampaignCommand command);
    PlatformWriteOutcome changeCampaignState(PlatformStateMutationCommand command);
}

interface PlatformAdSetPort {
    PlatformWriteOutcome submitAdSet(PlatformAdSetCommand command);
    PlatformWriteOutcome changeAdSetState(PlatformStateMutationCommand command);
    PlatformWriteOutcome updateAdSetBudget(PlatformBudgetMutationCommand command);
}

interface PlatformAdPort {
    PlatformWriteOutcome submitAd(PlatformAdCommand command);
    PlatformWriteOutcome changeAdState(PlatformStateMutationCommand command);
}

interface PlatformOperationReconciliationPort {
    PlatformReconciliationOutcome reconcile(PlatformReconciliationQuery query);
}
```

Typed command records have these exact required fields; a question mark means optional and all unmarked fields are required:

| Record | Fields |
| --- | --- |
| `PlatformCommandIdentity` | `operationUuid`, `platformAccountUuid`, `idempotencyKey`, `requestSha256` |
| `PlatformCampaignCommand` | identity fields; `platformCampaignUuid`, `campaignUuid`, `objective`, `desiredState`, `scheduleStart?`, `scheduleEnd?`, `accountTimezone` |
| `PlatformAdSetCommand` | identity fields; `platformAdSetUuid`, `platformCampaignUuid`, `budgetType`, `budgetAmount`, `currency`, `scheduleStart?`, `scheduleEnd?`, `accountTimezone`, `optimizationGoal`, `targetingProfileKey`, `placementProfileKey`, `desiredState` |
| `PlatformAdCommand` | identity fields; `platformAdUuid`, `platformAdSetUuid`, `productUuid`, `assetUuid`, `generationOutputUuid`, `reviewDecisionUuid`, `approvedChecksumSha256`, `creativeMappingKey`, `desiredState` |
| `PlatformStateMutationCommand` | identity fields; `entityType`, `entityUuid`, `expectedEntityVersion`, `targetDesiredState` |
| `PlatformBudgetMutationCommand` | identity fields; `platformAdSetUuid`, `expectedEntityVersion`, `budgetType`, `currency`, `previousBudgetAmount`, `newBudgetAmount` |
| `PlatformReconciliationQuery` | identity fields; `operationType`, `entityType`, `entityUuid`, `submitAttemptCount`, `reconciliationAttemptNumber`, `knownExternalId?` |

Every command is reconstructed from the immutable canonical operation payload and database row; no caller-supplied object is passed through after Transaction A. `expectedEntityVersion` is nonnegative. Money uses scale at most six. Optional schedule fields must be both absent or form the approved valid range; `knownExternalId` is present only when already stored. Commands contain no actor credentials, account raw ID, provider payload, URL, raw evidence, or fixture selector.

Internal orchestration methods are also exact and are not web endpoints:

```java
PlatformOperationView submit(UUID operationUuid, long expectedOperationVersion);
PlatformOperationView retry(UUID operationUuid, long expectedOperationVersion, Instant now);
PlatformOperationView reconcile(UUID operationUuid, long expectedOperationVersion);
```

`submit` accepts only `CREATED`; `retry` accepts only an eligible `FAILED_RETRYABLE`, uses the same operation/entity/payload/idempotency identity, and never creates a replacement row; `reconcile` accepts only `UNKNOWN_OUTCOME`. All use compare-and-set version claims. There is no unattended scheduler or automatic retry in 4A; tests invoke these internal methods explicitly.

Server-owned policy contracts are exact and fail closed:

```java
interface PlatformAccountPolicyProvider {
    PlatformAccountPolicy requirePolicy(UUID platformAccountUuid);
}

interface PlatformBudgetPolicyProvider {
    PlatformBudgetPolicy requirePolicy(UUID platformAccountUuid, BudgetType budgetType, LocalDate accountBusinessDate);
}
```

`PlatformAccountPolicy` contains `platformAccountUuid`, `providerKey`, `environment`, `currency`, `timezone`, `active`; `PlatformBudgetPolicy` contains `currency`, `budgetType`, `maxEntityAmount`, `maxOperationBatchAmount`, `maxAccountBusinessDayAmount`. The 4A implementation may supply only deterministic LOCAL/TEST fixtures matching the approved TWD ceilings. Absence or mismatch returns no permissive default and maps to `PLATFORM_POLICY_REJECTED` before dispatch.

`PlatformDeliveryReadPort`, `PlatformMetricsReadPort`, and every credential/secret contract are explicitly deferred and must not exist, even as marker interfaces, in 4A. They require their own later approved methods and security boundaries. Port calls remain reachable only from the internal 4A orchestration service; there is no controller, scheduler, event listener, AI dependency, Decision Engine dependency, credential lookup, or network client.

## Deterministic fake adapter contract

- Registered only under `(local | test) & !production` and an explicit fake-platform profile/property.
- Uses no HTTP/library SDK, filesystem credential, environment secret, random timing, DNS, socket, or paid service.
- Validates the same normalized request limits as the application port contract.
- Derives create IDs exactly as `prefix + first24(lowercaseHex(SHA-256(UTF-8("fake-platform-id-v1\n" + lowercase(operationUuid) + "\n" + idempotencyKey + "\n" + requestSha256))))`. Prefix is `fake-campaign-` for Campaign, `fake-adset-` for Ad Set, and `fake-ad-` for Ad. State and budget mutations return the entity's existing ID and never derive a replacement.
- Repeated submit with the same identity returns the same external ID; a different payload for the same identity returns an idempotency conflict.
- Server-owned fixtures map exactly: `SUCCESS -> WriteSucceeded`; `RETRYABLE_RATE_LIMIT -> WriteRetryableFailure(PLATFORM_RATE_LIMITED, 60)`; `TERMINAL_VALIDATION -> WriteTerminalFailure(PLATFORM_VALIDATION_FAILED)`; `TERMINAL_PERMISSION -> WriteTerminalFailure(PLATFORM_PERMISSION_DENIED)`; `MALFORMED_RESULT -> WriteUnknownOutcome(PLATFORM_RESPONSE_AMBIGUOUS)`; `AMBIGUOUS_TIMEOUT -> WriteUnknownOutcome(PLATFORM_RESPONSE_AMBIGUOUS)`; `RECONCILE_FOUND -> ReconciliationFound` with the same deterministic ID; `RECONCILE_NOT_FOUND -> ReconciliationNotFound`; and `RECONCILE_STILL_UNKNOWN -> ReconciliationStillUnknown`.
- Fixture selection is constructor/test configuration, not persisted arbitrary input and never a Browser/provider-origin field.
- Records invocation count and whether a Spring transaction was active so tests prove single submission and transaction separation.
- Default and production profiles expose no usable write adapter; startup or operation dispatch fails closed with `PLATFORM_ADAPTER_UNAVAILABLE`.

Contract tests must run the same test suite against every implementation of a write or reconciliation port. A future Meta adapter cannot weaken the fake contract.

The shared contract suite asserts each exact method and DTO field, absent/null rejection, canonical hash and idempotency replay, the three exact ID prefixes and 24-hex suffix, each stable outcome/error/evidence mapping, retry-after bounds, no replacement ID on mutation, reconciliation identity reuse, one invocation for concurrent/replayed commands, and absence of provider/secret/network types. An unexpected exception or null/unrecognized result is normalized to `PLATFORM_RESPONSE_AMBIGUOUS`; it can never become a retryable result after dispatch.

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
| Direct SQL identity/evidence | Attempts to mutate every account/entity/operation identity, write-once external ID, canonical payload/hash, or attempt identity fail. Ad inserts independently reject mismatched Asset/output (`23503` or semantic `23514` as specified), TEXT output, pending/rejected review, rejected decision, blocked preservation, inactive Product/Asset, null/mismatched Asset/output/snapshot checksum, and later evidence substitution (`23514`) |
| Direct SQL lifecycle | Invalid desired/observed/operation/attempt transitions, attempt counter drift, terminal updates, account restore, and version jumps fail |
| Budget mutation | Initial provenance is null; exact successful bounded `UPDATE_BUDGET` changes only amount/provenance/version; stale version, type/currency/policy change, missing/wrong/reused/non-success operation, unchanged/out-of-bound amount, direct amount update, and Audit rollback fail; concurrent changes have one winner |
| Delete protection | DELETE from each of seven tables fails; referenced V1–V11 records remain protected by `ON DELETE RESTRICT`; Audit remains append-only |
| Metric append-only revisions | Revision 1 and a changed revision 2 coexist; latest/as-of selection is deterministic; skipped/repeated revision, non-monotonic fetch time, exact duplicate fingerprint, negative/base invalid values, update, and delete fail; nullable metrics remain NULL in every revision |
| Domain unit | All valid and invalid state-machine edges, exact payload keys/value formats, canonical bytes/hash/idempotency construction, budget bounds, evidence checksum, metric fingerprint/revision, timezone/currency, profiles, and exhaustive normalized result/error mapping |
| Port contract | Every exact method/record field and required/optional rule; three fake ID prefixes plus 24-hex suffix; success, rate limit, validation, permission, malformed/null result, ambiguous timeout, idempotent replay/conflict, retry identity, reconcile found/not-found/unknown, stable evidence schema, and no provider/secret/network types escaping |
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
- Metrics are append-only numbered revisions. A changed delayed/corrected observation creates the next contiguous revision and latest/as-of selection follows the exact rule above; an exact duplicate is rejected and no prior revision is overwritten. Attempt rows remain immutable except for their one allowed STARTED-to-result finalization, after which their history cannot be updated or deleted.

## Warnings and known limitations

- The V12 model includes inert Campaign, Ad Set, Ad, and metric structures so later milestones do not redesign operation identity. 4A does not expose or schedule those capabilities.
- Database constraints cannot prove an IANA timezone name or independently recompute every canonical JSON hash; exact domain canonicalization, trigger coherence, and contract tests are mandatory together. Ad evidence semantics and metric revision identity remain database-enforced as specified.
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
- [ ] Budget policy identity remains immutable; each amount change has bounded canonical command evidence, optimistic concurrency, a successful operation pointer, and same-transaction Audit.
- [ ] Duplicate and concurrent commands cannot create a second operation/entity or second provider call.
- [ ] Every adapter call follows a committed operation and STARTED attempt and runs outside a database transaction.
- [ ] Ambiguous submission can only reconcile; it cannot blindly submit again.
- [ ] Deterministic fake adapter contract and all migration/Hibernate/direct-SQL/concurrency/Audit/security tests pass.
- [ ] No REST/UI/network/credential/auth/RBAC/tenant/production/spend or Stage 4B+ implementation exists.
- [ ] Full local and Remote CI pass on the exact implementation Head.
- [ ] Independent Manager Review records `APPROVE`, the implementation PR merges, and post-merge main CI passes before Milestone 4B unlocks.
