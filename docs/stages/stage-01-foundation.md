# Stage 01 — Project Foundation

## 目標

建立可重現、可測試、可安全啟動的 Backend、Frontend、PostgreSQL、CI 與開發環境骨架。本 Stage 不實作商品、AI、Meta Ads、Dashboard 或 Decision Engine 功能。

## 技術基線

- Backend: Java 21.0.9+10、Spring Boot 4.1.0、Maven Wrapper 3.9.11
- Frontend: Node.js 24.18.0、npm 11.16.0、Next.js 16.2.12、React 19.2.8
- Database: PostgreSQL 17.6
- Container: Docker Compose；所有 base image 使用明確版本
- CI: GitHub Actions；第三方 actions 固定 commit SHA
- Secret scan: Gitleaks 8.28.0

## 實作計畫

1. 建立 Backend 與 Frontend 專案骨架及自動化測試。
2. 建立 PostgreSQL 連線設定與 Testcontainers 真實整合測試。
3. 建立 Dockerfiles、Docker Compose、health checks 與應用連線重試設定。
4. 建立跨平台 PowerShell/Bash 開發及測試 scripts。
5. 建立 CI、獨立 Secret scanning、環境變數及 Docker build context 防護。
6. 文件化啟動方式、Logging、Error Handling 與驗收結果。

## 交付

- Backend 與 Frontend 專案骨架
- Backend `/actuator/health`；不建立其他 health endpoint
- Next.js `/api/backend-health` 同源代理
- PostgreSQL 與 Testcontainers 整合測試
- Docker Compose 與各 service health check
- 環境變數範本
- PowerShell/Bash scripts、`.editorconfig`、`.gitattributes`
- GitHub Actions CI 與 Gitleaks
- Log 與 Error Handling 規範

## 安全與可觀測性要求

- Actuator 只開放 `health`，且不顯示 component、資料庫或環境細節。
- Browser 不得直接存取 Docker 內部 hostname；Frontend 由 server-side Route Handler 代理。
- Backend 使用 Spring Boot ECS structured logging。
- `X-Request-ID` 支援傳入或產生 UUID、寫入 MDC、response header 與錯誤內容。
- Log 不得記錄 Token、Cookie、Authorization header 或密碼。
- `.env` 不得進入 Git 或 Docker image；runtime container 使用 non-root user。

## 驗收清單

- [x] `docker compose config` 成功且所有 image 版本已鎖定。
- [x] `docker compose up --build` 可啟動 PostgreSQL、Backend、Frontend。
- [x] PostgreSQL 通過 `pg_isready` health check。
- [x] Backend `/actuator/health` 回傳健康狀態，不包含元件與敏感細節。
- [x] Backend 與 Frontend container health check 成功。
- [x] Browser 只透過 `/api/backend-health` 取得 Backend health。
- [x] Backend 支援 `X-Request-ID` 全鏈路規格。
- [x] Validation error 包含 `path` 與選填 `fieldErrors`。
- [x] 非預期錯誤不回傳 stack trace。
- [x] Backend 單元測試與 PostgreSQL Testcontainers 整合測試成功。
- [x] Frontend lint、typecheck、test、build 成功，並使用 `npm ci`。
- [x] CI workflow 通過 actionlint，且 actions 固定 commit SHA。
- [x] Remote GitHub Actions CI 成功；Push 與 Pull Request 觸發的 `quality-and-compose`、`secret-scan` 均通過。
- [x] Gitleaks Git history 與目前目錄掃描無告警。
- [x] `.dockerignore` 排除 `.git`、`.env*`、文件及本機輸出。
- [x] Docker runtime 使用 non-root user。
- [x] README 提供跨平台標準指令與 PowerShell/Bash scripts。
- [x] Git history 與目前工作目錄不包含 Secret。
- [x] 未實作 Stage 02 以後的功能。

## 本機驗證結果（2026-08-01）

- Backend: 8 tests passed，包含 PostgreSQL 17.6 Testcontainers `SELECT 1` 與 Actuator health 整合驗證。
- Frontend: 2 test files、3 tests passed；lint、typecheck、Next.js production build 全部成功。
- Dependency audit: production npm dependencies 0 vulnerabilities。
- Docker Compose: PostgreSQL、Backend、Frontend 全部 healthy；health 與同源 proxy 均回傳 `{"status":"UP"}`。
- Actuator: `/actuator` 與 `/actuator/env` 均為 404，只有 `/actuator/health` 暴露。
- Runtime users: Backend=`app`、Frontend=`node`，皆為 non-root。
- Logging: console 為 ECS JSON，Request ID 透過 MDC 串接。
- Secret scan: Gitleaks 8.28.0 history scan 與 directory scan 均為 no leaks found。
- CI definition: actionlint 1.7.7 成功；Remote GitHub Actions 的 Push 與 Pull Request Run 均通過。

## 最終驗收紀錄（2026-08-02）

- 最終實作 Commit: `f4dda7fc1759885314f3e56125b5ff2f9a2e79d0`。
- Push Run `30708714984`: `quality-and-compose`、`secret-scan` 全部通過。
- Pull Request Run `30708716285`: `quality-and-compose`、`secret-scan` 全部通過。
- 人工差異與架構審查：通過，無 blocking code findings。
- Pull Request: `#1` 保持未合併，等待最終文件收尾 CI。

## Known limitations / Technical debt

- GitHub Actions Node.js 20 compatibility deprecation；Runner 目前強制使用 Node.js 24 執行既有 Actions，不影響本次 CI 結果。
- Actions 內部仍會產生 `punycode` 與 `url.parse()` deprecation warning。
- Backend 測試使用的 Byte Buddy dynamic Java agent 有 future deprecation warning。

最終文件收尾 CI 通過後，Stage 01 正式完成；Pull Request 仍須由人工決定合併，不得自行進入 Stage 02。
