# Project Manager and Stage Gate Policy

## Purpose

本政策定義 Project Manager、Lead Reviewer、Stage Gate Owner 與 Integration Owner 的權限、證據要求與決策邊界。目的不是增加形式，而是確保每個 Stage 只在實際程式碼、測試、安全與 CI 證據一致時進入下一階段。

## Authority

Owner 設定方向。Manager 執行開發節奏。Agent 實作。CI 提供證據。Owner 只在需要人類判斷或授權時介入。

在已核准 Stage／規格範圍內，且無 escalation 時，Manager **不需再等 Owner 確認**即可：

- 建立分支、commit、push。
- 開啟 Draft PR、更新 PR 描述、指派 Agent。
- 審查 Stage 規格、完成報告、Git Diff、Commit、Migration 與測試。
- 執行必要的本機驗證並檢查 GitHub PR／Remote CI。
- 提出 Findings，並要求既有規格內的修正。
- 修正 CI／測試、rebase／sync、處理普通 merge conflict。
- 更新 Stage 文件的驗收狀態。
- 對符合標準的 Stage 做出 `APPROVE`。
- 將 PR 標為 Ready for Review 並 squash-merge。
- 驗證 post-merge CI、關閉已完成 Stage。
- 立刻開下一已授權 Stage，以及已核准的平行 Stage。

前置條件：工作在已核准範圍內、必要測試通過、Required CI 通過、且無 `ESCALATE_TO_HUMAN` 觸發條件。

**不要等待「請確認合併」。**

Manager 不得只根據開發 Agent 的完成報告核准。若 Manager 本身對被審查內容做了功能修正，必須重新執行受影響驗證，並以新的 Head SHA 重新完成 Review。

## Not authority

Manager 仍不得：

- 直接 push 到 `main`。
- 在尚未 squash-merge **且** post-merge CI 通過前宣告 Stage Complete。
- 建立或提交正式環境 Credential，或啟用超出已核准成本邊界的付費 API。
- 繞過 CAPTCHA、MFA、OTP 或 QR 登入。
- 在規格無法裁決時自行改變產品方向。
- 核准或合併 `docs/management/escalation-policy.md` 所列必須升級的變更。

## Automatic merge

Manager 在 **以下全部成立** 時 squash-merge，無需再問 Owner：

- Implementation complete。
- Stage 驗收條件已滿足。
- 必要本機測試通過。
- Required Remote CI 通過。
- Manager Review 無阻擋 Finding。
- 無未解的安全或資料完整性阻擋。
- **本 Stage 已核准範圍**內不需要人類 credential／操作。
- 無超出範圍的產品決策。
- 無法律／合規 escalation。

然後：Draft → Ready for Review → `APPROVE` → squash-merge → post-merge CI → 關閉 Stage → **立刻開下一已授權 Stage**。

缺少正式／live credential **不阻擋**驗收允許 fixture、mock、sandbox 或 FAKE／stub 路徑的 Stage。Live transport 可以維持關閉。

普通 CI 失敗與普通 merge conflict 由 Manager 處理。只依 `docs/management/escalation-policy.md` 升級人工。

## Automatic next stage

Stage Complete 的定義是：squash-merge **且** post-merge CI **且** Stage 完成報告（含該 Stage 要求的 voice overlay）。若無必要人工操作且無 `ESCALATE_TO_HUMAN`，Manager **立刻**開下一已授權 Stage：分支、實作、Draft PR。不要等 Owner 說「繼續」。

下一 Stage 以已核准 roadmap／Stage 文件的 Next 列為準。缺少 live credential 不阻擋後續 FAKE／stub 路徑。

Agent 只能回報 *Implementation Complete — Ready for Manager Review*。只有 Manager 可宣布 **Stage Complete**，且必須在 squash-merge **且** post-merge CI 之後。

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
- 由 Manager 將 PR 標為 Ready 並 squash-merge；不要等待 Owner「請確認合併」。
- 合併後驗證 `main` 與 post-merge CI。
- post-merge CI 通過後關閉 Stage，並立刻開下一已授權 Stage。

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
- 不可用「請確認合併」阻擋已滿足 Automatic merge 條件的 PR。


## Voice progress reporting

Stage Voice Summary 與 Daily Voice Summary 是給 Project Owner 的口語摘要，不是 Gate 本身。產生規則見 `docs/management/voice-reporting.md`。

- 合併與 post-merge CI 之後，依證據產生 Stage Voice Summary。
- `.project/status.json` 只能從 git／PR／CI／Stage Report／Manager Review 產生，不得手改成比證據更樂觀。
- 語音失敗（TTS、通知、排程 artifact）不得用來阻擋產品合併，也不得用來略過本政策的 Gate。
- 本節不新增 Manager Decision 值。

## Current implementation phase

目前採用 Manager-first Gate，不是等待 Owner 按下合併。流程：

1. Developer 建立 Draft PR。
2. Required CI 完整通過。
3. Manager 執行實際 Diff、Migration、測試與 Remote CI Review。
4. Decision 為 `APPROVE` 且 Automatic merge 條件成立時，Manager 將 PR 標為 Ready 並 squash-merge。
5. 驗證 post-merge `main` CI 後關閉 Stage，並立刻開下一已授權 Stage。

`quality-and-compose` 與 `secret-scan` 是現有 CI。Repository 尚未啟用自動 `manager-gate` Required Check 或對應 Branch Protection。不得建立永遠成功、只驗證文字或可由 PR 作者自行繞過的假 Gate。啟用這類自動化前必須另行審查事件來源、權限、fork 行為、approval provenance、Action SHA pinning 與 bypass 管理。
