# Stage 04 Milestone 4A — Platform Persistence and Operation Foundation

## Gate status

- Status: Technical specification corrections for findings 4A-SPEC-005 through 011 completed; implementation not started
- Branch: `codex/stage-04a-platform-foundation-specification`
- Base Commit: `a90f6e0bb20d23da10edb66712d85261dafe14e8`
- Prerequisite: Stage 04 specification merged by PR #56; post-merge main CI Run `31921389709` passed
- Specification: Manager Decision remains `REQUEST_CHANGES`; Developer corrections are pending new exact-head Independent Manager re-review
- Implementation: Not started
- Migration: Not started; the approved implementation may add only `V12__create_platform_operation_foundation.sql`
- Local Verification: Passed — two-file scope, Markdown table sanity, `git diff --check`, and pinned Gitleaks 8.28.0 history/worktree scans
- Remote CI: Pending
- Manager Review: Re-review required after correction Push/PR CI
- Manager Decision: `REQUEST_CHANGES` on re-reviewed candidate Head `b8781c224748cceac7ec45706382e73c8f592825`; Developer correction Head pending decision
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
| `provider_key` | `VARCHAR(32)` | NOT NULL | Exactly `FAKE` in V12/4A; immutable |
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

The 4A provider matrix is closed:

| Persisted provider | Persisted environment | Active application profile | Adapter | Result |
| --- | --- | --- | --- | --- |
| `FAKE` | `LOCAL` | `local` and explicit `platform.adapter=fake` | deterministic fake | permitted |
| `FAKE` | `TEST` | `test` and explicit `platform.adapter=fake` | deterministic fake | permitted |
| `FAKE` | `LOCAL` | `test`, or `FAKE/TEST` under `local` | none | reject `PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH` before claim |
| any other provider/environment, including `META` or `PRODUCTION` | any | any | none | database insert rejects with `23514`; dispatch rejects `PLATFORM_PROVIDER_UNSUPPORTED` if an impossible legacy/corrupt row is observed |
| any persisted account | default or `production` | any | none | reject `PLATFORM_ADAPTER_UNAVAILABLE` before claim |

V12 checks `provider_key = 'FAKE'` and `environment IN ('LOCAL','TEST')`; there is no dormant `META` row. The dispatcher is a total switch over the persisted provider and environment and has no fallback adapter. Every persisted operation/attempt evidence object has `providerKey='FAKE'`; deferred evidence-coherence triggers compare that JSON value to the referenced account's `provider_key` and reject missing, non-FAKE, or mismatched evidence with `23514`. Adding `META`, `PRODUCTION`, another adapter, or another evidence provider requires a later approved migration/specification and the applicable human gate.

Contract tests cover both permitted rows, profile/environment cross-mismatch, missing explicit fake configuration, unsupported provider dispatch, default/production fail-closed behavior, and absence of fallback selection. Direct-SQL tests reject `META`, `PRODUCTION`, and evidence whose `providerKey` is absent, non-FAKE, or differs from its operation account.

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

The internal 4A fixture is fail-closed and TWD-only. The only enforced accounting rule in 4A is the current per-entity bound: `DAILY` requires `0 < budget_amount <= 100.000000`; `LIFETIME` requires `0 < budget_amount <= 300.000000`. V12 enforces this exact bound with a row CHECK as well as domain policy. Both increases and decreases are allowed only when the new value differs, remains positive and within the same immutable budget type's bound, and uses the successful one-operation/one-version coherence above. Replay of the same client identity returns the existing operation and consumes/applies nothing again; concurrent distinct changes use the stored expected Ad Set version and exactly one can commit.

The approved TWD `300.000000` operation-batch ceiling and TWD `1000.000000` account-business-day ceiling are explicitly deferred to 4B because 4A has no batch command, ledger/reservation table, external spend, scheduler, or account-day execution boundary. No 4A code may pretend to enforce or expose either aggregate. 4B must separately specify the exact consumed amount, included operation types, decrease/release semantics, `Asia/Taipei` business-date derivation, replay treatment, transactionally locked reservation/ledger model, and concurrency tests before those ceilings become executable. A different currency, missing policy, stale version, per-entity ceiling violation, or inactive account fails before adapter dispatch with a stable local error and produces no budget mutation. This deferral does not authorize higher aggregates, external writes, or spend.

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

V12 adopts **creation-time snapshot semantics**, not ongoing current-row coherence. It must enforce the full evidence chain in PostgreSQL at the transaction that inserts the Ad, not only in application validation. It creates a narrowly named `verify_platform_ad_evidence_snapshot()` function and a `DEFERRABLE INITIALLY DEFERRED` constraint trigger after INSERT on `platform_ads`. At deferred execution the function locks the referenced Product, Asset, generation output, and decision rows with `FOR SHARE` and rejects unless all of the following are true at that insertion transaction boundary:

1. The Product exists and `lifecycle_status = 'ACTIVE'`.
2. The Asset exists, belongs to that Product, has `asset_type = 'IMAGE'`, has `lifecycle_status = 'ACTIVE'`, and has a non-null lowercase SHA-256 checksum.
3. The generation output exists, belongs to that Product, has `generation_type = 'IMAGE'`, references exactly that Asset as `generated_asset_uuid`, has `review_status = 'APPROVED'`, has `preservation_status = 'PASSED'`, and has `output_checksum_sha256` equal to both the current Asset checksum and `platform_ads.approved_checksum_sha256`.
4. The review decision exists for exactly that generation output and has `decision = 'APPROVED'`. V11's existing deferred review-coherence trigger remains authoritative for decision/output version coherence.
5. The immutable Ad evidence columns and checksum snapshot cannot be updated after insert; the ordinary protection trigger rejects any attempted substitution.

The trigger raises SQLSTATE `23514` for semantic evidence incoherence; missing/mismatched composite references continue to fail with SQLSTATE `23503`. Application validation repeats the same checks in Transaction A for early errors, but it is defense in depth and never replaces database enforcement.

After a valid Ad row commits, later Product archival or Asset lifecycle/type/checksum mutation does **not** invalidate, update, or delete that historical Ad snapshot and does not invoke reciprocal V12 triggers. Divergence is deterministic: the row continues to mean “this chain was valid at `platform_ads.created_at` with this immutable approved checksum,” while any new Ad insertion or any later-milestone dispatch/activation using the evidence must revalidate current Product/Asset/output/decision state and reject divergence. 4A exposes no dispatch of an already-created Ad to a real provider. Direct-SQL tests must independently reject all invalid insert cases above and post-insert evidence substitution; separate snapshot tests must then archive the Product and mutate an otherwise V4-permitted Asset lifecycle/type/checksum, prove the existing Ad row/snapshot remains unchanged, and prove a second Ad insert against the now-diverged current rows fails. Ongoing-current-row coherence is not claimed.

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
| `max_attempts` | `INTEGER` | NOT NULL DEFAULT `3` | Exactly `3` for every 4A operation; immutable |
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

Every operation INSERT is structurally an unclaimed command: `status='CREATED'`, `attempt_count=0`, `reconciliation_count=0`, `max_attempts=3`, `external_id`, `normalized_error_code`, `safe_provider_trace_id`, `outcome_evidence`, `next_attempt_at`, `claimed_at`, and `completed_at` are NULL, and `version=0`. A BEFORE INSERT trigger rejects any constructed pre-claimed, pre-failed, pre-succeeded, pre-reconciled, or nonzero-version row with `23514`; column defaults are not sufficient evidence.

V12 also creates `verify_platform_operation_attempt_coherence()` with reciprocal `DEFERRABLE INITIALLY DEFERRED` constraint triggers on operation INSERT/status/counter changes and attempt INSERT/finalization. At commit: `SUBMITTING` has exactly one matching `SUBMIT/STARTED` attempt numbered `attempt_count`; a directly `SUCCEEDED` operation with `reconciliation_count=0` has a matching finalized `SUBMIT/SUCCEEDED` attempt; a reconciled `SUCCEEDED` operation has its latest `SUBMIT/UNKNOWN_OUTCOME` attempt plus latest `RECONCILE/SUCCEEDED` attempt; every other finalized operation status agrees with the latest attempt kind/number/status and normalized error/evidence; and no attempt number exceeds its operation counter. Direct SQL must not produce success, failure, unknown, or reconciliation state without the corresponding durable attempt history.

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
| `ReconciliationTerminalFailure` | `errorCode=PLATFORM_RECONCILIATION_TERMINAL`, `evidence` | `safeProviderTraceId` | `RECONCILING -> FAILED_TERMINAL` |

`externalId` is 1–128 characters matching `^[A-Za-z0-9._:-]+$`. `safeProviderTraceId`, when present, uses the same safe character class and length. `observedState`, when present, is one of the normalized observed states. `retryAfterSeconds` is integer `1..3600`; the application computes `next_attempt_at = claimCompletionTime + retryAfterSeconds` and never accepts a provider timestamp.

`NormalizedPlatformEvidence` serializes to a JSON object with required keys `schemaVersion` (number `1`), `providerKey` (exactly `FAKE`), `attemptKind` (`SUBMIT` or `RECONCILE`), and `resultKind` (`SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, `FOUND`, `NOT_FOUND`, `STILL_UNKNOWN`). Optional keys are only `externalIdFingerprint` (lowercase SHA-256 of external ID), `observedState`, and `retryAfterSeconds`; their presence must agree with the outcome and typed declarations. No other key or nested object is allowed. V12 checks provider is FAKE and deferred triggers require it to equal the operation account provider. The exact object is at most 8 KiB and is copied to the finalized attempt; `platform_operations.outcome_evidence` stores the latest finalized normalized evidence. Raw response/body/message/status/header/URL/account identifier is never retained.

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
| Reconciliation proves a non-recoverable normalized failure | `PLATFORM_RECONCILIATION_TERMINAL` | Reconciliation terminal |

No arbitrary code can be persisted. V12 `normalized_error_code` permits exactly the outcome subset `PLATFORM_RATE_LIMITED`, `PLATFORM_TEMPORARILY_UNAVAILABLE`, `PLATFORM_VALIDATION_FAILED`, `PLATFORM_PERMISSION_DENIED`, `PLATFORM_MAX_ATTEMPTS_EXCEEDED`, `PLATFORM_RESPONSE_AMBIGUOUS`, `PLATFORM_RECONCILIATION_NOT_FOUND`, `PLATFORM_RECONCILIATION_INCONCLUSIVE`, and `PLATFORM_RECONCILIATION_TERMINAL`; status/attempt/evidence triggers enforce their exact classifications. The remaining `PlatformStableErrorCode` members are local exception/view codes and cannot be persisted as an operation outcome. Domain constructors enforce the same boundary. Local validation, policy, adapter-availability, stale-version, environment/provider mismatch, and idempotency rejection occurs before Transaction A inserts a new operation or before an existing operation is claimed, creates no STARTED attempt, and performs no entity mutation. Retry against an ineligible operation returns the stable local error without changing that operation. Once dispatch begins, any condition that cannot prove the write was not applied maps to unknown, never retryable.

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
- A deferred `verify_platform_metric_account_coherence()` constraint trigger locks the referenced account and requires `platform_metric_snapshots.currency = platform_accounts.currency`, `timezone = platform_accounts.timezone`, and the account to be `ACTIVE`; mismatch fails with `23514`.

The model is exactly append-only revisions; it is not one canonical mutable row per window. The canonical window identity is `(platform_account_uuid, typed entity UUID, window_start, window_end, timezone, attribution_click_days, attribution_view_days, currency)`. A V12 insert trigger requires the first revision for an identity to be `1`; every later insert must use `MAX(revision_number) + 1` and a strictly later `fetched_at`. PostgreSQL uniqueness arbitrates concurrent attempts. The deferred account trigger makes the identity's currency/timezone database-coherent with the immutable referenced account; application validation performs the same check for early failure. `source_fingerprint` is lowercase SHA-256 of canonical JSON containing only `entityType`, the typed entity UUID, UTC window, timezone, attribution, currency, nullable base metrics, and freshness; JSON null is retained, object keys are lexically sorted, and `revisionNumber`, `fetchedAt`, `createdAt`, and derived metrics are excluded. Therefore a delayed/corrected observation with changed normalized values creates the next revision, while an exact duplicate fails regardless of a different fetch time.

The latest snapshot for a window is the row with greatest `revision_number`; an as-of read selects the greatest revision whose `fetched_at <= asOf`. No revision is updated, deleted, or silently replaced. The table is intentionally inert in 4A; no poller or metrics read port is defined or implemented until 4D. CTR/CPC/CPM/CPA/CVR/ROAS are derived later from the selected revision's stored base values only when denominators are valid; they are not persisted in V12.

## Database trigger contract

V12 creates narrowly named trigger functions and per-table triggers; it must not overload V1–V11 functions.

- Every new table rejects hard delete with SQLSTATE `23514`.
- Accounts: identity/config fields immutable; only `ACTIVE -> ARCHIVED` is allowed; effective transition increments version exactly once.
- Campaign/Ad/Ad Set policy identity and configuration fields are immutable; Ad Set `budget_amount` and `last_budget_operation_uuid` are the sole budget-mutation pair and must satisfy the deferred successful-operation coherence rule. External ID is write-once; observed state may follow normalized observation transitions; desired-state transitions follow the exact machine below; version increments exactly once for any effective update.
- Operations: INSERT is CREATED-only with exact zero/null/version defaults; immutable input and idempotency fields; exact state transitions; claim counter/timestamp coherence; deferred reciprocal operation/attempt coherence; terminal rows reject any update; external ID is write-once.
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

The Java contract is exact. Package placement may follow repository conventions, but names, nesting, types, enum members, and Optional/null rules may not change without a new specification review:

```java
enum ProviderKey { FAKE }
enum PlatformEnvironment { LOCAL, TEST }
enum PlatformObjective { OUTCOME_SALES }
enum PlatformOperationType { CREATE_CAMPAIGN, CREATE_AD_SET, CREATE_AD, PAUSE, RESUME, UPDATE_BUDGET }
enum PlatformEntityType { CAMPAIGN, AD_SET, AD }
enum PlatformBudgetType { DAILY, LIFETIME }
enum PlatformDesiredState { DRAFT, PAUSED, ACTIVE, ARCHIVED }
enum PlatformObservedState { UNKNOWN, PENDING, PAUSED, ACTIVE, COMPLETED, REJECTED, ERROR, DELETED }
enum PlatformOperationStatus { CREATED, SUBMITTING, SUCCEEDED, FAILED_RETRYABLE, FAILED_TERMINAL, UNKNOWN_OUTCOME, RECONCILING }
enum PlatformAttemptKind { SUBMIT, RECONCILE }
enum PlatformEvidenceResultKind { SUCCEEDED, FAILED_RETRYABLE, FAILED_TERMINAL, UNKNOWN_OUTCOME, FOUND, NOT_FOUND, STILL_UNKNOWN }
enum PlatformStableErrorCode {
    PLATFORM_RATE_LIMITED, PLATFORM_TEMPORARILY_UNAVAILABLE,
    PLATFORM_VALIDATION_FAILED, PLATFORM_PERMISSION_DENIED,
    PLATFORM_RESPONSE_AMBIGUOUS, PLATFORM_RECONCILIATION_NOT_FOUND,
    PLATFORM_RECONCILIATION_INCONCLUSIVE, PLATFORM_RECONCILIATION_TERMINAL,
    PLATFORM_CONTRACT_INVALID, PLATFORM_OPERATION_NOT_FOUND,
    PLATFORM_INVALID_OPERATION_STATE, PLATFORM_STALE_VERSION,
    PLATFORM_RETRY_NOT_DUE, PLATFORM_MAX_ATTEMPTS_EXCEEDED,
    PLATFORM_MAX_RECONCILIATIONS_EXCEEDED, PLATFORM_ACCOUNT_INACTIVE,
    PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH, PLATFORM_PROVIDER_UNSUPPORTED,
    PLATFORM_POLICY_REJECTED, PLATFORM_ADAPTER_UNAVAILABLE,
    PLATFORM_IDEMPOTENCY_CONFLICT, PLATFORM_EVIDENCE_INVALID
}

record PlatformCommandIdentity(
        UUID operationUuid,
        UUID platformAccountUuid,
        String idempotencyKey,
        String requestSha256) {}

record PlatformCampaignCommand(
        PlatformCommandIdentity identity,
        UUID platformCampaignUuid,
        UUID campaignUuid,
        PlatformObjective objective,
        PlatformDesiredState desiredState,
        Optional<Instant> scheduleStart,
        Optional<Instant> scheduleEnd,
        String accountTimezone) {}

record PlatformAdSetCommand(
        PlatformCommandIdentity identity,
        UUID platformAdSetUuid,
        UUID platformCampaignUuid,
        PlatformBudgetType budgetType,
        BigDecimal budgetAmount,
        String currency,
        Optional<Instant> scheduleStart,
        Optional<Instant> scheduleEnd,
        String accountTimezone,
        String optimizationGoal,
        String targetingProfileKey,
        String placementProfileKey,
        PlatformDesiredState desiredState) {}

record PlatformAdCommand(
        PlatformCommandIdentity identity,
        UUID platformAdUuid,
        UUID platformAdSetUuid,
        UUID productUuid,
        UUID assetUuid,
        UUID generationOutputUuid,
        UUID reviewDecisionUuid,
        String approvedChecksumSha256,
        String creativeMappingKey,
        PlatformDesiredState desiredState) {}

record PlatformStateMutationCommand(
        PlatformCommandIdentity identity,
        PlatformEntityType entityType,
        UUID entityUuid,
        long expectedEntityVersion,
        PlatformDesiredState targetDesiredState) {}

record PlatformBudgetMutationCommand(
        PlatformCommandIdentity identity,
        UUID platformAdSetUuid,
        long expectedEntityVersion,
        PlatformBudgetType budgetType,
        String currency,
        BigDecimal previousBudgetAmount,
        BigDecimal newBudgetAmount) {}

record PlatformReconciliationQuery(
        PlatformCommandIdentity identity,
        PlatformOperationType operationType,
        PlatformEntityType entityType,
        UUID entityUuid,
        int submitAttemptCount,
        int reconciliationAttemptNumber,
        Optional<String> knownExternalId) {}
```

Every record reference, enum, UUID, String, BigDecimal, and Optional container is non-null. Absence is represented only by `Optional.empty()` in the explicitly Optional fields; raw null is rejected by the compact constructor with `IllegalArgumentException(PLATFORM_CONTRACT_INVALID)`. Schedule optionals are both empty or both present with end after start. `expectedEntityVersion >= 0`; submit/reconciliation counts are positive when dispatched; money is positive, scale `0..6`, and normalized without rounding. Hashes are exactly 64 lowercase hex; currency is exactly three uppercase ASCII letters; timezone/profile/mapping strings follow their persisted closed rules. Commands contain no actor credential, raw account ID, provider payload, URL, raw evidence, map, JSON node, fixture selector, or SDK type.

Every command is reconstructed from the immutable canonical operation payload and database row after Transaction A. No caller-supplied record instance crosses the persistence boundary unchanged.

Outcome and evidence types are also exact and closed:

```java
enum PlatformRetryableCode { PLATFORM_RATE_LIMITED, PLATFORM_TEMPORARILY_UNAVAILABLE }
enum PlatformWriteTerminalCode { PLATFORM_VALIDATION_FAILED, PLATFORM_PERMISSION_DENIED }
enum PlatformUnknownCode { PLATFORM_RESPONSE_AMBIGUOUS }
enum PlatformReconciliationTerminalCode { PLATFORM_RECONCILIATION_TERMINAL }

record NormalizedPlatformEvidence(
        int schemaVersion,
        ProviderKey providerKey,
        PlatformAttemptKind attemptKind,
        PlatformEvidenceResultKind resultKind,
        Optional<String> externalIdFingerprint,
        Optional<PlatformObservedState> observedState,
        Optional<Integer> retryAfterSeconds) {}

sealed interface PlatformWriteOutcome permits WriteSucceeded, WriteRetryableFailure,
        WriteTerminalFailure, WriteUnknownOutcome {}
record WriteSucceeded(Optional<String> externalId,
        Optional<String> safeProviderTraceId,
        Optional<PlatformObservedState> observedState,
        NormalizedPlatformEvidence evidence) implements PlatformWriteOutcome {}
record WriteRetryableFailure(PlatformRetryableCode errorCode, int retryAfterSeconds,
        Optional<String> safeProviderTraceId,
        NormalizedPlatformEvidence evidence) implements PlatformWriteOutcome {}
record WriteTerminalFailure(PlatformWriteTerminalCode errorCode,
        Optional<String> safeProviderTraceId,
        NormalizedPlatformEvidence evidence) implements PlatformWriteOutcome {}
record WriteUnknownOutcome(PlatformUnknownCode errorCode,
        Optional<String> safeProviderTraceId,
        NormalizedPlatformEvidence evidence) implements PlatformWriteOutcome {}

sealed interface PlatformReconciliationOutcome permits ReconciliationFound,
        ReconciliationNotFound, ReconciliationStillUnknown, ReconciliationTerminalFailure {}
record ReconciliationFound(String externalId,
        Optional<String> safeProviderTraceId,
        Optional<PlatformObservedState> observedState,
        NormalizedPlatformEvidence evidence) implements PlatformReconciliationOutcome {}
record ReconciliationNotFound(Optional<String> safeProviderTraceId,
        NormalizedPlatformEvidence evidence) implements PlatformReconciliationOutcome {}
record ReconciliationStillUnknown(Optional<String> safeProviderTraceId,
        NormalizedPlatformEvidence evidence) implements PlatformReconciliationOutcome {}
record ReconciliationTerminalFailure(PlatformReconciliationTerminalCode errorCode,
        Optional<String> safeProviderTraceId,
        NormalizedPlatformEvidence evidence) implements PlatformReconciliationOutcome {}
```

All outcome/evidence references and Optional containers are non-null. `WriteSucceeded.externalId` is present for create and absent for mutation; reconciliation found always has it. Other variants cannot carry an external ID. Retry seconds are `1..3600` and present in evidence only for retryable failure. Fingerprint is present exactly when an external ID is present. Observed state is permitted only for success/found. Evidence kind and attempt kind must match the enclosing variant/method. `schemaVersion=1` and `providerKey=FAKE` always.

Internal orchestration methods are also exact and are not web endpoints:

```java
PlatformOperationView submit(UUID operationUuid, long expectedOperationVersion);
PlatformOperationView retry(UUID operationUuid, long expectedOperationVersion, Instant now);
PlatformOperationView reconcile(UUID operationUuid, long expectedOperationVersion);
```

`PlatformOperationView` is exactly:

```java
record PlatformOperationView(
        UUID operationUuid,
        UUID platformAccountUuid,
        PlatformOperationType operationType,
        PlatformEntityType entityType,
        UUID entityUuid,
        PlatformOperationStatus status,
        int attemptCount,
        int reconciliationCount,
        int maxAttempts,
        Optional<String> externalId,
        Optional<PlatformStableErrorCode> normalizedErrorCode,
        Optional<String> safeProviderTraceId,
        Optional<NormalizedPlatformEvidence> outcomeEvidence,
        Optional<Instant> nextAttemptAt,
        Optional<Instant> claimedAt,
        Optional<Instant> completedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
```

All view fields/Optional containers are non-null and reflect the committed row after the method returns. `entityUuid` is the one typed FK flattened for local callers; no raw canonical payload or actor/account reference is exposed.

`submit` accepts only `CREATED`; `retry` accepts only due `FAILED_RETRYABLE`, uses the same operation/entity/payload/idempotency identity, and never creates a replacement row; `reconcile` accepts only `UNKNOWN_OUTCOME`. Every operation is inserted with `max_attempts=3`; the first submit is attempt 1 and at most two explicit retries create attempts 2 and 3. A retryable outcome on attempt 3 becomes terminal `PLATFORM_MAX_ATTEMPTS_EXCEEDED`. Reconciliation claims are separately capped at 3; a fourth explicit request fails `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED` and leaves the operation unknown. There is no unattended scheduler or automatic retry.

Local entry failures throw one provider-neutral `PlatformOperationException` containing non-null `PlatformStableErrorCode code`, `Optional<UUID> operationUuid`, and a fixed generic message equal to the enum name; it has no raw cause/message in its public representation. Only the local subset below may be thrown before dispatch; provider/reconciliation subsets appear in persisted results/views according to the outcome tables.

```java
final class PlatformOperationException extends RuntimeException {
    private final PlatformStableErrorCode code;
    private final Optional<UUID> operationUuid;
    PlatformOperationException(PlatformStableErrorCode code, Optional<UUID> operationUuid) {
        super(Objects.requireNonNull(code).name());
        this.code = requireLocalCode(code);
        this.operationUuid = Objects.requireNonNull(operationUuid);
    }
    PlatformStableErrorCode code() { return code; }
    Optional<UUID> operationUuid() { return operationUuid; }
}
```

`requireLocalCode` accepts exactly the local table members below and rejects provider-outcome-only codes; the class has no constructor accepting a raw message or Throwable.

| Local code | Exact condition; no claim/attempt/dispatch occurs |
| --- | --- |
| `PLATFORM_CONTRACT_INVALID` | null/invalid typed input or canonical payload |
| `PLATFORM_OPERATION_NOT_FOUND` | UUID has no operation |
| `PLATFORM_INVALID_OPERATION_STATE` | submit/retry/reconcile called from any non-accepted state |
| `PLATFORM_STALE_VERSION` | expected operation/entity version differs |
| `PLATFORM_RETRY_NOT_DUE` | retry requested before `next_attempt_at` |
| `PLATFORM_MAX_ATTEMPTS_EXCEEDED` | retry requested when `attempt_count >= 3` |
| `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED` | reconcile requested when `reconciliation_count >= 3` |
| `PLATFORM_ACCOUNT_INACTIVE` | account is not ACTIVE |
| `PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH` | LOCAL/TEST account differs from active profile |
| `PLATFORM_PROVIDER_UNSUPPORTED` | provider is not the closed FAKE provider |
| `PLATFORM_POLICY_REJECTED` | per-entity budget/account policy missing or violated |
| `PLATFORM_ADAPTER_UNAVAILABLE` | no explicit fake adapter in permitted profile |
| `PLATFORM_IDEMPOTENCY_CONFLICT` | repeated request identity differs in any immutable contract field |
| `PLATFORM_EVIDENCE_INVALID` | current Ad evidence fails creation/dispatch checks |

Local entry codes are not persisted. The sole overlap is `PLATFORM_MAX_ATTEMPTS_EXCEEDED`: it is returned locally for an ineligible fourth submit request, and it is also persisted when the provider returns a retryable outcome for already-claimed attempt 3 under the explicit conversion rule. Pre-insert/pre-claim rejection returns the exception and writes no false operation/Audit. All methods use compare-and-set version claims. Tests cover every table row above, including unchanged DB/Audit/adapter counters.

Server-owned policy contracts are exact and fail closed:

```java
interface PlatformAccountPolicyProvider {
    PlatformAccountPolicy requirePolicy(UUID platformAccountUuid);
}

interface PlatformBudgetPolicyProvider {
    PlatformBudgetPolicy requirePolicy(UUID platformAccountUuid, PlatformBudgetType budgetType);
}
```

The returned records are exact: `PlatformAccountPolicy(UUID platformAccountUuid, ProviderKey providerKey, PlatformEnvironment environment, String currency, String timezone, boolean active)` and `PlatformBudgetPolicy(String currency, PlatformBudgetType budgetType, BigDecimal maxEntityAmount)`. They are non-null, contain no Optional/map, and the 4A implementation supplies only the closed FAKE LOCAL/TEST, TWD, Asia/Taipei fixtures and per-entity ceilings. Aggregate batch/day fields do not exist in 4A. Absence or mismatch returns no permissive default and maps to `PLATFORM_POLICY_REJECTED` before dispatch.

`PlatformDeliveryReadPort`, `PlatformMetricsReadPort`, and every credential/secret contract are explicitly deferred and must not exist, even as marker interfaces, in 4A. They require their own later approved methods and security boundaries. Port calls remain reachable only from the internal 4A orchestration service; there is no controller, scheduler, event listener, AI dependency, Decision Engine dependency, credential lookup, or network client.

## Deterministic fake adapter contract

- Registered only under `(local | test) & !production` and an explicit fake-platform profile/property.
- Uses no HTTP/library SDK, filesystem credential, environment secret, random timing, DNS, socket, or paid service.
- Validates the same normalized request limits as the application port contract.
- Derives create IDs exactly as `prefix + first24(lowercaseHex(SHA-256(UTF-8("fake-platform-id-v1\n" + lowercase(operationUuid) + "\n" + idempotencyKey + "\n" + requestSha256))))`. Prefix is `fake-campaign-` for Campaign, `fake-adset-` for Ad Set, and `fake-ad-` for Ad. State and budget mutations return the entity's existing ID and never derive a replacement.
- Repeated submit with the same identity returns the same external ID; a different payload for the same identity returns an idempotency conflict.
- Server-owned fixtures map exactly: `SUCCESS -> WriteSucceeded`; `RETRYABLE_RATE_LIMIT -> WriteRetryableFailure(PLATFORM_RATE_LIMITED, 60)`; `RETRYABLE_TEMPORARILY_UNAVAILABLE -> WriteRetryableFailure(PLATFORM_TEMPORARILY_UNAVAILABLE, 30)`; `TERMINAL_VALIDATION -> WriteTerminalFailure(PLATFORM_VALIDATION_FAILED)`; `TERMINAL_PERMISSION -> WriteTerminalFailure(PLATFORM_PERMISSION_DENIED)`; `MALFORMED_RESULT -> WriteUnknownOutcome(PLATFORM_RESPONSE_AMBIGUOUS)`; `AMBIGUOUS_TIMEOUT -> WriteUnknownOutcome(PLATFORM_RESPONSE_AMBIGUOUS)`; `RECONCILE_FOUND -> ReconciliationFound` with the same deterministic ID; `RECONCILE_NOT_FOUND -> ReconciliationNotFound`; `RECONCILE_STILL_UNKNOWN -> ReconciliationStillUnknown`; and `RECONCILE_TERMINAL -> ReconciliationTerminalFailure(PLATFORM_RECONCILIATION_TERMINAL)`.
- Fixture selection is constructor/test configuration, not persisted arbitrary input and never a Browser/provider-origin field.
- Records invocation count and whether a Spring transaction was active so tests prove single submission and transaction separation.
- Default and production profiles expose no usable write adapter; startup or operation dispatch fails closed with `PLATFORM_ADAPTER_UNAVAILABLE`.

Contract tests must run the same test suite against every implementation of a write or reconciliation port. A future Meta adapter cannot weaken the fake contract.

The shared contract suite asserts each exact method, record nesting/type, enum exhaustiveness, Optional/null rule, canonical hash and idempotency replay, the three exact ID prefixes and 24-hex suffix, every fixture above, both retryable codes and exact retry seconds, both write-terminal codes, ambiguous malformed/null/exception mapping, all four reconciliation variants including terminal, provider/evidence coherence, max-attempt conversion on attempt 3, max-reconciliation rejection, no replacement ID on mutation, reconciliation identity reuse, one invocation for concurrent/replayed commands, and absence of provider/secret/network types. An unexpected exception or null/unrecognized result is normalized to `PLATFORM_RESPONSE_AMBIGUOUS`; it can never become a retryable result after dispatch.

## Audit, logging, and redaction boundary

Audit is explicitly an **application transaction invariant**, not a V12 deferred database constraint. V12 does not inspect `audit_logs` and the budget/evidence/attempt coherence triggers do not claim to prove Audit presence. The application must use the existing `AuditService`, `AuditOperationContext`, and `AuditValueSanitizer` behind one mandatory internal `PlatformAuditWriter.write(PlatformAuditEvent event, AuditOperationContext context)` collaborator. `PlatformAuditEvent` is a closed internal value containing operation/entity UUID, `CREATE` or `UPDATE`, old/new normalized status/value fields, attempt number when applicable, and no arbitrary map.

Each Transaction A/B/C and reconciliation transaction is owned by one orchestration method that performs the state mutation and calls `PlatformAuditWriter` before commit. An Audit writer exception propagates and rolls back the entire state/attempt/entity mutation. Effective mutations write Audit in the same transaction; stale optimistic attempts, local validation failures, duplicate replays, and idempotency conflicts write no false state-change Audit.

Allowed Audit content:

- operation/entity UUID, normalized entity/operation/status, request ID, actor type/ID, attempt number, normalized error code, safe trace ID, timestamps, and bounded checksum/fingerprint;
- `CREATE` for newly persisted foundation records and `UPDATE` for effective transitions.

Forbidden everywhere, including logs, Audit, exceptions, JSON evidence, test snapshots, and completion reports:

- tokens, secrets, credentials, cookies, Authorization headers, raw external account IDs, raw provider request/response bodies, Graph URLs, arbitrary targeting, SDK objects, stack traces returned to a caller, or unbounded exception messages.

The existing sanitizer marker list is the minimum. Platform canonicalizers reject keys containing `authorization`, `cookie`, `credential`, `password`, `secret`, or `token`, case-insensitively, before persistence. Tests use sentinel secrets and capture logs/Audit/database rows to prove absence. Safe trace IDs are length/character bounded and never trusted as URLs.

Required Audit tests distinguish the enforcement layer: transaction integration tests assert every effective create/claim/finalize/reconcile/budget mutation commits exactly one expected Audit operation; a test `PlatformAuditWriter` that throws before returning proves the corresponding database mutation and attempt finalization roll back; a transaction-level test intentionally invokes the orchestration mutation with a missing/disabled writer binding and requires startup/constructor failure, not a silent commit. Direct SQL may bypass Audit by design and is used only to test database constraints; this limitation is recorded and must not be presented as database-level Audit enforcement.

## Verification matrix

| Area | Required evidence |
| --- | --- |
| Migration cold path | Empty PostgreSQL migrates V1 through V12; Flyway latest is 12; all seven tables, columns, constraints, indexes, functions, and triggers match this specification |
| Upgrade path | A populated V11 fixture containing Product, Campaign Plan, Asset, generated output, and approved decision migrates to V12 without data/version/checksum change |
| Migration immutability | Canonical SHA-256 assertions cover V1–V11 byte-for-byte; V12 is new only; no pending migration remains |
| Migration atomicity | A deliberate V12 object-name collision causes the migration to roll back with none of the other V12 objects left behind |
| Hibernate | `ddl-auto=validate` passes against V12; all enum lengths, JSONB, `CHAR`, money precision/scale, nullability, composite relationships, timestamps, and `@Version` mappings match |
| Direct SQL identity/evidence | Reject META/PRODUCTION accounts and mismatched/non-FAKE operation/attempt evidence. Reject mutation of every account/entity/operation identity, write-once external ID, canonical payload/hash, or attempt identity. Ad insert rejects mismatched Asset/output (`23503` or semantic `23514`), TEXT output, pending/rejected review, rejected decision, blocked preservation, inactive Product/Asset, null/mismatched checksums, and later Ad evidence substitution. Snapshot test proves later upstream lifecycle/type/checksum divergence leaves the historical Ad unchanged but blocks a new Ad insert |
| Direct SQL lifecycle | Reject non-CREATED operation INSERT, nonzero counters/version or prefilled result fields; reject missing/mismatched latest SUBMIT/RECONCILE attempt for each operation state, including pre-succeeded INSERT and success without finalized attempt; reject invalid desired/observed/operation/attempt transitions, counter drift, terminal updates, account restore, and version jumps |
| Budget mutation | Initial provenance is null; exact successful per-entity-bounded `UPDATE_BUDGET` changes only amount/provenance/version; stale version, type/currency/policy change, missing/wrong/reused/non-success operation, unchanged/out-of-bound amount, direct amount update, and Audit-writer rollback fail; replay is no-op and concurrent changes have one winner. Assert batch/account-day aggregates are absent/deferred, not partially enforced |
| Delete protection | DELETE from each of seven tables fails; referenced V1–V11 records remain protected by `ON DELETE RESTRICT`; Audit remains append-only |
| Metric append-only revisions | Revision 1 and changed revision 2 coexist; latest/as-of selection deterministic; skipped/repeated revision, non-monotonic fetch time, exact duplicate fingerprint, negative/base invalid values, update, and delete fail; nullable metrics remain NULL. Direct SQL rejects currency/timezone mismatch and inactive account; matched active account succeeds |
| Domain unit | All valid/invalid state edges; exact Java nesting/types/Optional/null rules; every local error; exact payload keys/formats; canonical bytes/hash; per-entity budget bounds/increase/decrease/replay; evidence checksum; metric account/fingerprint/revision; provider/profile matrix; exhaustive normalized outcome/error mapping; max attempts/reconciliations |
| Port contract | Every exact method/record field/enum and required/optional rule; three fake ID prefixes and 24-hex suffix; success, both retryable fixtures, both write-terminal fixtures, malformed/null/exception ambiguity, idempotent replay/conflict, retry identity/max-attempt conversion, reconcile found/not-found/unknown/terminal, stable FAKE evidence/provider coherence, LOCAL/TEST dispatch matrix, and no provider/secret/network types escaping |
| Persistence before call | A separate connection can read the operation and STARTED attempt inside fake invocation; adapter observes no active Spring transaction |
| Concurrency | Concurrent duplicate create returns one operation/entity and one submit call; concurrent claim has one winner; stale versions fail; max attempts cannot be exceeded |
| Retry | Retry reuses operation UUID, canonical payload, idempotency key, and entity; creates the next attempt row only; no replacement entity/operation |
| Ambiguous recovery | Timeout/crash produces UNKNOWN; submit count remains one; only reconcile is called; restart recovery never blindly submits; NOT_FOUND remains unresolved |
| Audit | Application transaction invariant: every effective create/claim/result/reconcile/budget transition has expected Audit; Audit writer exception rolls back state and attempt; missing writer binding fails construction/startup; duplicate/invalid paths write none; sentinel secret absent. Do not claim direct-SQL Audit enforcement |
| Profile/security | Only FAKE/LOCAL under local and FAKE/TEST under test dispatch; cross-environment, META, default/production, missing explicit fake config, unsupported provider, and evidence mismatch fail closed; no network-capable dependency or credential implementation |
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
- Ad evidence uses creation-time snapshot semantics. Later upstream divergence intentionally remains historical rather than invalidating the row; later dispatch must revalidate current evidence under its separately approved milestone.
- Operation-batch and account-business-day budget aggregates are not implemented or claimed in 4A; 4B must specify and approve an accounting/reservation model before they become executable.
- Database constraints cannot prove an IANA timezone name or independently recompute every canonical JSON hash; exact domain canonicalization, trigger coherence, and contract tests are mandatory together. Ad creation snapshot and metric account/revision identity remain database-enforced as specified.
- No dedicated as-of metrics performance index is required while metric reads are inert in 4A; 4D must review query evidence and add an additive index if needed without changing snapshot semantics.
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
