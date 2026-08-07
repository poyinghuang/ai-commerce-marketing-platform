# Stage 02 — Product Knowledge Center

## 架構決策

- PostgreSQL 是唯一的 System of Record。
- Google Sheets 僅作為 Import／Export Connector，不做自動雙向同步。
- Google Drive 僅透過 `StorageProvider` 使用；正式環境優先使用 Shared Drive。
- `product_uuid` 是永久且不可變的內部主鍵。
- `product_id` 由 PostgreSQL Sequence 產生，格式為 `PROD-00000001`，建立後不可修改。
- Sheet 新增商品時，UUID 與 Product ID 留空並由系統產生；更新時優先使用 UUID，其次使用 Product ID。
- SKU 不設 Global Unique，只建立查詢 Index。
- 所有寫入操作必須建立 Audit Log。
- Google API 必須透過 Adapter／Provider 隔離，測試使用 Mock／Stub。
- 已合併的 Flyway Migration 不得修改，只能新增版本。

## Milestone 2A — Persistence and Audit Foundation

### 驗收狀態

- Implementation：Passed
- Local verification：Passed
- Remote CI：Passed
- Human diff and architecture review：Passed
- Merge：Passed

### Commits

- `fa221c4687c63b831e6dbe19ae46a9fff9833b04`
- `d9fde3bb8a6ec9eaac37860eab837332e529f60d`
- `d3f6da72158416814a0ef95fdb7b3b698721961e`

### Remote CI

- Push Run `30731526761`：Passed
- PR Run `30731527681`：Passed

### Merge

- Squash Merge Commit：`c697a4d0e8f2ef219cb1ac7488f6c97e62f3ecec`

### 範圍

- Spring Data JPA 與 Flyway 基礎。
- Product 最小持久化骨架。
- UUID、timestamps、`@Version` 與 Archive 基礎。
- `AuditActorProvider`。
- Audit Log／Audit Change append-only schema。
- PostgreSQL Sequence 與 `product_id` 產生機制。
- PostgreSQL Testcontainers Migration／Constraint／Locking／Transaction 測試。
- Hibernate `ddl-auto=validate`。
- CI 支援 `codex/**` Stage Branch。

### Product ID Sequence 保證

- Sequence 只保證配發值唯一、遞增、`NO CYCLE`，並允許缺號。
- Sequence 不保證 Transaction Commit 順序。
- Concurrent Sequence Test 不要求連號或 Commit Order。

### Audit 寫入規則

- HTTP Mutation 的 actor 只能由可信的 `AuditActorProvider` 提供，不接受 Browser 傳入 `X-Actor-ID`。
- `local`／`test` 使用 server-side `local-admin`；`production` 與未啟用 local/test 的 default profile 使用拒絕 Mutation 的 Provider。
- `production,local` 與 `production,test` 組合仍必須 fail closed。
- SYSTEM flow 必須使用明確的 SYSTEM actor，並由 Server 產生非空 `request_id`；同一 Operation 內保持一致。
- Audit 寫入必須與業務 Mutation 位於同一 Transaction，任一失敗時一起 Rollback。
- `audit_logs` 與 `audit_log_changes` 由資料庫 Trigger 強制 append-only。
- `old_value` 與 `new_value` 寫入前先 Redact，再限制為最多 4096 字元；超出時加上 `[TRUNCATED]`。
- 不得記錄 Token、Cookie、Authorization Header、密碼或其他敏感值。

### Migration

- `V1__create_product_foundation.sql`
  - 建立 Product ID Sequence。
  - 建立 `products` 最小資料表與必要 Index。
  - 以 Trigger 保護 `product_uuid` 與 `product_id` 不可變。
- `V2__create_audit_foundation.sql`
  - 建立 `audit_logs` 與 `audit_log_changes`。
  - `audit_logs.action` 限制為 `CREATE`、`UPDATE`、`ARCHIVE`、`RESTORE`。
  - `audit_log_changes.value_type` 限制為 `STRING`、`UUID`、`ENUM`、`TIMESTAMP`。
  - `change_order` 為 `NOT NULL`，且必須大於等於零。
  - 使用 `unique(audit_uuid, change_order)`，並為 `(audit_uuid, field_name)` 建立普通 Index。
  - 以 Trigger 禁止 Audit Log 與 Change 的 UPDATE／DELETE。

### 驗收清單

- [x] Flyway 可由空 PostgreSQL 完整執行。
- [x] 重複啟動時沒有 Pending Migration。
- [x] Hibernate Schema Validation 通過。
- [x] Product UUID、Product ID 與 Archive 基礎行為通過整合測試。
- [x] Optimistic Locking 可阻止 stale update。
- [x] Concurrent Sequence Test 證明 Product ID 唯一且遞增，不假設無缺號或 Commit Order。
- [x] Audit 與業務 Mutation 在同一 Transaction Rollback。
- [x] Redaction 與 4096 字元截斷規則通過測試。
- [x] 直接 SQL／JDBC 測試證明 Product immutable 與 Audit append-only Trigger 生效。
- [x] 直接 SQL／JDBC 測試證明非法 Audit action 與 value type 會被 PostgreSQL 拒絕。
- [x] Profile 組合測試涵蓋 local、test、default、production、production/local 與 production/test。
- [x] `spring.flyway.clean-disabled=true` 已明確驗證。
- [x] Migration Scope 測試證明後續 Milestone 資料表尚未建立。
- [x] Backend、Frontend、Docker Compose、Actionlint 與 Gitleaks Regression 通過。
- [x] 人工差異與架構審查。
- [x] Commit、Push 與 Remote CI。
- [x] Merge。

## Stage 02 Milestones

### Milestone 2B — Product Master Vertical Slice

#### 驗收狀態

- Implementation：Complete
- Local verification：Passed
- Commit：Passed
- Push：Passed
- Remote CI：Passed
- Human diff and architecture review：Passed
- Merge：Passed
- Milestone 2C：Completed

#### Commits

- Implementation：`d294a66f3b752dc5f0f519295ad099728f73902c`
- Cross-platform migration checksum fix／final implementation head：`dc94387a6d2bb2943e1cdb8d80880e7d252c9cf3`
- Acceptance documentation：`7756d38d76a093d3f2cbd17b280e64e4f81125bf`

#### Remote CI

- Push Run `30931678353`：`quality-and-compose`、`secret-scan` Passed。
- Pull Request Run `30931686761`：`quality-and-compose`、`secret-scan` Passed。
- Post-merge Run `31013063296`：`quality-and-compose`、`secret-scan` Passed。

#### Merge

- Merge Commit：`9d2f9bd023ff8de39f23af8265fecdeac6cf0aeb`
- Completion Tag：`milestone-2b-complete`

#### 範圍

- V3 Product Master Migration。
- Product CRUD、Archive／Restore。
- List／Search／Filter／Sort／Pagination。
- `ETag`／`If-Match` Optimistic Concurrency。
- JSON Merge Patch 欄位存在性安全更新。
- Product mutation 與 Audit 同一 Transaction。
- Next.js 固定目的地 Product BFF。
- Product List、Create、Detail、Edit、Archive、Restore UI。
- Loading／Empty／Error／Version Conflict states。

#### API

- `POST /api/products`
- `GET /api/products`
- `GET /api/products/{productUuid}`
- `PATCH /api/products/{productUuid}`
- `DELETE /api/products/{productUuid}`
- `POST /api/products/{productUuid}/restore`

#### Migration

- `V3__add_product_master_fields.sql` 只增加 Product Master 欄位、Constraints 與查詢 Index。
- `product_name` 在資料庫保持 nullable，以允許既有 2A Product 無損升級；一般 Create API 強制 `productName` 必填。
- V1、V2 由固定 SHA-256 測試保護，未被修改。
- Checksum 僅將 CRLF 正規化為 LF；其他空白與 SQL 內容差異仍會造成測試失敗。
- 空資料庫可執行 V1→V2→V3；既有 V2 Product 可升級到 V3。

#### Concurrency 與 Idempotency

- Single Product Response 使用 `W/"<version>"`。
- PATCH、Archive、Restore 缺少 `If-Match` 回傳 428，stale version 回傳 412。
- Archive／Restore 已達目標狀態且 ETag 為目前版本時不增加 version、不建立 Audit。
- Archived Product 不接受一般 Patch。

#### Local verification

- Backend：49 tests passed；包含 Testcontainers、V1→V2→V3、V2 legacy upgrade、Hibernate validate、constraints、optimistic locking 與 audit transaction。
- Frontend：lint、typecheck、12 tests 與 production build passed。
- Docker Compose：cold build/start passed；PostgreSQL、Backend 與 Frontend 均為 healthy。
- Product vertical-slice smoke：Create、Get、List、Patch、Archive、Restore、428、412、archived mutation rejection 與 idempotent archive passed。
- Audit smoke：CREATE、UPDATE、ARCHIVE、RESTORE 各一筆；stale、blocked 與 idempotent no-op 未產生 Audit Event。
- `docker compose config`、actionlint 與 Gitleaks history/worktree scan passed。

#### Known limitations

- Byte Buddy dynamic Java agent future deprecation warning，非阻斷。
- GitHub Actions Node.js 20 compatibility deprecation annotation；目前 Runner 強制使用 Node.js 24。
- Windows `core.autocrlf` 產生 LF／CRLF 資訊性警告；`git diff --check` 通過。
- 尚未加入 Playwright E2E；目前由 component tests、production build 與 Compose API smoke 覆蓋。
- Windows Docker cold build 約八分鐘。

#### 2B 驗收清單

- [x] Product Master 欄位與資料庫 Constraints。
- [x] 空庫與 V2 legacy upgrade Migration 測試。
- [x] V1、V2 checksum immutability 測試。
- [x] Product Create、Read、Patch、Archive、Restore。
- [x] Pagination、Status／Category／SKU／Product ID Filter、Keyword Search 與 Sort Allowlist。
- [x] ETag／If-Match 428、412 與 Optimistic Locking。
- [x] Mutation 與 Audit 同 Transaction，只記錄實際差異。
- [x] Archive／Restore idempotent no-op 不建立 Audit。
- [x] 同源 Product BFF，沒有任意 URL Proxy 或 Browser Docker hostname。
- [x] Product UI 與 Loading／Empty／Error／Conflict states。
- [x] Backend Testcontainers tests。
- [x] Frontend lint、typecheck、tests 與 production build。
- [x] Docker Compose cold start 與 Product vertical-slice smoke test。
- [x] Gitleaks 與 actionlint。
- [x] Remote CI。
- [x] 人工差異與架構審查。
- [x] Merge。

### Milestone 2C — Knowledge, Plans, Campaigns and Assets

- Status：Completed
- Implementation：Passed
- Local Verification：Passed
- Remote CI：Passed — final acceptance Push Run `31195470219`；PR Run `31195478641`
- Manager Review：Passed；Manager Decision `APPROVE` for acceptance Head `ebc46b2988b6a606cbe2865b18be0b587e308ccc`
- Merge：Passed — final acceptance PR #20, Commit `dc703d9e50cfa583c675b0286e35beba2c39bc57`
- Post-merge CI：Passed — main Run `31196506563`
- Completion Tag：`milestone-2c-complete`
- Product Knowledge
- Creative Plans
- Campaign Plans
- Campaign Products
- Asset Metadata
- Aggregate API
- Detail Tabs
- 詳細規格與驗收條件：[Milestone 2C — Knowledge, Plans, Campaigns and Assets](stage-02c-knowledge-plans-campaigns-assets.md)
- 最終驗收報告：[Milestone 2C-8 — Acceptance and Delivery](stage-02c-8-acceptance.md)
- Acceptance Branch：`codex/2c-8-acceptance`

### Milestone 2D — Quality and Workflow

- Status：2D-1 completed; 2D-2 Manager approved, merge pending
- Branch：`codex/2d-2-recalculation-api`
- Specification Merge：`6ba92bdc48a50f61448ee347b89939f961bdb5e4`
- 2D-1 Merge：`c897bb6f6f3847e62fea9b6d334400349c87e3b0`; post-merge Run `31203178454` passed
- 2D-2 Implementation：`f3cdb3386584fe182ea8c3f2dabc3ffdb07ac44f`; Push Run `31207893627` and PR Run `31207911328` passed; Manager Decision `APPROVE`; merge pending
- Detailed specification：[Milestone 2D — Quality and Workflow](stage-02d-quality-workflow.md)
- 2D-1 delivery record：[Milestone 2D-1 — Schema and Scoring Domain](stage-02d-1-schema-scoring.md)
- 2D-2 delivery record：[Milestone 2D-2 — Recalculation, API, Audit, and Aggregate](stage-02d-2-recalculation-api.md)
- Deterministic Quality Score
- Blocking Reasons
- Manual Adjustment
- Product Readiness Workflow
- Quality UI

### Milestone 2E — Google Connectors and Final Integration

- Google Sheets Preview／Execute／Upsert
- Google Drive `StorageProvider`
- Connector UI
- Full Stage 02 integration and acceptance

## 明確排除

Milestone 2A 不包含 Product CRUD API、Product Frontend、Product Knowledge、Creative Plans、Campaigns、Assets、Quality Score、Workflow、Google Sheets、Google Drive、AI 素材生成、Meta Ads、Dashboard、Decision Engine 或 Stage 03 以上功能。
