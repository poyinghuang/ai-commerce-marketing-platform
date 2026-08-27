# Stage 08 — Live Connector Reads

## Gate status

- Specification: PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) squash-merged at `21aca71`; post-merge `main` CI Run `33090522880` passed
- Status: Specification squash-merged; **8A runtime** Draft PR [#83](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/83)
- Branch: `codex/stage-08a-live-sheets`
- Base: `21aca71932a5d3d92bf608435a0fb389f514590a` (PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) squash merge)
- Stage 07 prerequisite: Passed — spec PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) at `2851003`; 7A PR [#77](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/77) at `eb2618d`; 7B PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) at `da01b13`; 7C-1 PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) at `f5faa28`; 7C-2 PR [#80](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/80) at `ed3f4cf`; close-out PR [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81) at `49b73d6`; post-merge `main` CI Run `32996644069` passed
- Implementation: 8A unlocked after specification merge + post-merge CI
- Manager Decision: 8A runtime Manager Review not started
- Merge: Specification squash-merged at `21aca71`. 8A runtime not merged
- Optional Meta paused proof (`META_TEST_READ_WRITE_PAUSED` / `META_TEST_DELIVERY`): **Locked**
- Live Google Ads / LINE / TikTok / spend / production: **Locked**
- Auto-execute: **Forbidden**

This document is the implementation contract for **opt-in LOCAL live reads** of Google Sheets, Google Drive folder ensure, and Meta Insights. It does not authorize production credentials, CI secrets, live ads writes, a scheduler, Auth/RBAC/Tenant, or Decision Engine execute-on-approve.

## Objective

Replace stub/FAKE data on an explicit LOCAL path with real connector responses, while PostgreSQL remains the System of Record:

1. **Sheets** — live `spreadsheets.values.get` into the existing 2E Preview/Execute workflow.
2. **Drive** — live `StorageProvider.ensureProductTree` (search-before-create folders only; no file bytes).
3. **Meta Insights** — live Graph delivery status + Insights window into the existing 4D snapshot ingest.

Default Compose, `application-local.yml`, `application-test.yml`, and CI stay on stub/FAKE. Runtime is sliced. The first runtime PR after this specification merges is **8A only**.

## Repository-owner and Architecture-locked decisions

Credentials, live delivery, Auth/RBAC/Tenant, production, spend, or a second live ads platform still require `ESCALATE_TO_HUMAN`.

1. Live connectors run only under `(local | test) & !production` **and** an explicit per-slice flag. Missing credentials fail closed (`GOOGLE_AUTH_UNAVAILABLE` / `CONNECTOR_NOT_CONFIGURED` / `PLATFORM_ADAPTER_UNAVAILABLE`). Production remains the existing fail-closed Google beans with **no** credentials supplied by this Stage.
2. CI and GitHub Actions receive **no** Google or Meta secrets, service-account JSON, or access tokens. Automated tests use Stub, FAKE, or MockWebServer/WireMock fixtures.
3. Browser input cannot choose provider class, origin URL, credential, Drive root, Shared Drive ID, Graph version, ad account, token, or Insights window. Spreadsheet ID / sheet name remain the existing 2E Preview command fields; they are not a credential.
4. Origins are fixed: Sheets `https://sheets.googleapis.com`, Drive `https://www.googleapis.com`, Meta Graph `https://graph.facebook.com`. Tokens and provider error bodies never enter API errors, logs, Audit, or persistence.
5. Flyway V1–V17 stay byte-identical in this specification PR, in **8A**, and in **8B**. The first allowed later migration is **additive V18 in 8C only**, named below. Recovery from an 8C defect is forward-only V19+.
6. Application code continues to depend only on existing ports: `SheetValuesProvider`, `StorageProvider`, `PlatformDeliveryReadPort`, `PlatformMetricsReadPort`. Domain and application packages must not import Google or Meta SDK types.
7. Stage 06 remains suggestion-only. Stage 08 must not connect approve/reject to pause, budget, refresh, publication, or AI execute. 4D refresh stays an explicit human preview-confirm.
8. Stage 02E mapping, UUID-first matching, no Sheet write-back, Drive six folder roles, and search-before-create remain authoritative. 8A/8B must not add upload/download/delete/rename or bidirectional sync.
9. Stage 04 paused create, TWD / `Asia/Taipei`, `7d_click` / `1d_view`, null-not-zero, previous complete Taipei day, and GET-never-calls-adapter remain authoritative. Live Insights maps provider JSON **inside the adapter**.
10. Exactly one bean per port. Enabling a live bean unloads the stub/FAKE bean for that port. 8C must not load a live write adapter. `PlatformOperationTransactions` continues to reject any account whose `provider_key` is not `FAKE` or `FAKE_GOOGLE`.

## Slice plan

Do not start a later runtime row until the previous runtime row is merged and post-merge `main` CI has passed.

| Slice | Goal | Migration | REST/UI | Unlock |
| --- | --- | --- | --- | --- |
| **8A** | Opt-in LOCAL live Sheets `values.get` | None | Existing 2E Preview/Execute | This spec `APPROVE` + merge + post-merge CI + human secret record |
| **8B** | Opt-in LOCAL live Drive folder ensure | None | Existing storage-folder GET/POST | 8A merged + post-merge CI |
| **8C** | Opt-in LOCAL live Meta Insights + delivery read | V18 only | Existing 4D refresh | 8B merged + post-merge CI |
| Human live proof | Recorded local run against test resources | None | None | After each slice runtime, not CI |
| 7D/7E LINE / TikTok | Later specifications | Not this spec | Not this spec | Separate spec |
| Live Google Ads / `META_TEST_DELIVERY` | Later specifications | Not this spec | Not this spec | Separate human record |

## Secrets and test resources (human)

A recorded human decision must exist before **8A runtime may merge**. The record names:

- A Google **test** spreadsheet the service account can read (`spreadsheets.readonly`).
- A Google **test** Drive root folder (and optional Shared Drive) for 8B (`drive.file` only).
- A Meta **test** ad account for 8C with `ads_read` (not ads management, not spend). Campaign / Ad Set / Ad external IDs that already exist and stay `PAUSED`.
- How secrets are supplied on the operator machine: Application Default Credentials or an env path **outside the repository**. `META_TEST_ACCESS_TOKEN` as an environment variable. No JSON key, token, or `.env` in git. No GitHub Actions secret for this Stage.

Compose defaults do not set live flags. Operators who opt in export flags locally; they do not commit them.

## 8A — Live Google Sheets (first runtime)

### Goal

Let LOCAL select `GoogleSheetValuesProvider` instead of `StubSheetValuesProvider` without changing Preview/Execute, V6/V6.1, matching, or Audit.

### Selection

| Item | Value |
| --- | --- |
| Flag | `platform.sheets.provider` |
| Allowed LOCAL/TEST values | `stub` (default, current CI path), `google` |
| Profile | `@Profile("(local \| test) & !production")` for the stub; Google bean also loads on that profile when the flag is `google` |
| Production / default | Unchanged: existing `GoogleSheetValuesProvider` fail-closed without ADC |

Wrong flag values load neither stub nor Google on local/test.

### Contract reused

- Fixed origin, `FORMATTED_VALUE`, `majorDimension=ROWS`, 5s connect / 15s read, two-attempt retry on transient 429/5xx.
- Scope remains `https://www.googleapis.com/auth/spreadsheets.readonly`.
- Preview still reads the Sheet **before** the database transaction. Execute never rereads a changed Sheet.
- No Sheet write-back, webhook, or polling.

### Proof

- Profile matrix: local default stub; local `google` loads Google bean; production unchanged; mixed `production,local` stays Google fail-closed.
- MockWebServer: happy path, 401/403 sanitized, retry once on 429, no token in logs.
- Existing 2E Preview/Execute tests stay green on stub.
- CI Compose does not set `platform.sheets.provider=google`.
- Optional human proof (not CI): Preview/Execute against the recorded test spreadsheet.

## 8B — Live Google Drive folder ensure (after 8A)

### Goal

Let LOCAL select `GoogleDriveStorageProvider` instead of `StubStorageProvider` / `FakeObjectStorageProvider` without changing folder roles, V7, or Product-tree semantics.

This slice **creates folders** in the configured test Drive when they are missing. It is not file-byte I/O. Upload, download, delete, move, rename, and archive-folder-move stay forbidden.

### Selection

| Item | Value |
| --- | --- |
| Flag | `platform.storage.provider` |
| Allowed LOCAL/TEST values | `stub` (default), `fake-object` (7B), `google` (this slice) |
| Config | existing `GOOGLE_DRIVE_ROOT_FOLDER_ID`; optional `GOOGLE_SHARED_DRIVE_ID` |
| Production / default | Unchanged fail-closed Google bean |

### Contract reused

- Scope `https://www.googleapis.com/auth/drive.file`.
- Search-before-create with `appProperties` (`product_uuid`, `folder_role`).
- Shared Drive parameters unchanged from 2E-3.
- GET storage-folder still reads PostgreSQL only. POST ensure may call Drive, then persist IDs.

### Proof

- Profile matrix includes `google` vs stub vs fake-object; exactly one `StorageProvider`.
- MockWebServer: search hit reuses ID; miss creates; duplicate search → `STORAGE_FOLDER_STATE_CONFLICT`; missing root → `CONNECTOR_NOT_CONFIGURED`.
- CI stays `stub`.
- Optional human proof: ensure on one ACTIVE Product against the recorded test root; repeat ensure is idempotent.

## 8C — Live Meta Insights and delivery read (after 8B)

### Goal

Add a read-only live adapter on `PlatformDeliveryReadPort` and `PlatformMetricsReadPort` that calls a pinned Graph version, maps into existing 4D observations, and persists through the existing refresh transactions. No live Campaign/Ad Set/Ad create, pause, resume, budget, or creative write.

### Additive V18

Filename: `V18__allow_meta_provider_key.sql`.

- Expand `platform_accounts.provider_key` CHECK to `IN ('FAKE','FAKE_GOOGLE','META')`.
- Expand `is_valid_platform_evidence` `providerKey` the same way (schema version stays `1`).
- V1–V17 stay byte-identical.
- Java `ProviderKey` / `PlatformProvider` gain `META`.
- `PlatformOperationTransactions` **must still reject** `META` with `PLATFORM_PROVIDER_UNSUPPORTED`. 8C must not insert `platform_operations` for Insights refresh (4D already does not).

### Account and entities

- LOCAL/TEST initializer (flag on only) inserts one `provider_key=META` account: currency `TWD`, timezone `Asia/Taipei`, distinct UUID/reference/fingerprint constants in code.
- Durable Campaign / Ad Set / Ad `external_id` values come from human-recorded test IDs in **environment variables** (`META_TEST_CAMPAIGN_ID`, `META_TEST_ADSET_ID`, `META_TEST_AD_ID`), not from Browser input and not from git. If those env vars are absent, the initializer does not invent live IDs; 4D refresh on missing external ID stays ineligible.
- `/platforms/meta` 4B/4C writes stay on the existing FAKE account. 4D live refresh uses the META account only when the live Insights flag is on. GET 4D still reads PostgreSQL only.

### Adapter

| Item | Value |
| --- | --- |
| Class | `com.aicommerce.platform.delivery.infrastructure.provider.LiveMetaInsightsReadAdapter` |
| Flag | `platform.stage8.insights.live=true` |
| Token | `META_TEST_ACCESS_TOKEN` env; fail closed if blank |
| Graph | `https://graph.facebook.com/v22.0` (version pinned in code; not Browser-selectable) |
| Writes | none |

Delivery: `GET /{id}?fields=effective_status` with 5s/15s timeouts and one retry on 429/5xx. Map `PAUSED`, `ACTIVE`, `CAMPAIGN_PAUSED` / `ADSET_PAUSED` → `PAUSED`; `ACTIVE` → `ACTIVE`; anything else → `UNKNOWN`. Do not mutate desired state.

Insights: `GET /{id}/insights` with:

- `fields=impressions,reach,clicks,spend,actions,action_values`
- `time_range` = previous complete `Asia/Taipei` calendar day (`since` and `until` that date; adapter formats dates)
- `time_increment=1`
- `action_attribution_windows=['7d_click','1d_view']`
- `level` matching entity type

Map:

| Observation | Source |
| --- | --- |
| impressions, reach, clicks | numeric fields; missing → NULL |
| spend | `spend`; missing → NULL; reject negative |
| conversions | `actions` entry `omni_purchase` if present, else `purchase`; missing → NULL; never sum both |
| revenue | matching `action_values`; missing → NULL |
| freshness | `FRESH` on a well-formed row; `UNAVAILABLE` on transport/4xx after retry (persist nothing, same as 4D `THROW`/`MALFORMED` rules) |

Currency must be account `TWD`. A different currency or malformed number is `PLATFORM_CONTRACT_INVALID` and persists nothing. Null bases stay null; derived CTR/CPC/CPM/CPA/CVR/ROAS remain read-time and are never stored.

### Bean uniqueness

When `platform.stage8.insights.live=true`, `LiveMetaInsightsReadAdapter` is the only `PlatformDeliveryReadPort` / `PlatformMetricsReadPort` bean. `DeterministicFakePlatformReadAdapter` must not load. Fake **write** adapters remain `@Primary` for create/pause ports. Compile-path tests continue to forbid `ai` and `decision` packages from depending on the read ports.

### Proof

- MockWebServer: SUCCESS mapping, PARTIAL_NULL, malformed negative spend, 401 sanitized, no token in logs, adapter not invoked inside a transaction.
- V18 cold/upgrade and collision-forward tests. V1–V17 checksums unchanged.
- 4E Playwright and FAKE 4D tests stay on the default CI path (live flag off).
- Optional human proof: confirm-refresh one paused test Campaign; snapshot currency TWD; no `platform_operations` row.

## Inherited boundaries

1. PostgreSQL remains System of Record. Provider identifiers are external evidence.
2. AI and Decision Engine still have no compile-time path to a platform write or refresh port.
3. Stage 05 `GET /api/dashboard` JSON stays unchanged.
4. Authentication, RBAC, Tenant remain frozen.
5. `META_TEST_DELIVERY` stays disabled. This Stage does not collect production Meta tokens.

## Included in this specification (docs)

- Named 8A/8B/8C contracts, flags, class name, V18 allow-list, Graph mapping, and proofs.
- Human secret-handling rules and optional live-proof checklists.
- Unlock order and locks for live ads writes.

## Explicitly excluded

- Java, TypeScript, SQL, flags, or Compose edits in **this** PR.
- Production credentials, GitHub Actions secrets, Secret Manager, or deploying live flags on Compose CI.
- Live Google Ads API/SDK/MCC, LINE Ads, TikTok Ads, paid image APIs, spend, billing.
- Sheet write-back, Drive file bytes, folder delete/move/rename.
- `META_TEST_READ_WRITE_PAUSED` live create/pause, `META_TEST_DELIVERY`, or enabling `PlatformOperationTransactions` for `META`.
- Scheduler, cron, unattended Insights pull, or converting adapter failure into zero metrics.
- Auth/RBAC/Tenant, production deploy, or System of Record replacement.
- Decision Engine auto-execute or execute-on-approve.
- Editing V1–V17 in any Stage 08 slice. V18 in 8A or 8B.

## Eligibility and flags (runtime, not this PR)

| Slice | Gate |
| --- | --- |
| 8A live Sheets | `(local \| test) & !production` and `platform.sheets.provider=google` |
| 8B live Drive | plus `platform.storage.provider=google` and configured root folder |
| 8C live Insights | plus `platform.stage8.insights.live=true` and `META_TEST_ACCESS_TOKEN` |
| Compose / CI | stub Sheets, stub storage, Insights flag false, `platform.adapter=fake` |
| Production | existing fail-closed Google beans; no Meta Graph client |

## Human review

This specification authorizes live network paths and credentials (supplied outside git). Human review is **Yes**.

Manager Decision for this docs-only PR is `ESCALATE_TO_HUMAN` until the owner records:

1. Test spreadsheet ID (not committed as a secret; may be named in the human record).
2. Test Drive root (and Shared Drive if used).
3. Meta test ad account + paused entity IDs + `ads_read` token handling.
4. Confirmation that CI will not receive those secrets.

After that record exists, Manager may `APPROVE` this specification. 8A runtime still cannot start until this spec is merged and post-merge `main` CI has passed.

**Yes** remains in force before live Google Ads, LINE, TikTok, paid providers, production credentials, or `META_TEST_DELIVERY`.

## Stage gate

- [x] Stage 07 FAKE complete: spec #76, 7A #77, 7B #78, 7C-1 #79, 7C-2 #80, close-out #81 at `49b73d6`; post-merge `main` CI Run `32996644069` passed.
- [x] Specification squash-merged: PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) at `21aca71`; post-merge `main` CI Run `33090522880` passed.
- [ ] 8A runtime Manager `APPROVE` + merge + post-merge `main` CI.

Current gate is 8A runtime. 8B/8C, 7D/7E, live Google Ads, optional Meta paused proof, and auto-execute stay locked.
