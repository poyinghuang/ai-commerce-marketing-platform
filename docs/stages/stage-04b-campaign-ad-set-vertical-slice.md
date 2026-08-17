# Stage 04B — Campaign and Ad Set Vertical Slice

## Gate status

- Status: Specification drafted; Independent Manager Review pending
- Branch: `codex/stage-04b-campaign-adset-specification`
- Base: `4d89448cb48520c14f6ce991d803a0221503ebeb`
- Stage 4A prerequisite: Passed; PR #60 merged at `4d89448cb48520c14f6ce991d803a0221503ebeb`; post-merge CI Run `31998731088` passed
- Product settings: Approved by the repository owner on 2026-08-17
- Implementation: Not started
- Manager Decision: Pending
- Merge: Not started
- Stage 4C: Locked

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
| `reserved_amount` | NUMERIC(19,6) | Sum of reservation ledger for identity; `0..1000` |
| `ceiling_amount` | NUMERIC(19,6) | Exactly `1000.000000`, immutable |
| `created_at` / `updated_at` | TIMESTAMPTZ | Server times |
| `version` | BIGINT | Increments once per positive reservation |

For `CREATE_AD_SET` and `UPDATE_BUDGET`, Transaction A locks this row with `SELECT ... FOR UPDATE`, creating it at zero when absent. Campaign create and Campaign/Ad Set state commands create no account-day row. A positive reservation increments the row atomically; a zero decrease reservation leaves amount/version unchanged while retaining its immutable ledger evidence. A deferred trigger recomputes the exact ledger sum at commit, so direct SQL cannot forge either aggregate. UPDATE is allowed only for the exact positive reservation delta in the same transaction. DELETE fails.

### Database integrity and concurrency

- Batch total is the ledger sum and cannot exceed TWD 300.
- Account-day total is the ledger sum and cannot exceed TWD 1000.
- The batch insert captures one database `statement_timestamp()` anchor. Its `created_at` and `business_date=(created_at AT TIME ZONE 'Asia/Taipei')::date` are authoritative; database triggers copy that anchor/date to its reservation. Forged caller time/date fails coherence.
- Only `CREATE_AD_SET` and `UPDATE_BUDGET` have reservation rows. Campaign and state operations have immutable zero-reservation batches.
- A reciprocal deferred operation trigger requires exactly one batch for each newly inserted Stage 4B `CREATE_CAMPAIGN`, `CREATE_AD_SET`, Campaign/Ad Set `PAUSE`/`RESUME`, or `UPDATE_BUDGET` operation. It does not apply to `CREATE_AD` or later milestone entity types.
- Batch, operation, reservation, aggregate update, entity construction if applicable, and Audit commit atomically in Transaction A.
- Concurrent positive reservations on one account/day serialize on the aggregate row. The first transactions within the ceiling commit; a transaction exceeding the remaining capacity rolls back completely.
- Operation replay reads the existing operation/batch/reservation and returns it. It never re-locks/increments the aggregate.
- Direct SQL tests cover missing/extra/wrong operation, account, Ad Set, date, currency, kind, old/new/delta, batch ceiling, day ceiling, reused operation, zero-release semantics, mutation/delete, and failed-transaction atomicity.

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

## Transaction and operation lifecycle

### Preview

Preview is read-only. It canonicalizes and validates the same DTO and policies as confirmation, loads current versions, and returns a normalized summary, estimated reservation, remaining account-day capacity, warnings, and `confirmable`. It writes no batch, reservation, entity, operation, or Audit and calls no adapter. Preview is advisory: confirmation revalidates everything under lock.

### Confirmation Transaction A

1. Require explicit local/test web and fake-adapter configuration; resolve the fixed server-owned local actor and active FAKE account.
2. Validate canonical DTO, parent/entity version, account, schedule, profiles, per-entity budget, aggregate policy, and accepted operation state.
3. Resolve idempotency identity. A matching replay returns existing data before writes; a mismatch returns conflict.
4. Insert the batch to capture database time/business date. For `CREATE_AD_SET` or `UPDATE_BUDGET`, lock/create the matching account-day aggregate and validate remaining capacity; other commands create no account-day row.
5. Insert the Stage 4A entity/operation, the budget reservation when applicable, aggregate delta, and exact typed Audit in the same transaction as the one batch from step 4.
6. Commit before adapter dispatch.

Transactions B/C, retries, attempt-3 conversion, unknown-outcome handling, reconciliation, entity mutation, and Audit reuse the exact Stage 4A contracts. No controller catches ambiguity and creates a replacement operation. A stale optimistic version, policy rejection, or ledger ceiling failure creates no attempt or provider call.

## API contract

Controllers exist only when `platform.web.enabled=true`, `platform.fake.enabled=true`, and profile is `local` or `test`. Default/production return no routable mutation surface.

Every JSON request is a closed object: unknown, duplicate, null-for-required, secret-marker, provider/account, URL, raw JSON, and non-NFC text fields are rejected. UUIDs are lowercase canonical strings in responses; money is a JSON string in canonical plain decimal form, never a floating-point JSON number. The server resolves account, actor, objective, desired initial state, currency, timezone, optimization goal, targeting, placement, and ceilings; the Browser cannot override them.

| Command | Exact request fields | Rules |
| --- | --- | --- |
| Campaign preview/create | `clientRequestUuid`, `campaignUuid`, optional `scheduleStart`, optional `scheduleEnd` | Schedule is absent as a pair or both RFC 3339 UTC instants with end after start; create is always PAUSED/OUTCOME_SALES |
| Ad Set preview/create | `clientRequestUuid`, `budgetType`, `budgetAmount`, optional `scheduleStart`, optional `scheduleEnd` | Parent Campaign comes only from path; DAILY/LIFETIME and amount use approved bounds; create is PAUSED |
| State preview/confirm | `clientRequestUuid`, `targetDesiredState` | Target is exactly PAUSED or ACTIVE and must be the valid opposite effective desired state; entity type/UUID come from path |
| Budget preview/confirm | `clientRequestUuid`, `newBudgetAmount` | Type/currency/previous amount/entity version come from the locked Ad Set; equal amount is invalid |

`clientRequestUuid` is generated once before preview and reused unchanged for confirmation. Preview does not reserve that identity; a later confirmation race is decided by the durable Stage 4A idempotency constraint. Mutation commands also require `If-Match`; the header version must equal the locked entity version and becomes the Stage 4A canonical payload's `expectedEntityVersion`.

### Read-only previews

- `POST /api/platforms/meta/campaigns/preview`
- `POST /api/platforms/meta/campaigns/{platformCampaignUuid}/ad-sets/preview`
- `POST /api/platforms/meta/entities/{entityType}/{entityUuid}/state-preview`
- `POST /api/platforms/meta/ad-sets/{platformAdSetUuid}/budget-preview`

Preview response contains only normalized local UUIDs, allowed display fields, current version/ETag, canonical policy summary, reservation delta, batch/day ceilings, already-reserved day amount, remaining capacity, and confirmation warnings. It contains no canonical raw JSON, idempotency key, account external identifier, provider evidence, secret, or arbitrary URL.

### Confirmed mutations

- `POST /api/platforms/meta/campaigns`
- `POST /api/platforms/meta/campaigns/{platformCampaignUuid}/ad-sets`
- `POST /api/platforms/meta/campaigns/{platformCampaignUuid}/pause`
- `POST /api/platforms/meta/campaigns/{platformCampaignUuid}/resume`
- `POST /api/platforms/meta/ad-sets/{platformAdSetUuid}/pause`
- `POST /api/platforms/meta/ad-sets/{platformAdSetUuid}/resume`
- `POST /api/platforms/meta/ad-sets/{platformAdSetUuid}/budget`

Create requests require a client request UUID and exact closed input fields. Existing-entity mutations require `If-Match: W/"<version>"`. Successful confirmation returns `202 Accepted`, `Location: /api/platform-operations/{operationUuid}`, `X-Request-ID`, and `PlatformOperationView`. Replays return `200 OK` with the same operation and no side effect.

### Reads and recovery

- `GET /api/platforms/meta/campaigns/{platformCampaignUuid}`
- `GET /api/platforms/meta/ad-sets/{platformAdSetUuid}`
- `GET /api/platform-operations/{operationUuid}`
- `POST /api/platform-operations/{operationUuid}/retry` with operation `If-Match`
- `POST /api/platform-operations/{operationUuid}/reconcile` with operation `If-Match`

Entity reads return `ETag: W/"<entityVersion>"`. Operation reads return `ETag: W/"<operationVersion>"`. List/search, Ads, delivery, and metrics are not introduced.

### HTTP errors

All errors use the repository `ApiError` contract and safe generic messages.

| HTTP | Stable condition |
| --- | --- |
| 400 | malformed/unknown field, invalid UUID/enum/schedule/money/profile |
| 404 | entity/operation not found; controllers are absent outside allowed profiles |
| 409 | idempotency conflict, invalid state, retry/reconcile ineligible, policy/aggregate rejection |
| 412 | stale entity or operation `If-Match` |
| 428 | missing required `If-Match` |
| 429 | persisted retryable provider result; response includes operation Location only, no provider text |
| 500 | generalized unexpected error with request ID |
| 503 | explicit local/test adapter unavailable after route construction |

Budget aggregate rejection uses `PLATFORM_POLICY_REJECTED` with field-safe UI text and never exposes current account identifiers or ledger rows.

## Same-origin BFF and UI

- Fixed Next.js Route Handlers proxy only the exact routes above through `BACKEND_INTERNAL_URL`.
- The proxy forwards only `Content-Type`, `If-Match`, and safe `X-Request-ID`; it strips Cookie, Authorization, actor/account/provider headers, and unknown query parameters. It exposes only Content-Type, ETag, Location, and X-Request-ID response headers.
- `/platforms/meta` is available only when the server-side local/test feature flag is enabled. It provides Campaign create/read, child Ad Set create/read, state and budget preview, an explicit confirmation dialog, and an operation status panel.
- Confirmation is never the default button action. The dialog shows desired state PAUSED, currency, budget type/amount, positive reservation delta, batch/day ceilings, and that decreases/failures do not release capacity.
- Resume and budget changes require a second explicit confirm action in the local/test UX. This is not represented as production operator/approver separation.
- Stale 412 reloads the entity and invalidates the preview. Unknown outcome offers reconcile only. Retry appears only for due `FAILED_RETRYABLE`; there is no automatic timer submission.
- No token, external account ID, provider URL, raw error/evidence, canonical payload, or arbitrary targeting control is rendered or stored in Browser state.

## Audit contract

Stage 4A typed events remain authoritative. Stage 4B extends the closed subject enum with `PLATFORM_OPERATION_BATCH`, `PLATFORM_BUDGET_RESERVATION`, and `PLATFORM_ACCOUNT_BUDGET_DAY`, and extends the closed event-kind enum with `OPERATION_BATCH_CREATED`, `BUDGET_RESERVATION_CREATED`, and `ACCOUNT_DAY_RESERVED`. These additions are application transaction invariants; V13 does not claim that direct SQL can create Audit rows.

- Transaction A order: entity-created when applicable; batch-created; reservation-created when applicable; account-day-reserved only for a positive delta; operation-created.
- Audit contains UUIDs, business date, reservation kind, canonical numeric old/new/reserved amounts, currency, prior/new aggregate amount, operation correlation, actor, and request ID.
- It never contains account external identifiers, provider payload/evidence, raw request JSON, tokens, URLs, or free-form provider messages.
- Replay, preview, validation failure, stale version, policy rejection, or aggregate rejection writes zero Audit.
- An Audit writer failure at any append position rolls back batch, reservation, aggregate, entity, operation, and prior Audit rows.

## Verification and acceptance matrix

### Backend and database

- Cold migration V1–V13; populated V12 upgrade; Hibernate validate; V1–V12 canonical checksum assertions; deliberate V13 collision rollback.
- Direct-SQL identity, append-only, hard-delete, reciprocal batch/operation/reservation/day aggregate, exact delta, forged date/time, ceiling, replay, zero-release, and transaction rollback tests.
- Policy unit tests for exact account/objective/profile/schedule/currency/entity/batch/day bounds.
- Preview/confirm equivalence and confirmation-time revalidation.
- Campaign and Ad Set create/read/pause/resume; budget increase/decrease; ETag 428/412; idempotent replay/conflict.
- Transaction A atomicity and Audit order/content/rollback; separate-connection persistence before fake call; no active provider transaction.
- Concurrent duplicate confirmation, same account/day reservations near ceiling, stale state/budget mutation, retry claim, finalization, and reconciliation have deterministic one-winner behavior.
- Fake success, rate limit, temporary failure, validation/permission failure, ambiguity, malformed/null/exception, attempt-3 conversion, and reconciliation outcomes.
- Secret-marker, unknown-key, URL/origin, raw provider/account field, log/Audit/response redaction tests.

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
