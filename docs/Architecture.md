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
透過統一 Platform Adapter 對接 Meta、Google、LINE、TikTok 等平台。

### Decision Engine
讀取標準化成效資料，產生加碼、降預算、停投、換素材等建議。

### Dashboard
提供待辦、審核、投放、成效、異常與 AI 建議工作台。

## Provider Interfaces

- TextProvider
- ImageProvider
- VideoProvider
- StorageProvider
- PlatformAdapter
- AnalyticsProvider

## 建議技術方向

- Backend: Java / Spring Boot
- Frontend: React / Next.js
- Database: PostgreSQL
- Workflow: Temporal 或 n8n（MVP 可先用後端排程）
- Object Storage: Google Drive 起步，後續可抽象化
- AI: LLM API + Provider 層
