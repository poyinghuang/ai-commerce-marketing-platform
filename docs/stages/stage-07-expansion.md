# Stage 07 — Platform & Provider Expansion

## Gate status

- Status: Specification, 7A, 7B, 7C-1, and 7C-2 merged; Stage 07 FAKE close-out Draft PR [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81)
- Specification: PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) squash-merged at `2851003`; post-merge `main` CI Run `32928585609` passed
- 7A: PR [#77](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/77) squash-merged at `eb2618d`; post-merge `main` CI Run `32940348609` passed
- 7B: PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) squash-merged at `da01b13`; post-merge `main` CI Run `32944155884` passed
- 7C-1: PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) squash-merged at `f5faa28`; post-merge `main` CI Run `32971029793` passed
- 7C-2: PR [#80](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/80) squash-merged at `ed3f4cf`; post-merge `main` CI Run `32974606066` passed
- Branch: `codex/stage-07c-2-google-fake-ui` (merged)
- Base: `f5faa286e18889058f1af6758222a7793dc8f184` (PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) squash merge)
- Stage 06 prerequisite: Passed — spec PR [#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72) at `d77b2e0`; runtime PR [#73](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/73) at `d22d80a`; close-out PR [#75](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/75) at `771f776`; post-merge `main` CI Run `32923254447` passed
- Stage 05 prerequisite: Passed — spec PR #69, runtime PR #70, close-out PR #71
- Stage 04 prerequisite: Passed — 4A–4E FAKE; tag `stage-04-complete` peels to `031d657`
- Implementation: Merged
- Manager Decision: Merged on `main`
- Merge: Specification, 7A, 7B, 7C-1, and 7C-2 merged
- Optional Meta paused proof: **Locked** (separate human record)
- Live ads / paid providers / second **live** ads platform: **Locked** until a recorded human decision (`ESCALATE_TO_HUMAN`)
- Auto-execute: **Forbidden**

This document is the implementation contract for Stage 07 FAKE LOCAL/TEST expansion proofs. It does not authorize credentials, Google Ads / LINE / TikTok network calls, spend, production, Auth/RBAC/Tenant, a scheduler, or Decision Engine execute-on-approve.

## Parent stub (unchanged intent)

### 目標

驗證架構可擴充性。

### 驗收案例

- 新增第二個 Image Provider，不修改上游 Workflow
- 新增 Google Ads Adapter，不修改核心 Domain
- 新增 Storage Provider，不修改素材業務邏輯

## Objective

Prove the three parent-stub expansion cases **inside deterministic FAKE LOCAL/TEST** by adding alternate provider implementations behind the ports that already exist, without rewriting upstream generation workflow, ads Domain, or asset/folder business logic.

This specification is docs-only. Runtime is sliced. The first runtime PR after this specification merges is **7A only**. 7B and 7C stay locked until their own runtime PRs. LINE Ads, TikTok Ads, paid image APIs, and any live Google Ads API stay out of this Stage.

## Repository-owner and Architecture-locked decisions

Credentials, live delivery, Auth/RBAC/Tenant, production, spend, or a second live ads platform still require `ESCALATE_TO_HUMAN`.

1. Provider execution remains deterministic `FAKE` only under explicit `local` or `test` configuration. Default and production profiles expose no new Stage 07 controller, credential contract, or network path to Google Ads, LINE, TikTok, a commercial image API, or a second live object store.
2. Flyway V1–V16 stay byte-identical in this specification PR and in **7A** / **7B** runtime. The first allowed later migration is **additive V17 in 7C runtime only**, named below. Recovery from a 7C defect is forward-only V18+.
3. Application code continues to depend only on existing ports: `ImageGenerationProvider`, `StorageProvider`, `PlatformCampaignPort`, `PlatformAdSetPort`, `PlatformAdPort`, `PlatformDeliveryReadPort`, `PlatformMetricsReadPort`. New provider classes implement those ports. Domain and application packages must not import Google Ads / LINE / TikTok / commercial-image SDK types.
4. Browser input cannot choose provider class, origin URL, account, credential, workflow JSON, Drive ID, or Google Ads customer ID.
5. Stage 06 Decision Engine remains suggestion-only. Stage 07 must not connect approve/reject to pause, budget, refresh, publication, or AI execute.
6. Existing Stage 03 ComfyUI workflow JSON, `workflow_key` / `workflow_version` pair, and `RGBA_MASK_EXACT_V1` preservation remain authoritative. 7A must not edit them.
7. Existing Stage 02E folder roles, search-before-create, and Product-tree semantics remain authoritative. 7B must not add upload/download/delete/rename.
8. Existing Stage 04 paused create, TWD / `Asia/Taipei`, `OUTCOME_SALES` normalized objective, PostgreSQL as System of Record, and human preview-confirm writes remain authoritative. A FAKE Google adapter maps provider-specific details **inside the adapter**, not in Domain.

## Slice plan

Do not start a later runtime row until the previous runtime row is merged and post-merge `main` CI has passed.

| Slice | Goal | Migration | REST/UI | Unlock |
| --- | --- | --- | --- | --- |
| **7A** | Second LOCAL/TEST `ImageGenerationProvider` | None | None required | This spec `APPROVE` + merge + post-merge CI |
| **7B** | Second LOCAL/TEST `StorageProvider` | None | None required | 7A merged + post-merge CI |
| **7C-1** | `FAKE_GOOGLE` adapter + additive V17 | V17 only | None | 7B merged + post-merge CI |
| **7C-2** | Gated `/platforms/google` preview-confirm | None | Additive BFF/UI | 7C-1 merged + post-merge CI |
| 7D LINE / 7E TikTok | Later specifications | Not this spec | Not this spec | Separate spec after 7C-2 |

## 7A — Second Image Provider (first runtime)

### Goal

Add `com.aicommerce.platform.ai.infrastructure.provider.FakeSecondaryImageGenerationProvider` implementing `ImageGenerationProvider` so LOCAL/TEST can swap the image bean **without** changing `ImageGenerationService`, `ImageGenerationExecutionTransactions`, V8–V11, ComfyUI workflow JSON, or preservation.

### Selection

| Item | Value |
| --- | --- |
| Flag | `platform.image.provider` |
| Allowed LOCAL/TEST values | `stub` (default, current CI path), `fake-secondary` |
| Profile | `@Profile("(local \| test) & !production")` |
| Bean count | Exactly one `ImageGenerationProvider` in the context |
| Production / default | `DenyImageGenerationProvider` unchanged |
| Browser | Cannot set the flag, URL, or class |

Wrong value, missing profile, or production → no `FakeSecondaryImageGenerationProvider` bean. CI Compose remains `stub` unless a focused test overrides the property.

### Persistence without migration

V8 `ai_generation_jobs.provider_key` is already a non-blank `VARCHAR(64)`. 7A persists `FAKE_SECONDARY_IMAGE` when the secondary bean runs, and keeps the existing stub key when `stub` runs. No CHECK change. No V17.

`model_key` for the secondary bean is the literal `deterministic-fake-secondary`. Cost remains `BigDecimal.ZERO`. Bytes follow the same preservation contract as the stub: clone source bytes unless the existing changed-pixel fixture handle is used.

### Upstream workflow — forbidden edits

7A runtime MUST NOT modify:

- `ImageGenerationService` control flow, job FSM, or prompt-version binding
- ComfyUI resource workflow JSON, `workflow_key`, or `workflow_version`
- `AssetBinaryStore`, `ImagePreservationVerifier`, or `RGBA_MASK_EXACT_V1`
- V8–V11 SQL
- Creative Factory UI copy except an optional read-only provider label already returned by the job DTO

### Proof

- Compile-path: `com.aicommerce.platform.ai.application` source and class files mention `ImageGenerationProvider` and do not mention `FakeSecondaryImageGenerationProvider` or `ComfyUiImageGenerationProvider`.
- Profile test: `stub` → existing `StubImageGenerationProvider`; `fake-secondary` → `FakeSecondaryImageGenerationProvider`; default/production → deny.
- Focused generate test with `fake-secondary` persists `provider_key=FAKE_SECONDARY_IMAGE` and leaves preservation/Audit semantics unchanged.
- Existing image tests and Playwright `dashboard-stage5` / `dashboard-stage6` / `platform-stage4e` remain green on CI default (`stub`).

## 7B — Second Storage Provider

### Goal

Add `com.aicommerce.platform.connector.drive.infrastructure.provider.FakeObjectStorageProvider` implementing `StorageProvider.ensureProductTree` so LOCAL/TEST can swap the storage bean **without** changing `ProductStorageFolderService`, folder roles, or asset domain.

### Selection

| Item | Value |
| --- | --- |
| Flag | `platform.storage.provider` |
| Allowed LOCAL/TEST values | `stub` (default), `fake-object` |
| Profile | `(local \| test) & !production` |
| Bean count | Exactly one `StorageProvider` |
| Production | fail-closed Drive adapter unchanged when unconfigured |

### Persistence limitation (named)

V7 `ck_product_storage_folders_provider` allows only `GOOGLE_DRIVE`. 7B MUST NOT edit V7. The fake-object bean therefore still persists `storage_provider='GOOGLE_DRIVE'` and opaque folder IDs that are not Google IDs (existing stub already does this). A later additive migration may expand that CHECK; it is **not** V17 (V17 is reserved for 7C-1). Distinct `storage_provider` values are out of 7B.

### Upstream asset logic — forbidden edits

7B MUST NOT modify Product/Asset entities, asset binary APIs, Drive search-before-create Google origin, or 2E-4 connector UI beyond reading the stored tree.

### Proof

- Compile-path: `com.aicommerce.platform.asset` and `ProductStorageFolderService` do not import `FakeObjectStorageProvider` or Google SDK types (Drive adapter remains infrastructure).
- Ensure-tree with `fake-object` is idempotent, writes six roles, emits trusted Audit, and uses no network.

## 7C-1 — FAKE Google Ads adapter (no REST)

### Goal

Prove “Google Ads Adapter, no core Domain change” with a second **FAKE** provider key. No Google Ads API, no MCC, no OAuth, no spend.

### Additive V17 (runtime 7C-1 only; not this spec PR)

V1–V16 remain byte-identical. V17 is forward-only and contains no DROP, no backfill of Meta/FAKE rows, and no Frequency column.

Named CHECK expansions (exact allow-lists):

1. `platform_accounts.provider_key` and every other V12 `provider_key = 'FAKE'` CHECK that the 7C-1 diff actually touches: change to `provider_key IN ('FAKE','FAKE_GOOGLE')`.
2. Attempt `evidence` JSON `providerKey`: allow `'FAKE'` and `'FAKE_GOOGLE'` only.
3. Enum `ProviderKey` / `PlatformProvider` gain `FAKE_GOOGLE`. Java `NormalizedPlatformEvidence` currently requires `providerKey==FAKE`; 7C-1 changes that predicate to `providerKey==FAKE || providerKey==FAKE_GOOGLE`. Schema version stays `1`.

Existing FAKE Meta LOCAL/TEST account row remains. 7C-1 inserts a second LOCAL/TEST account row with `provider_key=FAKE_GOOGLE`, distinct `account_reference`, same `TWD` and `Asia/Taipei`.

### Adapter

`com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakeGooglePlatformAdapter` implements the same five write/read ports as the Meta fake. Create remains `PAUSED`. Observed-state machine, operation FSM, Audit ordinal, and budget ledger stay the Stage 4A–4D contracts. Domain campaign/ad-set/ad types do not gain Google-only fields.

Application code uses `account.providerKey()` / `operation.providerKey()` instead of hard-coding `ProviderKey.FAKE` on the 7C-1 paths that persist evidence. Switching on Google SDK types is forbidden.

### REST/UI

7C-1 adds **no** `/platforms/google` route and no Frontend page. Tests are Backend-only (adapter, V17 compatibility, compile-path: `com.aicommerce.platform.delivery.domain` and `delivery.application` do not import the Google fake class).

`/platforms/meta` and Stage 4E Playwright stay green.

## 7C-2 — Gated Google FAKE UI (after 7C-1)

Additive same-origin BFF + preview-confirm UI under `/platforms/google`, flag `platform.stage7.google.web.enabled` and Frontend `PLATFORM_STAGE7_GOOGLE_ENABLED`. Copy Stage 4B/4C two-step confirm. Page load stays GET-only. Browser cannot supply customer IDs or origins.

Out of this specification’s first runtime. Specified here so 7C-1 cannot “grow” a UI without a slice.

## Inherited boundaries

1. PostgreSQL remains System of Record. Provider identifiers are external evidence.
2. AI and Decision Engine still have no compile-time path to a platform write or refresh port.
3. Stage 05 `GET /api/dashboard` JSON stays unchanged in 7A/7B/7C-1.
4. Authentication, RBAC, Tenant remain frozen.

## Included in this specification (docs)

- Named 7A/7B/7C-1/7C-2 contracts, flags, class names, V17 allow-list, and proofs.
- Unlock order and human locks for live ads.

## Explicitly excluded

- Java, TypeScript, SQL, flags, or Compose edits in **this** PR.
- Real Google Ads / LINE / TikTok / commercial image / live object-store credentials, SDKs, network, billing, or spend.
- Editing V1–V16 in any Stage 07 slice.
- V17 in 7A or 7B.
- Changing ComfyUI workflow JSON or preservation algorithm.
- Asset upload/download, folder delete/move/rename.
- `/platforms/google` in 7A/7B/7C-1.
- LINE Ads, TikTok Ads, a second live Meta account, or `META_TEST_DELIVERY`.
- Decision Engine auto-execute or execute-on-approve.
- Auth/RBAC/Tenant, production deploy, or System of Record replacement.

## Eligibility and flags (runtime, not this PR)

| Slice | Gate |
| --- | --- |
| 7A secondary image bean | `(local \| test) & !production` and `platform.image.provider=fake-secondary` |
| 7B fake-object storage bean | `(local \| test) & !production` and `platform.storage.provider=fake-object` |
| 7C-1 Google fake adapter | `(local \| test) & !production` and `platform.adapter=fake` and account `provider_key=FAKE_GOOGLE` |
| 7C-2 Google web | plus `platform.web.enabled=true` and `platform.stage7.google.web.enabled=true` |
| Default / production | no secondary beans; no Google Ads client |

## Human review

Docs-only specification: **No**, unless credentials, spend, a second live ads platform, paid provider, or auto-execute appears in the diff.

**Yes** immediately before any live Google Ads / LINE / TikTok / paid image provider.

## Stage gate

- [x] Stage 06 FAKE complete: spec #72, runtime #73, close-out #75 at `771f776`; post-merge `main` CI Run `32923254447` passed.
- [x] Specification Head `cd40cbf` passed exact-head Push Run `32924104147` and Pull Request Run `32924107965` (`quality-and-compose` and `secret-scan`).
- [x] Specification squash-merged: PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) at `2851003`; post-merge `main` CI Run `32928585609` passed.
- [x] 7A runtime squash-merged: PR [#77](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/77) at `eb2618d`; post-merge `main` CI Run `32940348609` passed.
- [x] 7B runtime squash-merged: PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) at `da01b13`; post-merge `main` CI Run `32944155884` passed.
- [x] 7C-1 runtime squash-merged: PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) at `f5faa28`; post-merge `main` CI Run `32971029793` passed.
- [x] 7C-2 runtime squash-merged: PR [#80](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/80) at `ed3f4cf`; post-merge `main` CI Run `32974606066` passed.
- [ ] Stage 07 FAKE close-out Manager `APPROVE` + merge + post-merge `main` CI.

Stage 07 FAKE runtime is closed. Current gate is this docs-only close-out. 7D/7E LINE/TikTok and live Sheets/Drive/Meta Insights stay locked until a separate specification and a recorded human decision. Live ads stay locked.
