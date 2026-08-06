# Repository Agent Instructions

本文件適用於整個 Repository。任何較深層的 `AGENTS.md` 只能補充其目錄內的工作方式，不得降低本文件的 Stage Gate、測試、安全或人工升級要求。

## Delivery model

- 每個 Stage／Milestone 必須有核准規格、獨立分支、可驗收範圍與明確排除項目。
- PostgreSQL、API、安全與外部 Provider 邊界必須遵守已核准的 Architecture 與 Stage 文件。
- 已合併的 Flyway Migration 不得修改；只能新增版本。
- 開發完成不等於可合併。只有 Manager Decision 為 `APPROVE`，且必要人工核准已完成時，才可合併。
- 不得因時間、工具限制或既有實作而自行降低驗收標準。

## Required reading

開始實作或審查前，至少閱讀：

1. 本文件。
2. `README.md`。
3. 適用的 Architecture、Data Model、Development Rules 與安全文件。
4. 適用的 Stage／Milestone 規格。
5. 前置 Stage 的完成與驗收紀錄。
6. 若有 Pull Request：PR 描述、實際 Diff、Commit、Remote CI、Migration 與 API Contract。

## Project Manager and Stage Gate Owner

Project Manager 同時擔任 Lead Reviewer、Stage Gate Owner 與 Integration Owner。主要職責是驗證交付證據、列出 Findings、做出 Gate Decision，以及在全部 Gate 通過後管理合併與下一 Stage。

Manager 不得只依賴開發者或 Agent 的文字報告。必須檢查實際程式碼、Diff、Migration、測試與 Remote CI；無法執行的驗證必須標記為未驗證。

Manager Decision 只能是：

- `APPROVE`
- `REQUEST_CHANGES`
- `ESCALATE_TO_HUMAN`

完整 Authority、Review Process、Severity、Report 與 Gate 規則見：

- `docs/management/manager-policy.md`
- `docs/management/escalation-policy.md`
- `docs/management/stage-gate-template.md`

## Mandatory boundaries

以下情況不得由 Agent 自行核准或合併，必須 `ESCALATE_TO_HUMAN`：

- 破壞性 Migration、Drop、不可逆資料變更或正式資料 Migration。
- Authentication、RBAC、Tenant、權限或 Security Model 變更。
- Secret、Credential、Production Access 或正式環境部署。
- Billing、付款、付費服務或顯著成本。
- System of Record 變更。
- 已核准 API Contract 的破壞性修改。
- 跨 Milestone 擴大 Scope、規格衝突或重大產品決策。
- Critical Security Finding。
- Force Push、重寫 `main` 歷史或刪除正式 Tag。

## Verification baseline

依變更範圍執行所有適用檢查，至少包含 Git 狀態與差異檢查，並依 Stage 規格包含 Backend、Frontend、Migration、Hibernate、Compose、Smoke、Playwright、Gitleaks、dependency audit 與 actionlint。

未執行的測試不得標記為 Passed。Warning 必須如實記錄，不得當作 Passed 或隱藏。

## Current manager-gate phase

目前採用人工 Manager Gate：

1. Developer 建立 Draft PR。
2. Required CI 完整通過。
3. 手動啟動 Manager Review。
4. Manager 更新 Review Report、PR 與 Stage 文件。
5. Manager Decision 為 `APPROVE` 後才允許合併。

Repository 目前尚未啟用自動 `manager-gate` Required Check 或對應 Branch Protection。不得建立永遠成功、只驗證文字或可由 PR 作者自行繞過的假 Gate。
