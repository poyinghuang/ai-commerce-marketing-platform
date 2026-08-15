# Development Environment Migration

Audit date：2026-08-15

Target：全新 Windows 11 電腦

Repository：AI Commerce Marketing Platform

## Readiness summary

Repository 已具備精確 Runtime pin、Maven Wrapper、npm lockfile、Docker Compose、Testcontainers 與 Playwright。新增的 Bootstrap／Verify 流程負責把這些宣告轉成可重複執行的檢查。2026-08-15 的 Windows 11 完整驗證結果為 **READY WITH WARNINGS**；任何後續 `[FAIL]` 或未完成的核心 `[SKIP]` 都不得沿用此判定。

## 舊電腦環境盤點

| 項目 | 實際狀態 | Repository 要求 | 判定 |
|---|---|---|---|
| Node.js | `24.18.0` | `24.18.0` | 符合 |
| npm | `11.16.0` | `11.16.0` | 符合 |
| Java/Javac | Temurin `21.0.9+10` | Temurin `21.0.9+10` | 符合 |
| Maven | Wrapper `3.9.11` | Wrapper `3.9.11` | 符合 |
| Docker Engine | `29.6.1`，Linux Engine reachable | 可用 Linux Engine | 符合 |
| Docker Compose | `v5.1.4` | Compose plugin | 符合 |
| GitHub CLI | 已設定 authentication | 建立 PR 時需要 | 新電腦重新登入 |
| Google ADC | 未發現 | Live Connector 才需要 | 不搬移；需要時重新授權 |
| Repository `.env` | 未發現 | 選填、必須 ignored | 不需搬移 |
| SSH private key | 未發現 | 選填 | 新電腦需要時重建 |
| Host PostgreSQL | 已安裝但專案不依賴 | Compose PostgreSQL `17.6` | 不搬移 |

## 由 Git 自動攜帶

- `.java-version`、`frontend/.nvmrc`、`package.json` engines。
- Maven Wrapper 與 npm lockfile。
- Dockerfiles、Compose、health checks 與 CI workflow。
- `.editorconfig`、`.gitattributes`、ignore 規則。
- Migration、Testcontainers、Frontend tests 與 Playwright E2E。
- `.env.example`（只含安全模板，不含 Secret）。

## 必須由人員在新電腦處理

- Windows／WSL 功能、Docker Desktop 安裝、UAC、重開機與 GUI 初始設定。
- GitHub CLI browser/device login。
- 實際需要 Live Google connector 時，重新取得 ADC 或依公司規範配置 Service Account。
- 實際需要 SSH 時，產生並登錄新的 key。
- 公司 Proxy、VPN、Certificate、Endpoint Security 與套件來源政策。
- 任何 Production access、Secret、付費 AI provider 或 budget configuration。

## 不應搬移

- `node_modules`、`.next`、`target`、coverage、Playwright report、test results。
- Docker containers、images、volumes 與 local Database data。
- npm／Maven caches；它們可重建。
- 舊電腦的長效 token、過期 credential、Production dump 或完整使用者設定目錄。
- Repository 內不存在且目前功能不需要的 Python virtualenv、Redis、Worker 或 Queue 設定。

## 新 Windows 11 電腦重建程序

1. 依 [Development Setup](development-setup.md) 安裝精確版本的 Git、Node/npm、Temurin JDK 與 Docker Desktop。
2. 重新開啟 PowerShell，確認 `node --version`、`npm --version`、`java -version`、`javac -version`、`docker version` 與 `docker compose version`。
3. Clone Repository；不要複製舊工作目錄。
4. 在 Repository 根目錄執行 `.\scripts\bootstrap.ps1`，可連續執行三次確認冪等性。
5. 執行 `.\scripts\verify-env.ps1`，確認 Backend、Frontend、Migration、Compose、health、Playwright、audit 與 Gitleaks。
6. 只在需要時依 [Development Secrets Checklist](development-secrets-checklist.md) 重新登入或配置選填 credentials。
7. 執行 `git status --short`，確認工作樹沒有 Secret 或 build output。

## 回復與失敗策略

- Bootstrap 失敗：修正提示的單一系統前置條件後重跑；不需清除資料。
- Dependency 安裝失敗：保留 lockfile，修正網路／Proxy 後重跑 `npm ci` 或 Maven Wrapper；不得改成未鎖定安裝。
- Compose 驗證失敗：保存 logs，執行不含 `--volumes` 的 `docker compose --project-name ai-commerce-bootstrap-verify down`；不得 prune 或 reset 其他 project。
- E2E 失敗：保留測試輸出並修正原因，不可降低 Gate 或直接標為 Passed。
- Secret 疑慮：停止 push，撤銷／rotate credential，確認 Git history 與 Gitleaks，再由安全流程處理。

## 2026-08-15 實際驗證

| 驗證 | 結果 |
|---|---|
| PowerShell Bootstrap 連續三次 | Passed |
| Git Bash Bootstrap | Passed |
| 精確 Node/npm/JDK/Maven Wrapper | Passed |
| Backend／Testcontainers／Migration／Hibernate | 288 tests passed |
| Frontend lint／typecheck | Passed |
| Frontend unit/component tests | 22 files、134 tests passed |
| Frontend production build | Passed |
| Production dependency audit | 0 vulnerabilities |
| Docker Compose build／cold start | Passed，三個 services healthy |
| Actuator／same-origin health proxy | Passed |
| Playwright real-Compose E2E | 14 tests passed |
| Gitleaks history／worktree | No leaks found |
| actionlint 1.7.7 | Official checksum verified、Passed |
| Git diff check | Passed |

首次完整 Verify 的 Vitest fork worker曾發生一次 startup timeout；同一 suite 隨後獨立重跑與第二次完整 Gate 均通過。其他非阻斷輸出包含 npm `allow-scripts` 對 `unrs-resolver` 的待審提示、Mockito／Byte Buddy future deprecation、Testcontainers lifecycle 結束時的 Hikari connection warning、Surefire shutdown timeout，以及 Next.js build telemetry notice。這些 warning 不等同 Passed，需保留在後續 technical debt；目前沒有 Blocking Finding。

Linux／WSL 的 shell script 已通過語法檢查，且 Git Bash Bootstrap 實際通過；本次主機沒有 Linux distro，因此 Linux 原生 full Verify 仍由 Remote CI／Linux 環境負責，不宣稱已在本機 Linux 驗證。
