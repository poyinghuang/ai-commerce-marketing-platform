# Product Requirements Document

## 1. 產品名稱

AI Commerce Marketing Platform

## 2. 產品定位

協助電商品牌將商品資料、Creative Plan、素材生成、廣告投放、成效回收與優化建議整合至同一套平台。

## 3. 核心使用者

- 電商品牌經營者
- 廣告投手
- 行銷企劃
- 素材製作團隊
- 開發與營運團隊

## 4. 核心流程

商品建立 → 商品知識補充 → Creative Plan → 素材生成 → 人工審核 → 廣告平台建立 → 成效回收 → AI 建議 → 人工批准 → 後端執行

## 5. MVP 範圍

- Google Sheet 商品資料中心
- Google Drive 商品素材目錄
- Creative Plan 與 Campaign Plan
- AI 文字素材生成
- 圖片／影片 Provider 介面
- Meta Ads Adapter
- 基礎 Workflow
- 成效資料回收
- Dashboard
- 建議型 Decision Engine

## 6. 安全原則

AI 不直接操作廣告平台。AI 只能產生建議或結構化指令，真正的執行必須由 Backend 驗證後完成。

## 7. 非功能需求

- 所有流程可追蹤
- 所有 Prompt 有版本
- 所有平台與模型皆可替換
- 測試與正式環境分離
- Token 與金鑰不得寫入 Repository
