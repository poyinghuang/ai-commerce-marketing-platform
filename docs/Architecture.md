# System Architecture

## 架構原則

- Domain 與外部平台解耦
- Provider / Adapter Pattern
- AI 只輸出建議，不直接執行外部操作
- 所有 Workflow 可重試、可追蹤、可稽核
- Prompt 與商業規則集中管理

## 主要模組

### Product Knowledge Center
管理商品主檔、知識、Creative Plan、Campaign Plan、素材與完整度。

### AI Creative Factory
包含 Creative Planner、Copywriter、Image Agent、Video Agent 與 QA Agent。

### Marketing Platform Engine
透過統一 Platform Adapter 對接 Meta、Google、LINE、TikTok 等平台。Stage 04 已交付 Meta 的 FAKE LOCAL/TEST 切片。Stage 07 FAKE 已用同一組 ports 證明可擴充：第二個 FAKE Image / Storage bean，以及 `FAKE_GOOGLE` adapter（additive V17）與 gated `/platforms/google` UI。Stage 08A 已把 opt-in LOCAL live Sheets `values.get` 接到既有 `SheetValuesProvider`。Stage 08B 已把 opt-in LOCAL live Drive folder ensure 接到既有 `StorageProvider`。目前 Gate 是 8C：LOCAL 可用 `platform.stage8.insights.live=true` 把 Meta Graph 讀取接到既有 `PlatformDeliveryReadPort` / `PlatformMetricsReadPort`；CI 預設仍是 stub/FAKE。Live Google Ads / LINE / TikTok、production credential 與 spend 仍要人工核准。

### Decision Engine
讀取標準化成效資料，產生加碼、降預算、停投、換素材等建議。AI 只輸出建議，不直接執行外部操作。Stage 06 FAKE 切片是確定性規則引擎（`RULE_SET_V1`），建議需含原因、數據與風險，並由人工批准或拒絕；批准在該切片只寫決策紀錄，不呼叫平台寫入或刷新 Port。Dashboard 的「AI 建議」仍是 Stage 03 素材審核，與 Decision Engine「優化建議」分開。Frequency／受眾疲乏延後，直到成效快照有獨立 Frequency 欄位。

### Dashboard
提供待辦、審核、投放、成效、異常與 AI 建議工作台。

Stage 05 已交付該工作台，只讀既有 Product Quality、Campaign Plan、Stage 03 審核佇列與 Stage 04 FAKE 投放／成效。此處的「AI 建議」是既有素材審核（approve/reject），不是 Stage 06 Decision Engine。

## Provider Interfaces

- TextProvider
- ImageProvider（Stage 03 ComfyUI + stub；Stage 07A 增加第二個 FAKE bean，不改上游 workflow）
- VideoProvider
- StorageProvider（Stage 02E Drive stub；Stage 07B 增加第二個 FAKE bean；Stage 08B 可 opt-in 既有 Google Drive bean）
- PlatformAdapter（Stage 04 Meta FAKE；Stage 07C 增加 `FAKE_GOOGLE`，不改 Domain）
- AnalyticsProvider（Stage 08C 把 Meta Insights 接到既有 `PlatformMetricsReadPort` / `PlatformDeliveryReadPort`）
- SheetValuesProvider（Stage 02E stub；Stage 08A 可 opt-in 既有 Google Sheets `values.get` bean）

## 建議技術方向

- Backend: Java / Spring Boot
- Frontend: React / Next.js
- Database: PostgreSQL
- Workflow: Temporal 或 n8n（MVP 可先用後端排程）
- Object Storage: Google Drive 起步，後續可抽象化
- AI: LLM API + Provider 層
