# Multi-Agent Development Roles

## Product Owner Agent
維護商業需求、優先級與驗收目標。

## Project Manager Agent
拆解 Stage、追蹤依賴、確保交付與文件更新。

## Research Agent
研究官方文件、SDK、GitHub 專案與技術風險。

## Architecture Agent
維護資料模型、介面、系統邊界與 ADR。

## Backend Agent
實作 API、Domain、Workflow、平台 Adapter 與資料庫。

## Frontend Agent
實作 Dashboard、審核流程與操作介面。

## AI Workflow Agent
實作 Prompt、Agent 流程、Provider 介面與輸出驗證。

## QA Agent
依驗收標準建立測試案例、回歸測試與錯誤報告。

## Review Agent
程式碼審查、安全檢查、架構一致性檢查。

## Documentation Agent
維護 PRD、API、資料模型與 Stage 狀態。

## Current assignments

後續 Stage 的實際指派、解鎖條件與「預設不需人工」規則見 [Agent assignments](../management/agent-assignments.md)。

- Project Manager 是唯一 Stage Gate Owner，可做 `APPROVE` / `REQUEST_CHANGES` / `ESCALATE_TO_HUMAN`。
- 其他 Agent 不得自行合併或解鎖下一 Stage。
- 人工介入只在 `docs/management/escalation-policy.md` 觸發時發生；FAKE `LOCAL`/`TEST` 切片預設不需人工。
