# Development Rules

1. 每個 Stage 必須獨立開 Branch。
2. 每個功能必須有自動化測試。
3. 所有外部 API 必須有 Mock。
4. AI Agent 不得直接呼叫廣告平台寫入 API。
5. 所有金鑰使用環境變數或 Secret Manager。
6. Prompt 不可散落在程式碼中。
7. 每個 PR 必須更新對應文件。
8. 每個 Stage 必須通過 QA 與驗收清單。
9. 測試環境與正式環境必須分離。
10. 所有自動化操作必須留下 Audit Log。
11. 每個 Stage／Milestone 必須依 `docs/management/manager-policy.md` 完成 Manager Review；只有 `Manager Decision: APPROVE` 才可合併。
12. 符合 `docs/management/escalation-policy.md` 的安全、資料、權限、正式環境或重大產品決策，必須先取得人工核准。
