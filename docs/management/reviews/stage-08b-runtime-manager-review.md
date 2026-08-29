# Stage 08B Runtime Manager Review

> Formal Manager Gate for Stage 08 **8B** opt-in LOCAL live Google Drive folder ensure. Copied from `docs/management/stage-gate-template.md`. Unrun items are `Not verified`; failures are not recorded as Passed.

## Review identity

- Stage／Milestone：Stage 08 live connector reads — **8B** runtime (opt-in LOCAL `GoogleDriveStorageProvider`)
- Review date：2026-08-28
- Reviewer：Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository：`poyinghuang/ai-commerce-marketing-platform`
- Branch：`codex/stage-08b-live-drive` (tracks `origin/codex/stage-08b-live-drive`)
- Base Commit：`91a82978c0701ac360c84508db9f7ddfafd45eb0` (PR [#83](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/83) squash merge)
- Head Commit (reviewed implementation)：`2edf23a2e4642c29a8b77d921069a7dc9372c9fb`
- Pull Request：[#84](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/84) (Draft at review)
- Review cycle：1

This approval-record commit sits on top of `2edf23a`. Functional Java and tests were reviewed at `2edf23a`. Merge is allowed only after this approval-record Head passes exact-head Push and Pull Request `quality-and-compose` and `secret-scan`.

## Status before review

- Implementation：Developer delivery on Draft PR #84; two commits (`f614d70` feat, `2edf23a` Draft PR record)
- Local Verification：Developer recorded focused Drive tests (33), `git diff --check`, empty Flyway diff versus `origin/main`. Full Backend, Frontend, Playwright, Compose, Smoke, actionlint, and Gitleaks were not run locally by the developer
- Remote CI：Passed at exact reviewed Head `2edf23a`
- Human Review Required：No for merging this stub-default CI path. Live Drive enablement stays operator-side (ADC + `GOOGLE_DRIVE_ROOT_FOLDER_ID` outside git). No credentials in this PR or in GitHub Actions
- Merge：Not allowed until this approval-record Head’s exact-head CI succeeds and the PR leaves Draft
- Stage 08C / live Google Ads / LINE / TikTok / `META_TEST_DELIVERY` / auto-execute：Locked

## Scope reviewed

- Approved scope：LOCAL/TEST may select the existing `GoogleDriveStorageProvider` when `platform.storage.provider=google`. Default remains `stub`. `fake-object` (7B) unchanged. Production / default remain the fail-closed Google bean. Folder roles, V7, GET-from-PostgreSQL, POST ensure, search-before-create, and `drive.file` scope unchanged. No file-byte I/O
- Explicit out of scope：Flyway V1–V17 edits; V18; 8C Insights; live Google Ads / LINE / TikTok; upload / download / delete / move / rename; Browser-selectable origin / credential / Drive root; GitHub Actions secrets; production credentials; Auth/RBAC/Tenant; auto-execute
- Files reviewed：`origin/main...2edf23a` name list (10 files) and the Java/docs diffs. Independently inspected `GoogleDriveStorageProvider`, `GoogleDriveStorageProviderCondition`, `StorageProviderProfileTest`, `GoogleDriveStorageProviderTest`, `StubStorageProvider`, `FakeObjectStorageProvider`, `ProductStorageFolderService`, `ProductStorageFolderQueryService`, `ProductStorageFolderController`, `application-local.yml`, `application-test.yml`, `docker-compose.yml`, `.github/workflows/ci.yml`
- Forbidden or unexpected files：None. No frontend, BFF, workflow, Compose, `.env`, migration, or Auth/RBAC file. No `V18__allow_meta_provider_key.sql`. No `LiveMetaInsightsReadAdapter`
- Completion report compared：`docs/stages/stage-08b-implementation-completion.md` matches the shipped flag wiring. It had not yet recorded exact-head CI run IDs; those IDs are recorded in this review and the updated completion report

## Architecture and contracts

- Architecture documents reviewed：merged `docs/stages/stage-08-live-connector-reads.md` 8B contract, Stage 02E-3 Drive, Stage 07B fake-object storage, `docs/Architecture.md`, `AGENTS.md`, manager/escalation policies
- Migration reviewed：None in this PR. `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` is empty. Integration tests on this Head still apply Flyway through V17 only
- Domain／Transaction／Audit boundary：`ProductStorageFolderService` / query service / controller are not in the diff. GET still reads JPA only. POST still calls `StorageProvider.ensureProductTree` before persist. Application/domain packages still have no `com.google` imports. Isolation test `StorageFolderApplicationPortIsolationTest` is unchanged and green
- API contract changes：None. Existing `/api/products/{productUuid}/storage-folder` GET/POST
- Frontend／BFF contract changes：None. Frontend grep has no `storage.provider`, Drive root, or Google Drive bean
- Backward compatibility：CI / Compose / `application-local.yml` / `application-test.yml` stay `platform.storage.provider: stub`. Wrong LOCAL flag (`s3`) loads no `StorageProvider`. Mixed `production,local` still loads only the Google bean
- Rollback／forward recovery：No schema change. Runtime rollback leaves the existing 2E-3 search-before-create recovery boundary

## Impact

- Security impact：Fixed origin `https://www.googleapis.com`, scope `https://www.googleapis.com/auth/drive.file`, root/Shared Drive remain server `@Value` (not Browser input). Condition mirrors 8A Sheets: production/default always Google fail-closed; `(local | test) & !production` loads Google only when the flag is `google`. Duplicate search still `STORAGE_FOLDER_STATE_CONFLICT` without POST. Missing root still `CONNECTOR_NOT_CONFIGURED`. Provider bodies remain sanitized (existing 403 test). No new GitHub Actions secret. `ci.yml` still `permissions: contents: read`
- Data impact：No migration. V7 `storage_provider='GOOGLE_DRIVE'` CHECK unchanged. PostgreSQL remains System of Record for folder IDs
- Production impact：None authorized. Production still loads the fail-closed Google bean without credentials from this Stage
- External service／cost impact：CI does not call live Drive. Operators who set `platform.storage.provider=google` locally may create folders in a configured **test** root (`drive.file` only). That optional live path is not this PR’s CI default

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status -sb` | Passed | Branch tracks `origin/codex/stage-08b-live-drive`; HEAD `2edf23a` before this approval-record commit |
| Diff check | `git diff --check origin/main...2edf23a` | Passed | No whitespace errors |
| Commit history | `git log --oneline origin/main..2edf23a` | Passed | `f614d70` feat; `2edf23a` Draft PR 84 record |
| PR scope | `git diff --name-only origin/main...2edf23a`; `gh pr view 84` | Passed | 10 files. Draft, `MERGEABLE` / `CLEAN` |
| V1–V17 immutability | `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` | Passed | Empty. No V18 |
| Backend tests | Manager local focused 8B: `mvnw.cmd -B test -Dtest=StorageProviderProfileTest,GoogleDriveStorageProviderTest,FakeObjectStorageProviderTest,StorageFolderApplicationPortIsolationTest,ProductStorageFolderIntegrationTest,FakeObjectStorageFolderIntegrationTest,ProductStorageFolderRollbackIntegrationTest,ProductStorageFolderControllerTest` | Passed | 33 tests, 0 failures/errors/skips |
| Full Backend | Push `33161897827` step `Test Backend with PostgreSQL Testcontainers` | Passed | Remote CI: **715** tests, 0 failures/errors/skips |
| Migration tests | Same Backend step; Flyway still through V17 in storage-folder integration tests | Passed | Via Remote CI. No new migration class |
| Hibernate validation | Same Backend step | Passed | Via Remote CI |
| Frontend lint | CI `Verify Frontend` | Passed | Remote CI. No frontend files in this PR |
| Frontend typecheck | Same | Passed | Remote CI |
| Frontend tests | Same | Passed | Remote CI |
| Production build | Same | Passed | Remote CI |
| Docker Compose config | CI `Validate Docker Compose` | Passed | Compose does not set `platform.storage.provider=google` or `GOOGLE_DRIVE_*` |
| Docker Compose cold start | CI `Start and wait for the full stack` | Passed | Not re-run locally by Manager |
| Smoke tests | CI `Smoke test health chain and product vertical slice` | Passed | Remote CI |
| Playwright E2E | CI `Run Browser E2E` | Passed | Not re-run locally by Manager |
| Gitleaks | `secret-scan` Gitleaks 8.28.0 | Passed | Push job `98818090371`; PR job `98818098467`. Local Gitleaks binary not executed in this review |
| Dependency audit | CI `Verify Frontend` includes `npm audit --omit=dev` | Passed | Remote CI |
| actionlint | CI `Validate GitHub Actions workflow` | Passed | Remote CI |

Spec locks independently confirmed in source (not only the developer report):

- `GoogleDriveStorageProviderCondition` matches 8A Sheets: production or neither local nor test → Google; else only `platform.storage.provider=google`
- Profile matrix adds `local-google` / `test-google`; production ignores `stub` / `fake-object` / `google` flags; wrong local `s3` loads no stub, fake-object, or Google bean
- MockRest: missing root → `CONNECTOR_NOT_CONFIGURED`; search hit reuses IDs; miss creates; duplicate files → `STORAGE_FOLDER_STATE_CONFLICT` with GET only (server.verify, no POST)
- Origin remains `https://www.googleapis.com`; scope remains `drive.file`; Shared Drive query params unchanged
- `application-local.yml` / `application-test.yml` remain `platform.storage.provider: stub` (not in this PR’s file list)
- `docker-compose.yml` has no storage-provider or Drive-root env
- GET `ProductStorageFolderQueryService` uses JPA only; POST ensure still goes through the `StorageProvider` port

## Remote CI

- Push Run [`33161897827`](https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/33161897827)：`SUCCESS` at `2edf23a2e4642c29a8b77d921069a7dc9372c9fb`; `quality-and-compose` job `98818090252`; `secret-scan` job `98818090371`
- Pull Request Run [`33161900484`](https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/33161900484)：`SUCCESS` at the same Head; `quality-and-compose` job `98818098284`; `secret-scan` job `98818098467`
- Head SHA matches：Yes (`2edf23a` implementation)
- Required steps skipped：only `Upload Playwright failure artifacts` after E2E pass (both quality jobs)
- Warnings／annotations：Existing GitHub Actions Node.js 20 deprecation annotation (checkout / setup-java / setup-node forced onto Node 24); non-blocking. Existing javac “deprecated API” notices in unrelated packages; non-blocking

Prerequisite post-merge `main` CI Run `33143102962` for PR #83 remains the 8A baseline at `91a8297`.

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
|  |  |  | None |  |

## Known limitations

- Manager did not re-run local Playwright, Compose cold start, Smoke, actionlint, Gitleaks, or full Maven. Remote CI executed those required steps at `2edf23a`. Manager did re-run the focused 8B Backend suite (33 tests)
- Optional human ensure against a real test Drive root is **not** CI and was not executed in this review
- Named test Drive root / ADC handling stay on the operator machine. This PR does not commit them. Enabling `platform.storage.provider=google` locally remains `ESCALATE_TO_HUMAN` if credentials are added to git or GitHub Actions
- This PR rewrote `docs/stages/stage-08a-implementation-completion.md` to record PR #83 squash-merge and post-merge CI. There is still no separate 8A Manager Review file; that is historical bookkeeping, not an 8B defect
- Node.js 20 Action deprecation remains non-blocking
- 8C, live Google Ads, LINE/TikTok, `META_TEST_DELIVERY`, and auto-execute stay locked

## Stage Gate decision

- Decision：`APPROVE`
- Decision rationale：Exact-head Push and Pull Request `quality-and-compose` and `secret-scan` succeeded on implementation Head `2edf23a`. Independent inspection of the Condition, profile matrix, MockRest proofs, Compose/YAML defaults, empty Flyway diff, and isolation matches the merged Stage 08 **8B** contract: opt-in LOCAL live folder ensure, CI stays stub, no file bytes, no V18, no Insights. No `CRITICAL`, `BLOCKING`, or open `MAJOR` finding. Human Review Required is No for this stub-default merge
- Required next action：This approval-record commit must pass complete exact-head Push and Pull Request CI. Then rebind Approved Commit to that exact PR Head, mark PR #84 Ready, and squash-merge. Do not start 8C. After merge, wait for post-merge `main` CI before any 8C work
- Human approval required：`No`
- Merge allowed：`Yes`, after the approval-record Head passes required CI and the PR leaves Draft
- Next Stage allowed：only after merge and post-merge `main` verification

## Approval record

- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Commit (implementation)：`2edf23a2e4642c29a8b77d921069a7dc9372c9fb`
- Approved CI Runs (implementation)：Push `33161897827`; PR `33161900484`
- Commands actually executed：`git status -sb`; `git diff --check origin/main...HEAD`; `git log --oneline origin/main..HEAD`; `git diff --name-only origin/main...HEAD`; `git diff --exit-code origin/main -- backend/src/main/resources/db/migration`; `gh pr view 84`; `gh run view 33161897827` / `33161900484`; check-run annotations; CI log grep for Backend totals; focused `mvnw.cmd -B test` 8B classes; independent source inspection of Drive provider, condition, YAML, Compose, CI workflow, GET/POST services
- Merge allowed：Yes, after this approval-record Head’s exact-head CI
- Next Stage allowed：Only after merge and post-merge verification
