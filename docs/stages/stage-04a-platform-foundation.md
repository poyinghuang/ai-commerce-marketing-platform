# Stage 04 Milestone 4A — Platform Persistence and Operation Foundation

## Gate status

- Status: Technical specification correction for finding 4A-SPEC-018 completed; implementation not started
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

`budget_type`, currency, schedule, timezone, optimization goal, targeting profile, placement profile, parent, and account form immutable policy identity/configuration. `budget_amount` is not policy identity: it is the one mutable budget value. An effective direct or reconciled change must atomically set a different `last_budget_operation_uuid`, increment the Ad Set version exactly once, and write same-transaction Audit. V12 defines `verify_platform_budget_operation_coherence()` and two `DEFERRABLE INITIALLY DEFERRED` constraint triggers: one after UPDATE OF `budget_amount`, `last_budget_operation_uuid`, or `version` on `platform_ad_sets`, and the reciprocal trigger after UPDATE OF `status` on an `UPDATE_BUDGET` operation. At commit they enforce both directions: a changed amount references the same account/Ad Set operation in `SUCCEEDED`, and every newly succeeded budget operation—whether finalized by a SUBMIT/SUCCEEDED attempt or by an earlier SUBMIT/UNKNOWN_OUTCOME plus RECONCILE/SUCCEEDED attempt—is the Ad Set's new provenance pointer with its new amount applied. The canonical payload must contain the OLD amount, NEW amount, immutable budget type/currency, and exactly `OLD.version`; `NEW.version` must equal `OLD.version + 1`. Initial creation must have `last_budget_operation_uuid IS NULL`; changing the pointer without changing the amount, reusing an earlier operation, marking either direct or reconciled operation successful without the entity change, changing the amount without a newly successful operation, or changing any immutable policy field fails with SQLSTATE `23514`.

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
| `WriteSucceeded` | `externalId` for create, `evidence` | `safeProviderTraceId`, `observedState`; mutation has no returned `externalId` | `SUBMITTING -> SUCCEEDED` |
| `WriteRetryableFailure` | `errorCode`, `retryAfterSeconds`, `evidence` | `safeProviderTraceId` | `SUBMITTING -> FAILED_RETRYABLE` only when another attempt remains; otherwise `FAILED_TERMINAL` with `PLATFORM_MAX_ATTEMPTS_EXCEEDED` |
| `WriteTerminalFailure` | `errorCode`, `evidence` | `safeProviderTraceId` | `SUBMITTING -> FAILED_TERMINAL` |
| `WriteUnknownOutcome` | `errorCode`, `evidence` | `safeProviderTraceId` | `SUBMITTING -> UNKNOWN_OUTCOME` |
| `ReconciliationFound` | `externalId` for create, `evidence` | `safeProviderTraceId`, `observedState`; mutation has no returned `externalId` | `RECONCILING -> SUCCEEDED` |
| `ReconciliationNotFound` | `errorCode=PLATFORM_RECONCILIATION_NOT_FOUND`, `evidence` | `safeProviderTraceId` | attempt `NOT_FOUND`; operation `RECONCILING -> UNKNOWN_OUTCOME` |
| `ReconciliationStillUnknown` | `errorCode=PLATFORM_RECONCILIATION_INCONCLUSIVE`, `evidence` | `safeProviderTraceId` | `RECONCILING -> UNKNOWN_OUTCOME` |
| `ReconciliationTerminalFailure` | `errorCode=PLATFORM_RECONCILIATION_TERMINAL`, `evidence` | `safeProviderTraceId` | `RECONCILING -> FAILED_TERMINAL` |

`externalId` is 1–128 characters matching `^[A-Za-z0-9._:-]+$`. A create success requires it; a mutation success (`PAUSE`, `RESUME`, or `UPDATE_BUDGET`) forbids it and verifies the entity's existing durable external ID before dispatch without copying that ID into the outcome. `safeProviderTraceId`, when present, uses the same safe character class and length. `observedState`, when present, is one of the normalized observed states. `retryAfterSeconds` is integer `1..3600`; the application computes `next_attempt_at = claimCompletionTime + retryAfterSeconds` and never accepts a provider timestamp.

`NormalizedPlatformEvidence` serializes to a JSON object with required keys `schemaVersion` (number `1`), `providerKey` (exactly `FAKE`), `attemptKind` (`SUBMIT` or `RECONCILE`), and `resultKind` (`SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, `FOUND`, `NOT_FOUND`, `STILL_UNKNOWN`). Optional keys are only `externalIdFingerprint` (lowercase SHA-256 of the exact UTF-8 external ID), `observedState`, and `retryAfterSeconds`; their presence must agree with the outcome and typed declarations. Create success and create reconciliation-found require the fingerprint; every direct or reconciled mutation success forbids it because no returned ID exists. No other key or nested object is allowed. V12 checks provider is FAKE and deferred triggers require it to equal the operation account provider. The exact object is at most 8 KiB and is copied to the finalized attempt; `platform_operations.outcome_evidence` stores the latest finalized normalized evidence. Raw response/body/message/status/header/URL/account identifier is never retained.

For a successful create, Transaction C copies the validated returned ID to both the created entity's write-once `external_id` and `platform_operations.external_id`; both values must match, while evidence stores only its fingerprint. For a successful mutation, the entity's existing `external_id` is unchanged, `platform_operations.external_id` remains NULL, and the attempt and operation evidence omit `externalIdFingerprint`. The deferred operation/attempt coherence trigger and domain constructor enforce this operation-type-specific rule; no mutation result may echo, replace, or newly persist an external ID.

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

A claim is stale only when the persisted `claimed_at <= recoveryTime - PT5M`, where `recoveryTime` is the injected server clock Instant. The internal `recoverStaleClaim(UUID operationUuid, long expectedOperationVersion, Instant recoveryTime)` method accepts only `SUBMITTING` or `RECONCILING`, uses compare-and-set locking, and runs one of these exact single-database transactions; it never invokes a write/reconciliation port:

- stale `SUBMITTING`: lock the operation and its unique matching `SUBMIT/STARTED` attempt numbered `attempt_count`; finalize that attempt to `UNKNOWN_OUTCOME`, `normalized_error_code=PLATFORM_RESPONSE_AMBIGUOUS`, `safe_provider_trace_id=NULL`, application-generated evidence `NormalizedPlatformEvidence(1, FAKE, SUBMIT, UNKNOWN_OUTCOME, Optional.empty(), Optional.empty(), Optional.empty())`, `completed_at=recoveryTime`, and `version 0 -> 1`. Move the operation `SUBMITTING -> UNKNOWN_OUTCOME`, persist the same code and byte-equivalent evidence, set safe trace/external ID/next attempt/completed time NULL, preserve original `claimed_at`, counters and request identity, and increment operation version exactly once.
- stale `RECONCILING`: lock the operation and its unique matching `RECONCILE/STARTED` attempt numbered `reconciliation_count`; finalize that attempt to `UNKNOWN_OUTCOME`, `normalized_error_code=PLATFORM_RECONCILIATION_INCONCLUSIVE`, `safe_provider_trace_id=NULL`, application-generated evidence `NormalizedPlatformEvidence(1, FAKE, RECONCILE, STILL_UNKNOWN, Optional.empty(), Optional.empty(), Optional.empty())`, `completed_at=recoveryTime`, and `version 0 -> 1`. Move the operation `RECONCILING -> UNKNOWN_OUTCOME`, persist the same code and byte-equivalent evidence, set safe trace/external ID/next attempt/completed time NULL, preserve original `claimed_at`, counters and request identity, and increment operation version exactly once.

Not-stale, wrong-state, missing/mismatched STARTED attempt, and stale-version cases fail without mutation or Audit. Recovery never derives provider success/failure, never changes entity desired/observed state or budget, never creates another attempt, and never resubmits. A recovered submit becomes reconcile-eligible; a recovered reconciliation remains reconcile-eligible subject to the existing reconciliation cap.

Status/timestamp coherence is exact: `CREATED` has zero counters and no claim/completion; `SUBMITTING`/`RECONCILING` require `claimed_at` and no `completed_at`; `FAILED_RETRYABLE` requires `next_attempt_at`, no `completed_at`, and a normalized error; `UNKNOWN_OUTCOME` requires no `completed_at` and a normalized ambiguity code; terminal statuses require `completed_at` and no `next_attempt_at`. `SUCCEEDED` requires an external ID for create operations. Failure states cannot persist raw error text.

Each `SUBMITTING` claim increments `attempt_count` exactly once and atomically inserts the matching `SUBMIT/STARTED` attempt. Each `RECONCILING` claim increments `reconciliation_count` exactly once and inserts the matching `RECONCILE/STARTED` attempt. Optimistic-lock failure means the loser performs no call.

Exact field mutation by edge:

- Claim from `CREATED` or eligible `FAILED_RETRYABLE`: set `status=SUBMITTING`, increment `attempt_count`, set `claimed_at=now`, clear `next_attempt_at`, clear the operation's prior normalized error/trace/outcome evidence, increment version, and insert `SUBMIT/STARTED` with `attempt_number=attempt_count` in the same transaction.
- Submit success: set `status=SUCCEEDED`, set `completed_at`, persist normalized external/trace/evidence, clear error/retry fields, increment version, and apply matching bounded entity evidence. `UPDATE_BUDGET` also changes `budget_amount` and `last_budget_operation_uuid` in this same transaction using the payload's expected entity version.
- Retryable result with attempts remaining: set `status=FAILED_RETRYABLE`, persist retryable code/trace/evidence, set server-computed `next_attempt_at`, keep `completed_at` null, and increment version. When no attempt remains, use `FAILED_TERMINAL` and `PLATFORM_MAX_ATTEMPTS_EXCEEDED` instead.
- Terminal result: set `status=FAILED_TERMINAL`, persist terminal code/trace/evidence and `completed_at`, clear retry time, and increment version.
- Ambiguous result: set `status=UNKNOWN_OUTCOME`, persist ambiguity code/trace/evidence, keep completion/retry time null, and increment version. No submit edge exists from this state.
- Reconcile claim: from `UNKNOWN_OUTCOME`, set `RECONCILING`, increment `reconciliation_count`, set `claimed_at=now`, clear the prior normalized error/trace/outcome evidence, increment version, and atomically insert `RECONCILE/STARTED` numbered from the reconciliation count.
- Reconcile found for `CREATE_*` records the found ID and optional observation. Reconcile found for `PAUSE`/`RESUME` locks the entity at the durable payload's `expectedEntityVersion`, applies exactly its validated `targetDesiredState` transition and optional normalized observation, and increments entity version once. Reconcile found for `UPDATE_BUDGET` locks the Ad Set at the durable payload's `expectedEntityVersion`, applies exactly `newBudgetAmount` plus `last_budget_operation_uuid=operation_uuid` and optional observation, and increments entity version once; old amount, type, currency, bound, and reciprocal trigger predicates remain mandatory. The matching RECONCILE attempt, operation `SUCCEEDED`, entity mutation, and Audit all commit atomically. Terminal/still-unknown/not-found finalizes exactly as the normalized outcome table specifies. NOT_FOUND never changes desired state, observed state, budget, or external ID and never creates submit eligibility.

If `ReconciliationFound` is returned for a mutation but the locked entity version no longer equals the immutable payload's expected version, the externally confirmed result cannot be safely projected onto changed local state. The reconciliation finalization transaction therefore performs no entity mutation and does not persist the adapter's FOUND evidence as success. It instead constructs `NormalizedPlatformEvidence(1, FAKE, RECONCILE, UNKNOWN_OUTCOME, Optional.empty(), Optional.empty(), Optional.empty())` and atomically:

- finalizes the matching `RECONCILE/STARTED` attempt as `UNKNOWN_OUTCOME`, `normalized_error_code=PLATFORM_RESPONSE_AMBIGUOUS`, retains only the bounded `safe_provider_trace_id` from `ReconciliationFound`, stores the application-generated evidence, sets `completed_at=reconciliationCompletionTime`, and increments attempt version `0 -> 1`;
- moves the operation `RECONCILING -> UNKNOWN_OUTCOME`, stores the same code, retained trace, and byte-equivalent evidence, keeps `external_id`, `next_attempt_at`, and `completed_at` NULL, preserves claim/counters/request identity, and increments operation version exactly once;
- writes exactly the matching attempt-finalized and operation-transitioned Audit events with `PLATFORM_RESPONSE_AMBIGUOUS` and the retained trace; it writes no entity Audit event and persists neither the found observation nor any desired/budget/provenance value.

The original FOUND evidence/observation is input-only test evidence and is not persisted after this stale-version conversion. This is persistence uncertainty, not a provider failure or permission to call again. The operation remains reconcilable only through a later explicit reconcile request within the existing cap; the current finalization never re-calls either adapter. An Audit/constraint failure rolls back the attempt, operation, entity, and Audit together; after rollback the still-STARTED claim is handled by stale recovery without another provider call.

### Attempt

`STARTED` transitions exactly once to one result. A SUBMIT attempt may end `SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, or `UNKNOWN_OUTCOME`. A RECONCILE attempt may end `SUCCEEDED`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, or `NOT_FOUND`. `NOT_FOUND` means no matching external entity was proven; it is not authority to resubmit the write. The containing operation stays `UNKNOWN_OUTCOME` pending an explicit human-safe resolution policy in a later milestone.

## Optimistic locking and transaction boundaries

All mutable aggregates use `@Version`. Worker claim uses a version-qualified update or a JPA flush that guarantees exactly one claimant. No pessimistic database lock is held across an adapter call.

Required sequence:

1. Transaction A validates canonical input, trusted actor, account state, exact local references, immutable policy, budget ceiling, and expected entity version; inserts the paused entity if a create command requires it; inserts `platform_operations` in `CREATED`; writes same-operation Audit; commits. Duplicate identity resolution happens here and never dispatches twice.
2. Transaction B claims the exact operation version, moves it to `SUBMITTING`, increments `attempt_count`, inserts a `SUBMIT/STARTED` attempt, writes Audit, and commits.
3. The application invokes the adapter after Transaction B. `TransactionSynchronizationManager.isActualTransactionActive()` must be false inside the adapter.
4. Transaction C locks the operation and entity by the exact expected optimistic versions, finalizes the attempt, transitions the operation, applies bounded normalized entity evidence (including the budget/provenance pair for successful `UPDATE_BUDGET`), writes Audit, and commits atomically. A stale entity version cannot partially finalize a supposedly successful mutation; if the adapter may already have written, the exact UNKNOWN conversion above is used.
5. If response receipt is ambiguous or Transaction C cannot prove the result, persist/recover `UNKNOWN_OUTCOME`; do not resubmit.

Reconciliation uses the analogous claim/call/finalize sequence with `RECONCILING` and a RECONCILE attempt. A found mutation finalization re-locks the entity against the expected version from the immutable canonical payload and atomically applies the same desired-state or budget/provenance mutation as direct success, combined with optional observation. Version mismatch follows the exact persistence-uncertainty conversion and never marks the operation SUCCEEDED. Adapter exceptions are normalized outside persistence; raw exception text is not written to the database or Audit.

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
enum PlatformAttemptStatus { STARTED, SUCCEEDED, FAILED_RETRYABLE, FAILED_TERMINAL, UNKNOWN_OUTCOME, NOT_FOUND }
enum PlatformEvidenceResultKind { SUCCEEDED, FAILED_RETRYABLE, FAILED_TERMINAL, UNKNOWN_OUTCOME, FOUND, NOT_FOUND, STILL_UNKNOWN }
enum PlatformStableErrorCode {
    PLATFORM_RATE_LIMITED, PLATFORM_TEMPORARILY_UNAVAILABLE,
    PLATFORM_VALIDATION_FAILED, PLATFORM_PERMISSION_DENIED,
    PLATFORM_RESPONSE_AMBIGUOUS, PLATFORM_RECONCILIATION_NOT_FOUND,
    PLATFORM_RECONCILIATION_INCONCLUSIVE, PLATFORM_RECONCILIATION_TERMINAL,
    PLATFORM_CONTRACT_INVALID, PLATFORM_OPERATION_NOT_FOUND,
    PLATFORM_INVALID_OPERATION_STATE, PLATFORM_STALE_VERSION,
    PLATFORM_RETRY_NOT_DUE, PLATFORM_RECOVERY_NOT_DUE, PLATFORM_MAX_ATTEMPTS_EXCEEDED,
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
        String existingExternalId,
        long expectedEntityVersion,
        PlatformDesiredState targetDesiredState) {}

record PlatformBudgetMutationCommand(
        PlatformCommandIdentity identity,
        UUID platformAdSetUuid,
        String existingExternalId,
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

Every record reference, enum, UUID, String, BigDecimal, and Optional container is non-null. Absence is represented only by `Optional.empty()` in the explicitly Optional fields; raw null is rejected by the compact constructor with `IllegalArgumentException(PLATFORM_CONTRACT_INVALID)`. Schedule optionals are both empty or both present with end after start. Mutation `existingExternalId` is required, matches `^[A-Za-z0-9._:-]{1,128}$`, and is loaded from the locked entity row rather than accepted in `request_payload`; it is an internal dispatch target, not a returned result. `expectedEntityVersion >= 0`; submit/reconciliation counts are positive when dispatched; money is positive, scale `0..6`, and normalized without rounding. Hashes are exactly 64 lowercase hex; currency is exactly three uppercase ASCII letters; timezone/profile/mapping strings follow their persisted closed rules. Commands contain no actor credential, raw account ID, provider payload, URL, raw evidence, map, JSON node, fixture selector, or SDK type.

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
record ReconciliationFound(Optional<String> externalId,
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

All outcome/evidence references and Optional containers are non-null. Validation is performed by `validateWriteOutcome(PlatformOperationType operationType, Optional<String> durableEntityExternalId, PlatformWriteOutcome outcome)` before Transaction C and by the analogous `validateReconciliationOutcome(...)` before reconciliation finalization. For `CREATE_*`, `durableEntityExternalId` is empty until success/found, `WriteSucceeded.externalId` or `ReconciliationFound.externalId` is present, and evidence contains exactly its SHA-256 fingerprint. For `PAUSE`, `RESUME`, and `UPDATE_BUDGET`, `durableEntityExternalId` is present and valid before dispatch, both success variants have empty `externalId`, and evidence has no fingerprint; the durable ID is used only to verify the target and is never copied into the returned or persisted operation result. Other variants cannot carry an external ID. Retry seconds are `1..3600` and present in evidence only for retryable failure before max-attempt conversion. Observed state is permitted only for success/found. Evidence kind and attempt kind must match the enclosing variant/method. `schemaVersion=1` and `providerKey=FAKE` always. Any mismatch is `PLATFORM_CONTRACT_INVALID` before result persistence; because a provider call has already occurred, orchestration then follows the ambiguous-result rule rather than persisting a malformed success.

Internal orchestration methods are also exact and are not web endpoints:

```java
PlatformOperationView submit(UUID operationUuid, long expectedOperationVersion);
PlatformOperationView retry(UUID operationUuid, long expectedOperationVersion, Instant now);
PlatformOperationView reconcile(UUID operationUuid, long expectedOperationVersion);
PlatformOperationView recoverStaleClaim(UUID operationUuid, long expectedOperationVersion, Instant recoveryTime);
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

`submit` accepts only `CREATED`; `retry` accepts only due `FAILED_RETRYABLE`, uses the same operation/entity/payload/idempotency identity, and never creates a replacement row; `reconcile` accepts only `UNKNOWN_OUTCOME`. Every operation is inserted with `max_attempts=3`; the first submit is attempt 1 and at most two explicit retries create attempts 2 and 3. Reconciliation claims are separately capped at 3; a fourth explicit request fails `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED` and leaves the operation unknown. There is no unattended scheduler or automatic retry.

If the adapter returns `WriteRetryableFailure` for SUBMIT attempt 3, orchestration does not persist that retryable outcome. It constructs a new application-owned `NormalizedPlatformEvidence(1, FAKE, SUBMIT, FAILED_TERMINAL, Optional.empty(), Optional.empty(), Optional.empty())` and atomically finalizes the attempt and operation as follows:

- attempt: `status=FAILED_TERMINAL`, `normalized_error_code=PLATFORM_MAX_ATTEMPTS_EXCEEDED`, `safe_provider_trace_id` copied from the adapter outcome when present, the application-generated terminal evidence, `completed_at=claimCompletionTime`, and `version=1`;
- operation: `status=FAILED_TERMINAL`, `attempt_count=3`, `normalized_error_code=PLATFORM_MAX_ATTEMPTS_EXCEEDED`, the same retained safe trace and byte-equivalent terminal evidence, `external_id=NULL`, `next_attempt_at=NULL`, `completed_at=claimCompletionTime`, and the normal single version increment from its claimed version;
- the original retryable code, retry seconds, and retryable evidence are input-only test observations and are not persisted in any operation, attempt, Audit change, or log. No `retryAfterSeconds`, external-ID fingerprint, or observed state survives the transformation.

`retry` evaluates local eligibility in this exact order: operation existence, expected version, `attempt_count >= max_attempts`, accepted `FAILED_RETRYABLE` state, due time, account/policy/evidence, and claim. Therefore a fourth retry against the terminal attempt-3 row returns `PLATFORM_MAX_ATTEMPTS_EXCEEDED`, not `PLATFORM_INVALID_OPERATION_STATE`; it creates no attempt, Audit, entity change, or adapter invocation. Not-found and stale-version retain precedence over the max check.

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
| `PLATFORM_RECOVERY_NOT_DUE` | recovery requested before `claimed_at + PT5M` |
| `PLATFORM_MAX_ATTEMPTS_EXCEEDED` | retry requested when `attempt_count >= 3` |
| `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED` | reconcile requested when `reconciliation_count >= 3` |
| `PLATFORM_ACCOUNT_INACTIVE` | account is not ACTIVE |
| `PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH` | LOCAL/TEST account differs from active profile |
| `PLATFORM_PROVIDER_UNSUPPORTED` | provider is not the closed FAKE provider |
| `PLATFORM_POLICY_REJECTED` | per-entity budget/account policy missing or violated |
| `PLATFORM_ADAPTER_UNAVAILABLE` | no explicit fake adapter in permitted profile |
| `PLATFORM_IDEMPOTENCY_CONFLICT` | repeated request identity differs in any immutable contract field |
| `PLATFORM_EVIDENCE_INVALID` | current Ad evidence fails creation/dispatch checks |

For stale recovery, not found and stale version map normally, not-stale maps to `PLATFORM_RECOVERY_NOT_DUE`, wrong status maps to `PLATFORM_INVALID_OPERATION_STATE`, and a missing/mismatched/non-STARTED current attempt maps to `PLATFORM_EVIDENCE_INVALID`; all precede mutation. Local entry codes are not persisted. The sole overlap is `PLATFORM_MAX_ATTEMPTS_EXCEEDED`: it is returned locally for an ineligible fourth retry request, and it is also persisted under the exact application-generated terminal transformation when the provider returns a retryable outcome for already-claimed attempt 3. Pre-insert/pre-claim rejection returns the exception and writes no false operation/Audit. All methods use compare-and-set version claims. Tests cover every table row above, including the fourth-retry precedence and unchanged DB/Audit/adapter counters.

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
- Derives create IDs exactly as `prefix + first24(lowercaseHex(SHA-256(UTF-8("fake-platform-id-v1\n" + lowercase(operationUuid) + "\n" + idempotencyKey + "\n" + requestSha256))))`. Prefix is `fake-campaign-` for Campaign, `fake-adset-` for Ad Set, and `fake-ad-` for Ad. State and budget mutations first verify the supplied command target against the entity's existing durable ID, then return `WriteSucceeded(Optional.empty(), ...)` with no external-ID fingerprint; they never echo or derive an ID.
- A repeated direct invocation of a `CREATE_*` fake write port with the same immutable command identity deterministically returns the same derived external ID; this is a port-contract property used by isolated adapter tests, not authorization for orchestration to call twice. Mutation ports never return an ID. At orchestration level, replay of any already committed matching operation identity returns the existing `PlatformOperationView` without adapter invocation; mutation replay therefore has empty operation `externalId`, does not expose the durable entity ID, and changes no entity/operation/attempt/Audit row. A different payload for the same identity returns an idempotency conflict before any port call.
- Server-owned fixtures map exactly: `SUCCESS -> WriteSucceeded`; `RETRYABLE_RATE_LIMIT -> WriteRetryableFailure(PLATFORM_RATE_LIMITED, 60)`; `RETRYABLE_TEMPORARILY_UNAVAILABLE -> WriteRetryableFailure(PLATFORM_TEMPORARILY_UNAVAILABLE, 30)`; `TERMINAL_VALIDATION -> WriteTerminalFailure(PLATFORM_VALIDATION_FAILED)`; `TERMINAL_PERMISSION -> WriteTerminalFailure(PLATFORM_PERMISSION_DENIED)`; `MALFORMED_RESULT -> WriteUnknownOutcome(PLATFORM_RESPONSE_AMBIGUOUS)`; `AMBIGUOUS_TIMEOUT -> WriteUnknownOutcome(PLATFORM_RESPONSE_AMBIGUOUS)`; `RECONCILE_FOUND -> ReconciliationFound` with the same deterministic ID and fingerprint for create, but empty ID/fingerprint after verifying `knownExternalId` for mutation; `RECONCILE_NOT_FOUND -> ReconciliationNotFound`; `RECONCILE_STILL_UNKNOWN -> ReconciliationStillUnknown`; and `RECONCILE_TERMINAL -> ReconciliationTerminalFailure(PLATFORM_RECONCILIATION_TERMINAL)`. The fake reconciliation adapter confirms normalized provider outcome only; it never mutates PostgreSQL. Orchestration reconstructs the original durable mutation payload and applies PAUSE/RESUME/budget/provenance locally in the atomic found-finalization transaction.
- Fixture selection is constructor/test configuration, not persisted arbitrary input and never a Browser/provider-origin field.
- Records invocation count and whether a Spring transaction was active so tests prove single submission and transaction separation.
- Default and production profiles expose no usable write adapter; startup or operation dispatch fails closed with `PLATFORM_ADAPTER_UNAVAILABLE`.

Contract tests must run the same test suite against every implementation of a write or reconciliation port. A future Meta adapter cannot weaken the fake contract.

The shared contract suite asserts each exact method, record nesting/type, enum exhaustiveness, Optional/null rule, canonical hash and idempotency replay, the three exact ID prefixes and 24-hex suffix, every fixture above, both retryable codes and exact retry seconds, both write-terminal codes, ambiguous malformed/null/exception mapping, all four reconciliation variants including terminal, provider/evidence coherence, max-reconciliation rejection, reconciliation identity reuse, one invocation for concurrent/replayed commands, and absence of provider/secret/network types. Create port tests invoke the same command twice and require the same returned ID; orchestration replay tests separately require zero additional port calls. Mutation tests require a pre-existing durable ID, returned/persisted operation ID absence, fingerprint absence, unchanged entity ID, rejection of an adapter result that echoes or substitutes an ID, and an existing committed view with zero port calls/ID exposure on replay. Reconciliation-found mutation tests prove the fake returns confirmation without database mutation, while orchestration reconstructs and atomically applies the durable PAUSE, RESUME, or UPDATE_BUDGET payload plus optional observation. Attempt-3 tests inject each retryable code and retry seconds, then assert the exact application-generated terminal attempt/operation fields, retained safe trace, discarded original code/retry seconds, terminal evidence, no fourth invocation, and fourth-retry `PLATFORM_MAX_ATTEMPTS_EXCEEDED` precedence. An unexpected exception or null/unrecognized result is normalized to `PLATFORM_RESPONSE_AMBIGUOUS`; it can never become a retryable result after dispatch.

Reconciled-mutation transaction tests are required independently for `ACTIVE -> PAUSED`, `PAUSED -> ACTIVE`, budget increase, and budget decrease. Each asserts the immutable payload's expected version, exactly one entity version increment, optional observed state in the same update, SUCCEEDED operation/finalized RECONCILE attempt, exact three-event Audit matrix, no returned/persisted mutation ID, and one reconciliation-port call. Parameterized stale-version tests for all three mutation types assert the exact UNKNOWN conversion fields/evidence/Audit, unchanged entity/provenance, discarded FOUND evidence, and zero additional port calls. Separate throwing-Audit and forced deferred-budget-trigger cases prove total rollback; after rollback, stale-claim recovery finalizes without re-calling the reconciliation port.

## Audit, logging, and redaction boundary

Audit is explicitly an **application transaction invariant**, not a V12 deferred database constraint. V12 does not inspect `audit_logs` and the budget/evidence/attempt coherence triggers do not claim to prove Audit presence. The application must use the existing `AuditService`, `AuditOperationContext`, and `AuditValueSanitizer` behind one mandatory internal `PlatformAuditWriter.write(PlatformAuditEvent event, AuditOperationContext context)` collaborator. The closed internal contract is exact:

```java
enum PlatformAuditSubjectType {
    PLATFORM_CAMPAIGN, PLATFORM_AD_SET, PLATFORM_AD,
    PLATFORM_OPERATION, PLATFORM_OPERATION_ATTEMPT
}
enum PlatformAuditEventKind {
    ENTITY_CREATED, OPERATION_CREATED, ATTEMPT_CREATED,
    OPERATION_TRANSITIONED, ATTEMPT_FINALIZED, ENTITY_RESULT_APPLIED
}

record PlatformAuditEvent(
        PlatformAuditSubjectType subjectType,
        UUID subjectUuid,
        AuditAction action,
        PlatformAuditEventKind eventKind,
        UUID operationUuid,
        PlatformOperationType operationType,
        PlatformEntityType entityType,
        UUID entityUuid,
        Optional<PlatformOperationStatus> previousOperationStatus,
        Optional<PlatformOperationStatus> newOperationStatus,
        Optional<PlatformAttemptKind> attemptKind,
        Optional<Integer> attemptNumber,
        Optional<PlatformAttemptStatus> previousAttemptStatus,
        Optional<PlatformAttemptStatus> newAttemptStatus,
        Optional<PlatformDesiredState> previousDesiredState,
        Optional<PlatformDesiredState> newDesiredState,
        Optional<PlatformObservedState> previousObservedState,
        Optional<PlatformObservedState> newObservedState,
        Optional<BigDecimal> previousBudgetAmount,
        Optional<BigDecimal> newBudgetAmount,
        Optional<String> externalIdFingerprint,
        Optional<PlatformStableErrorCode> normalizedErrorCode,
        Optional<String> safeProviderTraceId) {}
```

Every reference and Optional container is non-null. `subjectUuid` is the exact entity, operation, or attempt UUID selected by `subjectType`; the correlation `operationUuid`, `operationType`, `entityType`, and `entityUuid` are always present. `CREATE` is permitted only for `ENTITY_CREATED`, `OPERATION_CREATED`, and `ATTEMPT_CREATED`; the other kinds require `UPDATE`. Operation status fields are present only for operation subjects, attempt kind/number/status only for attempt subjects, desired/observed state, budget, and external-ID fingerprint only for entity subjects, and result code/trace only on finalized attempt and matching finalized-operation events. CREATE kinds require the applicable previous value empty and new value present; UPDATE kinds require both previous and new applicable values, except a first observed state may use empty previous plus present new and a first external-ID fingerprint is represented by its single present field. Budget values have the canonical currency/scale rules but currency itself remains derivable from the immutable entity/account and is not repeated. The fingerprint is lowercase SHA-256 and raw external ID is forbidden. Constructors reject every other field combination with `PLATFORM_CONTRACT_INVALID`; there is no arbitrary map, free-form message, or provider payload.

Observed-state presence is exact. If a success/found outcome omits `observedState`, both Audit fields are empty and no entity event is created for observation. If it supplies a value different from the locked row (including NULL to a value), an `ENTITY_RESULT_APPLIED` event is mandatory with previous empty/present exactly matching the row and new present matching the outcome. If it supplies the same value, observation alone is a database no-op and creates no entity event; when external-ID, desired-state, or budget mutation already requires that entity event, both observed fields are present and equal to record the supplied but unchanged normalized observation. An entity result event must contain at least one effective external-ID, desired-state, observed-state, or budget change; equal observed fields alone are forbidden.

The writer maps the typed record to existing `AuditEvent`/`AuditChange` deterministically. `entityType` is the stable `PlatformAuditSubjectType` enum name, `entityUuid=subjectUuid`, and `productUuid` is the Ad's Product UUID only for `PLATFORM_AD`, otherwise NULL. Changes use only the present values in this fixed order and exact field names: `operationStatus`, `attemptKind`, `attemptNumber`, `attemptStatus`, `desiredState`, `observedState`, `budgetAmount`, `externalIdFingerprint`, `normalizedErrorCode`, `safeProviderTraceId`. Enum/code values use their names, UUIDs are lowercase, money is canonical plain decimal, absent old/new is SQL NULL, and the existing `AuditValueSanitizer` runs before append. The writer generates one UUID per Audit event, `AuditOperationContext` supplies request/actor/source context, and the transaction clock supplies `occurredAt`.

Each orchestration transaction owns the following exact event cardinality and content; no event is collapsed across subjects:

| Transaction/effective result | Exact events in the same transaction |
| --- | --- |
| Transaction A, `CREATE_*` | Exactly 2: entity `CREATE/ENTITY_CREATED` with its initial `desiredState` as new value; operation `CREATE/OPERATION_CREATED` with new operation status `CREATED` |
| Transaction A, mutation operation | Exactly 1: operation `CREATE/OPERATION_CREATED` with new operation status `CREATED`; the pre-existing entity is unchanged |
| Transaction B submit claim | Exactly 2: operation `UPDATE/OPERATION_TRANSITIONED` from `CREATED` or `FAILED_RETRYABLE` to `SUBMITTING`; matching attempt `CREATE/ATTEMPT_CREATED` with kind `SUBMIT`, number `attempt_count`, and new attempt status `STARTED` |
| Transaction C submit finalization, any normalized result | Exactly 2 baseline events: attempt `UPDATE/ATTEMPT_FINALIZED` from `STARTED` to the persisted result status, plus operation `UPDATE/OPERATION_TRANSITIONED` from `SUBMITTING` to the matching operation status; both carry the same persisted normalized code/trace when present |
| Transaction C successful create | The 2 baseline events plus exactly 1 entity `UPDATE/ENTITY_RESULT_APPLIED` carrying the newly recorded external-ID fingerprint; if outcome observation is present it also carries exact previous/new observed state, changed or unchanged under the presence rule; total 3 |
| Transaction C successful `PAUSE`/`RESUME` | The 2 baseline events plus exactly 1 entity `UPDATE/ENTITY_RESULT_APPLIED` carrying exact previous/new desired state and no external-ID field; present observation adds exact previous/new observed state even when equal, absent observation adds neither; total 3 |
| Transaction C successful `UPDATE_BUDGET` | The 2 baseline events plus exactly 1 entity `UPDATE/ENTITY_RESULT_APPLIED` carrying exact previous/new budget amount and no external-ID field; present observation adds exact previous/new observed state even when equal, absent observation adds neither; total 3 |
| Reconcile claim | Exactly 2: operation `UPDATE/OPERATION_TRANSITIONED` from `UNKNOWN_OUTCOME` to `RECONCILING`; matching attempt `CREATE/ATTEMPT_CREATED` with kind `RECONCILE`, reconciliation number, and new status `STARTED` |
| Reconcile finalization, any normalized result | Exactly 2 baseline events: reconcile attempt `UPDATE/ATTEMPT_FINALIZED` and operation `UPDATE/OPERATION_TRANSITIONED` from `RECONCILING` to the matching status, with identical persisted code/trace when present |
| Reconciliation-found for a create whose ID was previously unknown | The 2 reconcile-finalization events plus exactly 1 entity `UPDATE/ENTITY_RESULT_APPLIED` carrying the found external-ID fingerprint and exact previous/new observed state when supplied; total 3 |
| Reconciliation-found for `PAUSE`/`RESUME` | Exactly 3: baseline 2 events plus entity `UPDATE/ENTITY_RESULT_APPLIED` carrying the durable payload's exact previous/new desired state. Returned ID remains absent and durable ID unchanged. Present observation adds exact previous/new observed state to the same entity event even when unchanged; absent observation adds neither |
| Reconciliation-found for `UPDATE_BUDGET` | Exactly 3: baseline 2 events plus Ad Set `UPDATE/ENTITY_RESULT_APPLIED` carrying the durable payload's exact previous/new budget amount; the same transaction sets `last_budget_operation_uuid`, though that UUID is correlation rather than a separate Audit change. Returned ID remains absent. Present observation adds exact previous/new observed state to the same entity event even when unchanged; absent observation adds neither |
| Reconciliation-found mutation with stale entity version | Exactly 2: reconcile attempt `UPDATE/ATTEMPT_FINALIZED` from `STARTED` to `UNKNOWN_OUTCOME` and operation `UPDATE/OPERATION_TRANSITIONED` from `RECONCILING` to `UNKNOWN_OUTCOME`, both with `PLATFORM_RESPONSE_AMBIGUOUS` and retained safe trace. No entity event; no desired/observed/budget/provenance change; FOUND evidence is not persisted |
| Stale `SUBMITTING` recovery | Exactly 2: matching SUBMIT attempt `UPDATE/ATTEMPT_FINALIZED` from `STARTED` to `UNKNOWN_OUTCOME`, and operation `UPDATE/OPERATION_TRANSITIONED` from `SUBMITTING` to `UNKNOWN_OUTCOME`; both carry `PLATFORM_RESPONSE_AMBIGUOUS`, empty trace, and the recovery correlation/attempt number |
| Stale `RECONCILING` recovery | Exactly 2: matching RECONCILE attempt `UPDATE/ATTEMPT_FINALIZED` from `STARTED` to `UNKNOWN_OUTCOME`, and operation `UPDATE/OPERATION_TRANSITIONED` from `RECONCILING` to `UNKNOWN_OUTCOME`; both carry `PLATFORM_RECONCILIATION_INCONCLUSIVE`, empty trace, and the recovery correlation/attempt number |

Attempt-3 retryable-to-terminal conversion uses the Transaction C baseline with `FAILED_TERMINAL` and `PLATFORM_MAX_ATTEMPTS_EXCEEDED`; Audit receives the retained safe trace but neither the original retryable code nor retry seconds. Recovery Audit uses the same application transaction as attempt/operation finalization; a writer failure rolls back both rows and leaves the STARTED claim unchanged for safe restart. A Transaction A duplicate identity replay, stale optimistic loser, local validation/eligibility failure, idempotency conflict, fourth retry, mutation replay, or no-op observation emits zero events. An Audit writer exception at any position propagates and rolls back all database mutations and all earlier Audit appends in that transaction. A missing writer binding fails construction/startup.

Forbidden everywhere, including logs, Audit, exceptions, JSON evidence, test snapshots, and completion reports:

- tokens, secrets, credentials, cookies, Authorization headers, raw external account IDs, raw provider request/response bodies, Graph URLs, arbitrary targeting, SDK objects, stack traces returned to a caller, or unbounded exception messages.

The existing sanitizer marker list is the minimum. Platform canonicalizers reject keys containing `authorization`, `cookie`, `credential`, `password`, `secret`, or `token`, case-insensitively, before persistence. Tests use sentinel secrets and capture logs/Audit/database rows to prove absence. Safe trace IDs are length/character bounded and never trusted as URLs.

Required Audit tests distinguish the enforcement layer: transaction integration tests assert the exact matrix cardinality, subject UUID, action/kind, old/new typed fields, fixed change ordering, and correlation for create/mutation Transaction A, submit/reconcile claim, every final outcome, successful desired/observed-state and budget change, successful create/found ID recording, reconciled PAUSE/RESUME/UPDATE_BUDGET, reconciled stale-version uncertainty, both stale recovery paths, and attempt-3 conversion. Create, mutation, and reconciliation-found cases each cover observed state absent, first/changed, and present-but-unchanged; unchanged observation creates no standalone entity Audit, but is included when another entity field requires the event. A parameterized throwing writer fails before event 1, between every event pair, and after the final append but before commit, proving entity/operation/attempt plus all Audit rows roll back. Reconciled budget tests also force deferred-trigger failure and prove no Audit or partial provenance survives. Missing/disabled writer binding requires startup/constructor failure. Duplicate/invalid/replay/no-op paths assert zero Audit rows, and sentinel-secret tests assert redaction across every optional field. Direct SQL may bypass Audit by design and is used only to test database constraints; this limitation is recorded and must not be presented as database-level Audit enforcement.

## Verification matrix

| Area | Required evidence |
| --- | --- |
| Migration cold path | Empty PostgreSQL migrates V1 through V12; Flyway latest is 12; all seven tables, columns, constraints, indexes, functions, and triggers match this specification |
| Upgrade path | A populated V11 fixture containing Product, Campaign Plan, Asset, generated output, and approved decision migrates to V12 without data/version/checksum change |
| Migration immutability | Canonical SHA-256 assertions cover V1–V11 byte-for-byte; V12 is new only; no pending migration remains |
| Migration atomicity | A deliberate V12 object-name collision causes the migration to roll back with none of the other V12 objects left behind |
| Hibernate | `ddl-auto=validate` passes against V12; all enum lengths, JSONB, `CHAR`, money precision/scale, nullability, composite relationships, timestamps, and `@Version` mappings match |
| Direct SQL identity/evidence | Reject META/PRODUCTION accounts and mismatched/non-FAKE operation/attempt evidence. Reject mutation of every account/entity/operation identity, write-once external ID, canonical payload/hash, or attempt identity. Ad insert rejects mismatched Asset/output (`23503` or semantic `23514`), TEXT output, pending/rejected review, rejected decision, blocked preservation, inactive Product/Asset, null/mismatched checksums, and later Ad evidence substitution. Snapshot test proves later upstream lifecycle/type/checksum divergence leaves the historical Ad unchanged but blocks a new Ad insert |
| Direct SQL lifecycle | Reject non-CREATED operation INSERT, nonzero counters/version or prefilled result fields; reject missing/mismatched latest SUBMIT/RECONCILE attempt for each operation state, including pre-succeeded INSERT and success without finalized attempt; require create success ID/entity/operation/fingerprint coherence and mutation success NULL operation ID/no fingerprint; require both direct and reconciled mutation success to have the exact entity desired/budget/provenance mutation; reject invalid desired/observed/operation/attempt transitions, counter drift, terminal updates, account restore, and version jumps. Attempt-3 terminal rows require `PLATFORM_MAX_ATTEMPTS_EXCEEDED`, FAILED_TERMINAL evidence with no retry seconds, and matching trace/evidence across operation and attempt |
| Budget mutation | Initial provenance is null; exact successful per-entity-bounded `UPDATE_BUDGET`, whether direct or reconciled, changes only amount/provenance/optional observation/version; stale version, type/currency/policy change, missing/wrong/reused/non-success operation, reconciled success without amount/provenance, unchanged/out-of-bound amount, direct amount update, deferred-trigger failure, and Audit-writer rollback fail atomically; replay is no-op and concurrent changes have one winner. Assert batch/account-day aggregates are absent/deferred, not partially enforced |
| Delete protection | DELETE from each of seven tables fails; referenced V1–V11 records remain protected by `ON DELETE RESTRICT`; Audit remains append-only |
| Metric append-only revisions | Revision 1 and changed revision 2 coexist; latest/as-of selection deterministic; skipped/repeated revision, non-monotonic fetch time, exact duplicate fingerprint, negative/base invalid values, update, and delete fail; nullable metrics remain NULL. Direct SQL rejects currency/timezone mismatch and inactive account; matched active account succeeds |
| Domain unit | All valid/invalid state edges; exact Java nesting/types/Optional/null rules; every local error and precedence; exact payload keys/formats; canonical bytes/hash; per-entity budget bounds/increase/decrease/replay; direct/reconciled PAUSE, RESUME, and UPDATE_BUDGET equivalence under durable expected version; reconciled stale-version UNKNOWN transformation; create-versus-mutation returned/persisted ID and fingerprint rules; observed-state Audit absent/first/changed/unchanged presence rules; both stale recovery field transformations; attempt-3 application-generated terminal fields; metric account/fingerprint/revision; provider/profile matrix; exhaustive normalized outcome/error mapping; max attempts/reconciliations |
| Port contract | Every exact method/record field/enum and required/optional rule; three fake ID prefixes and 24-hex suffix; create returned-ID/fingerprint coherence; mutation durable-target verification with absent returned/persisted ID/fingerprint; both retryable fixtures and exact attempt-3 terminal transformation; both write-terminal fixtures; malformed/null/exception ambiguity; idempotent replay/conflict; reconcile found/not-found/unknown/terminal; stable FAKE evidence/provider coherence; LOCAL/TEST dispatch matrix; and no provider/secret/network types escaping |
| Persistence before call | A separate connection can read the operation and STARTED attempt inside fake invocation; adapter observes no active Spring transaction |
| Concurrency | Concurrent duplicate create returns one operation/entity and one submit call; concurrent claim and concurrent stale recovery each have one winner; stale versions fail; max attempts cannot be exceeded |
| Retry | Retry reuses operation UUID, canonical payload, idempotency key, and entity; creates the next attempt row only; no replacement entity/operation. Each retryable fixture on attempt 3 yields the exact terminal attempt/operation/evidence fields, retains only safe trace, discards original code/retry seconds, and a fourth request returns max-attempt before invalid-state with zero mutation/call/Audit |
| Ambiguous recovery | With fixed clocks, stale SUBMITTING and RECONCILING each finalize exactly the matching STARTED attempt and operation to the specified UNKNOWN fields/evidence/code/timestamps/version with zero adapter calls. ReconciliationFound against stale entity version converts to the exact persistence-uncertainty UNKNOWN attempt/operation with retained trace, discarded FOUND evidence, no entity mutation or entity Audit event, exactly two attempt/operation Audit events, and no re-call. For `recoverStaleClaim`, before-threshold/wrong-attempt/stale expected operation version fail unchanged; Audit failure rolls back; restart repeats safely with one winner. Recovered submit can only reconcile, recovered reconcile respects its cap; NOT_FOUND remains unresolved |
| Audit | Application transaction invariant: assert the exact typed event contract and transaction matrix cardinality/content for entity plus operation creation, submit/reconcile claim/finalization, successful create/desired/observed/budget/found entity result, both stale recoveries, and attempt-3 conversion. Throw before/between/after appends rolls back all state/Audit; missing binding fails construction/startup; duplicate/invalid/replay/no-op paths write none; sentinel secret is absent. Do not claim direct-SQL Audit enforcement |
| Profile/security | Only FAKE/LOCAL under local and FAKE/TEST under test dispatch; cross-environment, META, default/production, missing explicit fake config, unsupported provider, and evidence mismatch fail closed; no network-capable dependency or credential implementation |
| Regression | Full Backend Testcontainers suite; Frontend lint/typecheck/tests/build/audit even though unchanged; Compose config/cold health; Smoke; Playwright; actionlint; Gitleaks history/worktree; `git diff --check` |

Normal Remote Push and Pull Request CI must execute `quality-and-compose` and `secret-scan` without required-step skips.

## Rollback and forward recovery

- Before merge, discard/revert the unmerged implementation branch; never edit a merged migration.
- After V12 is merged or applied, rollback is forward-only. Do not run Flyway clean, drop V12 objects, or delete foundation data.
- If application deployment fails after V12 succeeds, roll back application binaries to the prior compatible version; unused additive tables remain inert.
- Repair a V12 defect with V13 or later after a new approved specification; do not alter V12.
- Persisted `CREATED`/`FAILED_RETRYABLE` work remains inert until an approved internal runner claims it. A persisted stale `SUBMITTING` or `RECONCILING` claim is recovered only through the exact matching-attempt transaction above; no adapter is called. Recovered submission moves only to reconciliation, never retry; recovered reconciliation remains unknown and respects the existing reconciliation cap.
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
