# Stage 04D — Delivery and Metrics Read Slice

## Gate status

- Status: Runtime squash-merged; post-merge `main` CI passed; Stage 4E closed; Stage 05 closed; Stage 06 closed; Stage 07 FAKE closed
- Branch: `codex/stage-04d-delivery-metrics-runtime` (merged)
- Base: `aa90804` (PR #65 squash merge)
- Stage 4C prerequisite: Passed; PR #64 squash-merged at `acb833d9622925fa185bf905aeac5bddf93f0d6e`; post-merge main CI Run `32504910043` passed
- Product settings: Stage 04 owner defaults of 2026-08-15 remain authoritative (Asia/Taipei, `7d_click` / `1d_view`, daily snapshots, account currency, null means unknown)
- Specification Manager Decision: Cycle 2 `APPROVE` for specification content Head `8e0705f44ca8d46ad92d521864c6d405f7a5cd26`
- Merge: Specification PR #65 squash-merged at `aa90804`; runtime PR #66 squash-merged at `2c2ab07a77d02d8e2c1d2f4e70010430b0e74cfb`
- Post-merge runtime CI: Run `32744056926` passed `quality-and-compose` and `secret-scan`
- Stage 4E: Specification PR #67 at `031d657`; runtime PR #68 at `42515e2`; tag `stage-04-complete`

This document is the implementation contract for Stage 4D. It does not authorize credentials, real Meta Insights, a scheduler, paid delivery, production, Auth/RBAC/Tenant, Dashboard, or Decision Engine behavior.

## Objective

Deliver a deterministic-FAKE, LOCAL/TEST-only entity-level delivery and metrics read slice. A user selects one existing Campaign, Ad Set, or Ad that belongs to the Stage 4B fixed account, reads normalized observed delivery status and the selected daily metric snapshot from PostgreSQL, and may explicitly confirm a refresh that pulls a fake observation and persists it. PostgreSQL remains the System of Record. Reads never mutate desired state, budget, creative evidence, or external IDs.

The slice proves bounded freshness, attribution, timezone, currency, latest/as-of selection, and null-safe derived KPIs. It does not deliver ads, spend money, call Meta, aggregate a Dashboard, or schedule unattended pulls.

## Repository-owner and Architecture-locked decisions

Stage 04 already approved timezone `Asia/Taipei`, attribution `7d_click` and `1d_view`, daily snapshots, account currency, and null-not-zero. Stage 4D locks the remaining implementation choices:

1. Provider execution remains deterministic `FAKE` only under explicit `local` or `test` configuration. Default and production profiles expose no Stage 4D controller, usable read adapter, credential contract, or network path.
2. `GET` delivery and `GET` metrics read PostgreSQL only. They never call an adapter, insert a snapshot, or update an entity.
3. The only ingest path is an explicit user-confirmed refresh. Stage 4D adds no scheduler, cron, listener, worker, or automatic retry/pull.
4. Refresh is not a `platform_operations` mutation. It cannot change desired state, budget, creative evidence, or external IDs, and it does not create operation/attempt rows.
5. The canonical metrics window is the previous complete `Asia/Taipei` calendar day, computed from database `statement_timestamp()`. The Browser cannot choose `windowStart` / `windowEnd`.
6. Optional `asOf` is the only legal metrics query parameter. It selects the greatest revision of that canonical window whose `fetched_at <= asOf`. It does not change window identity.
7. Derived CTR/CPC/CPM/CPA/CVR/ROAS are computed at read time from the selected revision and are never persisted. A NULL base value or zero/missing denominator omits the derived field; it is never coerced to zero.
8. V1–V14 remain byte-identical. V15 is additive indexes only: three partial as-of indexes named below. No new business table, no backfill, no DROP.
9. Dashboard, Decision Engine, lists, cross-entity totals, real Insights, spend, Auth/RBAC/Tenant, and Stage 4E Meta proof remain out of scope.

These nine decisions do not reopen Stage 04 owner defaults and do not require a new human product meeting. Credentials, live delivery, or a scheduler still require `ESCALATE_TO_HUMAN`.

## Inherited boundaries

1. Stage 4A append-only `platform_metric_snapshots` semantics, fingerprint rules, revision numbering, monotonic `fetched_at`, account currency/timezone coherence, and observed-state machine remain authoritative.
2. Stage 4B fixed LOCAL/TEST account tuples, actor, feature-gate fail-closed behavior, safe web disclosure, and `/platforms/meta` page remain authoritative.
3. Stage 4C Ad publication, V14 integrity, and creative evidence remain unchanged. Stage 4D does not create Ads.
4. AI and the Decision Engine cannot call a platform write port or a Stage 4D refresh port.
5. Authentication, RBAC, Tenant authority, credentials, real-provider access, and production remain separately gated.

## Included scope

- Normalized delivery GET for one Campaign, Ad Set, or Ad.
- Metrics GET for the canonical previous complete Taipei day, with optional as-of revision selection.
- Explicit delivery-sync and metrics-refresh preview plus confirmation.
- Deterministic fake `PlatformDeliveryReadPort` and `PlatformMetricsReadPort`.
- Additive V15 as-of indexes.
- Same-origin BFF and an additive operational panel on `/platforms/meta`.
- Exact freshness/null/duplicate/concurrency/Audit-adjacent, UI, and Remote CI acceptance.

## Explicitly excluded

- Real Meta accounts, tokens, Graph/Insights URLs, credentials, network calls, provider payloads, paid delivery, billing, or production.
- Schedulers, polling loops, unattended refresh, automatic retry, or converting adapter failure into zero metrics.
- Dashboard pages, KPI overviews, lists, search, CSV export, or cross-entity aggregates.
- Arbitrary Browser window/attribution/currency/timezone/account selection.
- Mutation of Campaign/Ad Set/Ad desired state, budget, creative evidence, or external IDs.
- Authentication, RBAC, Tenant, or security-model change.
- Editing V1–V14, destructive migration, or snapshot backfill.

## Eligibility

Every Stage 4D route uses the Stage 4B Backend gate plus `PLATFORM_STAGE4D_ENABLED=true`, then the exact fixed-account resolver, then account-scoped entity lookup.

A target is readable when:

- it is one of `CAMPAIGN`, `AD_SET`, or `AD`;
- it belongs to the resolved Stage 4B FAKE account for the active `local` or `test` profile;
- path UUID is canonical lowercase.

Refresh (preview/confirm) additionally requires:

- the entity is not `ARCHIVED`;
- the entity has a nonblank durable external ID from a prior successful create.

GET remains available for archived rows and for rows without an external ID. Missing entities, wrong-account IDs, and cross-profile IDs return the same public not-found with no identifier disclosure, write, Audit event, or adapter call.

Parent desired/observed state does not authorize or block a Stage 4D read. Observed state never overwrites desired state.

## Canonical window

The canonical window is database-owned. GET, preview, and refresh each run exactly this SQL and must not compute the window from `Clock`, JVM timezone, or Browser input:

```sql
SELECT
  ((platform_taipei_business_date(statement_timestamp()) - 1)
    AT TIME ZONE 'Asia/Taipei') AS window_start,
  (platform_taipei_business_date(statement_timestamp())
    AT TIME ZONE 'Asia/Taipei') AS window_end;
```

`platform_taipei_business_date` is the existing V13 function. The stored interval is half-open `[windowStart, windowEnd)` with `windowEnd > windowStart`. Attribution is exactly `attribution_click_days=7` and `attribution_view_days=1`. Currency and timezone are copied from the locked account (`TWD`, `Asia/Taipei`) and must satisfy `verify_platform_metric_account_coherence()`.

Refresh always writes that canonical window. GET always selects that same window. Historical other windows that test fixtures may have inserted are not listed by Stage 4D. Stage 04 provider cursors are unused: 4D fake ports have no cursor field, persist no cursor, and every route rejects a `cursor` query or body key.

## Snapshot selection

For the locked entity and canonical window identity:

- latest (no `asOf`): the row with greatest `revision_number`;
- as-of: among rows with `fetched_at <= asOf`, the row with greatest `revision_number`.

No snapshot for that identity returns `present=false` with `freshnessStatus=UNAVAILABLE` and omitted base/derived metric fields. This is HTTP `200`, not entity `404`.

Exact duplicate `source_fingerprint` for the window identity is rejected by V12 uniqueness (`23505`). Application refresh maps that outcome to HTTP `200` of the existing row whose `source_fingerprint` equals the computed fingerprint. That row may not be latest. Inserts nothing and does not `UPDATE` the entity. GET without `asOf` still returns the latest revision.

Required executable case: `SUCCESS` then `CORRECTED` then `SUCCESS` on the same entity/window. The third confirm returns the revision-1 `SUCCESS` `MetricsView` (`spend=25.000000`). A following GET without `asOf` returns revision 2 (`spend=26.000000`).

A changed observation creates revision `MAX(revision_number)+1` with strictly later `fetched_at`. Application code that receives a fake `fetchedAt` not strictly after the current latest `fetched_at` returns `PLATFORM_CONTRACT_INVALID` and inserts nothing. Metrics persist never `UPDATE` `platform_campaigns`, `platform_ad_sets`, or `platform_ads`.

## Derived metrics

Derived fields are computed only from the selected revision's stored base values. They are omitted when any required input is NULL or a denominator is zero. They are never stored in V12/V15 and are excluded from `source_fingerprint`.

| Field | Formula | JSON type |
| --- | --- | --- |
| `ctr` | `clicks / impressions` | canonical decimal string, scale 6 |
| `cpc` | `spend / clicks` | canonical decimal string, scale 6 |
| `cpm` | `spend * 1000 / impressions` | canonical decimal string, scale 6 |
| `cpa` | `spend / conversions` | canonical decimal string, scale 6 |
| `cvr` | `conversions / clicks` | canonical decimal string, scale 6 |
| `roas` | `revenue / spend` | canonical decimal string, scale 6 |

Division uses `RoundingMode.HALF_UP` at scale 6. Money strings match Stage 4B canonical plain decimals. Counts remain JSON numbers.

## Additive V15 migration intent

V15 is named `V15__add_platform_metric_as_of_indexes.sql`. V1–V14 remain byte-for-byte unchanged. V15 contains only:

```sql
CREATE INDEX idx_platform_metrics_campaign_as_of
  ON platform_metric_snapshots (
    platform_campaign_uuid, window_start, window_end, timezone,
    attribution_click_days, attribution_view_days, currency,
    fetched_at DESC, revision_number DESC)
  WHERE platform_campaign_uuid IS NOT NULL;

CREATE INDEX idx_platform_metrics_ad_set_as_of
  ON platform_metric_snapshots (
    platform_ad_set_uuid, window_start, window_end, timezone,
    attribution_click_days, attribution_view_days, currency,
    fetched_at DESC, revision_number DESC)
  WHERE platform_ad_set_uuid IS NOT NULL;

CREATE INDEX idx_platform_metrics_ad_as_of
  ON platform_metric_snapshots (
    platform_ad_uuid, window_start, window_end, timezone,
    attribution_click_days, attribution_view_days, currency,
    fetched_at DESC, revision_number DESC)
  WHERE platform_ad_uuid IS NOT NULL;
```

Runtime EXPLAIN on the exact latest and as-of GET SQL against a fixture with at least three revisions must show these indexes (as-of) or the existing unique revision indexes (latest) are used. Snapshot semantics, triggers, and fingerprints are unchanged. Recovery from a V15 defect is forward-only through V16+.

## Port contract

These ports are created in Stage 4D. They must not exist as unused markers before this specification is approved. They are reachable only from Stage 4D application services under the FAKE LOCAL/TEST gate. They are not web types.

`PlatformCommandIdentity` is operation-scoped and is not reused. Read commands carry the locked account, typed entity, and durable external ID only.

```java
interface PlatformDeliveryReadPort {
    DeliveryObservation readObservedState(DeliveryReadCommand command);
}

interface PlatformMetricsReadPort {
    MetricObservation readWindow(MetricReadCommand command);
}

record DeliveryReadCommand(
        UUID platformAccountUuid,
        PlatformEntityType entityType,
        UUID entityUuid,
        String durableExternalId,
        PlatformDesiredState currentDesiredState) {}

record MetricReadCommand(
        UUID platformAccountUuid,
        PlatformEntityType entityType,
        UUID entityUuid,
        String durableExternalId,
        Instant windowStart,
        Instant windowEnd,
        String timezone,
        int attributionClickDays,
        int attributionViewDays,
        String currency) {}

record DeliveryObservation(
        PlatformObservedState observedState,
        Optional<String> safeProviderTraceId) {}

record MetricObservation(
        Optional<Long> impressions,
        Optional<Long> reach,
        Optional<Long> clicks,
        Optional<Long> conversions,
        Optional<BigDecimal> spend,
        Optional<BigDecimal> revenue,
        FreshnessStatus freshnessStatus,
        Instant fetchedAt,
        Optional<String> safeProviderTraceId) {}
```

Every reference and Optional container is non-null. Compact constructors reject null containers, blank external IDs, attribution other than 7/1, currency other than `TWD`, timezone other than `Asia/Taipei`, `windowEnd <= windowStart`, negative metrics, spend/revenue scale outside `0..6`, and `fetchedAt` in the future relative to the orchestration clock. `DeliveryReadCommand.currentDesiredState` is copied from the locked entity after eligibility and is never read from PostgreSQL inside the adapter. `safeProviderTraceId`, when present, matches `^[A-Za-z0-9._:-]{1,128}$` and never appears in an HTTP DTO, log body, or metric row. Ports must not query PostgreSQL or open a transaction.

Malformed, null, or throwing adapter results are `PLATFORM_CONTRACT_INVALID` or `PLATFORM_ADAPTER_UNAVAILABLE` as mapped below. They insert no snapshot, update no entity, and are never rewritten as `UNAVAILABLE` zeros.

## Deterministic fake adapter

- Registered only under `(local | test) & !production` with the existing fake-platform property.
- Uses no HTTP, SDK, filesystem credential, environment secret, DNS, socket, or paid service.
- Records invocation count and whether a Spring transaction was active. GET paths must stay at zero invocations. Refresh confirmation calls the matching port exactly once outside a database transaction.
- Fixture selection is constructor/test configuration, never a Browser field.

Closed fixtures. Delivery `SUCCESS` / `DELAYED` / `PARTIAL_NULL` / `CORRECTED` return `observedState = PAUSED` when `currentDesiredState` is `PAUSED`, `ACTIVE` when it is `ACTIVE`, and `UNKNOWN` otherwise. Those fixtures never return `PENDING`, `COMPLETED`, `REJECTED`, `ERROR`, or `DELETED`. The adapter uses only the command record.

| Fixture | Delivery result | Metrics result |
| --- | --- | --- |
| `SUCCESS` (default) | observed state from `currentDesiredState` as above | `FRESH` constants below |
| `DELAYED` | same observation rule | same constants, `freshnessStatus=DELAYED` |
| `UNAVAILABLE` | `UNKNOWN` | all base metrics empty, `freshnessStatus=UNAVAILABLE` |
| `PARTIAL_NULL` | same as `SUCCESS` | `impressions=10000`, `clicks=100`, other bases NULL, `FRESH` |
| `CORRECTED` | same as `SUCCESS` | `SUCCESS` constants except `spend=26.000000` |
| `MALFORMED` | invalid record / null | invalid record / null |
| `THROW` | unchecked exception | unchecked exception |

Default `SUCCESS` metric constants, independent of UUID:

```text
impressions = 10000
reach = 8000
clicks = 100
conversions = 4
spend = 25.000000
revenue = 100.000000
```

`fetchedAt` is the orchestration clock truncated to whole seconds. Spend/revenue stay in `0..1000.000000` or NULL so fake figures cannot be mistaken for authorized budget. Fake metrics are not provider spend.

Contract tests run the same suite against every implementation of each read port. A future Meta adapter cannot weaken the fake contract.

## Refresh transactions

Delivery sync and metrics refresh are separate confirmations.

### Metrics refresh

1. Resolve account, lock `account -> typed entity` in that order, recheck identity after each lock.
2. Reject archived / missing external ID before any adapter call.
3. Compute the canonical window with the SQL in Canonical window.
4. Commit/release the eligibility transaction. Call `PlatformMetricsReadPort.readWindow` with **no** open database transaction.
5. Persist transaction: lock `account -> entity` again. Compute `source_fingerprint` with the exact algorithm below. If a row for this window identity already has that fingerprint, return that row as replay (HTTP `200`) with zero insert and zero entity `UPDATE`. If new, insert the next revision and commit. Never `UPDATE` the entity.

Fingerprint bytes are lowercase SHA-256 of UTF-8 JSON produced as one object with exactly these keys in lexicographic order. Serialization is compact: no pretty-print, no space after `:` or `,`, no newlines. No other key is permitted. JSON null is retained for nullable metrics. Counts are JSON numbers. `spend` and `revenue` are JSON strings of canonical plain decimals with exact scale 6, or JSON null. Instants are second-precision UTC matching `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$`. UUIDs are lowercase. `entityUuid` is the typed entity UUID (Campaign, Ad Set, or Ad), not the account. The object below is wrapped for reading only.

```text
{
  "attributionClickDays": 7,
  "attributionViewDays": 1,
  "clicks": <number|null>,
  "conversions": <number|null>,
  "currency": "TWD",
  "entityType": "CAMPAIGN"|"AD_SET"|"AD",
  "entityUuid": "<uuid>",
  "freshnessStatus": "FRESH"|"DELAYED"|"UNAVAILABLE",
  "impressions": <number|null>,
  "reach": <number|null>,
  "revenue": "<decimal>"|null,
  "spend": "<decimal>"|null,
  "timezone": "Asia/Taipei",
  "windowEnd": "<instant>",
  "windowStart": "<instant>"
}
```

`revisionNumber`, `fetchedAt`, `createdAt`, derived KPIs, account UUID, external ID, and trace IDs are excluded. Runtime tests must hash one golden SUCCESS Campaign fixture and assert the stored `source_fingerprint`.

### Delivery sync

1. Same eligibility lock order and adapter-outside-transaction rule using `PlatformDeliveryReadPort.readObservedState`.
2. Persist transaction: lock `account -> entity`; if `observedState` equals the locked row (including both NULL), this is a no-op: no version increment and no Audit. If it differs, apply the Stage 4A observed-state machine, increment entity version exactly once, set `updated_at` to statement time, and emit one `ENTITY_RESULT_APPLIED` with only observed-state fields.

Desired state, budget, creative columns, and external ID are not in the UPDATE list. Concurrent pause/resume uses the existing entity version; a colliding mutation wins or loses by optimistic lock without corrupting desired state.

SQLSTATE `40001`/`40P01` during persist maps to HTTP `409 PLATFORM_REFRESH_CONCURRENCY_CONFLICT` with zero automatic retry. The Stage 4B ledger conflict code is not reused.

Duplicate fingerprint `23505` maps to metrics `200` of the matching fingerprint row as specified, not of latest.

## Empty POST contract

`POST` preview and confirm routes for delivery-sync and metrics-refresh are empty-body routes, matching Stage 4C retry/reconcile:

- HTTP method `POST`;
- no query and no fragment;
- `Content-Type` must be absent;
- body must be empty (zero bytes after trim);
- no `If-Match`.

Any `Content-Type`, any non-empty body, or any query is `400 PLATFORM_REQUEST_INVALID`. GET delivery has no query. GET metrics allows only the optional `asOf` query defined above.

## Audit contract

- Metrics insert is the durable evidence. Stage 4D adds no Audit subject/event for snapshots and does not extend `PlatformAuditEventKind` or `PlatformAuditSubjectType`.
- Delivery observation change emits exactly one Stage 4A `ENTITY_RESULT_APPLIED` with ordered `observedState` old/new. Unchanged observation is a zero-event path.
- Preview, GET, eligibility rejection, malformed adapter results, and fingerprint replay are zero-event paths.
- Sentinel redaction and existing Stage 4A/4B/4C Audit suites remain required regression.

## API contract

All routes require the Stage 4B + Stage 4D Backend gates and the fixed-account resolver. `entityType` path tokens are exactly `CAMPAIGN`, `AD_SET`, and `AD`. `{entityUuid}` is a canonical lowercase UUID.

| Route | Request | Success |
| --- | --- | --- |
| `GET /api/platform-entities/{entityType}/{entityUuid}/delivery` | no query/body | `200 DeliveryView` + entity weak `ETag` |
| `POST /api/platform-entities/{entityType}/{entityUuid}/delivery-sync/preview` | empty body, no query, no `Content-Type` | `200 DeliveryPreview` |
| `POST /api/platform-entities/{entityType}/{entityUuid}/delivery-sync` | empty body, no query, no `Content-Type` | `200 DeliveryView` + entity weak `ETag` |
| `GET /api/platform-entities/{entityType}/{entityUuid}/metrics` | optional `asOf` only | `200 MetricsView` |
| `POST /api/platform-entities/{entityType}/{entityUuid}/metrics-refresh/preview` | empty body, no query, no `Content-Type` | `200 MetricsPreview` |
| `POST /api/platform-entities/{entityType}/{entityUuid}/metrics-refresh` | empty body, no query, no `Content-Type` | `200 MetricsView` |

`asOf` is exactly one RFC-3339 UTC instant matching `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$`. Future `asOf`, empty, duplicated, extra query keys including `cursor`, fragments, or `asOf` on any non-metrics-GET route are `400 PLATFORM_REQUEST_INVALID` with field `query`. POST refresh/preview reject any query, any `Content-Type`, and any non-empty body.

Exact Backend records and JSON declaration order:

```java
enum Stage4DWarning {
    DETERMINISTIC_FAKE_ONLY,
    NO_REAL_PROVIDER_OR_SPEND,
    NULL_METRICS_MEAN_UNKNOWN
}

record DeliveryPreview(
        PlatformEntityType entityType,
        UUID entityUuid,
        PlatformDesiredState desiredState,
        Optional<PlatformObservedState> observedState,
        boolean syncEligible,
        List<Stage4DWarning> warnings,
        boolean confirmable) {}

record DeliveryView(
        PlatformEntityType entityType,
        UUID entityUuid,
        PlatformDesiredState desiredState,
        Optional<PlatformObservedState> observedState,
        Optional<String> externalIdFingerprint,
        Instant updatedAt,
        long version) {}

record MetricsPreview(
        PlatformEntityType entityType,
        UUID entityUuid,
        Instant windowStart,
        Instant windowEnd,
        String timezone,
        int attributionClickDays,
        int attributionViewDays,
        String currency,
        boolean refreshEligible,
        List<Stage4DWarning> warnings,
        boolean confirmable) {}

record MetricsView(
        PlatformEntityType entityType,
        UUID entityUuid,
        Instant windowStart,
        Instant windowEnd,
        String timezone,
        int attributionClickDays,
        int attributionViewDays,
        String currency,
        boolean present,
        FreshnessStatus freshnessStatus,
        Optional<Integer> revisionNumber,
        Optional<Instant> fetchedAt,
        Optional<Long> impressions,
        Optional<Long> reach,
        Optional<Long> clicks,
        Optional<Long> conversions,
        Optional<String> spend,
        Optional<String> revenue,
        Optional<String> ctr,
        Optional<String> cpc,
        Optional<String> cpm,
        Optional<String> cpa,
        Optional<String> cvr,
        Optional<String> roas,
        List<Stage4DWarning> warnings) {}
```

All reference fields and Optional containers are non-null. `Optional.empty()` is omitted through `NON_ABSENT`. Lists are immutable and always contain the three warnings in declaration order. `confirmable` is true on `200` preview; ineligible preview is an error, not `confirmable=false`. Delivery GET/sync emit `W/"<entityVersion>"`. Metrics responses have neither ETag nor Location. Raw external IDs, fingerprints of metrics, provider traces, account UUIDs, and SQL/EXPLAIN text are forbidden in HTTP bodies.

Existing Stage 4B/4C mutation routes are unchanged and still reject unknown query parameters.

## Error mapping

Validation precedence: feature/profile gate and fixed-account configuration; route/content/query/body shape; canonical path UUID/entityType; account-scoped lookup; eligibility; adapter contract; persist constraints. The first failure wins.

Runtime adds these names to `PlatformStableErrorCode` and uses them as public `code` values: `PLATFORM_ENTITY_ARCHIVED`, `PLATFORM_DELIVERY_NOT_SYNCABLE`, `PLATFORM_REFRESH_CONCURRENCY_CONFLICT`. They are not present in V14 application code; adding them is part of 4D runtime, not this specification PR.

| HTTP | Public code | Fixed message | Source |
| --- | --- | --- | --- |
| 400 | `PLATFORM_REQUEST_INVALID` | `Platform request is invalid` | route/query/body/`asOf`/`entityType` token |
| 400 | `PLATFORM_CONTRACT_INVALID` | `Platform contract is invalid` | adapter observation fails constructor/monotonic fetch rules |
| 404 | `PLATFORM_RESOURCE_NOT_FOUND` | `Platform resource was not found` | account-scoped entity miss |
| 409 | `PLATFORM_ENTITY_ARCHIVED` | `The platform entity is archived` | refresh of `ARCHIVED` |
| 409 | `PLATFORM_DELIVERY_NOT_SYNCABLE` | `The platform entity has no durable external id` | refresh without external ID |
| 409 | `PLATFORM_REFRESH_CONCURRENCY_CONFLICT` | `The refresh could not be stored; retry` | `40001`/`40P01` |
| 503 | `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` | `The local platform account is unavailable` | inactive/environment/provider mismatch |
| 503 | `PLATFORM_ADAPTER_UNAVAILABLE` | `The fake platform adapter is unavailable` | missing port, `THROW` fixture |

`MALFORMED` maps to `400 PLATFORM_CONTRACT_INVALID` with zero persistence. Public messages reveal no account, raw external ID, provider trace, fingerprint, or existence outside the scoped account. Wrong-account and unknown-UUID share `PLATFORM_RESOURCE_NOT_FOUND`.

## BFF and UI

The BFF adds only the six Backend paths above. It does not reuse `/api/platforms/meta/...` for metrics. Empty POST preview/confirm paths follow the Stage 4C retry rule: no `Content-Type` and empty body. GET metrics is the only route that may carry `asOf`; all other 4D routes reject any query. TypeScript types mirror the public records. Safe-key allowlists add exactly: `syncEligible`, `refreshEligible`, `present`, `freshnessStatus`, `revisionNumber`, `fetchedAt`, `impressions`, `reach`, `clicks`, `conversions`, `spend`, `revenue`, `ctr`, `cpc`, `cpm`, `cpa`, `cvr`, `roas`, `windowStart`, `windowEnd`, `timezone`, `attributionClickDays`, `attributionViewDays`. Inherited 16 KiB / 1 MiB / 10-second / redirect / abort / sanitizer rules remain. Browser input cannot choose a Backend origin.

`/platforms/meta` gains a Delivery and metrics panel only when the server-side Stage 4D flag is enabled. It:

- loads delivery and metrics with GET after the user selects an existing Campaign, Ad Set, or Ad;
- shows desired versus observed state, freshness, canonical window, base metrics, and derived KPIs or the null-means-unknown warning;
- requires a second explicit confirmation for delivery sync and for metrics refresh;
- renders the FAKE / no-real-provider / no-spend warnings;
- never auto-refreshes, never posts on page load, and never mutates pause/resume/budget/creative as part of this panel.

## Verification and acceptance

- Cold V1→V15 and populated V14→V15 preserve every V1–V14 checksum and every populated platform/metric/Audit row byte-for-byte. V15 collision rollback and forward-only V16 recovery evidence are required.
- Direct-SQL continues to reject update/delete of snapshots, skipped revisions, non-monotonic `fetched_at`, duplicate fingerprints, negative bases, and account currency/timezone mismatch.
- Latest and as-of selection, `present=false`, NULL bases, derived omit/compute, duplicate-fingerprint refresh of a non-latest matching row, and the SUCCESS→CORRECTED→SUCCESS confirm-versus-GET case are executable.
- One golden compact fingerprint JSON/hash for the default SUCCESS Campaign constants is asserted against the stored `source_fingerprint`.
- GET makes zero adapter calls. Confirm calls the matching port once, outside a transaction.
- `MALFORMED`/`THROW` persist nothing and do not store zeroed `UNAVAILABLE` rows.
- Concurrent duplicate-fingerprint and concurrent delivery no-op/change have one winner and full loser rollback.
- EXPLAIN evidence for the exact GET SQL is attached to the runtime completion report.
- Typed Audit: observation change is one `ENTITY_RESULT_APPLIED`; snapshot insert has zero Audit; rejection paths have zero Audit.
- Backend/BFF snapshots cover every public record, omitted Optional, warning list, `asOf` 400 matrix, and forbidden fields.
- Component and Playwright cases cover GET display, explicit refresh confirmation, freshness/null warnings, and zero automatic refresh.
- Full Backend regression; Frontend lint/typecheck/tests/build; `npm audit --omit=dev`; Compose config/cold health; Smoke; Playwright; actionlint; pinned Gitleaks; `git diff --check`.
- Exact-head Push and Pull Request CI run `quality-and-compose` and `secret-scan` with no required-step skip other than Playwright artifact upload after an E2E pass.

## Stage gate

- [x] Stage 4C runtime PR #64 squash-merged at `acb833d9622925fa185bf905aeac5bddf93f0d6e`.
- [x] Post-merge `main` CI Run `32504910043` passed `quality-and-compose` and `secret-scan`.
- [x] Independent Manager Review records exactly `APPROVE` for reviewed content Head `8e0705f44ca8d46ad92d521864c6d405f7a5cd26`.
- [x] The approval-record commit passed full exact-head Push and Pull Request CI.
- [x] Specification PR #65 squash-merged at `aa90804`; post-merge `main` CI Run `32540462993` passed.
- [x] Runtime PR #66 squash-merged at `2c2ab07a77d02d8e2c1d2f4e70010430b0e74cfb`; post-merge main CI Run `32744056926` passed.

Stage 4E FAKE is closed. Stage 05 Dashboard is closed. Stage 06 Decision Engine is closed. Stage 07 FAKE is closed. Optional Meta paused proof stays locked.
