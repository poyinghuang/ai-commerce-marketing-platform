# Development Setup

本文件是 AI Commerce Marketing Platform 的可搬移開發環境入口。所有版本以 Repository 內的 pin 為準；Bootstrap 不會安裝系統套件、要求管理員權限、建立 Secret 或執行破壞性 Docker／Database 操作。

## 支援環境

- Windows 11：PowerShell 5.1 以上、Docker Desktop Linux containers。
- Linux／WSL2：POSIX shell、可連線的 Docker Engine。Ubuntu 是建議發行版，但不是 Repository 的隱性依賴。
- macOS：shell 腳本預期可用，但目前主要驗證平台是 Windows 11 與 Linux CI。

## 必要工具與固定版本

| 工具 | 必要版本 | 版本來源 | 是否需要全域安裝 |
|---|---:|---|---|
| Git | 支援目前 GitHub workflow 的版本 | 作業系統套件 | 是 |
| Node.js | `24.18.0` | `frontend/.nvmrc`、`frontend/package.json` | 是，建議用 Volta 或其他版本管理器 |
| npm | `11.16.0` | `frontend/package.json` | 是，需與 Node pin 同時符合 |
| Java | Temurin `21.0.9+10` | `.java-version`、CI | 是，完整 JDK |
| Maven | `3.9.11` | Maven Wrapper | 否，使用 `backend/mvnw.cmd` 或 `backend/mvnw` |
| Docker | 可執行 Linux containers | Docker Desktop／Engine | 是 |
| Docker Compose | Compose plugin | `docker compose` | 是 |
| Chromium | `@playwright/test` 對應版本 | `frontend/package-lock.json` | 否，由 Bootstrap 安裝 |

專案不需要全域 Maven、Python、Gradle、Redis、Host PostgreSQL、Worker 或 Queue。PostgreSQL `17.6` 由 Docker Compose 提供；Backend integration tests 使用 Testcontainers。

## Windows 11 首次設定

需要 UAC、WSL2、重開機、Docker Desktop GUI 或系統 PATH 的動作必須由人員執行。以下是建議流程，安裝前仍應確認套件來源與公司政策：

```powershell
winget install --source winget --id Git.Git --exact
winget install --source winget --id EclipseAdoptium.Temurin.21.JDK --exact --version 21.0.9.10
winget install --source winget --id Docker.DockerDesktop --exact
```

使用 Volta 安裝精確 Node/npm：

```powershell
volta install node@24.18.0
volta install npm@11.16.0
```

重新開啟 PowerShell，啟動 Docker Desktop並等待 Linux Engine ready，然後：

```powershell
git clone <repository-url>
cd ai-commerce-marketing-platform
.\scripts\bootstrap.ps1
.\scripts\verify-env.ps1
```

## Linux／WSL 首次設定

先依發行版官方方式安裝 Git、Temurin JDK、Docker Engine／Compose plugin，以及能鎖定版本的 Node manager，再執行：

```shell
git clone <repository-url>
cd ai-commerce-marketing-platform
sh ./scripts/bootstrap.sh
sh ./scripts/verify-env.sh
```

若 Playwright 回報缺少 Linux Chromium 系統函式庫，請由人員確認後執行：

```shell
sudo npx playwright install-deps chromium
```

Bootstrap 不會自行使用 `sudo`。

## Bootstrap 行為

`bootstrap.ps1` 與 `bootstrap.sh` 可以連續重跑，並執行：

1. 驗證 Node、npm、JDK、Maven Wrapper、Docker Compose 與 Docker Engine。
2. 使用 Maven Wrapper預先解析 Backend dependencies。
3. 使用 `npm ci` 依 lockfile 重建 Frontend dependencies。
4. 安裝 package-pinned Playwright Chromium。
5. 驗證 `docker compose config`。

它們不會執行 `docker compose down --volumes`、Docker prune、Database reset、Secret 產生、Production login、Git commit、push 或 merge。

## 完整驗證

`verify-env` 會先重跑 Bootstrap，再執行：

- Backend tests、Testcontainers migration tests 與 Hibernate validation。
- Frontend lint、typecheck、unit/component tests、production build 與 production dependency audit。
- Docker Compose config、隔離 cold start、Backend Actuator health 與 Frontend same-origin health proxy。
- 真實 Compose stack 的 Playwright E2E。
- Gitleaks Git history 與 worktree scan。
- 本機有 `actionlint` 時驗證 workflow；沒有時明確標為 `SKIP`，由 CI 的固定版本檢查負責。

驗證 stack 預設使用獨立 Compose project `ai-commerce-bootstrap-verify`、Frontend port `13000`、Backend port `18080`。可在程序環境變數覆寫：

```powershell
$env:DEV_VERIFY_COMPOSE_PROJECT_NAME="ai-commerce-bootstrap-verify-2"
$env:DEV_VERIFY_FRONTEND_PORT="13001"
$env:DEV_VERIFY_BACKEND_PORT="18081"
.\scripts\verify-env.ps1
```

結束時只停止驗證 containers，不刪除 volume。腳本永遠不應指向 Production Database。

## 設定與 Secret

本機 Compose 可使用已提交的安全 defaults，Bootstrap 不會自動建立 `.env`。需要選填 Connector 或 live provider 時，從 `.env.example` 理解變數名稱，值只可來自受控 Secret／環境設定；請先閱讀 [Development Secrets Checklist](development-secrets-checklist.md)。

## 輸出與 Exit Code

所有腳本使用：

- `[PASS]`：已執行且通過。
- `[WARN]`：非核心風險或需要後續處理，不等同 Passed。
- `[FAIL]`：核心檢查失敗；最終 exit code 非零。
- `[SKIP]`：未執行或不適用，不得解讀成 Passed。
- `[HUMAN ACTION REQUIRED]`：需要人員權限、選擇、登入或系統操作。

## 常見問題

### `mvnw.cmd` 找不到

必須先進入 Repository 根目錄：

```powershell
cd C:\path\to\ai-commerce-marketing-platform
.\backend\mvnw.cmd --version
```

### Codex／IDE 仍看到舊 Node 或 Java

版本管理器或 JDK 安裝完成後，新終端機通常會取得新 PATH；已開啟的 Codex／IDE 可能仍保留啟動時環境。完全結束應用後重新開啟，並用 `Get-Command node`、`Get-Command java`、`node --version` 與 `java -version` 確認實際 executable。必要時把正確 JDK 設為 `JAVA_HOME`，並讓其 `bin` 位於 PATH 前方。

### Docker Client 可用但 Engine 失敗

`docker version` 必須同時顯示 Client 與 Server。若只有 Client，啟動 Docker Desktop、確認使用 Linux containers，等待 Engine ready 後再執行。

### Port 已使用

停止占用程序，或使用 `DEV_VERIFY_FRONTEND_PORT`／`DEV_VERIFY_BACKEND_PORT` 選擇未使用的高位 port。驗證腳本不會自行終止其他程序。

### Testcontainers 無法連線

先確認 `docker info` 成功。Windows 使用 Docker Desktop Linux Engine；公司 Proxy／防火牆若阻止 image pull，需由人員依組織政策處理。

### npm 版本不符

Node 版本正確不代表 npm 一定正確；兩者都必須符合 pin。使用版本管理器安裝 npm `11.16.0`，重新開啟 shell 後再驗證。
