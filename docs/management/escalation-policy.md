# Stage Gate Escalation Policy

## Decision

符合本政策任一條件時，Manager Decision 必須為：

`ESCALATE_TO_HUMAN`

人工核准前不得 Merge、執行正式操作、降低驗收標準或啟動依賴該 Stage 的後續工作。

## Mandatory escalation triggers

### Data and Migration

- 修改已合併的 Flyway Migration。
- Drop table、column、constraint 或其他破壞性 schema 變更。
- 可能造成不可逆資料遺失、資料重寫或大規模 backfill。
- 正式資料 Migration、Flyway clean 或 production database operation。
- 無法證明 rollback／forward recovery，且失敗會影響既有資料。

### Security and access

- Authentication、RBAC、Tenant、authorization 或 trust boundary 變更。
- Secret、Token、Credential、Production Access 或 Secret Manager 變更。
- Critical Security Finding、權限繞過或敏感資料暴露。
- 需要提高 GitHub Workflow permissions、使用高權限 PAT 或向不受信任 PR 暴露 Secret。

### Product, architecture and contract

- System of Record 變更。
- 已核准 API Contract 的破壞性修改。
- 規格互相衝突或需要跨 Milestone 擴大 Scope。
- 有多個合理方案且選擇會影響產品、商業行為或使用者資料。
- 新增付費服務、Billing／付款或可能造成顯著成本。

### Production and repository integrity

- 正式環境部署、正式第三方 Credential 或 production traffic change。
- Force Push、重寫 `main` 歷史或刪除正式 Tag。
- CI 無法通過，且唯一修正方式是降低驗收標準或 required check。
- 完成報告與實際修改明顯不一致，無法建立可信交付證據。

## Required escalation record

Escalation 必須記錄：

- Stage／PR／Base SHA／Head SHA。
- Trigger category 與具體問題。
- 受影響資料、使用者、環境與 Contract。
- 已確認的事實與尚未驗證的假設。
- 可行方案、trade-off 與 Manager 建議。
- 在人工決策前被凍結的操作。
- 人工決策者、日期與決策證據連結。

不得把選項包裝成已批准決策，也不得在等待期間偷偷實作其中一個方案。

## After human decision

- `Approved`：把核准範圍、限制與證據寫回 Stage 文件，建立新的可驗證 Head，重新執行受影響 Gate。
- `Rejected`：停止該變更並保留決策紀錄。
- `Needs revision`：更新規格後重新進入 Stage Review；不得沿用舊的 Manager Approval。
