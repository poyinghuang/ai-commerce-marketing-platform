# Stage 04B — Campaign and Ad Set Vertical Slice

## Gate status

- Status: Repository-owner decision recorded; specification corrections complete pending independent re-review
- Branch: `codex/stage-04b-campaign-adset-specification`
- Base: `4d89448cb48520c14f6ce991d803a0221503ebeb`
- Stage 4A prerequisite: Passed; PR #60 merged at `4d89448cb48520c14f6ce991d803a0221503ebeb`; post-merge CI Run `31998731088` passed
- Product settings: Approved by the repository owner on 2026-08-17
- Implementation: Blocked; not started
- Manager Decision: `ESCALATE_TO_HUMAN` on reviewed Head; findings resolved pending independent re-review
- Merge: Not started
- Stage 4C: Locked

The reviewed content Head is `2b0dba3bc94b0e9be9d699f23a42880da3e0fee7`. Exact-head Push CI `32007307611` and PR CI `32007310578` passed. On 2026-08-17 the repository owner selected the narrowly scoped deterministic-FAKE exception described below. Findings `4B-SPEC-001` through `4B-SPEC-009` are corrected in this document but remain `RESOLVED_PENDING_RE_REVIEW`; PR #61 stays Draft and all Stage 4B runtime work remains frozen until a corrected exact Head passes full CI and independent re-review.

## Objective

Deliver the first user-visible, fake-provider-only Campaign and Ad Set vertical slice. A local/test user can preview and explicitly confirm creation of paused Campaigns and Ad Sets, read their normalized state, request pause/resume, make bounded Ad Set budget changes, and inspect/retry/reconcile the resulting durable operation. PostgreSQL remains authoritative and every mutation uses the Stage 4A operation, attempt, evidence, idempotency, reconciliation, optimistic-concurrency, and Audit foundation.

This milestone also adds the approved budget authorization ledger. The ledger limits newly authorized budget exposure; it is not provider spend, billing, settlement, or a metric.

## Approved decisions

1. Provider execution remains deterministic `FAKE` only under explicit `local` or `test` configuration. Default and production profiles expose no Stage 4B mutation controller, usable adapter, credential contract, or network path.
2. Objective is exactly `OUTCOME_SALES`.
3. Campaigns and Ad Sets are created `PAUSED`. Resume is a separate explicit confirmation.
4. Account currency is exactly `TWD`; account timezone is exactly `Asia/Taipei`.
5. Ad Set budget is `DAILY <= 100.000000` or `LIFETIME <= 300.000000`, positive, scale `0..6`.
6. One explicit confirmation creates one operation batch containing exactly one Stage 4B mutation operation. The batch ceiling is TWD `300.000000`.
7. Account-business-day newly authorized exposure is capped at TWD `1000.000000`.
8. `CREATE_AD_SET` consumes the complete initial budget. An `UPDATE_BUDGET` increase consumes only `newBudgetAmount - previousBudgetAmount`. A decrease consumes zero and never restores batch/day capacity.
9. A matching idempotent replay creates no batch, reservation, aggregate increment, entity mutation, Audit event, or adapter invocation. A conflict fails before all writes.
10. Reservation is conservative and irreversible in 4B: terminal failure, ambiguity, reconciliation, pause, archive, decrease, or an unsubmitted created operation does not release authorization capacity. Cancellation/release/settlement is out of scope.
11. Business date is derived by PostgreSQL from database `statement_timestamp()` at `Asia/Taipei`; Browser/application input cannot choose it.
12. Targeting profile and placement profile are exactly `TW_BROAD_FEEDS_V1`: Taiwan, age 18–65, Facebook Feed and Instagram Feed only. No arbitrary targeting JSON, expansion, special-ad-category, audience, pixel, or provider field is accepted.
13. The local/test fixed trusted actor is acceptance infrastructure, not an authentication or RBAC implementation. Any real account, credential, spend, production route, Tenant authority, or actual operator/approver enforcement requires `ESCALATE_TO_HUMAN`.
14. **Repository-owner FAKE exception (approved 2026-08-17):** the same fixed actor may confirm `RESUME` and budget changes only when all Stage 4B gates select deterministic `FAKE` in `LOCAL` or `TEST`. This exception authorizes no credential, network, real-provider, paid-delivery, billing, production, Tenant, Auth, or RBAC path. Every non-FAKE or production-capable path remains inert and requires real `PLATFORM_OPERATOR`/`PLATFORM_APPROVER` separation plus a new human security approval.

## Scope

### Included

- Campaign preview, confirm-create, read, pause, and resume.
- Ad Set preview, confirm-create, read, pause, resume, and bounded budget increase/decrease.
- Same-origin Next.js BFF and one `/platforms/meta` local/test management UI.
- Operation status, explicit eligible retry, and explicit ambiguous-outcome reconciliation.
- Additive V13 budget-batch, reservation-ledger, and account-day aggregate persistence.
- Server-owned account, objective, schedule, targeting, placement, currency, and budget policies.
- Exact ETag/If-Match concurrency, stable errors, request IDs, typed Audit, deterministic fake outcomes, and full local/remote acceptance.

### Excluded

- Ads/creative publication, asset selection/upload/delete, delivery reads, metrics, Dashboard, Decision Engine, scheduler, automatic retry, automatic reconciliation, and Stage 4C+.
- Real Meta SDK/HTTP, Graph URL/version, credentials, account/page/Instagram IDs, external writes, paid delivery, billing, and production deployment.
- Authentication, RBAC, Tenant, authorization, approval-role enforcement, or a claim that the local/test fixed actor is a security boundary.
- Budget capacity release, settlement against actual spend, refunds, cancellation, cross-day transfer, batch size greater than one, or arbitrary currency.
- Editing V1–V12, destructive migration, backfill, or mutation of existing Stage 4A evidence.

## Additive V13 persistence contract

V13 is named `V13__add_platform_budget_authorization_ledger.sql`. V1–V12 remain byte-for-byte unchanged.

### `platform_operation_batches`

| Column | Type | Rule |
| --- | --- | --- |
| `operation_batch_uuid` | UUID PK | Server-generated immutable identity |
| `operation_uuid` | UUID UNIQUE NOT NULL | One Stage 4B operation per batch; `DEFERRABLE INITIALLY DEFERRED` FK to `platform_operations` |
| `platform_account_uuid` | UUID NOT NULL | Must equal operation account |
| `client_request_uuid` | UUID NOT NULL | Must equal operation request identity |
| `requested_actor_type` | VARCHAR(32) NOT NULL | Must equal operation actor type |
| `requested_actor_id` | VARCHAR(128) NOT NULL | NFC-normalized; must equal operation actor ID |
| `currency` | CHAR(3) NOT NULL | Exactly `TWD` |
| `business_date` | DATE NOT NULL | Database-derived `Asia/Taipei` date |
| `reserved_amount` | NUMERIC(19,6) NOT NULL | Exact sum of batch reservations; `0..300` |
| `created_at` | TIMESTAMPTZ NOT NULL | Database `statement_timestamp()` captured by the batch insert |
| `version` | BIGINT NOT NULL | Exactly zero; batch is immutable |

Unique durable request identity is `(platform_account_uuid, requested_actor_type, requested_actor_id, client_request_uuid)`. Insert is permitted only in the same Transaction A as the exact operation and any reservation. UPDATE/DELETE always fail with SQLSTATE `23514`.

### `platform_budget_reservations`

| Column | Type | Rule |
| --- | --- | --- |
| `budget_reservation_uuid` | UUID PK | Server-generated immutable identity |
| `operation_batch_uuid` | UUID UNIQUE NOT NULL | Exactly one reservation record per batch |
| `operation_uuid` | UUID UNIQUE NOT NULL | Exact budget-affecting operation |
| `platform_account_uuid` | UUID NOT NULL | Matches batch and operation |
| `account_budget_day_uuid` | UUID NOT NULL | Exact matching day-row Audit identity |
| `platform_ad_set_uuid` | UUID NOT NULL | Target or newly created Ad Set |
| `reservation_kind` | VARCHAR(32) NOT NULL | `INITIAL`, `INCREASE`, or `DECREASE_NO_RELEASE` |
| `previous_budget_amount` | NUMERIC(19,6) NULL | NULL only for INITIAL |
| `new_budget_amount` | NUMERIC(19,6) NOT NULL | Canonical durable request value |
| `reserved_amount` | NUMERIC(19,6) NOT NULL | INITIAL=new; INCREASE=new-old; DECREASE=0 |
| `currency` | CHAR(3) NOT NULL | Exactly TWD and matches account/request |
| `business_date` | DATE NOT NULL | Matches batch/day aggregate |
| `created_at` | TIMESTAMPTZ NOT NULL | Matches batch database statement |

The row is append-only and hard-delete protected. The database overwrites `business_date` and `created_at` from its parent batch, so application or direct-SQL values cannot choose a different clock/date. The canonical operation payload, operation type, entity UUID, account, amounts, currency, batch, and reservation must agree through reciprocal `DEFERRABLE INITIALLY DEFERRED` triggers. `CREATE_AD_SET` requires INITIAL. `UPDATE_BUDGET` requires INCREASE when new > old and DECREASE_NO_RELEASE when new < old. Equal amounts fail before Transaction A and create no row.

### `platform_account_budget_days`

| Column | Type | Rule |
| --- | --- | --- |
| `platform_account_uuid` | UUID | Composite PK with business date/currency |
| `business_date` | DATE | PostgreSQL-derived Asia/Taipei date |
| `currency` | CHAR(3) | Exactly TWD |
| `account_budget_day_uuid` | UUID UNIQUE NOT NULL | Server-generated stable Audit subject identity; immutable |
| `reserved_amount` | NUMERIC(19,6) | Sum of reservation ledger for identity; `0..1000` |
| `ceiling_amount` | NUMERIC(19,6) | Exactly `1000.000000`, immutable |
| `created_at` / `updated_at` | TIMESTAMPTZ | Server times |
| `version` | BIGINT | Increments once per positive reservation |

For `CREATE_AD_SET` and `UPDATE_BUDGET`, Transaction A first executes `INSERT ... VALUES(server UUID, account, date, 'TWD', 0, 1000.000000, statement_timestamp(), statement_timestamp(), 0) ON CONFLICT (platform_account_uuid,business_date,currency) DO NOTHING`, then selects the resulting row `FOR UPDATE` in that fixed order. The transaction uses the persisted winner's `account_budget_day_uuid`; it never assumes its candidate UUID won. This makes concurrent first use serialize without a spurious uniqueness error. Campaign create and Campaign/Ad Set state commands create no account-day row. A positive reservation increments amount and version exactly once and sets `updated_at` to the database statement time; a zero decrease creates the zero row when absent but leaves amount, version, and timestamps unchanged after bootstrap while retaining immutable reservation evidence. A deferred trigger recomputes the exact ledger sum at commit, so direct SQL cannot forge either aggregate. UPDATE is allowed only for the exact positive reservation delta in the same transaction. DELETE fails. PostgreSQL serialization/deadlock exhaustion maps to HTTP 409 / `PLATFORM_LEDGER_CONCURRENCY_CONFLICT`; the application retries no transaction automatically.

### Database integrity and concurrency

- Batch total is the ledger sum and cannot exceed TWD 300.
- Account-day total is the ledger sum and cannot exceed TWD 1000.
- A batch `BEFORE INSERT` trigger overwrites, rather than defaults, `created_at=statement_timestamp()`, `business_date=(created_at AT TIME ZONE 'Asia/Taipei')::date`, `currency='TWD'`, and `version=0`. Its trigger copies that exact anchor/date to the reservation. Supplied past/future values cannot select the date and direct-SQL forged values are proven overwritten or rejected by final coherence.
- Each reservation stores `account_budget_day_uuid` and has a composite FK `(account_budget_day_uuid,platform_account_uuid,business_date,currency)` to a declared unique day-row tuple with those four columns. Batch, reservation and day rows also have explicit account FKs. A reservation cannot reference a missing/wrong day row.
- Only `CREATE_AD_SET` and `UPDATE_BUDGET` have reservation rows. Campaign and state operations have immutable zero-reservation batches.
- A batch `BEFORE INSERT` trigger rejects an `operation_uuid` already visible at statement start, requiring batch-first order. A reciprocal deferred operation trigger applies to every matching operation row inserted after V13 installation, regardless of SQL/application caller, and requires exactly one batch for `CREATE_CAMPAIGN`, `CREATE_AD_SET`, Campaign/Ad Set `PAUSE`/`RESUME`, or `UPDATE_BUDGET`. It does not apply to `CREATE_AD` or later milestone entity types.
- Existing V12 operations are not backfilled. Every pre-V13 unbatched operation is preserved byte-for-byte and is read-only/inert through Stage 4B HTTP: status reads remain allowed, while submit/retry/reconcile return 409 / `PLATFORM_LEGACY_OPERATION_INERT` with zero mutation/call/Audit. A batch cannot be attached retroactively.
- Day-row insertion is valid only at zero amount, ceiling `1000.000000`, version zero, and database-owned timestamps. A positive update must equal the one new reservation delta, increment version by one, and use the same transaction/anchor; zero delta performs no update. Reciprocal deferred checks require at least one final reservation for every committed day row, and require final `reserved_amount` plus reservation count to equal the immutable ledger; orphan bootstrap rows and extra updates fail at commit.
- Batch, operation, reservation, aggregate update, entity construction if applicable, and Audit commit atomically in Transaction A.
- Concurrent positive reservations on one account/day serialize on the aggregate row. The first transactions within the ceiling commit; a transaction exceeding the remaining capacity rolls back completely.
- Operation replay reads the existing operation/batch/reservation and returns it. It never re-locks/increments the aggregate.
- Direct SQL tests cover batch-first/retroactive attachment, new operation without batch, batch without new operation, missing/extra/wrong operation, account, Ad Set, day UUID/date/currency, kind, old/new/delta, forged batch timestamps, forged initial day amount/ceiling/version, extra aggregate update, batch/day ceilings, reused operation, zero-release semantics, mutation/delete, and deferred-commit rollback.

## Server-owned policy

`PlatformBudgetPolicy` remains the Stage 4A per-entity contract. Stage 4B adds:

```java
record PlatformAggregateBudgetPolicy(
    String currency,
    ZoneId businessZone,
    BigDecimal maxOperationBatchAmount,
    BigDecimal maxAccountBusinessDayAmount) {}
```

The only implementation returns `TWD`, `Asia/Taipei`, `300.000000`, and `1000.000000` under explicit local/test fake configuration. Missing or mismatched policy returns `PLATFORM_POLICY_REJECTED` before Transaction A. The Browser cannot send ceilings, business date, provider/account identity, timezone, targeting JSON, placement JSON, or policy keys outside the closed profiles.

### Exact runtime gate, account, and actor

The Stage 4B web/controller configuration is present only when all four conditions are true: active Spring profile is exactly `local` or `test`; `platform.adapter=fake`; `platform.web.enabled=true`; and `platform.stage4b.enabled=true`. `platform.fake.enabled` is not introduced. Default, production, a missing property, any other adapter value, or mixed `production` profile fails closed by omitting the Stage 4B controller and fake adapter. The Next.js Stage 4B Route Handlers additionally require server-only `PLATFORM_STAGE4B_ENABLED=true`; otherwise they return their own sanitized 404 without contacting Backend.

The server-owned fixtures are exact: local uses UUID `00000000-0000-4000-8000-00000000004b`, reference `stage4b-local`, environment `LOCAL`, and fingerprint `4f1eee978e5efed2d42ac62995484b642870cda74dea26cd2d2f63653d51cf36`; test uses UUID `00000000-0000-4000-8000-00000000005b`, reference `stage4b-test`, environment `TEST`, and fingerprint `9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2`. A gate-conditional local/test initializer uses `INSERT ... ON CONFLICT DO NOTHING` for that exact non-secret FAKE fixture, then reads it back and refuses to mutate any mismatch. No initializer bean exists in default/production. Resolution requires exactly that one `ACTIVE` row with `provider_key=FAKE`, the exact UUID/reference/environment/fingerprint, `currency=TWD`, and `timezone=Asia/Taipei`. Zero rows, conflict/mismatch, or any additional candidate selected by the reference query returns HTTP 503 / `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` before Transaction A, without UUID or account-reference disclosure. Startup/profile tests prove no credential bean, provider URL, HTTP client, Meta SDK, or network adapter can coexist with this gate.

The only actor tuple is the existing `AuditActor.localAdmin()` value: `requestedActorType=LOCAL_ADMIN`, NFC actor ID `local-admin`, and Audit source `API`; all are injected server-side and no actor header/body/query field is accepted. The server-owned Ad Set optimization goal is exactly `OFFSITE_CONVERSIONS`. The targeting and placement keys remain `TW_BROAD_FEEDS_V1`.

### Campaign Plan eligibility and immutable mapping

Campaign creation locks the referenced `campaign_plans` row and accepts it only when all of these are true:

- lifecycle is `ACTIVE`, `archived_at` is NULL, and its locked `version` equals `expectedCampaignPlanVersion` returned by preview;
- `platform` is exactly `META`, `objective` is exactly `OUTCOME_SALES`, and `currency` is exactly `TWD` after trimming; NULL, aliases, or case-folded alternatives are rejected rather than inferred;
- `start_date` and `end_date` are both present, `start_date >=` the database-derived Asia/Taipei business date, and `end_date >= start_date`;
- at least one of `budget_daily` or `budget_total` is positive; a later Ad Set `DAILY` amount cannot exceed non-NULL `budget_daily`, and a `LIFETIME` amount cannot exceed non-NULL `budget_total`. A missing corresponding Plan budget makes that Ad Set type ineligible.

The immutable Campaign snapshot maps `campaign_uuid` directly, objective to `OUTCOME_SALES`, timezone to `Asia/Taipei`, and schedule to `[start_date at 00:00 Asia/Taipei, (end_date + 1 day) at 00:00 Asia/Taipei)`, converted to UTC `Instant`. Browser schedule overrides are forbidden. Existing V12 uniqueness `(campaign_uuid, platform_account_uuid)` is authoritative; an existing mapping returns `PLATFORM_CAMPAIGN_ALREADY_MAPPED`, except an exact durable request replay which returns its existing operation.

Ad Set creation locks the parent Platform Campaign and its Campaign Plan. `If-Match` must equal the Platform Campaign version, the Campaign must belong to the fixed account and have desired state `PAUSED` or `ACTIVE`, and the Plan must still satisfy the rules above. The Ad Set copies the parent Platform Campaign schedule exactly; no child override exists, so containment is structural. Confirmation revalidates the Plan version, eligibility, corresponding budget cap, parent version, and account under lock. Preview returns both versions; a changed Plan returns 412 / `PLATFORM_CAMPAIGN_PLAN_STALE`, while an archived/ineligible Plan returns 409 / `PLATFORM_CAMPAIGN_PLAN_INELIGIBLE`.

## Transaction and operation lifecycle

### Preview

Preview is read-only. It canonicalizes and validates the same DTO and policies as confirmation, loads current versions, and returns a normalized summary, estimated reservation, remaining account-day capacity, warnings, and `confirmable`. It writes no batch, reservation, entity, operation, or Audit and calls no adapter. Preview is advisory: confirmation revalidates everything under lock.

### Confirmation Transaction A

1. Require the exact four-part Backend gate and BFF server flag; resolve the exact server-owned actor and the one reference-selected active FAKE account; validate only closed DTO/header syntax needed to form durable identity and stable intent.
2. Resolve durable idempotency identity **before** Campaign Plan/entity lookup or current-version/state/policy validation. A matching V13 replay returns existing data without consulting mutable current rows; a matching unbatched V12 row returns `PLATFORM_LEGACY_OPERATION_INERT`; an intent mismatch returns conflict.
3. For a new identity, lock/revalidate Campaign Plan and parent/entity versions and validate all time-independent account/profile/Plan/per-entity/state rules. Derive schedule from the locked Plan and compute the exact reservation delta, but do not yet compare `start_date` with a current clock.
4. Insert the immutable batch with its now-known reserved amount. Read back its database-owned `created_at`/`business_date`; that persisted date is the **only** date used for Campaign Plan start-date eligibility. A failure rolls back the batch. For budget operations, execute the atomic day-row bootstrap, lock its persisted winner and validate capacity against this same batch date.
5. Insert the Stage 4A entity/operation, budget reservation when applicable, aggregate delta and exact typed Audit in the same transaction.
6. Commit before adapter dispatch.

Replay equality is exact and does not recompute server-derived schedule/policy from current rows. It compares the durable identity plus: Campaign create — operation type and `campaignUuid`; Ad Set create — operation type, parent Platform Campaign UUID, budget type, canonical amount and the original parent `If-Match` version; state — operation type, entity type/UUID, target desired state and the original entity `If-Match` version; budget — entity UUID, canonical new amount and the original entity `If-Match` version. `expectedCampaignPlanVersion` is confirmation-time validation-only, is not added to the closed V12 canonical operation payload/hash, and is ignored after an operation with the durable identity exists. The previously persisted canonical schedule/payload remains authoritative. An exact replay therefore succeeds after Plan version change/archive or an Asia/Taipei date rollover; a changed business intent conflicts.

V13 defines immutable SQL function `platform_taipei_business_date(timestamptz)` and both the batch trigger and eligibility query use it. Boundary tests assert the dates immediately before/at/after Asia/Taipei midnight. A barrier test pauses a transaction after the batch insert and proves later eligibility reads the persisted batch date even if another clock statement observes a different date; rollback leaves no batch.

Transactions B/C, retries, attempt-3 conversion, unknown-outcome handling, reconciliation, entity mutation, and Audit reuse the exact Stage 4A contracts. No controller catches ambiguity and creates a replacement operation. A stale optimistic version, policy rejection, or ledger ceiling failure creates no attempt or provider call.

## API contract

Controllers use the exact four-part runtime gate above. Every JSON body is a closed object and is at most 16 KiB UTF-8. Unknown/duplicate fields, explicit NULL, invalid canonical UUID/money, secret-marker keys, provider/account/actor, URL, raw JSON, schedule, targeting, placement, policy and non-NFC text fields are rejected. Requests use lowercase canonical UUID strings and canonical plain-decimal money strings (no exponent, plus sign, trailing fractional zero, or JSON number). Empty-body routes reject any non-whitespace byte. The server alone resolves account, actor, objective, initial state, currency, timezone, schedule, optimization, profiles and ceilings.

The exact request records are:

```java
record CampaignPreviewRequest(UUID clientRequestUuid, UUID campaignUuid) {}
record CampaignConfirmRequest(UUID clientRequestUuid, UUID campaignUuid,
                              long expectedCampaignPlanVersion) {}
record AdSetPreviewRequest(UUID clientRequestUuid, PlatformBudgetType budgetType,
                           String budgetAmount) {}
record AdSetConfirmRequest(UUID clientRequestUuid, PlatformBudgetType budgetType,
                           String budgetAmount, long expectedCampaignPlanVersion) {}
record StateMutationRequest(UUID clientRequestUuid,
                            PlatformDesiredState targetDesiredState) {}
record BudgetMutationRequest(UUID clientRequestUuid, String newBudgetAmount) {}
```

Every field is required and non-null. Both confirm-record occurrences of `expectedCampaignPlanVersion` must be `>= 0`; state target is only `PAUSED` or `ACTIVE` and must differ from the locked desired state. On confirmation, `/pause` requires `targetDesiredState=PAUSED` and `/resume` requires `ACTIVE`; mismatch is 400 `PLATFORM_REQUEST_INVALID` before lookup. `clientRequestUuid` is generated once before preview and reused at confirmation. Ad Set parent UUID is path-owned and confirmation carries the exact Plan version returned by preview. State and budget requests are shared by preview/confirm; entity UUID is path-owned. Equal budget is invalid. No request contains an entity type discriminator, so Stage 4C `AD` cannot enter any 4B path.

The exact response records are additive API DTOs, not JPA entities:

```java
record PlatformPolicyView(String currency, String businessZone,
    String objective, String optimizationGoal, String targetingProfile,
    String placementProfile, Optional<String> maxEntityAmount,
    String maxBatchAmount, String maxAccountDayAmount) {}
record ReservationPreviewView(String kind, Optional<String> previousAmount,
    Optional<String> newAmount, String reservedDelta, LocalDate businessDate,
    String batchReservedAfter, String accountDayReservedBefore,
    String accountDayReservedAfter, String accountDayRemainingAfter) {}
record PlatformMutationPreviewView(UUID clientRequestUuid,
    PlatformOperationType operationType, PlatformEntityType entityType,
    Optional<UUID> entityUuid, Optional<Long> expectedEntityVersion,
    Optional<Long> expectedCampaignPlanVersion,
    PlatformDesiredState desiredState, Optional<PlatformBudgetType> budgetType,
    Optional<String> budgetAmount, Optional<Instant> scheduleStart,
    Optional<Instant> scheduleEnd, PlatformPolicyView policy,
    ReservationPreviewView reservation, List<String> warnings,
    boolean confirmable) {}
record PlatformCampaignView(UUID platformCampaignUuid, UUID campaignUuid,
    long campaignPlanVersion, String objective, String accountTimezone,
    PlatformDesiredState desiredState, Optional<PlatformObservedState> observedState,
    Optional<Instant> scheduleStart, Optional<Instant> scheduleEnd,
    Optional<String> externalIdFingerprint, long version) {}
record PlatformAdSetView(UUID platformAdSetUuid, UUID platformCampaignUuid,
    PlatformDesiredState desiredState, Optional<PlatformObservedState> observedState,
    PlatformBudgetType budgetType, String budgetAmount, String currency,
    String accountTimezone, String optimizationGoal,
    String targetingProfile, String placementProfile,
    Optional<Instant> scheduleStart, Optional<Instant> scheduleEnd,
    Optional<String> externalIdFingerprint, long version) {}
record PlatformOperationApiView(UUID operationUuid,
    PlatformOperationType operationType, PlatformEntityType entityType,
    UUID entityUuid, PlatformOperationStatus status, int attemptCount,
    int reconciliationCount, int maxAttempts,
    Optional<PlatformStableErrorCode> normalizedErrorCode,
    Optional<Instant> nextAttemptAt, Optional<Instant> completedAt,
    Instant createdAt, Instant updatedAt, long version) {}
```

All records and `Optional` containers are non-null. Optionals serialize as a property only when present; Java NULL never serializes. Create previews have `entityUuid` and `expectedEntityVersion` empty because the server allocates the Platform entity UUID only during the confirmation transaction; existing-entity previews require both present. Reservation kind is `NONE`, `INITIAL`, `INCREASE`, or `DECREASE_NO_RELEASE`; `NONE` has both amount optionals empty and all deltas zero. Warning values are a closed ordered subset of `CAPACITY_NOT_RELEASED`, `CONFIRMATION_REVALIDATES`, and `FAKE_ONLY_NO_REAL_DELIVERY`. Fingerprints are lowercase SHA-256; raw external IDs never leave Backend.

`PlatformOperationView` remains the exact internal Stage 4A application record. Controllers/BFF never serialize it directly; they map it to `PlatformOperationApiView`. The public record intentionally excludes `platformAccountUuid`, raw `externalId`, `safeProviderTraceId`, `outcomeEvidence`, and `claimedAt`. Its normalized error is the closed enum value already persisted, never provider text. Success, retryable, terminal, ambiguous, reconciliation and GET contract tests serialize the actual JSON and assert those forbidden property names and sentinel values are absent; BFF tests repeat the assertion after proxying.

### Routes, headers, and statuses

| Route | Request/header | Success |
| --- | --- | --- |
| `POST /api/platforms/meta/campaigns/preview` | `CampaignPreviewRequest`; no `If-Match` | 200 + `PlatformMutationPreviewView` |
| `POST /api/platforms/meta/campaigns` | `CampaignConfirmRequest`; no `If-Match` | 202 + operation `ETag`, `Location`, `X-Request-ID`, `PlatformOperationApiView`; exact replay 200 |
| `POST /api/platforms/meta/campaigns/{campaign}/ad-sets/preview` | `AdSetPreviewRequest`; no `If-Match` | 200 + preview containing parent and Plan versions |
| `POST /api/platforms/meta/campaigns/{campaign}/ad-sets` | `AdSetConfirmRequest`; parent `If-Match` required | 202 operation response; exact replay 200 |
| `POST /api/platforms/meta/campaigns/{campaign}/state-preview` | `StateMutationRequest`; no `If-Match` | 200 + preview |
| `POST /api/platforms/meta/ad-sets/{adSet}/state-preview` | `StateMutationRequest`; no `If-Match` | 200 + preview |
| `POST /api/platforms/meta/campaigns/{campaign}/pause` or `/resume` | matching `StateMutationRequest`; target `If-Match` required | 202 operation response; exact replay 200 |
| `POST /api/platforms/meta/ad-sets/{adSet}/pause` or `/resume` | matching `StateMutationRequest`; target `If-Match` required | 202 operation response; exact replay 200 |
| `POST /api/platforms/meta/ad-sets/{adSet}/budget-preview` | `BudgetMutationRequest`; no `If-Match` | 200 + preview |
| `POST /api/platforms/meta/ad-sets/{adSet}/budget` | `BudgetMutationRequest`; target `If-Match` required | 202 operation response; exact replay 200 |
| Campaign/Ad Set/operation `GET` routes | no body/query | 200 exact view + matching weak `ETag` |
| `POST /api/platform-operations/{operation}/retry` | empty body; operation `If-Match` | 202 + operation headers/view |
| `POST /api/platform-operations/{operation}/reconcile` | empty body; operation `If-Match` | 202 + operation headers/view |

The GET routes remain `/api/platforms/meta/campaigns/{uuid}`, `/api/platforms/meta/ad-sets/{uuid}`, and `/api/platform-operations/{uuid}`. Mutation/operation `ETag` is `W/"<operationVersion>"`; entity `ETag` is `W/"<entityVersion>"`. `Location` is `/api/platform-operations/{operationUuid}`. `If-Match` accepts exactly one weak decimal ETag, with no wildcard/list/strong tag. Header version becomes the canonical payload's `expectedEntityVersion`. Retry error precedence remains Stage 4A: not-found, stale ETag, max attempts, invalid state, not due. Reconcile precedence is not-found, stale ETag, reconciliation cap, invalid state. Operation reads return only `PlatformOperationApiView`; entity/operation reads reveal no account UUID, canonical payload, evidence, trace or raw external ID. Lists, search, Ads, delivery and metrics are absent.

### Stable errors

All Backend errors use exact `ApiError(code,message,requestId,timestamp,path,fieldErrors)`. `message` is the fixed safe public phrase for `code`; `path` is the normalized route without query; `fieldErrors` is empty except closed validation errors and contains only this allowlist in request declaration order: `clientRequestUuid`, `campaignUuid`, `expectedCampaignPlanVersion`, `budgetType`, `budgetAmount`, `targetDesiredState`, `newBudgetAmount`, `If-Match`, `body`. Each `FieldErrorDetail(field,message)` uses `Invalid value` except `If-Match` (`Invalid If-Match`) and `body` (`Invalid request body`), and never includes a rejected value. No ledger/account/provider identifier or provider text is included.

| HTTP | Exact stable codes |
| --- | --- |
| 400 | `PLATFORM_REQUEST_INVALID`, `PLATFORM_CONTRACT_INVALID` |
| 404 | `PLATFORM_RESOURCE_NOT_FOUND`; outside-gate controller/Route Handler absence |
| 409 | `PLATFORM_IDEMPOTENCY_CONFLICT`, `PLATFORM_INVALID_OPERATION_STATE`, `PLATFORM_RETRY_NOT_DUE`, `PLATFORM_MAX_ATTEMPTS_EXCEEDED`, `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED`, `PLATFORM_POLICY_REJECTED`, `PLATFORM_EVIDENCE_INVALID`, `PLATFORM_CAMPAIGN_PLAN_INELIGIBLE`, `PLATFORM_CAMPAIGN_ALREADY_MAPPED`, `PLATFORM_BUDGET_CAP_EXCEEDED`, `PLATFORM_LEDGER_CONCURRENCY_CONFLICT`, `PLATFORM_LEGACY_OPERATION_INERT` |
| 412 | `PLATFORM_ENTITY_STALE`, `PLATFORM_OPERATION_STALE`, `PLATFORM_CAMPAIGN_PLAN_STALE` |
| 428 | `PLATFORM_IF_MATCH_REQUIRED` |
| 429 | `PLATFORM_PROVIDER_RETRYABLE`; safe operation `Location`, no provider body |
| 500 | `INTERNAL_ERROR` |
| 503 | `PLATFORM_ACCOUNT_CONFIGURATION_INVALID`, `PLATFORM_ADAPTER_UNAVAILABLE` |

The exact public message map is immutable in 4B:

| Code | Message |
| --- | --- |
| `PLATFORM_REQUEST_INVALID` | `Platform request is invalid` |
| `PLATFORM_CONTRACT_INVALID` | `Platform contract is invalid` |
| `PLATFORM_RESOURCE_NOT_FOUND` | `Platform resource was not found` |
| `PLATFORM_IDEMPOTENCY_CONFLICT` | `The request conflicts with an existing operation` |
| `PLATFORM_INVALID_OPERATION_STATE` | `The operation is not eligible for this action` |
| `PLATFORM_RETRY_NOT_DUE` | `The operation is not yet eligible for retry` |
| `PLATFORM_MAX_ATTEMPTS_EXCEEDED` | `The operation has no retry attempts remaining` |
| `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED` | `The operation has no reconciliation attempts remaining` |
| `PLATFORM_POLICY_REJECTED` | `Platform policy rejected the request` |
| `PLATFORM_EVIDENCE_INVALID` | `Platform evidence is inconsistent` |
| `PLATFORM_CAMPAIGN_PLAN_INELIGIBLE` | `The Campaign Plan is not eligible for platform creation` |
| `PLATFORM_CAMPAIGN_ALREADY_MAPPED` | `The Campaign Plan already has a platform campaign` |
| `PLATFORM_BUDGET_CAP_EXCEEDED` | `The authorized budget capacity is insufficient` |
| `PLATFORM_LEDGER_CONCURRENCY_CONFLICT` | `The budget authorization changed concurrently` |
| `PLATFORM_LEGACY_OPERATION_INERT` | `The legacy operation is read-only` |
| `PLATFORM_ENTITY_STALE` | `The platform entity changed; reload and retry` |
| `PLATFORM_OPERATION_STALE` | `The platform operation changed; reload and retry` |
| `PLATFORM_CAMPAIGN_PLAN_STALE` | `The Campaign Plan changed; preview again` |
| `PLATFORM_IF_MATCH_REQUIRED` | `If-Match is required` |
| `PLATFORM_PROVIDER_RETRYABLE` | `The fake provider result may be retried later` |
| `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` | `The local platform account is unavailable` |
| `PLATFORM_ADAPTER_UNAVAILABLE` | `The fake platform adapter is unavailable` |
| `INTERNAL_ERROR` | `An unexpected error occurred` |

The exhaustive Stage 4A source mapping is:

| Internal `PlatformStableErrorCode` | Exposed route | HTTP / public code |
| --- | --- | --- |
| `PLATFORM_CONTRACT_INVALID` | any | 400 / same code |
| `PLATFORM_OPERATION_NOT_FOUND` | operation GET/retry/reconcile | 404 / `PLATFORM_RESOURCE_NOT_FOUND` |
| `PLATFORM_INVALID_OPERATION_STATE` | confirm/retry/reconcile | 409 / same code |
| `PLATFORM_STALE_VERSION` | Campaign state; Ad Set create/state/budget | 412 / `PLATFORM_ENTITY_STALE` |
| `PLATFORM_STALE_VERSION` | retry/reconcile | 412 / `PLATFORM_OPERATION_STALE` |
| `PLATFORM_RETRY_NOT_DUE` | retry | 409 / same code |
| `PLATFORM_RECOVERY_NOT_DUE` | none | recovery has no HTTP route; controller contract test proves unreachable |
| `PLATFORM_MAX_ATTEMPTS_EXCEEDED` | retry | 409 / same code |
| `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED` | reconcile | 409 / same code |
| `PLATFORM_ACCOUNT_INACTIVE`, `PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH`, `PLATFORM_PROVIDER_UNSUPPORTED` | any 4B route | 503 / `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` |
| `PLATFORM_POLICY_REJECTED` | preview/confirm/retry | 409 / same code |
| `PLATFORM_ADAPTER_UNAVAILABLE` | confirm/retry/reconcile | 503 / same code |
| `PLATFORM_IDEMPOTENCY_CONFLICT` | confirm | 409 / same code |
| `PLATFORM_EVIDENCE_INVALID` | confirm/retry/reconcile | 409 / same code |

Stage 4B-only mapping is also closed: malformed DTO/header/body -> 400 `PLATFORM_REQUEST_INVALID`; missing resource -> 404 `PLATFORM_RESOURCE_NOT_FOUND`; missing ETag -> 428 `PLATFORM_IF_MATCH_REQUIRED`; Campaign Plan version -> 412 `PLATFORM_CAMPAIGN_PLAN_STALE`; Plan eligibility/duplicate -> their exact 409 codes; Plan or per-entity budget bound -> 409 `PLATFORM_POLICY_REJECTED`; batch/day capacity -> 409 `PLATFORM_BUDGET_CAP_EXCEEDED`; ledger serialization exhaustion -> 409 `PLATFORM_LEDGER_CONCURRENCY_CONFLICT`; unbatched operation -> 409 `PLATFORM_LEGACY_OPERATION_INERT`; fixed-account failure -> 503 `PLATFORM_ACCOUNT_CONFIGURATION_INVALID`.

Provider outcomes are not converted into local exception codes. Persisted `PLATFORM_RATE_LIMITED` or `PLATFORM_TEMPORARILY_UNAVAILABLE` returns 429 `PLATFORM_PROVIDER_RETRYABLE` with operation `Location`/ETag, and its exact persisted enum is visible only through the safe operation DTO. Persisted validation/permission terminal, ambiguity, reconciliation-not-found/inconclusive/terminal and success return 202 with `PlatformOperationApiView`; their normalized enum/status are preserved without provider text. Tests cover every row for create, state, budget, retry and reconcile.

Validation chooses the first error in DTO field declaration order; route/path/header validation precedes body parsing, then durable replay resolution, locked versions, eligibility/state, and capacity. A persisted provider result never gets remapped by controller guesswork.

## Same-origin BFF and UI

- Fixed Next.js Route Handlers proxy only the exact routes above through validated `BACKEND_INTERNAL_URL`; there is no generic entity-type or caller-controlled backend path.
- The proxy forwards only `Content-Type`, `If-Match`, and safe `X-Request-ID`; it strips Cookie, Authorization, actor/account/provider headers, and unknown query parameters. It exposes only Content-Type, ETag, Location, and X-Request-ID response headers.
- Stage 4B request bodies are capped at 16 KiB UTF-8 before `fetch`; excess returns 413 / `PAYLOAD_TOO_LARGE`. Backend response bodies are streamed/read only up to 1 MiB; excess aborts and returns 502 / `BACKEND_RESPONSE_TOO_LARGE`. The fetch deadline is exactly 10 seconds using an abort signal: timeout returns 504 / `BACKEND_TIMEOUT`; DNS/connect/reset/invalid Backend response returns 502 / `BACKEND_BAD_GATEWAY`. These BFF errors contain only `{code,message}` and never echo exception text. Client disconnect aborts the Backend fetch and creates no retry.
- Query strings and fragments are rejected on every Stage 4B route. POST `Content-Type` must be exactly `application/json` except retry/reconcile, which require no Content-Type and zero body. Backend redirect following is disabled.
- Exact BFF errors are: 413 `PAYLOAD_TOO_LARGE` / `Request body is too large`; 502 `BACKEND_RESPONSE_TOO_LARGE` / `Backend response is too large`; 502 `BACKEND_BAD_GATEWAY` / `Backend is unavailable`; and 504 `BACKEND_TIMEOUT` / `Backend request timed out`.
- `/platforms/meta` is available only when the server-side local/test feature flag is enabled. It provides Campaign create/read, child Ad Set create/read, state and budget preview, an explicit confirmation dialog, and an operation status panel.
- Confirmation is never the default button action. The dialog shows desired state PAUSED, currency, budget type/amount, positive reservation delta, batch/day ceilings, and that decreases/failures do not release capacity.
- Resume and budget changes require a second explicit confirm action in the local/test UX. Under the repository-owner-approved FAKE exception this same fixed actor confirmation is permitted only behind all four Backend gates and the server-only BFF flag. It is not production operator/approver separation and cannot be reused by a real-provider route.
- Stale 412 reloads the entity and invalidates the preview. Unknown outcome offers reconcile only. Retry appears only for due `FAILED_RETRYABLE`; there is no automatic timer submission.
- No token, external account ID, provider URL, raw error/evidence, canonical payload, or arbitrary targeting control is rendered or stored in Browser state.

## Audit contract

Stage 4A `PlatformAuditEvent` and its exact matrix remain unchanged. Stage 4B adds three subject enum values (`PLATFORM_OPERATION_BATCH`, `PLATFORM_BUDGET_RESERVATION`, `PLATFORM_ACCOUNT_BUDGET_DAY`), a separate closed kind enum, a separate typed record, and an overload on the mandatory writer. A Stage 4A event constructor rejects the three new subjects; a Stage 4B record rejects every Stage 4A subject.

```java
enum PlatformBudgetAuditEventKind {
    OPERATION_BATCH_CREATED, BUDGET_RESERVATION_CREATED, ACCOUNT_DAY_RESERVED
}
enum PlatformReservationKind { INITIAL, INCREASE, DECREASE_NO_RELEASE }

record PlatformBudgetAuditEvent(
    PlatformAuditSubjectType subjectType,
    UUID subjectUuid,
    AuditAction action,
    PlatformBudgetAuditEventKind eventKind,
    UUID operationUuid,
    PlatformOperationType operationType,
    PlatformEntityType entityType,
    UUID entityUuid,
    UUID operationBatchUuid,
    Optional<UUID> budgetReservationUuid,
    Optional<UUID> accountBudgetDayUuid,
    LocalDate businessDate,
    Optional<PlatformReservationKind> reservationKind,
    String currency,
    Optional<BigDecimal> previousBudgetAmount,
    Optional<BigDecimal> newBudgetAmount,
    BigDecimal reservedAmount,
    Optional<BigDecimal> previousAggregateAmount,
    Optional<BigDecimal> newAggregateAmount) {}

interface PlatformAuditWriter {
    void write(PlatformAuditEvent event, AuditOperationContext context);
    void write(PlatformBudgetAuditEvent event, AuditOperationContext context);
}
```

All references/Optional containers are non-null; currency is exactly `TWD`; amounts are canonical scale `0..6`; `reservedAmount` permits zero, while budget values are positive and aggregates are non-negative. Correlation operation/entity fields always match the durable operation. Presence is exact:

- `OPERATION_BATCH_CREATED`: subject/batch UUID are equal, action `CREATE`; reservation/day UUIDs and kind are all present only for budget-affecting operations; budget-field presence follows the same INITIAL/non-INITIAL rule as the reservation event; aggregate fields are empty; reserved amount equals batch total.
- `BUDGET_RESERVATION_CREATED`: subject/reservation UUID are equal, action `CREATE`; reservation/day UUID and kind are present; INITIAL has previous budget empty, otherwise both budget values present; aggregate fields are empty; reserved amount equals the durable delta.
- `ACCOUNT_DAY_RESERVED`: subject/day UUID are equal, action `UPDATE`; reservation/day UUID and kind are present; budget fields are empty; previous/new aggregate are present, different and satisfy `new=previous+reservedAmount`; reserved amount is positive. A zero delta never produces this event.

The writer maps present fields to `AuditChange` in this fixed order: `operationBatchUuid` UUID, `budgetReservationUuid` UUID, `accountBudgetDayUuid` UUID, `businessDate` DATE, `reservationKind` ENUM, `currency` STRING, `budgetAmount` DECIMAL, `reservedAmount` DECIMAL, `aggregateReservedAmount` DECIMAL. Creation values use NULL old/value new; budget and aggregate pairs use their exact prior/new values. Subject UUID is stored in `audit_logs.entity_uuid`; operation/request/actor/source correlation comes from the same `AuditOperationContext`. No free-form map or message exists.

Exact Transaction A order/cardinality is:

| Command | Exact ordered Audit events |
| --- | --- |
| Campaign create | entity `ENTITY_CREATED`; batch `OPERATION_BATCH_CREATED` with zero; operation `OPERATION_CREATED` — 3 |
| Campaign/Ad Set PAUSE or RESUME | batch with zero; operation created — 2 |
| Ad Set create, positive INITIAL | entity created; batch; reservation; account-day reserved; operation created — 5 |
| Budget increase | batch; reservation; account-day reserved; operation created — 4 |
| Budget decrease | batch with zero; reservation `DECREASE_NO_RELEASE`; operation created — 3 |

The account-day bootstrap row is infrastructure state and emits no standalone CREATE Audit; its first positive change is the specified UPDATE event. Replay, preview, invalid request, stale version/Plan, ineligible state, idempotency conflict, policy/capacity/concurrency rejection, and legacy-operation rejection write zero Audit. A writer exception before event 1, between every pair, or after the final append before commit rolls back the entity, batch, reservation, day aggregate, operation and every earlier Audit row. V13 deliberately does not claim direct-SQL Audit enforcement.

Audit/log/response redaction forbids account references/UUID disclosure in public errors, external identifiers, provider payload/evidence, canonical request JSON, Authorization/Cookie, tokens, credentials, URLs and exception text. Tests assert exact constructors, presence matrix, change names/order/value types, all five cardinality rows including zero-delta/absent/present day state, no-event paths, rollback positions and sentinel-secret absence.

## Verification and acceptance matrix

### Backend and database

- Cold migration V1–V13; populated V12 upgrade; Hibernate validate; V1–V12 canonical checksum assertions; deliberate V13 collision rollback. The populated fixture contains a LOCAL/TEST FAKE account, Campaign Plan/Product/Asset/output/review evidence, Campaign/Ad Set/Ad rows with versions/external fingerprints, successful budget provenance and metric revisions. Its unbatched operation fixtures are: `CREATED` with no attempt; `SUBMITTING` with the matching numbered `SUBMIT/STARTED` attempt; `FAILED_RETRYABLE` with its finalized submit attempt/evidence and due time; `UNKNOWN_OUTCOME` with coherent finalized ambiguous submit attempt/evidence; `RECONCILING` with the matching numbered `RECONCILE/STARTED` attempt; `SUCCEEDED` with coherent finalized success attempt/evidence; submit-terminal `FAILED_TERMINAL`; and reconciliation-terminal `FAILED_TERMINAL` with `normalized_error_code=PLATFORM_RECONCILIATION_TERMINAL` plus coherent finalized `RECONCILE` attempt/evidence, counters and timestamps. Every fixture satisfies the V12 deferred operation/attempt/evidence constraints before migration; V13 preserves every value byte-for-byte and each legacy operation exhibits the documented read-only/inert HTTP behavior.
- Direct-SQL identity, append-only, hard-delete, reciprocal batch/operation/reservation/day aggregate, exact delta, forged date/time, ceiling, replay, zero-release, and transaction rollback tests.
- Policy unit tests for exact account/objective/profile/schedule/currency/entity/batch/day bounds.
- Preview/confirm equivalence and confirmation-time revalidation.
- Replay-before-validation tests cover changed/archived Campaign Plan, changed business date, original-vs-changed If-Match intent and payload conflict; SQL-function and barrier tests cover Asia/Taipei midnight anchoring.
- Campaign and Ad Set create/read/pause/resume; budget increase/decrease; ETag 428/412; idempotent replay/conflict.
- Transaction A atomicity and Audit order/content/rollback; separate-connection persistence before fake call; no active provider transaction.
- Concurrent duplicate confirmation, stale state/budget mutation, retry claim, finalization, and reconciliation have deterministic one-winner behavior. Barrier-controlled first-ever same-account/day transactions cover totals below, exactly at, and above the ceiling plus a decrease-only first row; they prove bootstrap has no spurious uniqueness failure/lost update, exact committed UUID/amount/version/timestamps, and full loser rollback.
- Fake success, rate limit, temporary failure, validation/permission failure, ambiguity, malformed/null/exception, attempt-3 conversion, and reconciliation outcomes.
- Secret-marker, unknown-key, URL/origin, raw provider/account field, log/Audit/response redaction tests.
- Exhaustive internal-source/public-error mapping tests plus operation JSON/BFF snapshots prove account UUID, raw external ID, trace, evidence, canonical payload and forbidden keys never cross the web boundary.

### Frontend/BFF/browser

- Fixed-route and header allowlist tests, query rejection, timeout/body/size handling, and no Browser direct backend/provider origin.
- Preview summary and confirmation dialog for Campaign, Ad Set, state, and budget.
- Explicit paused creation, reservation/remaining-capacity display, non-release warning, stale reload, replay, conflict, retry eligibility, and reconcile-only unknown outcome.
- Default/production feature absence and local/test-only exposure.
- Compose cold start, product regression Smoke, Stage 4B browser E2E, actionlint, dependency audit, and Gitleaks.

### Required commands

- Backend full Testcontainers suite.
- Frontend `npm ci`, `npm audit --omit=dev`, lint, typecheck, tests, and build.
- Docker Compose config/cold health, Smoke, Playwright.
- Pinned actionlint and Gitleaks history/worktree.
- `git diff --check` and actual Push/PR `quality-and-compose` plus `secret-scan` with no required-step skip.

## Forward recovery

- V13 is additive and never edited after merge. A defect is repaired with V14+.
- Before merge, rollback is branch/PR closure. After merge, runtime UI/controller exposure can be disabled by server-side feature flag without deleting evidence.
- Ledger/reservation rows are never deleted or rewritten. A future release/settlement model requires a new approved additive milestone and compensating entries.
- No Stage 4C work starts until Stage 4B implementation receives exact-head `APPROVE`, merges, and post-merge `main` CI passes.

## Stage Gate acceptance

- [ ] Specification-only PR contains this file and its Manager Review record only.
- [ ] Independent Manager Review approves exact API, V13, budget accounting, transaction, Audit, security, UI, and test contracts.
- [ ] Full documentation-head CI passes before specification merge.
- [ ] Implementation begins from post-merge `main` on a new branch; this specification PR contains no runtime code.
- [ ] Implementation PR remains Draft until full local/remote evidence and independent Manager `APPROVE`.
- [ ] Stage 4C remains locked until Stage 4B merge and post-merge verification.
