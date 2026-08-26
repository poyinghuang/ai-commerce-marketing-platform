# Stage 06 Runtime Manager Review

> Formal Manager Gate for Stage 06 Decision Engine **runtime**. Copied from `docs/management/stage-gate-template.md`. Unrun items are `Not verified`; failures are not recorded as Passed.

## Review identity

- Stage／Milestone：Stage 06 Decision Engine runtime (FAKE LOCAL/TEST suggestion-only)
- Review date：2026-08-25
- Reviewer：Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository：`poyinghuang/ai-commerce-marketing-platform`
- Branch：`codex/stage-06-decision-engine-runtime` (tracks `origin/codex/stage-06-decision-engine-runtime` only)
- Base Commit：`d77b2e043e97179e5235ee50c68677757f36bd63` (PR [#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72) squash merge)
- Head Commit (reviewed implementation)：`50fcd8b71656381b612e25bafe1e14b5bce8ddfe`
- Pull Request：[#73](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/73) (Draft at review)
- Developer Agent：[Runtime developer](e7db2c23-c9f6-4f4b-9e49-aa73e6442b61)
- Review cycle：1

This approval-record commit sits on top of `50fcd8b`. Functional Java, V16, BFF, UI, and tests were reviewed at `50fcd8b`. Merge is allowed only after this approval-record Head passes exact-head Push and Pull Request `quality-and-compose` and `secret-scan`.

## Status before review

- Implementation：Developer delivery on Draft PR #73; six commits from unlock docs through Draft PR record
- Local Verification：Developer recorded focused decision tests, full Backend 666, Frontend lint/typecheck/Vitest/build, `npm audit --omit=dev`, Compose config, `git diff --check`. Playwright, Compose cold health, Smoke, actionlint, and Gitleaks were not run locally
- Remote CI：Passed at exact reviewed Head `50fcd8b`
- Human Review Required：No escalation trigger on this FAKE suggestion-only runtime
- Merge：Not allowed until this approval-record Head’s exact-head CI succeeds and the PR leaves Draft
- Stage 07 Expansion：Locked. Optional Meta paused proof：Locked

## Scope reviewed

- Approved scope：Deterministic-FAKE LOCAL/TEST suggestion engine over existing Stage 4D campaign-grain snapshots; additive V16 recommendation tables; gated `/api/decision-recommendations*`; additive Dashboard **優化建議** region and BFF; approve/reject as decision records only
- Explicit out of scope：auto-execute, execute-on-approve, scheduler, LLM, Frequency column, `AUDIENCE_FATIGUE` emission, credentials, real Meta, spend, production, Auth/RBAC/Tenant, V1–V15 edits, `GET /api/dashboard` JSON change, Stage 07
- Files reviewed：`origin/main...50fcd8b` name list and the runtime Java/SQL/BFF/UI/test/docs diffs. Independently inspected V16, `DecisionService`/`DecisionQueries`/`RuleSetV1`/`EvidenceFingerprint`/`DecisionController`/`DecisionRequestBoundaryFilter`, BFF `forwardDecision`, `dashboard-workbench.tsx`, Playwright `dashboard-stage6.spec.ts` / `dashboard-stage5.spec.ts`, integration and compile-path tests, `MigrationCompatibilityTest` V16 SHA and forward-only V17 collision, flags, Compose
- Forbidden or unexpected files：None. No dashboard Java package change. No credential, production, workflow, or Auth/RBAC file. Only new migration is `V16__create_decision_recommendations.sql`
- Completion report compared：`docs/stages/stage-06-implementation-completion.md` matches the shipped suggestion-only surface. It had not yet recorded exact-head CI run IDs; those IDs are recorded in this review and the updated completion report

## Architecture and contracts

- Architecture documents reviewed：`docs/stages/stage-06-decision-engine.md` (merged spec), Stage 05 Dashboard, Stage 4D window/KPI/fingerprint, Stage 03D approve `{}` / reject `{reason}` + If-Match, `AGENTS.md`, manager/escalation policies
- Migration reviewed：Additive V16 only. Unique key includes window identity + attribution 7/1 + `TWD`. BIGINT bases and NUMERIC(19,6) derived columns with NULL/`>= 0`. Protect/delete/coherence triggers. No DROP, no snapshot rewrite, no Frequency column, no backfill. `git diff --exit-code origin/main --` V1–V15 SQL is empty
- Domain／Transaction／Audit boundary：Generate locks the local/test account, then eligible campaigns in `platform_campaign_uuid` ASC. Persist looks up the unique-key row first; `23505` uses a PostgreSQL savepoint and loads once (no retry). `40001`/`40P01` map to `409` `DECISION_CONCURRENCY_CONFLICT`. Approve/reject lock account then the recommendation, insert an append-only decision, mark terminal, increment version once. `decide()` does not call platform ports, Stage 4D refresh, `ReviewDecisionService`, or insert `platform_operations`. Audit uses the existing `audit_logs` helper with allowed identity/status/decision fields; fingerprints and metrics are absent from audit changes. Replay emits zero Audit
- API contract changes：Additive `/api/decision-recommendations*`. Empty POST generate (no query, no Content-Type, no If-Match, no body). Approve follows 03D `{}` + optional Content-Type. Reject `{reason}`. Missing/malformed/stale If-Match → `428`/`400`/`412`. `GET /api/dashboard` Java DTO is unchanged; integration test asserts the dashboard JSON has no recommendation fields
- Frontend／BFF contract changes：Fail-closed BFF (`PLATFORM_STAGE6_ENABLED`, 1 MiB, forbidden DTO keys, generate omits Content-Type). Additive **優化建議** when Stage 05 and Stage 06 flags are on. **AI 建議** remains Stage 03. Two-step confirm. Page load GET `/api/dashboard` plus GET `/api/decision-recommendations?status=PENDING` only
- Backward compatibility：Existing 03D/4B/4C/4D/05 routes remain. Stage 05 heading assertions still run. `platform-stage4e.spec.ts` remains in the Playwright suite executed by CI
- Rollback／forward recovery：Forward-only. `failedV16MigrationRollsBackEveryPartialV16Object` and `failedForwardOnlyV17CollisionLeavesEveryV16ObjectInPlace` exist. A later Frequency or index need is V17+

## Impact

- Security impact：`@Profile("(local | test) & !production")` plus `platform.adapter=fake`, `platform.web.enabled=true`, `platform.stage6.enabled=true`. Default/production/`adapter=meta`/missing flag fail closed (`DecisionProfileGateTest`). BFF strips forbidden keys including fingerprints. No Auth/RBAC/Tenant change. No credential files
- Data impact：Additive recommendation tables only. Ads entities, snapshots, and `desired_state` are not mutated by approve/reject. Not a System of Record change for delivery
- Production impact：None authorized. Production profile does not load the decision controller
- External service／cost impact：None. Generate reads PostgreSQL snapshots only. No provider call, no spend

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status -sb` | Passed | Branch tracks `origin/codex/stage-06-decision-engine-runtime`; HEAD `50fcd8b` before this approval-record commit |
| Diff check | `git diff --check origin/main...50fcd8b` | Passed | No whitespace errors |
| Commit history | `git log --oneline origin/main..50fcd8b` | Passed | Unlock docs; V16; backend; dashboard/BFF; completion report; Draft PR 73 record |
| PR scope | `git diff --name-only origin/main...50fcd8b`; `gh pr view 73` | Passed | Additive decision package, V16, flags, BFF/UI, tests, Stage 06 docs. Draft, `MERGEABLE`/`CLEAN` |
| V1–V15 immutability | `git diff --exit-code origin/main --` V1–V15 SQL files | Passed | Exit 0. Name-only migration diff is only `V16__create_decision_recommendations.sql` |
| Backend tests | Push `32811159731` / PR `32811163525` step `Test Backend with PostgreSQL Testcontainers` | Passed | Remote CI. Developer local: 666 tests. Manager did not re-run Maven locally |
| Migration tests | Same Backend step; `MigrationCompatibilityTest` V16 SHA `88d388201a974403d963a6dcd7d5cfafbdc460cff013c1639cd7ec8134db6484`; V16 rollback and V17 collision | Passed | Via Remote CI |
| Hibernate validation | Same Backend step; schema tests now expect Flyway through `"16"` | Passed | Extra V16 tables are unmapped; `ddl-auto=validate` still succeeded in CI |
| Frontend lint | CI `Verify Frontend` | Passed | Remote CI |
| Frontend typecheck | Same | Passed | Remote CI |
| Frontend tests | Same | Passed | Developer local: 165 Vitest tests. Remote CI Verify Frontend |
| Production build | Same | Passed | Remote CI |
| Docker Compose config | CI `Validate Docker Compose` | Passed | Developer also ran `docker compose config --quiet` locally |
| Docker Compose cold start | CI `Start and wait for the full stack` | Passed | Not re-run locally by Manager |
| Smoke tests | CI `Smoke test health chain and product vertical slice` | Passed | Remote CI |
| Playwright E2E | CI `Run Browser E2E` | Passed | Includes `dashboard-stage5`, `dashboard-stage6`, and `platform-stage4e`. Not re-run locally by Manager |
| Gitleaks | `secret-scan` Gitleaks 8.28.0 | Passed | Push job `97690622796`; PR job `97690634074`. Local Gitleaks binary not executed in this review |
| Dependency audit | CI `Verify Frontend` includes `npm audit --omit=dev` | Passed | Developer local: 0 vulnerabilities |
| actionlint | CI `Validate GitHub Actions workflow` | Passed | Remote CI |

Spec locks independently confirmed in source (not only the developer report):

- `SUCCESS` generate emits `INCREASE_BUDGET` only; constructed `spend=100`, `conversions=2`, `revenue=400` co-emits `INCREASE_BUDGET` and `DECREASE_BUDGET`
- Inapplicable `PENDING` left unchanged (version and fingerprint stable)
- Golden hasher SHA-256 `c6d95966c5b6f0d94f55e75e5ddb3fb5ebc4ea2843449cae09375e073053e33f`; live generate hashes live window + campaign UUID + snapshot fingerprint
- `AUDIENCE_FATIGUE` never emitted; Frequency absent from JSON
- Approve/reject: zero adapter invocations, zero `platform_operations`, zero `ai_review_decisions` / `ai_generation_jobs`, `desired_state` unchanged
- Empty POST generate; If-Match `428`/`412`; already-decided `409`
- Compile-path: decision package source and class files mention none of the five platform ports. Manager grep of `com.aicommerce.platform.decision` also found no `ReviewDecisionService`. `dashboard` Java still has none of the five ports
- Playwright: `/dashboard` load records zero mutating `/api/**`; generate then approve/reject stay on decision routes; Stage 05 **AI 建議** headings remain

## Remote CI

- Push Run [`32811159731`](https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/32811159731)：`SUCCESS` at `50fcd8b71656381b612e25bafe1e14b5bce8ddfe`; `quality-and-compose` job `97690622640`; `secret-scan` job `97690622796`
- Pull Request Run [`32811163525`](https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/32811163525)：`SUCCESS` at the same Head; `quality-and-compose` job `97690633924`; `secret-scan` job `97690634074`
- Head SHA matches：Yes (`50fcd8b` implementation)
- Required steps skipped：only `Upload Playwright failure artifacts` after E2E pass (both quality jobs)
- Warnings／annotations：Existing GitHub Actions Node.js 20 deprecation annotation; non-blocking

Prerequisite post-merge `main` CI Run `32804409128` for PR #72 remains the Stage 06 spec baseline at `d77b2e0`.

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
|  |  |  | None |  |

## Known limitations

- Manager did not re-run local Maven, Playwright, Compose cold start, Smoke, actionlint, or Gitleaks. Remote CI executed those required steps at `50fcd8b`
- Generate bodies larger than 16 KiB return HTTP 413 with a short `DECISION_REQUEST_INVALID` JSON (no `requestId`/`path`). Empty POST generate does not hit this path
- `DecisionHasNoPlatformWritePathTest` names the five platform ports, not `ReviewDecisionService`. Manager source grep of the decision package is empty for that type
- Frequency / `AUDIENCE_FATIGUE` remain deferred until a later additive snapshot column
- Stale `PENDING` cards remain until a human rejects or approves them
- Approved recommendations still do not execute. Apply/preview-confirm remains a later gated slice and is `ESCALATE_TO_HUMAN` if proposed as execute-on-approve
- Node.js 20 Action deprecation remains non-blocking

## Stage Gate decision

- Decision：`APPROVE`
- Decision rationale：Exact-head Push and Pull Request `quality-and-compose` and `secret-scan` succeeded on implementation Head `50fcd8b`. Independent inspection of V16, generate/approve persistence, `RULE_SET_V1`, BFF/UI, and tests matches the merged Stage 06 specification: suggestion-only, no five-port compile path, no `GET /api/dashboard` JSON change, V1–V15 immutable, FAKE LOCAL/TEST fail-closed. No `CRITICAL`, `BLOCKING`, or open `MAJOR` finding. Human Review Required remains No
- Required next action：This approval-record commit must pass complete exact-head Push and Pull Request CI. Then rebind Approved Commit to that exact PR Head, mark PR #73 Ready, and squash-merge. Do not start Stage 07. Optional Meta paused proof stays locked. After merge, wait for post-merge `main` CI before any close-out
- Human approval required：`No`
- Human approval reason／evidence：Escalation policy checked. No credential, Auth/RBAC/Tenant, production, spend, destructive migration, System of Record change, breaking prior API, auto-execute, execute-on-approve, or critical security trigger. Approve/reject persist decision records only

## Approval record

- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Commit：`50fcd8b71656381b612e25bafe1e14b5bce8ddfe` (implementation). After this approval-record commit’s exact-head CI, Manager rebinds Approved Commit to that newer PR Head and merges only that SHA
- Approved CI Run：Push `32811159731` (job `97690622640`); Pull Request `32811163525` (job `97690633924`); secret-scan jobs `97690622796` / `97690634074`
- Commands actually executed：`git fetch` identity via `git status -sb`, `git rev-parse HEAD`, `git rev-parse origin/main`, `git merge-base HEAD origin/main`, `git log origin/main..HEAD`, `git diff --check origin/main...HEAD`, `git diff --name-only origin/main...HEAD`, `git diff --exit-code origin/main --` V1–V15 SQL; `gh pr view 73`; `gh run view` `32811159731` and `32811163525`; `gh api` jobs `97690622640` and `97690633924`; independent inspection of V16, decision package, BFF/UI, tests, flags, and Stage 06/05/4D/03D contracts
- Merge allowed：Yes under governance after this approval-record Head passes exact-head CI and the PR leaves Draft. **Not executed** in this review commit
- Next Stage allowed：No until merge and post-merge `main` CI. Stage 07 Expansion remains locked (human required before a second live ads platform). Optional Meta paused proof remains locked
