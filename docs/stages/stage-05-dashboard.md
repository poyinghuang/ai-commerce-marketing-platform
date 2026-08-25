# Stage 05 — Dashboard

## Gate status

- Status: Specification squash-merged; runtime squash-merged; Stage 05 FAKE closed
- Specification: PR [#69](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/69) squash-merged at `3bbbc692393a2663fa2d5cbc04feddb11ce27c47`; post-merge `main` CI Run `32759493087` passed
- Runtime: PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) squash-merged at `9e0f4b407f464424df0b57dc469f2936b6cbc927`; post-merge `main` CI Run `32792642634` passed `quality-and-compose` and `secret-scan`
- Branch: `codex/stage-05-dashboard-runtime` (merged)
- Base: `3bbbc692393a2663fa2d5cbc04feddb11ce27c47` (PR #69 squash merge)
- Stage 04 prerequisite: Passed — 4A–4E FAKE LOCAL/TEST on `main`; tag `stage-04-complete` peels to `031d657`; post-merge `main` CI Run `32754399607` passed
- Product settings: Stage 04 owner defaults of 2026-08-15 remain authoritative (Asia/Taipei, `7d_click` / `1d_view`, daily snapshots, account currency, null means unknown, create stays `PAUSED`)
- Implementation: Merged
- Manager Decision: Merged on `main`
- Merge: Runtime PR #70 squash-merged at `9e0f4b4`
- Stage 06 Decision Engine specification: Unlocked
- Optional Meta paused proof: **Locked** (separate human record)

This document is the implementation contract for Stage 05. It does not authorize credentials, `META_TEST_READ_WRITE_PAUSED`, `META_TEST_DELIVERY`, real Meta, spend, production, Auth/RBAC/Tenant, a scheduler, a new platform write or refresh port, or Decision Engine behavior.

## Parent stub (unchanged intent)

### 目標

建立營運工作台。

### 頁面

- 今日待辦
- 商品與資料完整度
- 素材待審核
- Campaign 狀態
- KPI Overview
- AI 建議
- 異常事件

### 驗收

- 使用者可從 Dashboard 找到待處理事項
- 可追蹤每個商品與 Campaign 狀態
- 可查看核心 KPI
- 可接受或拒絕 AI 建議

## Objective

Deliver a LOCAL/TEST ops workbench that reads PostgreSQL and existing same-origin APIs so an operator can see pending work, product readiness, campaign-plan and FAKE platform-campaign status, a null-safe account KPI overview, pending creative reviews, and anomalies — then approve or reject a pending generation output through the **existing** Stage 03 review routes.

The slice does not invent a suggestion engine, mutate desired ads state, refresh metrics on page load, or call a platform adapter.

## Repository-owner and Architecture-locked decisions

These decisions implement the Architecture Dashboard module and the Stage 05 stub against tables and routes that already exist. They do not reopen Stage 04 owner defaults and do not require a new human product meeting. Credentials, live delivery, Auth/RBAC/Tenant, or a scheduler still require `ESCALATE_TO_HUMAN`.

1. Provider execution remains deterministic `FAKE` only under explicit `local` or `test` configuration. Default and production profiles expose no Stage 05 controller, usable dashboard BFF, credential contract, or network path to a real ads provider.
2. Stage 05 adds **no** Flyway version. V1–V15 remain byte-identical. Recovery from a later index need is forward-only through a separately specified V16+.
3. Dashboard HTTP is **read-only** except that the UI may call the **already shipped** Stage 03D review endpoints after an explicit human click. Stage 05 adds no approve/reject/pause/resume/budget/refresh/execute Backend route.
4. `GET /api/dashboard` and section list GETs read PostgreSQL only. They never call `PlatformCampaignPort`, `PlatformAdSetPort`, `PlatformAdPort`, `PlatformDeliveryReadPort`, or `PlatformMetricsReadPort`, never insert a metric snapshot, never create a `platform_operations` row, and never execute an AI job.
5. Page load and first paint issue **zero** `POST` / `PATCH` / `DELETE` to Backend or BFF, including zero auto review, zero 4D refresh, and zero platform mutation. Playwright records that invariant on `/dashboard`.
6. The stub phrase **AI 建議** in this Stage means **Stage 03 pending generation outputs** (`PENDING_REVIEW`) and the existing approve/reject workflow. It does **not** mean Stage 06 Decision Engine suggestions (budget up/down, pause, creative swap, fatigue). Stage 06 stays locked. The Dashboard package must not persist or display a Decision Engine recommendation type.
7. KPI Overview is a **Campaign-grain** rollup of the Stage 4D canonical previous complete `Asia/Taipei` day for the Stage 4B FAKE account. Ad Set and Ad snapshots are ignored so spend is not double-counted. Derived CTR/CPC/CPM/CPA/CVR/ROAS use the Stage 4D formulas on the **aggregated** bases and are omitted when a required base is missing or a denominator is zero. NULL is never coerced to zero.
8. `PAUSED` desired or observed state after Stage 04 create is **not** a todo and **not** an anomaly. Unknown operations, `ERROR` observed state, failed generation jobs, and non-`READY` product readiness are.
9. Browser input cannot choose Backend origin, account, timezone, currency, attribution, metric window, or `asOf`. Dashboard GETs reject every undeclared query key.

## Inherited boundaries

1. Stage 02 product list, campaign-plan list, quality projections (`quality_scores`, `quality_score_blockers`, `workflow_status`), and same-origin Product/Campaign BFF remain authoritative for those domains.
2. Stage 03D review: `POST /api/ai-generation-outputs/{outputUuid}/approve` and `/reject` with `If-Match`, empty approve body, reject `{ "reason" }`, blockers, Audit, and fail-closed production actor rules remain unchanged. Dashboard must not weaken them.
3. Stage 4A–4D operation, ledger, paused create, delivery GET, metrics GET, explicit refresh, and fake-adapter contracts remain unchanged. Dashboard must not call refresh on load.
4. Stage 4E: `com.aicommerce.platform.ai` still has no compile-time dependency on the five platform write/read ports named above. Stage 05 adds the same prohibition for `com.aicommerce.platform.dashboard`.
5. Authentication, RBAC, Tenant authority, credentials, real-provider access, and production remain separately gated.

## Included scope

- Feature-gated Backend dashboard reads under `local`/`test`, `platform.adapter=fake`, `platform.web.enabled=true`, `platform.stage5.enabled=true`.
- Same-origin BFF for those GET paths and the existing review POST paths (already present).
- Route `/dashboard` and a home-page link when the Frontend flag is on.
- Workbench sections matching the parent stub, populated from PostgreSQL with bounded lists and overflow pagination.
- Campaign-grain KPI Overview from existing `platform_metric_snapshots` using the Stage 4D canonical window SQL.
- Explicit approve/reject of a pending output from the Dashboard UI via existing review APIs.
- Profile/flag fail-closed tests, DTO contract tests, KPI null-safety tests, zero-adapter GET proof, Frontend component tests, and Compose-backed Playwright.

## Explicitly excluded

- Real Meta / Google / LINE / TikTok accounts, tokens, Graph/Insights URLs, credentials, network calls, paid delivery, billing, or production.
- Schedulers, polling loops, unattended metrics refresh, automatic retry, or converting missing metrics into zeros.
- Stage 06 Decision Engine types, tables, scores, auto-execute, or “increase/decrease budget / pause / swap creative” cards.
- New platform write, pause/resume, budget, Ad create, or Stage 4D preview/confirm routes.
- New public review mutation routes, AI execute-on-load, or `SYSTEM` approval.
- Authentication, RBAC, Tenant, or security-model change.
- CSV export, saved views, Browser-chosen date ranges, cross-account totals, or summing Ad Set/Ad snapshots into the account KPI.
- Editing V1–V15, destructive migration, snapshot backfill, or V16 in this Stage.
- Optional `META_TEST_READ_WRITE_PAUSED` proof.

## Eligibility and flags

| Layer | Gate |
| --- | --- |
| Backend controller and services | `@Profile("(local \| test) & !production")` and `@ConditionalOnExpression` requiring `platform.adapter=fake`, `platform.web.enabled=true`, `platform.stage5.enabled=true` |
| Frontend page `/dashboard` | `PLATFORM_STAGE5_ENABLED=true`; otherwise `notFound()` |
| Frontend BFF dashboard GETs | same Frontend flag; otherwise public `404` `DASHBOARD_DISABLED` |
| Compose / `application-local.yml` | set Stage 05 flags `true` in runtime (not this specification PR) |
| Default and production | no controller bean; no usable dashboard |

Section availability **inside** a successful dashboard GET:

| Section | `available` |
| --- | --- |
| todos (product + review + failed jobs) | always `true` when the controller exists |
| products, reviews, campaign plans | always `true` |
| platform campaigns, UNKNOWN_OPERATION / PLATFORM_ERROR todos and anomalies | `true` only when `platform.stage4b.enabled=true`; else `available=false`, empty items, `truncated=false` |
| kpis | `true` only when Stage 4B **and** `platform.stage4d.enabled=true`; else `available=false` and omit window/metric fields |

A disabled subsection is HTTP `200` with `available=false`, not a missing dashboard.

Wrong profile, missing flag, or `platform.adapter` other than `fake` yields no controller (framework `404`). Public messages reveal no account UUID, raw external ID, provider trace, fingerprint, or SQL.

## HTTP contract

All Stage 05 Backend paths are GET. Unknown methods on those paths are not added.

### `GET /api/dashboard`

- Rejects **any** query string (`400` `DASHBOARD_REQUEST_INVALID`).
- `Cache-Control: no-store`.
- No `ETag` (aggregate read; not an optimistic-concurrency resource).
- Returns `DashboardView` (below). Each collection in the summary contains at most **20** items. `truncated=true` when more rows exist for that section.

### `GET /api/dashboard/{section}`

`section` is exactly one of:

`todos` | `products` | `reviews` | `campaigns` | `platform-campaigns` | `anomalies`

Any other token is `400` `DASHBOARD_REQUEST_INVALID`.

Allowed query keys only: `page` (default `0`, `>=0`), `size` (default `20`, `1..100`). Sort is **server-owned** (see each section). Unknown keys, duplicate keys, or extra keys are `400`.

Response: `DashboardPageView` with `content`, `page`, `size`, `totalElements`, `totalPages`.

When the matching subsection would be `available=false` on the summary, the paged route still returns `200` with empty `content` and `totalElements=0` (same fail-open-empty rule). It does not 404.

### Mutations

Stage 05 adds none. Approve/reject remain:

- `POST /api/ai-generation-outputs/{outputUuid}/approve`
- `POST /api/ai-generation-outputs/{outputUuid}/reject`

as specified in `docs/stages/stage-03d-human-review.md`.

## Public records

JSON uses `NON_ABSENT` for `Optional` fields. Lists are never `null`. Instants are second-precision UTC. UUIDs are lowercase. Money strings match Stage 4B canonical plain decimals (scale 6). Counts are JSON numbers.

Forbidden in every Stage 05 HTTP body (and BFF must fail closed if a Backend body contains them): `platformAccountUuid`, `accountReference`, `externalId`, `canonicalPayload`, `requestPayload`, `outcomeEvidence`, `safeProviderTraceId`, `authorization`, `cookie`, `token`, `credential`, `secret`, `providerBody`, `providerUrl`, `sourceFingerprint`.

```text
DashboardView
  generatedAt: Instant
  todos: DashboardSection<TodoItem>
  products: DashboardSection<ProductReadinessItem>
  reviews: DashboardSection<PendingReviewItem>
  campaigns: DashboardSection<CampaignPlanItem>
  platformCampaigns: DashboardSection<PlatformCampaignItem>
  anomalies: DashboardSection<AnomalyItem>
  kpis: KpiOverview

DashboardSection<T>
  available: boolean
  items: List<T>          // summary: 0..20
  truncated: boolean
  totalElements: long     // full count even when truncated

TodoItem
  kind: PRODUCT_READINESS | PENDING_REVIEW | FAILED_GENERATION | UNKNOWN_OPERATION | PLATFORM_ERROR
  subjectUuid: UUID
  productUuid: Optional<UUID>
  href: String            // same-origin path starting with /
  title: String
  summary: String
  occurredAt: Instant

ProductReadinessItem
  productUuid: UUID
  productName: String
  lifecycleStatus: ACTIVE   // archived products omitted
  readinessStatus: DRAFT | NEEDS_REVIEW | READY
  finalScore: int
  blockerCount: int
  href: String              // /products/{uuid}?tab=quality

PendingReviewItem
  generationOutputUuid: UUID
  productUuid: UUID
  generationType: TEXT | IMAGE
  reviewStatus: PENDING_REVIEW
  version: long             // for If-Match
  blockerCount: int
  approvalBlocked: boolean
  href: String              // /products/{uuid}?tab=creative-factory

CampaignPlanItem
  campaignUuid: UUID
  campaignName: String
  lifecycleStatus: String
  startDate: Optional<String>
  endDate: Optional<String>
  platform: Optional<String>
  href: String              // /campaigns/{uuid}

PlatformCampaignItem
  platformCampaignUuid: UUID
  campaignUuid: UUID
  campaignName: String
  desiredState: PAUSED | ACTIVE | ARCHIVED
  observedState: Optional<String>
  href: String              // /platforms/meta (entity selected in UI by uuid)

AnomalyItem
  kind: UNKNOWN_OPERATION | PLATFORM_ERROR | FAILED_GENERATION
  subjectUuid: UUID
  href: String
  title: String
  summary: String
  occurredAt: Instant

KpiOverview
  available: boolean
  windowStart: Optional<Instant>
  windowEnd: Optional<Instant>
  timezone: Optional<String>          // Asia/Taipei when available
  currency: Optional<String>          // TWD when available
  attributionClickDays: Optional<int> // 7
  attributionViewDays: Optional<int>  // 1
  eligibleCampaignCount: Optional<int>
  presentCampaignCount: Optional<int>
  incomplete: Optional<boolean>
  impressions, reach, clicks, conversions: Optional<Long>
  spend, revenue: Optional<String>
  ctr, cpc, cpm, cpa, cvr, roas: Optional<String>
```

`href` values are path-only (`^/[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$`), max 512 characters, never a `http` origin.

## Section rules

### 今日待辦 (`todos`)

Closed `kind` set. Sort: `occurredAt DESC`, then `subjectUuid ASC`.

| kind | Source | Not included |
| --- | --- | --- |
| `PRODUCT_READINESS` | ACTIVE products whose `workflow_status.status` is `DRAFT` or `NEEDS_REVIEW` | `READY`; `ARCHIVED` products |
| `PENDING_REVIEW` | `ai_generation_outputs.review_status = PENDING_REVIEW` | `APPROVED` / `REJECTED` |
| `FAILED_GENERATION` | jobs in `FAILED` or `BUDGET_REJECTED` | `CREATED` / `SUCCEEDED` / `CANCELLED` |
| `UNKNOWN_OPERATION` | `platform_operations.status = UNKNOWN_OUTCOME` for the Stage 4B account | `SUCCEEDED`; omitted when 4B unavailable |
| `PLATFORM_ERROR` | platform Campaign/Ad Set/Ad `observed_state = ERROR` for that account | `PAUSED` / `ACTIVE` / `UNKNOWN` as such; omitted when 4B unavailable |

### 商品與資料完整度 (`products`)

ACTIVE products joined to `workflow_status` and `quality_scores`. Sort: readiness `DRAFT`, then `NEEDS_REVIEW`, then `READY`; then `final_score ASC`; then `product_uuid ASC`.

### 素材待審核 / AI 建議 (`reviews`)

Same pending-output population as `PENDING_REVIEW` todos. Sort: `updated_at DESC`, `generation_output_uuid ASC`. The UI may approve/reject here using Stage 03D APIs after explicit confirmation. Approval remains blocked when Stage 03D blockers exist (`approvalBlocked=true`); the UI disables approve and still allows reject with a reason.

### Campaign 狀態 (`campaigns` and `platformCampaigns`)

- `campaigns`: existing Campaign **plans** (`campaign_plans`), ACTIVE lifecycle, same default semantics as `GET /api/campaigns`. Sort: `updated_at DESC`, `campaign_uuid ASC`.
- `platformCampaigns`: `platform_campaigns` for the resolved Stage 4B FAKE account, not `ARCHIVED` desired state. Sort: `updated_at DESC`, `platform_campaign_uuid ASC`. `PAUSED` is a normal status label, not a warning.

### 異常事件 (`anomalies`)

Union of `UNKNOWN_OPERATION`, `PLATFORM_ERROR`, and `FAILED_GENERATION` only. Sort: `occurredAt DESC`, `subjectUuid ASC`.

### KPI Overview

When `kpis.available=true`:

1. Resolve the Stage 4B FAKE account exactly as Stage 4B (same UUID/reference/fingerprint/environment checks). Misconfiguration is `503` `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` with the existing public message — not a dashboard-shaped `200`.
2. Compute the canonical window with the **exact** Stage 4D SQL (`platform_taipei_business_date(statement_timestamp())`), not JVM `Clock` or Browser input.
3. `eligibleCampaignCount` = count of `platform_campaigns` for that account whose `desired_state <> 'ARCHIVED'`.
4. For each eligible campaign, select the **latest** `platform_metric_snapshots` row for that campaign UUID and canonical window identity (greatest `revision_number`). Ignore rows whose `platform_ad_set_uuid` or `platform_ad_uuid` is not null.
5. `presentCampaignCount` = eligible campaigns that have such a row.
6. `incomplete` is `true` when `presentCampaignCount < eligibleCampaignCount`, or when any present row has a NULL base among impressions/reach/clicks/conversions/spend/revenue.
7. Totals: `SUM` of non-null bases among **present** campaign rows. If every present row is NULL for a given base, omit that field. Do not store totals. Do not write snapshots.
8. Derived fields: Stage 4D formulas and `HALF_UP` scale 6 on the aggregated bases. Omit when any required aggregate base is missing or a denominator is zero.

`asOf` is not accepted. Dashboard never lists historical windows.

## Application package

Runtime (not this specification PR) places read services under `com.aicommerce.platform.dashboard` (web/application/infrastructure). That package:

- may query PostgreSQL with `JdbcTemplate`;
- must not inject or mention the five platform ports named in decision 4;
- must not call `ReviewDecisionService.approve` or `reject` (mutations stay on the existing AI controller);
- may reuse the existing read-only blocker evaluation used by `GET /api/ai-generation-outputs/{outputUuid}` to set `approvalBlocked` and `blockerCount`;
- must not live inside `com.aicommerce.platform.ai`.

A compile-path test equivalent to `AiHasNoPlatformWritePathTest` covers the dashboard package.

## BFF and UI

BFF adds only:

- `GET /api/dashboard`
- `GET /api/dashboard/todos|products|reviews|campaigns|platform-campaigns|anomalies`

Reuse `forwardAllowlistedRequest` (10 s timeout, no Browser-chosen origin, allowlisted query). Request bodies are forbidden on these GETs. Response size: reject Backend bodies larger than **1 MiB** (fail closed `502`), matching Stage 4B, because the 20-item cap must keep payloads small.

Safe path regex must not match `/api/dashboard/../` or extra segments.

UI:

- New App Router page `/dashboard` (force-dynamic).
- Home `/` remains available and gains a Dashboard action when the flag is on. Existing `/products`, `/campaigns`, `/platforms/meta`, and connector routes stay.
- Seven visible regions matching the stub. Empty states are explicit, not spinners forever.
- KPI region shows canonical window, coverage (`present` / `eligible`), incomplete warning, bases, derived KPIs or the null-means-unknown copy from Stage 4D.
- Review region: approve disabled when `approvalBlocked`; reject requires a nonblank reason; both require `If-Match` from `version`; both require a second explicit confirm control (not a GET).
- FAKE / no-real-provider / no-spend copy remains visible when platform or KPI sections are shown.
- Deep links use `href` from the API. The UI does not construct Graph URLs.

## Error mapping

Validation precedence: feature/profile gate; route/section token; query shape; account configuration (KPI/platform only). The first failure wins.

| HTTP | Public code | Fixed message | Source |
| --- | --- | --- | --- |
| 400 | `DASHBOARD_REQUEST_INVALID` | `Dashboard request is invalid` | query, section token, size/page |
| 404 | `DASHBOARD_DISABLED` | `Dashboard is unavailable` | BFF flag off |
| 503 | `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` | `The local platform account is unavailable` | KPI/platform account resolver, same as Stage 4B |
| 503 | `BACKEND_UNAVAILABLE` | `Backend is unavailable` | existing BFF |

Do not add a dashboard write error catalog. Review mutations keep Stage 03D codes (`412`, `409` `AI_REVIEW_BLOCKED`, etc.).

## Verification and acceptance

This specification PR is docs-only. Runtime must prove:

- `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` remains empty (no V16).
- Default/production/`adapter=meta`/missing `stage5` expose no dashboard controller.
- Summary and paged JSON snapshots for empty and populated fixtures; omitted Optionals; forbidden fields absent.
- Todo kinds exclude `PAUSED` platform create; include `UNKNOWN_OUTCOME` and `PENDING_REVIEW`.
- KPI: campaign grain only; NULL spend omits account spend and derived money KPIs; missing snapshots set `incomplete=true` and do not zero-fill; GET records **zero** adapter invocations and **zero** snapshot inserts.
- `com.aicommerce.platform.dashboard` has no dependency on the five platform ports; `com.aicommerce.platform.ai` still has none.
- Frontend: flag-off `notFound`; flag-on seven regions; approve/reject happy path and blocked approve; no POST on load.
- Playwright Compose: `/dashboard` first load records zero `POST`/`PATCH`/`DELETE` to `/api/**` before click; after explicit confirm, approve hits only the existing output approve path; 4E `platform-stage4e.spec.ts` stays green.
- Full Backend regression; Frontend lint/typecheck/tests/build; `npm audit --omit=dev`; Compose config/cold health; Smoke; Playwright; actionlint; pinned Gitleaks; `git diff --check`.
- Exact-head Push and Pull Request `quality-and-compose` and `secret-scan` with no required-step skip other than Playwright artifact upload after an E2E pass.

Parent stub mapped to executable proof:

| Stub | Runtime proof |
| --- | --- |
| 找到待處理事項 | todos contain readiness + pending review fixtures; UI lists them |
| 追蹤商品與 Campaign 狀態 | products + campaigns + platformCampaigns items and hrefs |
| 核心 KPI | KpiOverview contract tests + UI window/coverage/derived-or-omit |
| 接受或拒絕 AI 建議 | Playwright approve and reject via Stage 03D routes from `/dashboard` |

## Stage gate

- [x] Stage 04 FAKE complete: tag `stage-04-complete` on `031d6575a2d6102af5dd1574ca2c2d74799310f4`; post-merge `main` CI Run `32754399607` passed.
- [x] Independent Manager Review recorded `APPROVE` for the specification Head.
- [x] The specification Head passed full exact-head Push and Pull Request CI.
- [x] Specification squash-merged as PR [#69](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/69) at `3bbbc69`; post-merge `main` CI Run `32759493087` passed.
- [x] Runtime squash-merged as PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) at `9e0f4b4`; post-merge `main` CI Run `32792642634` passed.

Stage 05 FAKE is closed. Stage 06 Decision Engine specification is the current gate. Optional Meta paused proof stays locked until a separate human record.
