# AI Commerce Marketing Platform

以 AI 為核心的電商素材、廣告投放、成效分析與決策平台。本 Repository 已完成 Stage 01 與 Stage 02 Milestone 2A，並提供 Milestone 2B Product Master Vertical Slice；尚未包含 Product Knowledge、AI、廣告平台、Dashboard 或 Decision Engine 功能。

## Stage 01 技術基線

- Java 21.0.9+10 / Spring Boot 4.1.0 / Maven 3.9.11 Wrapper
- Node.js 24.18.0 / npm 11.16.0 / Next.js 16.2.12
- PostgreSQL 17.6
- Docker Compose

## 使用 Docker Compose 啟動

需求：Docker 與 Docker Compose。

```shell
docker compose up --build
```

服務啟動後：

- Frontend: <http://localhost:3000>
- Backend health: <http://localhost:8080/actuator/health>
- Frontend 同源 health proxy: <http://localhost:3000/api/backend-health>
- Product UI: <http://localhost:3000/products>

## Product Master API

Backend 提供：

- `POST /api/products`
- `GET /api/products`
- `GET /api/products/{productUuid}`
- `PATCH /api/products/{productUuid}`
- `DELETE /api/products/{productUuid}`
- `POST /api/products/{productUuid}/restore`

Create 由系統產生永久 `product_uuid` 與 `PROD-00000001` 格式 Product ID。PATCH、Archive 與 Restore 使用 `ETag`／`If-Match`；Browser 僅呼叫 Next.js 同源 Product Route Handlers，不直接呼叫 Docker hostname。

停止服務：

```shell
docker compose down
```

連同本機 PostgreSQL volume 移除時才使用 `docker compose down --volumes`；此操作會刪除本機資料。

## 跨平台 scripts

Windows PowerShell：

```powershell
.\scripts\dev.ps1
.\scripts\test.ps1
```

macOS / Linux：

```shell
sh ./scripts/dev.sh
sh ./scripts/test.sh
```

## 個別啟動

先啟動可供本機使用的 PostgreSQL，並依 `.env.example` 設定環境變數。

Backend：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="local"
.\mvnw.cmd spring-boot:run
```

Frontend：

```powershell
cd frontend
npm ci
$env:BACKEND_INTERNAL_URL="http://localhost:8080"
npm run dev
```

`BACKEND_INTERNAL_URL` 是 Next.js server-only 設定，禁止改成 `NEXT_PUBLIC_*`。Browser 只存取 `/api/backend-health`，不直接解析 Docker hostname `backend`。

## 測試

Backend 測試包含 PostgreSQL Testcontainers 整合驗證，因此必須先啟動 Docker：

```powershell
cd backend
.\mvnw.cmd test
```

Frontend：

```powershell
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

## 安全與設定

- 複製 `.env.example` 為 `.env` 僅供本機使用；不得提交 `.env`。
- Production Secret 必須使用部署平台的 Secret Manager 或安全環境變數。
- `.env*`、Git metadata、文件及本機 build output 不會進入 Docker build context。
- CI 使用 Gitleaks 獨立掃描 Repository。
- Backend Actuator 只暴露 health，且不顯示 component details。

Logging、Request ID 與錯誤格式請見 [Logging and Error Handling](docs/Logging-and-Error-Handling.md)。開發必須依照 [Development Rules](docs/Development-Rules.md) 與各 [Stage 文件](docs/stages/) 逐階段進行。
