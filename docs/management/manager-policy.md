# Project Manager and Stage Gate Policy

## Purpose

本政策定義 Project Manager、Lead Reviewer、Stage Gate Owner 與 Integration Owner 的權限、證據要求與決策邊界。目的不是增加形式，而是確保每個 Stage 只在實際程式碼、測試、安全與 CI 證據一致時進入下一階段。

## Authority

在不涉及重大風險、產品決策或人工核准項目時，Manager 可以：

- 審查 Stage 規格、完成報告、Git Diff、Commit、Migration 與測試。
- 執行必要的本機驗證並檢查 GitHub PR／Remote CI。
- 提出 Findings 與要求既有規格內的修正。
- 更新 Stage 文件的驗收狀態。
- 對符合標準的 Stage 做出 `APPROVE`。
- 在 Repository Policy 允許且所有 Gate 通過時合併 PR。
- 合併後驗證 `main`，並允許建立下一 Stage。

Manager 不得只根據開發 Agent 的完成報告核准。若 Manager 本身對被審查內容做了功能修正，必須重新執行受影響驗證，並以新的 Head SHA 重新完成 Review。

## Required reading before review

1. 根目錄 `AGENTS.md`。
2. `README.md`。
3. 適用的 Architecture、Data Model、Development Rules 與安全文件。
4. 適用的 Stage 規格與前置 Stage 完成文件。
5. PR 描述、Base／Head SHA、Commit list 與 Files changed。
6. Remote CI 結果與實際 Job／step。
7. Migration、API Contract、Frontend／BFF Contract 與完成報告。

缺少必要輸入時，不得假設其內容或宣稱 Review 完成。

## Review process

每個 Stage／Milestone 依序執行：

1. 確認 Repository、Branch、Base Commit、Head Commit 與 PR。
2. 確認本機工作樹、upstream 與遠端狀態。
3. 比對核准範圍、排除項目與實際修改檔案。
4. 確認沒有修改禁止檔案或已合併 Migration。
5. 檢查完整 Git Diff 與 Commit history。
6. 檢查 Migration compatibility、constraints、rollback 與 data impact。
7. 檢查 Domain、Transaction、Audit、Concurrency 與 Lifecycle boundary。
8. 檢查 API、Frontend、BFF 與 backward compatibility contract。
9. 檢查 Secret、SSRF、header、credential、logging 與 production boundary。
10. 執行規格要求的本機測試。
11. 檢查 Remote CI，確認必要 Job 沒有被 filter 或 condition 跳過。
12. 比對完成報告與實際結果。
13. 列出 Findings、Known limitations、Contract／Security／Data impact。
14. 做出唯一 Stage Gate Decision。

## Finding severity

| Severity | 定義 | 是否阻擋 |
| --- | --- | --- |
| `CRITICAL` | 安全、資料遺失、權限繞過或正式環境風險 | 必須阻擋並升級人工 |
| `BLOCKING` | 不符合規格、測試失敗、Contract 或 Migration 錯誤 | 阻擋 |
| `MAJOR` | 可能造成明顯故障、維護風險或必要測試不足 | 必要時阻擋 |
| `MINOR` | 非阻斷改善 | 不阻擋 |
| `NOTE` | 資訊與後續建議 | 不阻擋 |

不得用個人風格偏好建立 Blocking Finding。

## Gate decisions

### APPROVE

只有以下條件全部成立才可使用：

- 必要本機測試與 Remote CI 全部通過。
- 無 `CRITICAL`、`BLOCKING` 或未處理的必要 `MAJOR` Finding。
- 無未核准 Scope Change 或破壞性 Contract 變更。
- 無已合併 Migration 修改、資料遺失或 Secret 洩漏。
- 無跳過必要驗收。
- 完成報告與實際結果一致。
- 工作樹乾淨，PR 為目前核准 Head 且可安全合併。
- 所有 Human Review／Approval requirement 已滿足。

`APPROVE` 後必須：

- 更新 Stage 文件為 `Manager Review: Passed` 與 `Manager Decision: APPROVE`。
- 記錄 Approved Commit SHA、CI Run、實際驗證命令與 Known limitations。
- 更新 PR 描述或 Manager Review comment。
- 允許合併；合併後驗證 `main` 與 post-merge CI。
- 只有合併完成後才允許依賴該 Stage 的下一階段。

### REQUEST_CHANGES

當問題可在既有核准規格內修正時使用。每個阻擋 Finding 必須提供：

- Severity。
- 受影響檔案與精確證據。
- 預期修正。
- 必須新增或修改的測試。
- 修正後重新驗證命令。

修正後必須以新的 Head SHA 重新檢查相關 Diff、測試與 CI。

### ESCALATE_TO_HUMAN

符合 `docs/management/escalation-policy.md` 任一條件時使用。人工決策前不得 Merge、降低標準或啟動依賴該 Stage 的後續工作。

## Automatic approval boundary

Manager 可直接核准：

- 已核准範圍內的 additive implementation 或 additive Migration。
- API／UI Vertical Slice。
- 測試、文件、CI 或跨平台測試修正。
- 無破壞性 Contract 的重構。
- 符合既有 Security Boundary 的 BFF 修改。

Manager 不可自行核准：

- 破壞性 Migration 或正式資料操作。
- Production deployment、Authentication、RBAC、Tenant 或 Security Model。
- Billing／付款、正式第三方 Credential 或顯著成本。
- System of Record 或已核准需求的重大改變。

## Verification requirements

至少執行所有適用項目：

```text
git status --short
git diff --check
git log --oneline --decorate -10
```

並依 Stage 規格執行：

- Backend tests。
- Frontend lint、typecheck、tests 與 production build。
- Flyway migration tests 與 Hibernate validation。
- Docker Compose config、cold start 與 smoke tests。
- Playwright E2E。
- Gitleaks、dependency audit 與 actionlint。

每一項必須記錄 `Passed`、`Failed` 或 `Not verified`。無法執行的項目不得假設通過。

## Manager review report

每次正式 Review 使用 `docs/management/stage-gate-template.md`，至少包含：

- Stage、Branch、Base／Head Commit、PR。
- Scope／Files／Migration reviewed。
- Tests executed 與 Remote CI。
- Findings 與 Known limitations。
- Contract、Security、Data impact。
- Decision、Required next action 與 Human approval requirement。

## Stage status fields

尚未開始時：

```text
Implementation: Not started
Local Verification: Not started
Remote CI: Not started
Manager Review: Not started
Manager Decision: Pending
Human Review Required: No
Merge: Not started
```

核准時：

```text
Manager Review: Passed
Manager Decision: APPROVE
Human Review Required: No
Approved Commit: <SHA>
Approved CI Run: <RUN_ID>
```

若需要人工核准，`Human Review Required` 必須為 `Yes`，並記錄原因與決策證據。

## Integrity rules

- 不可宣稱未執行的測試通過。
- 不可隱藏 failure、warning、skipped Job 或 incomplete evidence。
- 不可降低驗收標準來取得綠燈。
- 不可修改原始需求以配合現有實作。
- 不可合併有 Blocking Finding 的 PR。
- 不可在人工核准完成前繼續依賴工作。

## Voice progress reporting

Stage Voice Summary 與 Daily Voice Summary 是給 Project Owner 的口語摘要，不是 Gate 本身。產生規則見 `docs/management/voice-reporting.md`。

- 合併與 post-merge CI 之後，依證據產生 Stage Voice Summary。
- `.project/status.json` 只能從 git／PR／CI／Stage Report／Manager Review 產生，不得手改成比證據更樂觀。
- 語音失敗（TTS、通知、排程 artifact）不得用來阻擋產品合併，也不得用來略過本政策的 Gate。
- 本節不新增 Manager Decision 值。

## Current implementation phase

目前使用人工 Manager Review。`quality-and-compose` 與 `secret-scan` 是現有 CI；尚未啟用自動 `manager-gate` Required Check 或 Branch Protection。自動化前必須另行審查事件來源、權限、fork 行為、approval provenance、Action SHA pinning 與 bypass 管理。
