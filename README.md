# AI Commerce Marketing Platform

以 AI 為核心的電商素材、廣告投放、成效分析與決策平台。本 Repository 已完成 Stage 01、完整 Stage 02 Product Knowledge Center、完整 Stage 03 AI Creative Factory、Stage 04 Meta Ads Adapter 的 4A–4E FAKE LOCAL/TEST 切片（tag `stage-04-complete`），以及 Stage 05 Dashboard FAKE LOCAL/TEST runtime。目前 Gate 是 Stage 06 Decision Engine **specification**（建議型、不自動執行）；runtime 在規格核准並合併前不得開始。

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
- Dashboard UI: <http://localhost:3000/dashboard>（`PLATFORM_STAGE5_ENABLED=true`）

## Product Master API

Backend 提供：

- `POST /api/products`
- `GET /api/products`
- `GET /api/products/{productUuid}`
- `PATCH /api/products/{productUuid}`
- `DELETE /api/products/{productUuid}`
- `POST /api/products/{productUuid}/restore`

Create 由系統產生永久 `product_uuid` 與 `PROD-00000001` 格式 Product ID。PATCH、Archive 與 Restore 使用 `ETag`／`If-Match`；Browser 僅呼叫 Next.js 同源 Product Route Handlers，不直接呼叫 Docker hostname。

Backend 的 Stage 2D Quality API 提供：

- `GET /api/products/{productUuid}/quality`
- `PATCH /api/products/{productUuid}/quality/manual-adjustment`

Quality 使用確定性規則、blocking reasons 與版本型 `ETag`。非零人工調整限 `-20..20`，必須提供理由並使用可信 Audit Actor。Product detail 的 `?tab=quality` 提供 component breakdown、readiness、blocking reasons 與人工調整介面；Browser 只透過固定 same-origin Quality Route Handlers 存取 Backend，不直接依賴 Docker 內部 hostname。

Stage 2E Google Connector 的核准規格位於 `docs/stages/stage-02e-google-connectors.md`。PostgreSQL 仍是唯一 System of Record；Google Sheets 僅作為明確觸發的 Preview／Import Connector，Google Drive 僅透過 `StorageProvider` 建立與保存資料夾 metadata。

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

Browser E2E uses the real Docker Compose Frontend, Backend, and PostgreSQL stack. Start an isolated cold stack, install the package-pinned Chromium runtime, and run:

The committed suite covers the Product Center graph, lifecycle/concurrency/Audit behavior, and deterministic Quality progression, blocker invariance, adjustment persistence, stale ETag recovery, and archive/restore recovery.

```powershell
$env:AI_BUDGET_CURRENCY="USD"
$env:AI_MAX_JOB_COST="5.000000"
$env:AI_MAX_BATCH_COST="20.000000"
$env:AI_MAX_DAILY_COST="100.000000"
docker compose up --detach --build --wait --wait-timeout 180
cd frontend
npx playwright install chromium
$env:PLAYWRIGHT_BASE_URL="http://127.0.0.1:3000"
$env:PLAYWRIGHT_AUDIT_DB_ASSERTION="1"
npm run test:e2e
cd ..
docker compose down --volumes
```

The E2E data is synthetic and the final command removes the ephemeral PostgreSQL volume. Do not point this flow at a persistent or production database.

## 安全與設定

- 複製 `.env.example` 為 `.env` 僅供本機使用；不得提交 `.env`。
- Production Secret 必須使用部署平台的 Secret Manager 或安全環境變數。
- `.env*`、Git metadata、文件及本機 build output 不會進入 Docker build context。
- CI 使用 Gitleaks 獨立掃描 Repository。
- Backend Actuator 只暴露 health，且不顯示 component details。

Logging、Request ID 與錯誤格式請見 [Logging and Error Handling](docs/Logging-and-Error-Handling.md)。開發必須依照 [Development Rules](docs/Development-Rules.md) 與各 [Stage 文件](docs/stages/) 逐階段進行。

## Stage 03 text generation

Milestone 3B adds the Product `Creative Factory` tab and provider-neutral text generation APIs. Generation
is fail-closed until `AI_BUDGET_CURRENCY`, `AI_MAX_JOB_COST`, `AI_MAX_BATCH_COST`, and
`AI_MAX_DAILY_COST` are explicitly supplied as server-side environment configuration. The local/test
profile uses a deterministic Stub and a local default prompt template. Default/production profiles expose
no usable model profile and normal CI receives no production credentials.

## Stage 03 protected-product image generation

Milestone 3C extends the same Creative Factory with provider-neutral background image generation. The
local/test flow uses deterministic source pixels and a process-local binary store; the ComfyUI adapter is
restricted to a fixed server origin and a repository-owned workflow. The Product region is verified by an
exact RGBA mask comparison before generated Asset metadata and output evidence are committed. Default and
production profiles remain fail closed, and this milestone contains no approval, publication, redraw, video,
Ads, or Decision Engine behavior.

## Stage Gate governance

Repository 採用人工 Manager Gate。每個 Stage／Milestone 必須在 Remote CI 通過後接受實際 Diff、Migration、Contract、安全與測試審查；只有 `Manager Decision: APPROVE` 才允許合併。規則與報告格式請見：

- [Repository Agent Instructions](AGENTS.md)
- [Manager Policy](docs/management/manager-policy.md)
- [Escalation Policy](docs/management/escalation-policy.md)
- [Stage Gate Review Template](docs/management/stage-gate-template.md)

自動 `manager-gate` Required Check 與 Branch Protection 尚未啟用，不得將其標記為已通過。
