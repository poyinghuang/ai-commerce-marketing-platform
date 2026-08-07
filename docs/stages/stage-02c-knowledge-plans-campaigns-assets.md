# Milestone 2C — Knowledge, Plans, Campaigns and Assets

## 文件狀態

- Status：Acceptance in progress
- Branch：completed through slice branches; acceptance branch `codex/2c-8-acceptance`
- Implementation：Passed — 2C-1 through 2C-7 merged
- Migration：Passed — additive V4 merged and immutable
- Specification Commit：`274e5fafa992434c638a4935f55af28d23542e63`
- Manager-reviewed Head：`ebc46b2988b6a606cbe2865b18be0b587e308ccc`
- Local Verification：Passed
- Remote CI：Passed — final acceptance Push Run `31195470219`；PR Run `31195478641`
- Manager Review：Passed
- Manager Decision：APPROVE
- Human Review Required：No
- Merge：Pending Milestone 2C-8 approved acceptance delivery
- Implementation Commit／Push／PR：Passed for 2C-1 through 2C-7
- Milestone 2D：Not started

本文件固定 Milestone 2C 的範圍、契約、測試與驗收條件。2C-1 至 2C-7 已依此規格交付；目前只進行 2C-8 最終驗收與文件收尾，在 Remote CI、Manager Review、Merge、post-merge verification 與 completion tag 完成前不得開始 Milestone 2D。

## 目標

在既有 Product Master Vertical Slice 上，提供可追蹤、可並行更新且可由商品詳情頁操作的 Product Knowledge、Creative Plan、Campaign Plan、Campaign Product 與 Asset Metadata。所有正式資料仍以 PostgreSQL 為唯一 System of Record，並可由 Aggregate API 依 `product_uuid` 取得完整視圖。

## 明確範圍

- 一個 Product 可有多筆 Product Knowledge。
- 一個 Product 可有多筆 Creative Plan。
- 一個 Campaign Plan 可關聯多個 Product；一個 Product 可參與多個 Campaign Plan。
- 一個 Product 可有多筆 Asset Metadata，並可選擇關聯 Creative Plan 或已指派該 Product 的 Campaign Plan。
- 所有新增、更新、封存與還原操作均使用可信 `AuditActorProvider`，並與 Audit Log 位於同一 Transaction。
- 所有可修改的 2C Resource 均使用獨立 `version`、`ETag` 與 `If-Match`。
- Product Detail 增加 Knowledge、Creative Plans、Campaigns、Assets Tabs。
- 提供 Campaign 基本管理頁面，支援 Campaign 與 Product 關聯管理。
- 提供 `GET /api/products/{productUuid}/aggregate`。

## 明確排除

- Quality Score、Blocking Reasons、Manual Adjustment 與 Workflow Status；這些屬於 2D。
- Google Sheets Import／Export、Google Drive API、Folder 建立、檔案上傳、下載與同步；這些屬於 2E。
- AI 素材生成、Prompt、LLM 評分或建議。
- Meta Ads、平台刊登、廣告同步、投放與成效資料。
- 店鋪、訂單、價格歷史、庫存同步、平台 Listing 與數位資產 Binary 儲存。
- 新的登入、角色或 RBAC 系統。
- Dashboard、Decision Engine 與 Stage 03 以上功能。

## 正式架構原則

1. PostgreSQL 是唯一 System of Record；2C 不使用 Sheet 或 Drive 保存正式資料。
2. 所有關聯使用永久內部 UUID；SKU、名稱與外部 ID 不作為主鍵。
3. V1、V2、V3 不得修改。2C 使用新增的 V4 Flyway Migration。
4. Entity 不直接綁定 HTTP Request；Create DTO 與 field-presence-safe Merge Patch 必須明確列出可寫欄位。
5. 所有 Domain Mutation 與 Audit 必須在同一 Transaction；失敗、stale、blocked 或 no-op 操作不得留下 Audit。
6. 不 Hard Delete。Knowledge、Plan、Campaign、Campaign Product 與 Asset Metadata 均使用 Archive／Restore。
7. Product 被封存後，既有 2C 資料仍可唯讀，但不得新增或修改其 Knowledge、Creative Plan、Campaign Product 或 Asset Metadata。還原 Product 後才可繼續 Mutation。
8. Archive Product 不連鎖封存子資料，避免破壞歷史關聯。
9. Archive Campaign 不連鎖封存 Product association 或 Asset Metadata，但會阻止其 association mutation 與新的 Asset 關聯，直到 Campaign 還原。
10. Browser 只呼叫 Next.js 同源 Route Handler；Backend origin 只能由 server-side `BACKEND_INTERNAL_URL` 決定。
11. Asset 只保存 provider-neutral metadata。2C 不建立 Drive Folder、Drive-specific Product 欄位或呼叫任何 Google API。

## V4 Migration 設計

建議檔名：

`V4__create_knowledge_plans_campaigns_assets.sql`

V4 應在同一 PostgreSQL transaction 中建立以下資料表、Constraints、Indexes 與 DB immutability protection。所有時間使用 `TIMESTAMP WITH TIME ZONE`，所有 enum-like 欄位使用字串與 Check Constraint，不使用資料庫 ordinal。

### 共通欄位

每個 2C Entity 包含：

- `<entity>_uuid UUID PRIMARY KEY`
- `lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'`
- `archived_at TIMESTAMP WITH TIME ZONE`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `version BIGINT NOT NULL DEFAULT 0`
- `ACTIVE` 時 `archived_at IS NULL`；`ARCHIVED` 時 `archived_at IS NOT NULL`

UUID、Product 關聯及建立後不可重新指派的 Identity 欄位，必須由 PostgreSQL Trigger 防止直接 SQL 修改。所有 Foreign Key 使用 `ON DELETE RESTRICT`。

### `product_knowledge`

| 欄位 | 型別 | 規則 |
| --- | --- | --- |
| `knowledge_uuid` | UUID | Primary Key |
| `product_uuid` | UUID | FK → `products`，不可修改 |
| `knowledge_type` | varchar(32) | `FEATURE`、`BENEFIT`、`AUDIENCE`、`PAIN_POINT`、`FAQ`、`PROOF`、`OTHER` |
| `title` | varchar(256) | 必填、trim 後不可空白 |
| `content` | varchar(20000) | 必填、trim 後不可空白 |
| `source` | varchar(2048) | 選填；人工來源描述或 URL，不自動抓取 |
| 共通欄位 |  | Archive、timestamps、version |

Indexes：`(product_uuid, lifecycle_status, updated_at DESC)`、`(product_uuid, knowledge_type)`。

### `creative_plans`

| 欄位 | 型別 | 規則 |
| --- | --- | --- |
| `creative_plan_uuid` | UUID | Primary Key |
| `product_uuid` | UUID | FK → `products`，不可修改 |
| `plan_name` | varchar(256) | 必填、不可空白 |
| `primary_audience` | varchar(2000) | 選填 |
| `secondary_audience` | varchar(2000) | 選填 |
| `pain_point` | varchar(4000) | 選填 |
| `core_benefit` | varchar(4000) | 選填 |
| `creative_angle` | varchar(4000) | 選填 |
| `emotional_direction` | varchar(1000) | 選填 |
| `brand_tone` | varchar(1000) | 選填 |
| `visual_style` | varchar(2000) | 選填 |
| `main_color` | varchar(128) | 選填描述值，不在 2C 解析色碼 |
| `character_setting` | varchar(2000) | 選填 |
| `cta` | varchar(1000) | 選填 |
| 共通欄位 |  | Archive、timestamps、version |

Index：`(product_uuid, lifecycle_status, updated_at DESC)`。2C 不加入 AI-generated、approved_by、approved_at 或 Quality 欄位。

### `campaign_plans`

| 欄位 | 型別 | 規則 |
| --- | --- | --- |
| `campaign_uuid` | UUID | Primary Key；API 使用此 UUID |
| `campaign_name` | varchar(256) | 必填、不可空白 |
| `activity_type` | varchar(64) | 選填 |
| `start_date` | date | 選填 |
| `end_date` | date | 選填；不得早於 `start_date` |
| `objective` | varchar(2000) | 選填 |
| `platform` | varchar(64) | 選填描述值；不觸發 Platform Adapter |
| `budget_daily` | numeric(19,4) | 選填且不得小於 0 |
| `budget_total` | numeric(19,4) | 選填且不得小於 0 |
| `currency` | varchar(3) | 有任一 budget 時必填，格式為三碼大寫 |
| `promotion` | varchar(2000) | 選填 |
| `landing_page` | varchar(2048) | 選填 |
| 共通欄位 |  | Archive、timestamps、version |

Indexes：`(lifecycle_status, updated_at DESC)`、`(start_date, end_date)`、`LOWER(campaign_name)`。

### `campaign_products`

| 欄位 | 型別 | 規則 |
| --- | --- | --- |
| `campaign_product_uuid` | UUID | Primary Key |
| `campaign_uuid` | UUID | FK → `campaign_plans`，不可修改 |
| `product_uuid` | UUID | FK → `products`，不可修改 |
| `role` | varchar(128) | 選填 |
| `priority` | integer | 選填且不得小於 0 |
| `budget_weight` | numeric(5,2) | 選填，範圍 `0.00..100.00` |
| 共通欄位 |  | Archive、timestamps、version |

使用 `UNIQUE (campaign_uuid, product_uuid)`。若同一組關聯已封存，新的 Create 必須回傳 conflict，Client 取得該 association 的 ETag 後使用 Restore endpoint；不得無視 concurrency 自動還原或建立第二筆。Indexes：`(product_uuid, lifecycle_status)`、`(campaign_uuid, lifecycle_status)`。

### `assets`

| 欄位 | 型別 | 規則 |
| --- | --- | --- |
| `asset_uuid` | UUID | Primary Key |
| `product_uuid` | UUID | FK → `products`，不可修改 |
| `creative_plan_uuid` | UUID | 選填；若存在，必須屬於同一 Product |
| `campaign_uuid` | UUID | 選填；若存在，Product 必須已關聯該 Campaign |
| `asset_type` | varchar(32) | `IMAGE`、`VIDEO`、`DOCUMENT`、`OTHER` |
| `purpose` | varchar(256) | 選填 |
| `storage_provider` | varchar(64) | 選填 provider key；2C 不呼叫 Provider |
| `provider_file_id` | varchar(512) | 選填 opaque identifier，不限定 Google Drive |
| `file_url` | varchar(2048) | 選填 metadata，不由 Backend 抓取 URL |
| `media_type` | varchar(255) | 選填 MIME type |
| `original_filename` | varchar(512) | 選填 |
| `size_bytes` | bigint | 選填且不得小於 0 |
| `checksum_sha256` | varchar(64) | 選填，若存在必須為小寫十六進位 SHA-256 |
| `provider_metadata` | jsonb | 選填，只存 provider-specific metadata，不存 Secret 或 Token |
| 共通欄位 |  | Archive、timestamps、version |

Indexes：`(product_uuid, lifecycle_status, updated_at DESC)`、`creative_plan_uuid`、`campaign_uuid`、`(storage_provider, provider_file_id)`。`provider_metadata` 不建立通用 GIN Index，除非未來有明確查詢案例。

`provider_metadata` 若存在必須為 JSON object，序列化後最多 16 KiB；Application 必須遞迴拒絕名稱包含 token、secret、password、authorization、cookie 或 credential 的 key。Asset 的 `(creative_plan_uuid, product_uuid)` 必須由 composite FK 證明 Creative Plan 屬於同一 Product；`(campaign_uuid, product_uuid)` 必須由 composite FK 指向既有 Campaign Product association。Application 另需確認被引用的 Product、Creative Plan、Campaign 與 association 皆為 ACTIVE。

### Audit Constraint 擴充

V4 只能透過 drop/recreate Check Constraint 的方式擴充 `audit_log_changes.value_type`，保留既有值並新增：

- `DECIMAL`
- `INTEGER`
- `DATE`

V1–V3 不得修改。Migration Compatibility Test 應新增 V3 canonical checksum，且仍只將 CRLF 正規化為 LF。

## Domain 與 Transaction 邊界

- `KnowledgeCommandService`：Knowledge Create、Patch、Archive、Restore。
- `CreativePlanCommandService`：Creative Plan Create、Patch、Archive、Restore。
- `CampaignCommandService`：Campaign Create、Patch、Archive、Restore，以及 Product association Create、Patch、Archive、Restore。
- `AssetCommandService`：Asset Metadata Create、Patch、Archive、Restore。
- `ProductAggregateQueryService`：唯讀組合 Product 與 2C collections，不執行 Mutation。
- Query Service 使用明確 Repository／Specification，不把 JPA Entity 回傳至 Web layer。
- 每個 Command 只建立一個 `AuditOperationContext`，並在同一 Transaction 中保存 Domain 與 Audit。
- 跨 Entity 驗證（Product active、Creative Plan 所屬 Product、Campaign Product 關聯）必須在鎖定／版本檢查與 Mutation 的同一 Transaction 中完成。

## REST API Contract

### 共通規則

- Create：`201 Created`、`Location`、`ETag: W/"0"`。
- Single GET／successful Patch／Restore：回傳目前 Resource ETag。
- Patch：`application/merge-patch+json`；缺失欄位不變、顯式 `null` 依欄位必填規則清除或拒絕、未知或 immutable 欄位拒絕。
- Patch／Archive／Restore：要求目前 Resource 的 `If-Match`；缺少回傳 428，格式錯誤回傳 400，stale 回傳 412。
- Archive：`204 No Content` 與最新 ETag；Restore：`200 OK` 與 Resource body。
- 對已在目標狀態且 ETag 正確的 Archive／Restore 為 idempotent no-op，不增加 version、不建立 Audit。
- Collection 預設只回傳 ACTIVE；支援 `status=ACTIVE|ARCHIVED|ALL`、page、size 與 sort allowlist；`size` 最大 100，排序固定追加 UUID secondary ordering。
- Archived Product 的 Aggregate 可讀；其子 Resource Mutation 回傳 409 `PRODUCT_ARCHIVED`。
- Response 使用 camelCase；UUID 欄位不可由 Client 指定。

### Product Knowledge

- `POST /api/products/{productUuid}/knowledge`
- `GET /api/products/{productUuid}/knowledge`
- `GET /api/products/{productUuid}/knowledge/{knowledgeUuid}`
- `PATCH /api/products/{productUuid}/knowledge/{knowledgeUuid}`
- `DELETE /api/products/{productUuid}/knowledge/{knowledgeUuid}`
- `POST /api/products/{productUuid}/knowledge/{knowledgeUuid}/restore`

Create／Patch 可寫欄位：`knowledgeType`、`title`、`content`、`source`。

### Creative Plans

- `POST /api/products/{productUuid}/creative-plans`
- `GET /api/products/{productUuid}/creative-plans`
- `GET /api/products/{productUuid}/creative-plans/{creativePlanUuid}`
- `PATCH /api/products/{productUuid}/creative-plans/{creativePlanUuid}`
- `DELETE /api/products/{productUuid}/creative-plans/{creativePlanUuid}`
- `POST /api/products/{productUuid}/creative-plans/{creativePlanUuid}/restore`

Create／Patch 可寫欄位為 V4 Creative Plan 業務欄位；Product、identity、version、timestamps 與 archive 欄位不可寫。

### Campaign Plans 與 Product Association

- `POST /api/campaigns`
- `GET /api/campaigns`
- `GET /api/campaigns/{campaignUuid}`
- `PATCH /api/campaigns/{campaignUuid}`
- `DELETE /api/campaigns/{campaignUuid}`
- `POST /api/campaigns/{campaignUuid}/restore`
- `POST /api/campaigns/{campaignUuid}/products`
- `GET /api/campaigns/{campaignUuid}/products`
- `GET /api/campaigns/{campaignUuid}/products/{productUuid}`
- `PATCH /api/campaigns/{campaignUuid}/products/{productUuid}`
- `DELETE /api/campaigns/{campaignUuid}/products/{productUuid}`
- `POST /api/campaigns/{campaignUuid}/products/{productUuid}/restore`

Create association body：`productUuid`、`role`、`priority`、`budgetWeight`。後續 Patch 不得改變 Product 或 Campaign identity。

### Asset Metadata

- `POST /api/products/{productUuid}/assets`
- `GET /api/products/{productUuid}/assets`
- `GET /api/products/{productUuid}/assets/{assetUuid}`
- `PATCH /api/products/{productUuid}/assets/{assetUuid}`
- `DELETE /api/products/{productUuid}/assets/{assetUuid}`
- `POST /api/products/{productUuid}/assets/{assetUuid}/restore`

2C 只接受 JSON metadata，不接受 multipart upload、remote URL fetch、signed URL 或 Provider credentials。

`fileUrl` 與 `landingPage` 若存在只接受 `http`／`https` URI。Backend 不連線至該 URL；Frontend 外部連結必須使用安全 scheme 與 `rel="noopener noreferrer"`。

### Aggregate

`GET /api/products/{productUuid}/aggregate?includeArchived=false`

Response：

- `product`
- `knowledge[]`
- `creativePlans[]`
- `campaigns[]`，包含 association metadata
- `assets[]`

每個 Resource 都包含自己的 `version` 與 lifecycle fields。Aggregate 不使用單一 mutation ETag，因為它組合多個獨立 Aggregate Member；UI Mutation 必須使用目標 Resource 的 ETag。`includeArchived=true` 僅允許 server-defined boolean，不接受任意 include expression。

### 既有 Product API 相容性

- 2B 的六個 Product endpoint、request/response body、ETag 與錯誤語意保持不變。
- Product Response 不內嵌 2C collection，避免破壞 list/detail contract 與 N+1 query。
- 需要完整視圖時明確呼叫 Aggregate endpoint。

## Error Codes

沿用既有 `ApiError`：`code`、`message`、`requestId`、`timestamp`、`path`、選填 `fieldErrors`。不得回傳 stack trace。

新增或泛化下列錯誤：

| HTTP | Code | 使用情境 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 欄位、filter、sort、日期、金額或關聯驗證失敗 |
| 400 | `INVALID_MERGE_PATCH` | 非 object、未知欄位、immutable 欄位或 null 語意非法 |
| 400 | `INVALID_IF_MATCH` | ETag 格式非法 |
| 404 | `KNOWLEDGE_NOT_FOUND` | Knowledge 不存在或不屬於 path Product |
| 404 | `CREATIVE_PLAN_NOT_FOUND` | Creative Plan 不存在或不屬於 path Product |
| 404 | `CAMPAIGN_NOT_FOUND` | Campaign 不存在 |
| 404 | `CAMPAIGN_PRODUCT_NOT_FOUND` | Association 不存在 |
| 404 | `ASSET_NOT_FOUND` | Asset 不存在或不屬於 path Product |
| 409 | `PRODUCT_ARCHIVED` | 對 archived Product 執行 2C Mutation |
| 409 | `RESOURCE_ARCHIVED` | 對 archived 2C Resource 執行一般 Patch |
| 409 | `RELATIONSHIP_CONFLICT` | Asset／Campaign／Product 關聯不一致或重複 Active association |
| 428 | `PRECONDITION_REQUIRED` | Mutation 缺少 `If-Match` |
| 412 | `PRECONDITION_FAILED` | 目標 Resource version 已改變 |
| 503 | `AUDIT_ACTOR_UNAVAILABLE` | 無可信 actor 時 fail closed |

## Audit 設計

- `entity_type`：`PRODUCT_KNOWLEDGE`、`CREATIVE_PLAN`、`CAMPAIGN_PLAN`、`CAMPAIGN_PRODUCT`、`ASSET`。
- `action` 沿用 `CREATE`、`UPDATE`、`ARCHIVE`、`RESTORE`。
- `product_uuid`：Knowledge、Creative Plan、Campaign Product、Asset 必填；Campaign Plan 本身可為 null。
- Campaign Product Audit 同時記錄 `campaignUuid` 與 `productUuid`。
- 只記錄實際改變欄位；空 Patch 與 idempotent no-op 不建立 Event。
- Secret、Token、Cookie、Authorization、完整 provider metadata 與不受信任的大型 content 不得直接進 Audit。
- `content` 等大型文字仍先 Redact，再使用既有 4096 字元截斷規則。
- Budget 使用 `DECIMAL`、priority／size 使用 `INTEGER`、日期使用 `DATE`；URL、provider ID 與 checksum 使用 `STRING`。
- Mutation rollback 時 Domain 與 Audit 必須一起 rollback。

## Frontend Routes 與操作流程

### Product Detail Tabs

`/products/[productUuid]` 保留 Product Master，並新增：

- `?tab=knowledge`
- `?tab=creative-plans`
- `?tab=campaigns`
- `?tab=assets`

Tab 狀態使用 allowlist；未知值回到 Product Master。每個 Tab 必須提供 Loading、Empty、Error、Create、Edit、Archive、Restore 與 stale-version conflict reload。Archived Product 顯示唯讀資料與清楚的 mutation-disabled 原因。

### Campaign Routes

- `/campaigns`
- `/campaigns/new`
- `/campaigns/[campaignUuid]`

Campaign detail 管理 Campaign fields 與 Product associations。Product lookup 使用既有受限 Product API，不新增任意 autocomplete proxy。

### BFF

- 擴充固定 route allowlist；不得改成可接受任意 backend path 或 URL 的通用 Proxy。
- Query、method、request headers 與 response headers 均使用 endpoint-specific allowlist。
- 不轉送 Cookie、Authorization 或 hop-by-hop headers。
- 轉送 `ETag`、`If-Match`、`Location`、`Content-Type`、`X-Request-ID` 與原始 Backend status/body。
- 維持 body size、timeout、safe request ID 與 server-only Backend origin 限制。
- Aggregate 的 `includeArchived` 只接受 `true|false`。

## 角色與權限決策

2C 不建立 authentication 或 RBAC。local/test 仍使用 server-side `local-admin`，SYSTEM flow 使用明確 SYSTEM actor，production 若沒有可信 Actor Provider 則所有 Mutation fail closed。Browser 不得提供 `X-Actor-ID`。真正的使用者角色、授權與 Tenant boundary 必須在獨立安全 Milestone 設計後才能加入。

## Playwright E2E

2C 必須納入最小 Playwright E2E，並以 Docker Compose 的真實 Backend／PostgreSQL 執行，不 Mock Domain API：

1. 建立 Product，新增 Knowledge 與 Creative Plan，建立 Campaign 並關聯 Product，新增 Asset Metadata，最後在 Aggregate／Tabs 看見資料。
2. 兩個頁面使用 stale ETag 更新同一 Knowledge，第二個操作顯示 412 並可重新載入。
3. 封存 Product 後 Tabs 唯讀、子 Resource Mutation 被 409 阻擋；還原後可繼續操作。
4. Archive／Restore 子 Resource 後，預設 collection 隱藏／恢復且不產生重複 no-op Audit。

Playwright package、Browser runtime 與任何 container image 必須鎖定版本；CI 必須執行上述 E2E 並保留失敗時的非敏感 trace／screenshot。E2E 不取代 Frontend component tests、BFF tests 或 Compose API smoke。

## 測試矩陣

### Migration／Database

- 空庫執行 V1→V2→V3→V4；重跑無 Pending。
- 有 2B Product、Audit 與 Archive 資料時升級 V4，既有資料與 version 不變。
- V1–V3 canonical checksum 固定且只正規化 CRLF→LF。
- Hibernate `ddl-auto=validate` 通過。
- 每個 Check Constraint、FK、unique pair、budget weight、日期與非負值均有直接 JDBC 測試。
- 直接 SQL 證明 UUID／owner identity 不可修改。
- `spring.flyway.clean-disabled=true` 保持啟用。

### Backend Unit／Integration

- 每個 Create／Patch／Archive／Restore happy path。
- Merge Patch 缺失、null、未知、immutable、空 Patch 與 validation rollback。
- 428、invalid ETag、412、409 archived、404 owner mismatch。
- Optimistic locking concurrency 與 idempotent no-op。
- Campaign many-to-many、duplicate association、restore 與 budget weight boundary。
- Asset Creative Plan／Campaign ownership consistency。
- Aggregate active/default 與 includeArchived response，避免 N+1 regression。
- 每個 Mutation 的 Audit action、entity、actor、request ID、actual changes、redaction 與 transaction rollback。
- failed／stale／blocked／no-op operation 不產生 Audit。

### Frontend／BFF

- Route allowlist、query allowlist、header boundary、timeout、body size 與 arbitrary URL rejection。
- 各 Tab Loading、Empty、Error、Create、Edit、Archive、Restore。
- 409／412／428 對應可理解且可恢復的 UI。
- Campaign association 與 Aggregate rendering。
- lint、typecheck、Vitest 與 production build。

### Compose／CI／Security

- Docker Compose cold start，PostgreSQL／Backend／Frontend healthy。
- Product 2B regression 與 2C vertical-slice smoke。
- Backend Testcontainers、Frontend、Compose、actionlint、Gitleaks、npm audit 全部執行，不能被 path filter 跳過。
- 固定 Node、Playwright package 與 Browser image/version，CI 產物不得包含 Secret 或未遮罩 payload。

## 回滾策略

- PostgreSQL 的 V4 DDL 必須可交易；Migration 失敗時整筆 rollback，不留下半套 schema。
- 已合併或已部署的 Migration 不做 down migration、不修改 V4；任何修正只能新增 V5+ forward migration。
- 回滾 Application 時，2B 版本會忽略新增資料表；V4 不改變既有 Product endpoint 或 Product columns，因此 2B Runtime 可繼續讀寫 Product Master。
- Frontend 可獨立回滾至 2B，Backend 新 endpoint 保持未使用。
- 若需移除 2C 資料，必須先備份並由獨立核准的 forward cleanup migration 執行，不使用 Flyway clean。
- 新 API 與 UI 必須保持 additive；不得要求 Product Client 同步升級。

## 任務與依賴拆解

1. **2C-1 Schema and Domain**：核准 V4、Entity、enum、Repository、constraints、checksum 與 migration tests。
2. **2C-2 Knowledge Vertical Slice**：Knowledge API、Audit、BFF、Product Detail Tab。
3. **2C-3 Creative Plan Vertical Slice**：Creative Plan API、Audit、BFF、Tab。
4. **2C-4 Campaign Vertical Slice**：Campaign API、Product association、Audit、Campaign UI 與 Product Tab。
5. **2C-5 Asset Metadata Vertical Slice**：provider-neutral metadata API、ownership validation、Audit、BFF、Tab。
6. **2C-6 Aggregate and Integration**：Aggregate API、query performance、Compose smoke、完整 regression。
7. **2C-7 Browser E2E**：加入鎖定版本的 Playwright、真實 Compose flow 與 CI。
8. **2C-8 Acceptance**：文件、Remote CI、人工差異與架構審查、Merge 與 completion tag。

依賴順序：2C-1 → 各 Vertical Slice；Knowledge／Creative Plan 可在 Schema 完成後獨立進行，Campaign 必須先於帶 Campaign 關聯的 Asset 驗證，Aggregate 與 E2E 最後整合。

## 驗收清單

- [x] 規格與所有決策項目經人工核准。
- [x] V1–V3 未修改；V4 可由空庫與 2B 資料庫安全升級。
- [x] Hibernate validate、Migration checksum 與 repeat migration 通過。
- [x] 同一 Product 可建立多筆 Knowledge 與 Creative Plan。
- [x] 同一 Product 可參與多個 Campaign；同一 Campaign 可關聯多個 Product。
- [x] Asset Metadata 可關聯正確的 Product、Creative Plan 與 Campaign Product。
- [x] 所有 2C Resource 支援 Read、Create、field-safe Patch、Archive、Restore 與 optimistic concurrency。
- [x] Archive／Restore no-op 不增加 version、不建立 Audit。
- [x] Archived Product 的 2C 資料可讀但不可 Mutation。
- [x] Aggregate 以 `product_uuid` 回傳完整且可控制 archived inclusion 的視圖。
- [x] 既有 2B Product API Contract 與 UI regression 通過。
- [x] 所有成功 Mutation 具可信 actor、同 Transaction Audit 與實際欄位差異。
- [x] failed、stale、blocked 與 no-op operation 沒有 Audit。
- [x] BFF 無 SSRF、任意 path、Cookie／Authorization 洩漏或未限制 header forwarding。
- [x] Product Detail Tabs 與 Campaign UI 具 Loading、Empty、Error、Archived 與 Version Conflict states。
- [x] Backend、Frontend、Testcontainers、Compose smoke、Gitleaks、actionlint、npm audit 與已合併 slice 的 Remote CI 全部通過。
- [x] 四個最小 Playwright E2E scenario 全部通過。
- [x] 無 Google API、AI、Quality、Workflow、Meta Ads、Dashboard、Decision Engine 或 Stage 03 功能。
- [x] Milestone 2C-8 的 Remote CI 與最終 Manager Review 通過；等待合併、post-merge CI 與 completion tag。

## 已核准決策

1. **Approved** — V4 一次建立五個 2C tables，並擴充 Audit value types 為 `DECIMAL`、`INTEGER`、`DATE`。
2. **Approved** — 所有 2C Resource 使用獨立 ETag／If-Match 與 Archive／Restore；Aggregate 不作為 Mutation concurrency token。
3. **Approved** — Product archive 不 cascade，但會阻止所有子 Resource Mutation。
4. **Approved** — Campaign 對外 route 使用 `/api/campaigns`，資料表仍命名為 `campaign_plans`。
5. **Approved** — 新增 `/campaigns` 管理頁面，而不只在 Product Detail Tab 操作。
6. **Approved** — Asset 僅保存 provider-neutral metadata；Google Drive Folder／API 延後到 2E。
7. **Approved** — Playwright、固定 Browser runtime 與四個最小 E2E scenario 納入 2C-7 必要驗收。
8. **Approved** — 2C 不新增 authentication／RBAC，production mutation 仍依可信 Actor Provider fail closed。
9. **Approved** — 分支已改名為 `codex/stage-02-knowledge-plans`，符合現有 `codex/**` Push CI filter；不修改 Workflow 支援 `feat/**`。
