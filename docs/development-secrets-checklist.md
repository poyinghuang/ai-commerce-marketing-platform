# Development Secrets Checklist

本文件只記錄 Secret 類型與安全處理方式，不記錄任何值。Bootstrap 與一般 CI 不建立、不讀出、不複製 Production credentials。

## 基本規則

- 不把 `.env`、Google credential JSON、SSH private key、PEM、P12、PFX 或 token 加入 Git 或 Docker build context。
- 不在 Issue、PR、Log、Screenshot、Chat 或完成報告貼出 Secret 值。
- 不把 Production Database、AI provider、Google或 GitHub credentials 提供給一般 Developer task／CI。
- Secret 必須由受控 Secret Manager、環境變數或使用者目錄外部 credential store 提供。
- Repository 只提交 `.env.example` 與變數名稱；範例值必須是無效、安全的 placeholder。
- 每次遷移以「重新簽發／重新登入」優先，避免直接複製長效 credentials。

## GitHub

- 建議在新電腦執行 `gh auth login`，以官方 device/browser flow 重新登入。
- 執行 `gh auth status` 只確認狀態，不把 token 顯示或寫入 Repository。
- SSH private key 若非必要，不從舊電腦搬移；在新電腦產生新 key、加入 agent，再由人員在 GitHub 登錄 public key。
- 若必須移轉既有 private key，使用組織核准的加密通道並保留最小檔案權限；不得透過 Repository。

## Google Sheets／Drive

- Live connector 是選填功能；正常 local/test 使用 Mock／Stub，不需要 Production credentials。
- Application Default Credentials 位於使用者設定範圍，不得放進 Repository。
- 新電腦優先透過核准的 `gcloud auth application-default login` 或公司 credential workflow 重新取得授權。
- Service Account JSON 僅在架構與安全政策明確允許時使用，路徑由 `GOOGLE_APPLICATION_CREDENTIALS` 指向 Repository 外檔案。
- `GOOGLE_DRIVE_ROOT_FOLDER_ID` 與選填的 `GOOGLE_SHARED_DRIVE_ID` 是設定值；正式環境仍須由受控 configuration 提供。

## AI Provider／ComfyUI

- Stage 03 正常 local/test 使用 deterministic Stub，不需要 Production AI credentials。
- `COMFYUI_BASE_URL` 只接受受控 server-side 設定；Browser 不得控制 target URL。
- Budget guard 設定 `AI_BUDGET_CURRENCY`、`AI_MAX_JOB_COST`、`AI_MAX_BATCH_COST`、`AI_MAX_DAILY_COST` 必須由人員控制；變更可能影響成本，不由 Bootstrap 自動修改。
- 不傳送 Secret、Customer PII、Order 或 Payment 資料給 AI provider。

## Database

- Local PostgreSQL 由 Compose 建立，使用開發用途 defaults。
- 不把 Production connection string、password、dump 或 snapshot 搬入新電腦。
- 驗證腳本使用隔離 Compose project，且不執行 volume deletion 或 reset。

## 移轉前後檢查

舊電腦：

- 執行 `git status --short`，確認沒有未追蹤的 `.env`／credential／key 檔被誤加入。
- 記錄需要重新登入的服務名稱，不記錄 Secret 值。
- 撤銷不再需要的舊 device token 或 key。

新電腦：

- Clone 後先執行 Bootstrap 與 Verify；不要先複製 Secret。
- 只為實際需要的選填功能設定 credentials。
- 執行 `git status --short`、Gitleaks 與 Docker build context 檢查。
- 完成後確認 Secret 檔仍位於 Repository 外，且 `.env` 保持 ignored。
