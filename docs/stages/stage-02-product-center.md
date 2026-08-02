# Stage 02 — Product Knowledge Center

## 架構決策

- PostgreSQL 是唯一 System of Record。
- Google Sheets 僅作為 Import／Export Connector，不做自動雙向同步。
- Google Drive 僅透過 `StorageProvider` 使用；正式環境優先使用 Shared Drive。
- `product_uuid` 是永久且不可變的內部主鍵。
- `product_id` 由 PostgreSQL Sequence 配發，格式為 `PROD-00000001`，建立後不可修改。
- SKU 可修改且不設 Global Unique，只建立查詢 Index。
- 所有 Mutation 必須透過可信的 `AuditActorProvider` 建立 Audit Log。
- 已合併的 Flyway Migration 不修改；後續 Milestone 只能增加新版本。

## Milestone 2A — Persistence and Audit Foundation

狀態：實作完成，等待人工驗收；尚未 Commit／Push。

### 範圍

- Spring Data JPA 與 Flyway 基礎。
- V1 Product Foundation 與 V2 Audit Foundation。
- Product UUID、timestamps、optimistic locking 與 archive 骨架。
- Product ID Sequence 與格式化配發。
- `AuditActorProvider`、Audit Writer、敏感值遮蔽與截斷。
- Database Trigger 保護 Product identity 與 append-only Audit tables。
- PostgreSQL Testcontainers migration、constraint、locking 與 transaction 測試。
- CI push branch filter 支援 `main` 與 `codex/**`。
- Backend Compose health budget 配合 Flyway/JPA 冷啟動調整，health endpoint 與依賴順序不變。

### Product ID Sequence 保證

Sequence 保證配發值唯一、配發值遞增、`NO CYCLE`，並允許因 rollback 或並行操作產生缺號。Sequence 不保證連號，也不保證 transaction commit 順序。

### Audit 寫入規則

- local/test 使用 server-side `local-admin`。
- SYSTEM flow 使用明確 SYSTEM actor；沒有 HTTP request 時由 server 產生非空 `request_id`，同一 operation 共用相同 context。
- production 未設定可信 Actor Provider 時拒絕 Mutation。
- Browser 不得透過 `X-Actor-ID` 或其他輸入指定 actor。
- `old_value` 與 `new_value` 先依欄位名稱 Redact，再限制為最多 4096 Unicode code points；截斷值以 `[TRUNCATED]` 結尾。
- `audit_logs` 與 `audit_log_changes` 由 PostgreSQL Trigger 強制 append-only。

### 2A Migration

- `V1__create_product_foundation.sql`
  - `product_id_seq`
  - `products`
  - immutable `product_uuid`／`product_id` trigger
- `V2__create_audit_foundation.sql`
  - `audit_logs`
  - `audit_log_changes`
  - `UNIQUE (audit_uuid, change_order)`
  - non-unique index `(audit_uuid, field_name)`
  - append-only triggers

### 2A 驗收項目

- [x] 空 PostgreSQL 可依序套用 V1、V2。
- [x] Hibernate `ddl-auto=validate` 通過。
- [x] Product UUID 與 Product ID 無法透過直接 SQL 修改。
- [x] Product ID 符合格式，並行配發不重複；測試不假設連號或 commit order。
- [x] timestamps、`@Version`、archive 與非唯一 SKU 行為已驗證。
- [x] stale write 觸發 optimistic locking failure。
- [x] Product 與 Audit 位於同一 transaction，Audit constraint failure 時一併 rollback。
- [x] Audit change 以 `(audit_uuid, change_order)` 唯一，同欄位可出現多筆 change。
- [x] `change_order` 為 `NOT NULL`、非負數且符合 `SMALLINT` 範圍。
- [x] Audit values 先 Redact，再執行 4096 字元限制與截斷標示。
- [x] SYSTEM operation 產生並共用非空 `request_id`。
- [x] 直接 SQL 的 UPDATE／DELETE 無法繞過兩張 Audit table 的 append-only Trigger。
- [x] Stage 01 Backend tests 維持通過。
- [x] Frontend 鎖定映像內的 lint、typecheck、test 與 production build 通過。
- [x] Docker Compose 三個服務 healthy，Backend 與同源 proxy 均回傳 `UP`。
- [x] Gitleaks working tree 與 Git history 掃描無洩漏。
- [ ] 人工差異與架構審查。
- [ ] Commit、Push 與 Remote CI。

## 後續 Milestone（本輪未實作）

- 2B：Product CRUD、Knowledge、Creative Plan、Campaign 與 Aggregate API。
- 2C：Quality Score 與 Workflow Status。
- 2D：Google Sheets Import／Export 與 Google Drive `StorageProvider`。
- Frontend Product 管理介面將隨對應 Milestone 實作，不提前建立 Stage 05 Dashboard。

## 明確排除

Milestone 2A 不包含 Product CRUD Controller、Frontend、Knowledge、Creative Plan、Campaign、Asset、Quality Score、Workflow、Google Sheets、Google Drive、AI 素材生成、Meta Ads、Dashboard、Decision Engine 或 Stage 03 以上功能。
