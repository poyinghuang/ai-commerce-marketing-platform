# Stage 06 — Decision Engine

## Gate status

- Status: Specification Draft PR [#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72) (docs-only); runtime locked
- Branch: `codex/stage-06-decision-engine-specification`
- Draft PR: [#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72)
- Base: `3d3b7b3175c2d47d850a29144173d10fc50a48a5` (PR [#71](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/71) squash merge)
- Stage 05 prerequisite: Passed — spec PR [#69](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/69) at `3bbbc69`; runtime PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) at `9e0f4b4`; close-out PR [#71](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/71) at `3d3b7b3`; post-merge `main` CI Run `32795522589` passed `quality-and-compose` and `secret-scan`
- Stage 04 prerequisite: Passed — 4A–4E FAKE LOCAL/TEST on `main`; tag `stage-04-complete` peels to `031d657`
- Product settings: Stage 04 owner defaults of 2026-08-15 remain authoritative (Asia/Taipei, `7d_click` / `1d_view`, daily snapshots, account currency, null means unknown, create stays `PAUSED`)
- Implementation: Not started; unlock only after this specification `APPROVE`, squash-merge, and post-merge `main` CI
- Manager Decision: Not started
- Merge: Not started
- Optional Meta paused proof: **Locked** (separate human record)
- Auto-execute: **Forbidden** in this Stage and until a recorded human decision (`ESCALATE_TO_HUMAN`)

This document is the implementation contract for Stage 06. It does not authorize credentials, `META_TEST_READ_WRITE_PAUSED`, `META_TEST_DELIVERY`, real Meta, spend, production, Auth/RBAC/Tenant, a scheduler, a new platform write or refresh port, an apply/execute path, or LLM-authored recommendations.

This specification PR is docs-only. It must not add Java, TypeScript, Flyway, flags, or UI.

## Parent stub (unchanged intent)

### 目標

建立建議型優化引擎。

### 指標

- CTR
- CPA
- ROAS
- CVR
- CPM
- Frequency

### 建議類型

- 增加預算
- 降低預算
- 暫停
- 更換素材
- 重新生成素材
- 受眾疲乏
- 素材疲乏

### 驗收

- 每項建議需包含原因、數據與風險
- 建議可人工批准或拒絕
- 未批准不得執行

## Objective

Deliver a LOCAL/TEST suggestion engine that reads existing Stage 4D campaign-grain metric snapshots from PostgreSQL, evaluates a closed deterministic rule set, persists recommendation cards, and lets an operator approve or reject each card as a **decision record**.

Approve and reject must not pause, resume, change budget, publish, refresh metrics, execute an AI job, or call a platform adapter. Unapproved recommendations must not execute. Approved recommendations in this Stage also must not execute. A later gated slice may add a separate apply/preview-confirm that reuses existing Stage 4B/4C/03 routes; that slice is out of scope here.

The slice does not invent Frequency, mutate desired ads state, refresh metrics on generate or page load, or overload Dashboard **AI 建議** (Stage 03 `PENDING_REVIEW`).

## Repository-owner and Architecture-locked decisions

These decisions implement the Architecture Decision Engine module and the Stage 06 stub against tables that already exist. They do not reopen Stage 04 owner defaults, Stage 05 Dashboard GET semantics, or Stage 03 review. Credentials, live delivery, Auth/RBAC/Tenant, a scheduler, or any execute-on-approve path still require `ESCALATE_TO_HUMAN`.

1. Provider execution remains deterministic `FAKE` only under explicit `local` or `test` configuration. Default and production profiles expose no Stage 06 controller, usable decision BFF, credential contract, or network path to a real ads provider.
2. Stage 06 runtime (not this specification PR) adds **additive Flyway V16** for recommendation persistence only. V1–V15 remain byte-identical. V16 must not DROP, rewrite snapshots, backfill metrics, or add a Frequency column. Recovery from a later index or Frequency need is forward-only through a separately specified V17+.
3. The engine is **suggestion-only**. `POST .../approve` and `POST .../reject` persist an immutable human decision and terminal status. They must not call `PlatformCampaignPort`, `PlatformAdSetPort`, `PlatformAdPort`, `PlatformDeliveryReadPort`, or `PlatformMetricsReadPort`, must not insert a metric snapshot, must not create a `platform_operations` row, must not change `desired_state` or Ad Set budget, and must not call `ReviewDecisionService` or AI execute.
4. Generate and list read PostgreSQL only. They never call the five ports named above. Generate does not refresh metrics. Page load and first paint on `/dashboard` remain **zero** `POST` / `PATCH` / `DELETE` (Stage 05 invariant). Generate runs only after an explicit human click.
5. Recommendations are **deterministic `RULE_SET_V1`**. This Stage adds no LLM, no Prompt template, and no model profile. AI Workflow owns the rule catalog in application code, not scattered strings in the UI.
6. Grain is **Campaign** only, matching Stage 05 KPI. Ad Set and Ad snapshots are ignored so spend is not double-counted. Browser input cannot choose Backend origin, account, timezone, currency, attribution, metric window, or `asOf`.
7. Parent stub **Frequency** is **deferred**. `platform_metric_snapshots` has no frequency column. Stage 06 must not coerce Frequency from impressions/reach, must not persist a fake-zero Frequency, and must not emit `AUDIENCE_FATIGUE`. CTR, CPA, ROAS, CVR, and CPM use the Stage 4D formulas on the selected campaign-grain revision and are omitted when a required base is missing or a denominator is zero.
8. Dashboard **AI 建議** remains Stage 03 `PENDING_REVIEW`. Decision Engine cards use a distinct label **優化建議**, distinct types, and distinct routes. `GET /api/dashboard` is unchanged. `com.aicommerce.platform.dashboard` still must not persist recommendation rows or inject the five platform ports.
9. `PAUSED` after Stage 04 create is not a defect. Budget and pause suggestions may still be emitted for a `PAUSED` campaign as information; risk copy must state that approval does not resume or change budget. `PAUSE` emits only when `desired_state` is `ACTIVE`.
10. Connecting approve → Stage 4B pause/budget, Stage 4C publication, Stage 4D refresh, or Stage 03 generate/approve is a **later** gated slice. Appearing in this Stage is auto-execute for gate purposes and is `ESCALATE_TO_HUMAN`.

## Inherited boundaries

1. Stage 4A append-only `platform_metric_snapshots` semantics, fingerprint rules, revision numbering, account currency/timezone coherence, and observed-state machine remain authoritative.
2. Stage 4B fixed LOCAL/TEST account tuples, actor, feature-gate fail-closed behavior, safe web disclosure, paused create, and `/platforms/meta` preview-confirm writes remain authoritative. Stage 06 must not add a second write path.
3. Stage 4C Ad publication and V14 integrity remain unchanged. Stage 06 does not create Ads.
4. Stage 4D PostgreSQL-only GET, explicit refresh, canonical Taipei window SQL, null-safe derived KPIs, and V15 as-of indexes remain unchanged. Generate uses the same window SQL as Stage 4D/05 (`platform_taipei_business_date(statement_timestamp())`), not JVM `Clock` or Browser input.
5. Stage 05 Dashboard GET, seven stub regions, campaign-grain KPI, and Stage 03D review from `/dashboard` remain unchanged. Playwright zero-POST-on-load for `/dashboard` remains required.
6. Stage 03D review: `POST /api/ai-generation-outputs/{outputUuid}/approve` and `/reject` remain the only creative-review mutations. Decision Engine approve/reject is a different resource.
7. `com.aicommerce.platform.ai` still has no compile-time dependency on the five platform ports. Stage 06 adds the same prohibition for `com.aicommerce.platform.decision`. The decision package must not live inside `ai` or `dashboard`.
8. Authentication, RBAC, Tenant authority, credentials, real-provider access, and production remain separately gated.

## Included scope

- Feature-gated Backend decision reads and decision-record mutations under `local`/`test`, `platform.adapter=fake`, `platform.web.enabled=true`, `platform.stage6.enabled=true`.
- Additive V16 tables `decision_recommendations` and `decision_recommendation_decisions` (runtime PR, not this PR).
- Deterministic `RULE_SET_V1` over Stage 4B FAKE account campaign-grain snapshots for the canonical previous complete `Asia/Taipei` day.
- Explicit generate, list, get, approve, and reject HTTP routes.
- Same-origin BFF for those paths.
- Additive **優化建議** region on `/dashboard` when Frontend `PLATFORM_STAGE5_ENABLED=true` and `PLATFORM_STAGE6_ENABLED=true`.
- Profile/flag fail-closed tests, DTO contract tests, rule null-safety tests, zero-adapter generate/approve proof, compile-path test, Frontend component tests, and Compose-backed Playwright.

## Explicitly excluded

- Real Meta / Google / LINE / TikTok accounts, tokens, Graph/Insights URLs, credentials, network calls, paid delivery, billing, or production.
- Schedulers, polling loops, unattended generate, automatic retry, or converting missing metrics into zeros.
- Auto-execute, apply, or approve that calls pause/resume/budget/refresh/execute/publication.
- LLM Decision Engine, new Prompt files, or model selection.
- Frequency column, `AUDIENCE_FATIGUE` emission, Ad Set/Ad-grain recommendations, or summing Ad Set/Ad snapshots.
- New platform write, pause/resume, budget, Ad create, or Stage 4D preview/confirm routes.
- Changing `GET /api/dashboard` records, renaming **AI 建議**, or persisting recommendations in the dashboard package.
- Authentication, RBAC, Tenant, or security-model change.
- CSV export, saved views, Browser-chosen date ranges, cross-account totals, or a proposed budget amount that could be mistaken for an apply payload.
- Editing V1–V15, destructive migration, snapshot backfill, or V16 in this specification PR.
- Optional `META_TEST_READ_WRITE_PAUSED` proof.

## Eligibility and flags

| Layer | Gate |
| --- | --- |
| Backend controller and services | `@Profile("(local \| test) & !production")` and `@ConditionalOnExpression` requiring `platform.adapter=fake`, `platform.web.enabled=true`, `platform.stage6.enabled=true` |
| Frontend region **優化建議** on `/dashboard` | `PLATFORM_STAGE5_ENABLED=true` and `PLATFORM_STAGE6_ENABLED=true`; otherwise the region is not rendered |
| Frontend BFF decision paths | `PLATFORM_STAGE6_ENABLED=true`; otherwise public `404` `DECISION_DISABLED` |
| Compose / `application-local.yml` | set Stage 06 flags `true` in runtime (not this specification PR); keep Stage 05 flags as already shipped |
| Default and production | no controller bean; no usable decision API |

Stage 06 Backend does **not** require `platform.stage5.enabled` or `platform.stage4d.enabled` beans. Missing snapshots yield generate counts of zero, not a disabled controller.

Wrong profile, missing flag, or `platform.adapter` other than `fake` yields no controller (framework `404`). Public messages reveal no account UUID, raw external ID, provider trace, fingerprint, or SQL.

The only actor tuple is the existing `AuditActor.localAdmin()` value: `requestedActorType=LOCAL_ADMIN`, NFC actor ID `local-admin`, and Audit source `API`; all are injected server-side and no actor header/body/query field is accepted. `SYSTEM` actors are rejected on approve/reject. Generate is also a human-clicked LOCAL_ADMIN action.

## HTTP contract

Unknown methods on these paths are not added. `Cache-Control: no-store` on every success. UUIDs in paths are canonical lowercase.

### `POST /api/decision-recommendations/generate`

Explicit generate. Empty-body route matching Stage 4D refresh:

- HTTP method `POST`;
- no query and no fragment;
- `Content-Type` must be absent;
- body must be empty (zero bytes after trim);
- no `If-Match`.

Any `Content-Type`, any non-empty body, or any query is `400` `DECISION_REQUEST_INVALID`.

Must not run on dashboard page load.

### `GET /api/decision-recommendations`

Allowed query keys only: `page` (default `0`, `>=0`), `size` (default `20`, `1..100`), `status` (default `PENDING`; exactly `PENDING`, `APPROVED`, or `REJECTED`). Unknown, duplicate, or extra keys are `400`.

Sort is server-owned: `updated_at DESC`, `recommendation_uuid DESC`.

Response: `DecisionPageView` with `content`, `page`, `size`, `totalElements`, `totalPages`.

### `GET /api/decision-recommendations/{recommendationUuid}`

No query. Returns `RecommendationDetailView` including decision history. Missing or other-account UUID is `404` `DECISION_NOT_FOUND`. Weak `ETag` `W/"<version>"`.

### `POST /api/decision-recommendations/{recommendationUuid}/approve`

- body must be absent or `{}`
- requires `If-Match: W/"<version>"`
- no query

### `POST /api/decision-recommendations/{recommendationUuid}/reject`

- body `{ "reason": "..." }`, 1–2000 characters after trim
- requires `If-Match: W/"<version>"`
- no query
- unknown fields or non-object bodies are `400`

Missing, malformed, and stale ETags return `428`, `400`, and `412` respectively, matching Stage 03D precedence where those codes already exist for review; Decision Engine uses the public codes in Error mapping below and must not reuse `AI_REVIEW_BLOCKED`.

## Public records

JSON uses `NON_ABSENT` for `Optional` fields. Lists are never `null`. Instants are second-precision UTC. UUIDs are lowercase. Money strings match Stage 4B canonical plain decimals (scale 6). Counts are JSON numbers. Derived KPI strings match Stage 4D (`HALF_UP` scale 6).

Forbidden in every Stage 06 HTTP body (and BFF must fail closed if a Backend body contains them): `platformAccountUuid`, `accountReference`, `externalId`, `canonicalPayload`, `requestPayload`, `outcomeEvidence`, `safeProviderTraceId`, `authorization`, `cookie`, `token`, `credential`, `secret`, `providerBody`, `providerUrl`, `sourceFingerprint`, `evidenceFingerprint`, `metricSourceFingerprint`.

```text
enum RecommendationType {
  INCREASE_BUDGET,
  DECREASE_BUDGET,
  PAUSE,
  SWAP_CREATIVE,
  REGENERATE_CREATIVE,
  AUDIENCE_FATIGUE,
  CREATIVE_FATIGUE
}

enum RecommendationStatus {
  PENDING,
  APPROVED,
  REJECTED
}

enum Stage06Warning {
  DETERMINISTIC_FAKE_ONLY,
  NO_REAL_PROVIDER_OR_SPEND,
  NULL_METRICS_MEAN_UNKNOWN,
  APPROVAL_DOES_NOT_EXECUTE
}

GenerateView
  generatedAt: Instant
  windowStart: Instant
  windowEnd: Instant
  timezone: "Asia/Taipei"
  currency: "TWD"
  consideredCampaignCount: long
  createdCount: long
  updatedCount: long
  replayedCount: long
  skippedIncompleteCount: long
  items: List<RecommendationView>   // echo of rows inserted or evidence-updated this call; 0..20
  truncated: boolean
  warnings: List<Stage06Warning>

DecisionPageView
  content: List<RecommendationView>
  page: int
  size: int
  totalElements: long
  totalPages: int

RecommendationView
  recommendationUuid: UUID
  platformCampaignUuid: UUID
  campaignUuid: UUID
  campaignName: String
  recommendationType: RecommendationType
  status: RecommendationStatus
  windowStart: Instant
  windowEnd: Instant
  timezone: "Asia/Taipei"
  currency: "TWD"
  attributionClickDays: 7
  attributionViewDays: 1
  desiredState: PAUSED | ACTIVE | ARCHIVED
  reasonSummary: String
  riskSummary: String
  evidence: RecommendationEvidence
  href: String                      // same-origin path starting with /
  productUuid: Optional<UUID>
  version: long
  createdAt: Instant
  updatedAt: Instant
  warnings: List<Stage06Warning>

RecommendationEvidence
  impressions: Optional<Long>
  reach: Optional<Long>
  clicks: Optional<Long>
  conversions: Optional<Long>
  spend: Optional<String>
  revenue: Optional<String>
  ctr: Optional<String>
  cpc: Optional<String>
  cpm: Optional<String>
  cpa: Optional<String>
  cvr: Optional<String>
  roas: Optional<String>
  // frequency is omitted in this Stage

RecommendationDetailView
  (same fields as RecommendationView)
  decision: Optional<RecommendationDecisionView>

RecommendationDecisionView
  recommendationDecisionUuid: UUID
  decision: APPROVED | REJECTED
  reason: Optional<String>
  decidedAt: Instant
```

Every successful generate, list item, and detail includes all four `Stage06Warning` values in that declaration order.

`href` by type:

| Type | `href` | `productUuid` |
| --- | --- | --- |
| `INCREASE_BUDGET`, `DECREASE_BUDGET`, `PAUSE`, `SWAP_CREATIVE`, `CREATIVE_FATIGUE` | `/platforms/meta` | omit |
| `REGENERATE_CREATIVE` | `/products/{productUuid}?tab=creative-factory` when a single first ACTIVE `campaign_products` row exists (lowest `priority` NULLS LAST, then `product_uuid`); otherwise `/campaigns/{campaignUuid}` | present only in the single-product case |
| `AUDIENCE_FATIGUE` | never returned in this Stage | — |

The UI does not construct Graph URLs.

## Canonical window and campaign selection

1. Resolve the Stage 4B FAKE account with the same fingerprint/reference rules as Dashboard (`local` vs `test` UUID pair). Failure is `503` `PLATFORM_ACCOUNT_CONFIGURATION_INVALID`.
2. Compute the canonical window with the **exact** Stage 4D SQL (`platform_taipei_business_date(statement_timestamp())`), not JVM `Clock` or Browser input.
3. Eligible campaigns: `platform_campaigns` for that account whose `desired_state <> 'ARCHIVED'`.
4. For each eligible campaign, select the **latest** `platform_metric_snapshots` row for that campaign UUID and canonical window identity (greatest `revision_number`). Ignore rows whose `platform_ad_set_uuid` or `platform_ad_uuid` is not null.
5. A campaign with no such row is skipped (`skippedIncompleteCount`). A present row may still fail individual rules when required bases are NULL; that campaign is considered but that type is not emitted.

`asOf` is not accepted. Generate never lists historical windows.

## RULE_SET_V1

Evaluate every type independently (not first-match-wins) except `AUDIENCE_FATIGUE`, which is never emitted. Derived fields use Stage 4D formulas on that campaign's selected revision. If a required derived field is omitted, the type does not emit.

`ruleSetKey` stored on every row is exactly `RULE_SET_V1`.

| Type | Required | Predicate | `reasonSummary` (exact) |
| --- | --- | --- | --- |
| `INCREASE_BUDGET` | `roas` | `roas >= 3.000000` | `Campaign-grain ROAS is at or above 3.000000 on the canonical previous Taipei day.` |
| `DECREASE_BUDGET` | `roas` or `cpa` | `roas <= 1.000000` OR `cpa >= 50.000000` (either clause may fire when that field is present) | `Campaign-grain ROAS is at or below 1.000000, or CPA is at or above 50.000000, on the canonical previous Taipei day.` |
| `PAUSE` | `conversions`, `spend`, `desired_state` | `desired_state = ACTIVE` AND `conversions = 0` AND `spend > 0` | `The campaign is ACTIVE, recorded conversions are zero, and spend is present on the canonical previous Taipei day.` |
| `SWAP_CREATIVE` | `ctr` | `ctr < 0.005000` | `Campaign-grain CTR is below 0.005000 on the canonical previous Taipei day.` |
| `REGENERATE_CREATIVE` | `ctr` | `ctr < 0.005000` | `Campaign-grain CTR is below 0.005000; consider generating a new approved IMAGE through the existing Creative Factory.` |
| `CREATIVE_FATIGUE` | `impressions`, `ctr` | `impressions >= 20000` AND `ctr < 0.008000` | `Campaign-grain impressions are at least 20000 and CTR is below 0.008000 on the canonical previous Taipei day.` |
| `AUDIENCE_FATIGUE` | Frequency (unavailable) | never | never |

Every emitted row uses the same `riskSummary`:

`Approval records the operator decision only. It does not change desired state, Ad Set budget, creatives, or metrics, and it does not call a platform adapter.`

Do not include a proposed budget amount, pause command, or generation payload on the recommendation.

Stage 4D default `SUCCESS` constants (`impressions=10000`, `clicks=100`, `conversions=4`, `spend=25.000000`, `revenue=100.000000`) yield `roas=4.000000` and `ctr=0.010000`, so generate emits `INCREASE_BUDGET` only for those campaigns. Other types require test-only snapshot rows (direct SQL in tests is allowed; generate still must not call an adapter).

`INCREASE_BUDGET` and `DECREASE_BUDGET` must not both emit for the same campaign and window under this rule set (thresholds do not overlap). Runtime must assert that invariant.

## Persistence (V16, runtime PR only)

V16 is named `V16__create_decision_recommendations.sql`. V1–V15 remain byte-for-byte unchanged. This specification PR contains no SQL file.

### `decision_recommendations`

- `recommendation_uuid UUID PRIMARY KEY`
- `platform_account_uuid UUID NOT NULL` FK `platform_accounts` `ON DELETE RESTRICT`
- `platform_campaign_uuid UUID NOT NULL`
- `campaign_uuid UUID NOT NULL` FK `campaign_plans` `ON DELETE RESTRICT`
- composite FK `(platform_campaign_uuid, platform_account_uuid)` → `platform_campaigns`
- `recommendation_type VARCHAR(32) NOT NULL CHECK` in the seven enum values
- `status VARCHAR(16) NOT NULL CHECK` in `PENDING`, `APPROVED`, `REJECTED`
- `window_start TIMESTAMPTZ NOT NULL`, `window_end TIMESTAMPTZ NOT NULL`, `timezone VARCHAR(64) NOT NULL CHECK timezone='Asia/Taipei'`
- `attribution_click_days SMALLINT NOT NULL CHECK = 7`, `attribution_view_days SMALLINT NOT NULL CHECK = 1`
- `currency CHAR(3) NOT NULL CHECK = 'TWD'`
- `desired_state VARCHAR(16) NOT NULL` (copied at generate time)
- `reason_summary VARCHAR(500) NOT NULL`, `risk_summary VARCHAR(500) NOT NULL`
- evidence bases and derived columns matching `RecommendationEvidence` (NULL allowed; no frequency column)
- `rule_set_key VARCHAR(32) NOT NULL CHECK = 'RULE_SET_V1'`
- `evidence_fingerprint CHAR(64) NOT NULL CHECK` `^[0-9a-f]{64}$`
- `version BIGINT NOT NULL DEFAULT 0 CHECK >= 0`
- `created_at`, `updated_at TIMESTAMPTZ NOT NULL`

Unique: `(platform_account_uuid, platform_campaign_uuid, recommendation_type, window_start, window_end, timezone)`.

`CHECK (window_end > window_start)`.

Identity columns, window, type, and account are immutable after insert. While `status='PENDING'`, generate may update evidence columns, `desired_state`, `reason_summary`, `risk_summary`, `evidence_fingerprint`, `version`, and `updated_at`. Terminal rows cannot return to `PENDING` and cannot change evidence.

Hard DELETE is rejected (append/protect), matching Stage 4A table style.

### `decision_recommendation_decisions`

Mirror Stage 03D `ai_review_decisions`:

- `recommendation_decision_uuid UUID PRIMARY KEY`
- `recommendation_uuid UUID NOT NULL UNIQUE` FK `ON DELETE RESTRICT`
- `decision VARCHAR(16) NOT NULL CHECK` in `APPROVED`, `REJECTED`
- `reason VARCHAR(2000)`; `APPROVED` requires `NULL`; `REJECTED` requires nonblank trimmed text
- `reviewer_type VARCHAR(32) NOT NULL CHECK` in `LOCAL_ADMIN`, `TRUSTED_ACTOR`
- `reviewer_id VARCHAR(128) NOT NULL` nonblank
- `request_id VARCHAR(128) NOT NULL` existing safe request-ID character set
- `reviewed_recommendation_version BIGINT NOT NULL CHECK >= 0`
- `decided_at TIMESTAMPTZ NOT NULL`

No UPDATE/DELETE of decision rows. Deferred coherence: one matching decision iff recommendation status is terminal, and recommendation `version` is exactly one greater than `reviewed_recommendation_version`.

PostgreSQL remains the System of Record. Recommendations are not an ads ledger and not provider spend.

## Generate transaction

1. Resolve account and canonical window inside a read eligibility step.
2. For each eligible campaign, load the latest campaign-grain snapshot for that window (or skip).
3. Evaluate `RULE_SET_V1`. Compute `evidence_fingerprint` as lowercase SHA-256 of UTF-8 compact JSON (no pretty-print, no space after `:` or `,`) with exactly these keys in lexicographic order:

```text
{
  "campaignUuid": "<lowercase uuid>",
  "metricSourceFingerprint": "<64 hex from the selected snapshot>",
  "recommendationType": "<enum name>",
  "ruleSetKey": "RULE_SET_V1",
  "windowEnd": "<second-precision UTC instant>",
  "windowStart": "<second-precision UTC instant>"
}
```

4. For each emitted type:
   - no row for the unique key → `INSERT` `PENDING`, `createdCount++`
   - `PENDING` and same fingerprint → replay, zero version bump, `replayedCount++`
   - `PENDING` and different fingerprint → update evidence, increment `version` once, `updatedCount++`
   - `APPROVED` or `REJECTED` → replay existing terminal row, do not reopen, `replayedCount++` (not counted in `items` echo unless it was insert/update)

5. Commit. Generate that only replays is still HTTP `200`.

Generate emits **zero** Audit events on a pure replay (all candidates replayed, `createdCount=0`, `updatedCount=0`). Insert or evidence-update emits exactly one Audit `CREATE`/`UPDATE` on subject `DECISION_RECOMMENDATION` per changed row, with redacted values (no fingerprint, no account UUID in the public audit payload beyond existing sanitizer rules).

Approve/reject: lock recommendation, verify `If-Match` version, reject terminal with `409` `DECISION_ALREADY_DECIDED`, insert decision, set status, increment version once, append Audit `CREATE` on `DECISION_RECOMMENDATION_DECISION` in the same transaction. Failed/stale requests write no decision and do not increment version.

SQLSTATE `40001`/`40P01` maps to `409` `DECISION_CONCURRENCY_CONFLICT` with zero automatic retry.

## Application package

Runtime (not this specification PR) places services under `com.aicommerce.platform.decision` (web/application/infrastructure). That package:

- may query PostgreSQL with `JdbcTemplate` or mapped repositories;
- must not inject or mention the five platform ports named in decision 3;
- must not call `ReviewDecisionService.approve` or `reject`;
- must not call Stage 4D refresh internals;
- must not live inside `com.aicommerce.platform.ai` or `com.aicommerce.platform.dashboard`.

A compile-path test equivalent to `AiHasNoPlatformWritePathTest` and `DashboardHasNoPlatformWritePathTest` covers the decision package. `ai` and `dashboard` must still have no dependency on the five ports.

## BFF and UI

BFF adds only:

- `POST /api/decision-recommendations/generate`
- `GET /api/decision-recommendations`
- `GET /api/decision-recommendations/{recommendationUuid}`
- `POST /api/decision-recommendations/{recommendationUuid}/approve`
- `POST /api/decision-recommendations/{recommendationUuid}/reject`

Reuse `forwardAllowlistedRequest` (10 s timeout, no Browser-chosen origin, allowlisted query). GET request bodies are forbidden. Response size: reject Backend bodies larger than **1 MiB** (fail closed `502`), matching Stage 4B.

Safe path regex must not match `/api/decision-recommendations/../` or extra segments.

UI:

- No new App Router page is required. `/dashboard` stays the Stage 05 workbench.
- When both Frontend flags are on, add an eighth visible region **優化建議**, visually distinct from **AI 建議**. Empty state is explicit. Do not rename **AI 建議**.
- Region load is `GET /api/decision-recommendations?status=PENDING` only (no generate on load).
- A **Generate suggestions** control requires a second explicit confirm and then `POST .../generate`.
- Approve disabled only when status is not `PENDING`. Reject requires a nonblank reason. Both require `If-Match` from `version` and a second explicit confirm (not a GET).
- Successful approve/reject copy must state that ads state did not change.
- FAKE / no-real-provider / no-spend / approval-does-not-execute copy remains visible in the region.
- Home `/` does not need a new nav item if Dashboard is already linked.

## Error mapping

Validation precedence: feature/profile gate; query/body/ETag shape; account configuration; resource state. The first failure wins.

| HTTP | Public code | Fixed message | Source |
| --- | --- | --- | --- |
| 400 | `DECISION_REQUEST_INVALID` | `Decision request is invalid` | query, body, UUID shape, empty-POST rules |
| 404 | `DECISION_DISABLED` | `Decision engine is unavailable` | BFF flag off |
| 404 | `DECISION_NOT_FOUND` | `Decision recommendation was not found` | unknown UUID |
| 409 | `DECISION_ALREADY_DECIDED` | `Decision recommendation is already decided` | approve/reject on terminal |
| 409 | `DECISION_CONCURRENCY_CONFLICT` | `Decision recommendation could not be updated` | serialization failure |
| 412 | `DECISION_STALE` | `Decision recommendation version is stale` | If-Match |
| 428 | `DECISION_PRECONDITION_REQUIRED` | `If-Match is required` | missing If-Match on mutate |
| 503 | `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` | `The local platform account is unavailable` | account resolver, same as Stage 4B |
| 503 | `BACKEND_UNAVAILABLE` | `Backend is unavailable` | existing BFF |

Do not add an execute error catalog. Stage 4B/4C/4D/03D codes remain on those routes only.

## Verification and acceptance

This specification PR is docs-only. Runtime must prove:

- `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` is **not** empty only because of additive `V16__create_decision_recommendations.sql`; V1–V15 bytes unchanged; this specification PR itself has an empty migration diff.
- Default/production/`adapter=meta`/missing `stage6` expose no decision controller.
- Generate and GET record **zero** adapter invocations and **zero** snapshot inserts and **zero** `platform_operations` rows.
- Approve/reject record zero adapter invocations, zero `desired_state`/budget column changes, zero AI job rows, and zero Stage 03D review decisions.
- `AUDIENCE_FATIGUE` is never inserted. Frequency is absent from JSON.
- NULL spend omits ROAS/CPA and does not emit money rules; NULL clicks/impressions omit CTR rules; missing snapshots increment `skippedIncompleteCount` and do not zero-fill.
- `SUCCESS` fixtures emit `INCREASE_BUDGET` only; overlap invariant holds.
- `com.aicommerce.platform.decision` has no dependency on the five platform ports; `ai` and `dashboard` still have none.
- `GET /api/dashboard` JSON contract remains unchanged (no decision fields).
- Frontend: both flags required for the region; flag-off BFF `404`; approve/reject happy path; generate not on load.
- Playwright Compose: `/dashboard` first load still records zero `POST`/`PATCH`/`DELETE` to `/api/**` before click; after generate confirm, only `POST /api/decision-recommendations/generate`; after approve confirm, only the decision approve path; Stage 05 review path and 4E `platform-stage4e.spec.ts` stay green.
- Full Backend regression; Frontend lint/typecheck/tests/build; `npm audit --omit=dev`; Compose config/cold health; Smoke; Playwright; actionlint; pinned Gitleaks; `git diff --check`.
- Exact-head Push and Pull Request `quality-and-compose` and `secret-scan` with no required-step skip other than Playwright artifact upload after an E2E pass.

Parent stub mapped to executable proof:

| Stub | Runtime proof |
| --- | --- |
| CTR, CPA, ROAS, CVR, CPM | evidence fields from Stage 4D formulas; omitted when unknown |
| Frequency | omitted; `AUDIENCE_FATIGUE` never emitted |
| 增加/降低預算、暫停、更換/重新生成素材、素材疲乏 | `RULE_SET_V1` types and contract tests |
| 受眾疲乏 | enum exists; generate never inserts |
| 原因、數據與風險 | `reasonSummary`, `RecommendationEvidence`, `riskSummary` required on every card |
| 人工批准或拒絕 | Playwright approve and reject on recommendation routes |
| 未批准不得執行 | compile-path + approve does not mutate platform/AI write tables; unapproved rows stay `PENDING` with zero adapter calls |

## Stage gate

- [x] Stage 05 FAKE complete: spec PR #69, runtime PR #70, close-out PR #71 at `3d3b7b3`; post-merge `main` CI Run `32795522589` passed.
- [ ] Independent Manager Review recorded `APPROVE` for the specification Head.
- [ ] The specification Head passed full exact-head Push and Pull Request CI.
- [ ] Specification squash-merged; post-merge `main` CI passed.
- [ ] Runtime locked until the previous box is true.

Stage 06 runtime stays locked until this specification is `APPROVE`d, squash-merged, and post-merge `main` CI has passed. Optional Meta paused proof stays locked until a separate human record.
