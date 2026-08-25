# Stage 06 Specification Manager Review

## Review identity

- Stage／Milestone：Stage 06 Decision Engine specification
- Review date：2026-08-25
- Reviewer：Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository：`poyinghuang/ai-commerce-marketing-platform`
- Branch：`codex/stage-06-decision-engine-specification`
- Base Commit：`3d3b7b3175c2d47d850a29144173d10fc50a48a5` (PR [#71](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/71))
- Head Commit：cycle-1 reviewed content `b58096edc7a70e0e4907ac4cac3820bc80f04a27`
- Pull Request：[#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72) (Draft)
- Developer Agent：AI Workflow / Architecture (docs-only specification)
- Specialist reviewers：Manager performed Architecture and Database contract review of the proposed V16 tables. No runtime code; Code Reviewer and Frontend specialist not assigned.

## Status before review

- Specification：Developer draft on Draft PR #72
- Runtime implementation：Locked; not started
- Local Verification：Passed for documentation scope (`git status --short` empty before this report, `git diff --check`, docs-only file list, V1–V15 untouched)
- Remote CI：Passed at exact reviewed Head `b58096e`
- Human Review Required：No escalation trigger on this FAKE suggestion-only specification
- Merge：Not allowed
- Stage 06 runtime：Locked

## Scope reviewed

- Approved scope：Stage 06 deterministic-FAKE LOCAL/TEST suggestion-engine specification, including proposed additive V16 recommendation tables and local/test API/BFF/UI acceptance
- Explicit out of scope：Runtime, V16 SQL file, Java/TypeScript/flags, credentials, real Meta, spend, production, Auth/RBAC/Tenant, scheduler, LLM, execute-on-approve, Frequency column, optional Meta paused proof
- Files reviewed：Actual `origin/main...b58096e` diff, PR #72 description/commits, Stage 06 specification, Stage 05 Dashboard contract, Stage 4D fingerprint/KPI/window contract, Stage 03D review mutations, V11 `ai_review_decisions`, V12 `platform_metric_snapshots` / `platform_campaigns`, Architecture, agent-assignments, escalation policy
- Forbidden or unexpected files：None; the PR diff is documentation-only. `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` is empty
- Completion report compared：Stage 05 close-out merge SHA `3d3b7b3` and post-merge CI Run `32795522589` match GitHub. This specification has no runtime completion report

## Architecture and contracts

- Architecture documents reviewed：`AGENTS.md`, manager/escalation policies, Architecture Decision Engine paragraph, Stage 05 Dashboard, Stage 4D metrics, Stage 03D review, Development Rules item 4 (AI must not call ads write APIs)
- Migration reviewed：Proposed V16 tables only; V1–V15 remain immutable. Cycle-1 draft left evidence column types, unique-key attribution identity, and fingerprint Instant spelling unnamed
- Domain／Transaction／Audit boundary：Suggestion-only approve/reject and PostgreSQL-only generate are consistent with Architecture. Cycle-1 draft left generate lock order, `23505` replay, audit `value_type`/entity_type, and stale-PENDING behavior unnamed
- API contract changes：Additive `/api/decision-recommendations*` routes. `GET /api/dashboard` remains unchanged. Cycle-1 mixed generate empty-POST with 03D `{}` approve without saying BFF may forward `Content-Type` on `{}`
- Frontend／BFF contract changes：Additive **優化建議** region; **AI 建議** stays Stage 03. Cycle-1 did not lock Stage 05 heading-test compatibility
- Backward compatibility：Documentation-only. Existing 03D/4B/4D/05 routes remain
- Rollback／forward recovery：Forward-only V17+ for a V16 defect is stated. No DROP or backfill

## Impact

- Security impact：No executed security change. Deterministic FAKE LOCAL/TEST boundary is preserved; runtime remains locked. Approve does not call the five platform ports
- Data impact：Documentation-only. Proposed V16 is additive recommendation tables, not a System of Record change for ads entities
- Production impact：None authorized
- External service／cost impact：None authorized. Recommendations are not provider spend

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status -sb` | Passed | Branch tracks `origin/codex/stage-06-decision-engine-specification`; tree clean before review-record edits |
| Diff check | `git diff --check origin/main...b58096e` | Passed | No whitespace errors |
| Commit history | `git log origin/main..b58096e` | Passed | Two documentation commits only |
| PR scope/patch | `git diff --name-only origin/main...HEAD`; `gh pr view 72` | Passed | Six Markdown files; no runtime/migration/workflow/dependency file |
| V1–V15 immutability | `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` | Passed | Empty |
| Backend tests | Push `32797067219`; PR `32797070570` | Passed | Existing suite on current `main` + docs. Does not execute proposed V16 |
| Migration/Hibernate | Same runs | Passed for current V1–V15 | V16 is specification-only |
| Frontend lint/typecheck/tests/build | Same runs | Passed | Existing frontend verification |
| Production build | Same runs | Passed | Frontend Verify step |
| Docker Compose config | Same runs | Passed | Validate Docker Compose |
| Docker Compose cold start | Same runs | Passed | Start and wait for the full stack |
| Smoke tests | Same runs | Passed | Health chain and product vertical slice |
| Playwright E2E | Same runs | Passed | Run Browser E2E; artifact upload skipped because E2E passed |
| Gitleaks | Same runs `secret-scan` | Passed | Jobs `97650446315` / `97650455709`. Local Gitleaks binary was not executed in this review |
| Dependency audit | Same runs | Passed | Frontend Verify includes `npm audit --omit=dev` |
| actionlint | Same runs | Passed | Validate GitHub Actions workflow |

## Remote CI

- Push Run `32797067219`：`SUCCESS` at `b58096edc7a70e0e4907ac4cac3820bc80f04a27`; `quality-and-compose` job `97650446516`; `secret-scan` job `97650446315`
- Pull Request Run `32797070570`：`SUCCESS` at the same Head; `quality-and-compose` job `97650455816`; `secret-scan` job `97650455709`
- Head SHA matches：Yes
- Required steps skipped：None other than Playwright artifact upload after an E2E pass (`Upload Playwright failure artifacts` skipped)
- Warnings／annotations：Existing GitHub Actions Node.js 20 deprecation annotation; non-blocking

Prerequisite post-merge `main` CI Run `32795522589` for PR #71 remains `SUCCESS` at `3d3b7b3175c2d47d850a29144173d10fc50a48a5`.

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| 06-SPEC-001 | BLOCKING | RULE_SET_V1 overlap sentence vs predicates | Cycle-1 required runtime to assert `INCREASE_BUDGET` and `DECREASE_BUDGET` never co-emit. Predicates `roas >= 3` and `cpa >= 50` can both be true (`spend=100`, `conversions=2`, `revenue=400`). | Delete the mutual-exclusion invariant. Require a co-emit fixture. Do not assert exclusivity. |
| 06-SPEC-002 | MAJOR | Generate loop vs existing PENDING | Cycle-1 only specified insert/update/replay for **emitted** types. When a rule stops firing, the existing `PENDING` row is unspecified (leave, auto-reject, or delete). | Leave inapplicable `PENDING` rows unchanged. Runtime test: seed INCREASE, worsen ROAS, generate, version and status unchanged. |
| 06-SPEC-003 | MAJOR | `evidence_fingerprint` Instant spelling | Cycle-1 said “second-precision UTC instant” without the Stage 4D `Z` pattern or a golden hash. | Lock compact lexicographic JSON, Instant `Z` seconds, and golden SHA-256 `c6d95966c5b6f0d94f55e75e5ddb3fb5ebc4ea2843449cae09375e073053e33f`. |
| 06-SPEC-004 | MAJOR | V16 evidence columns | Cycle-1 said “matching RecommendationEvidence” without SQL names/types. | Name BIGINT bases and NUMERIC(19,6) derived columns with NULL/`>= 0` checks. HTTP remains canonical strings. |
| 06-SPEC-005 | MAJOR | Unique key / snapshot select | Unique key omitted attribution and currency. Snapshot select omitted `entity_type='CAMPAIGN'`. | Unique key includes window identity + attribution 7/1 + `TWD`. Select matches Dashboard campaign-grain SQL. |
| 06-SPEC-006 | MAJOR | `desiredState` vs eligibility | Eligibility is `<> ARCHIVED` (includes `DRAFT`). HTTP union was `PAUSED\|ACTIVE\|ARCHIVED`. | HTTP `DRAFT\|PAUSED\|ACTIVE`. `ARCHIVED` is never generated. `PAUSE` still requires `ACTIVE`. |
| 06-SPEC-007 | MAJOR | Generate counts | `consideredCampaignCount` and `skippedIncompleteCount` were used but not defined. | Eligible count vs missing-snapshot count; snapshot-but-no-emit is considered, not skipped. |
| 06-SPEC-008 | MAJOR | Generate concurrency / audit | No lock order, no `23505` mapping, audit `value_type`/entity_type unnamed. Approve empty-POST vs 03D `{}` mixed. | Account-then-campaign UUID order; `23505` replay; reuse `audit_logs` helper with named fields; approve follows 03D `{}` + optional `Content-Type`. |

Architecture applied the required contract lock-downs on this branch after `b58096e`. Those edits change the specification Head and are **not** approved by this cycle.

## Known limitations

- This PR is documentation-only. Proposed V16 and Decision Engine routes are not executed by current CI.
- Local Gitleaks was not run; Remote `secret-scan` is the secret evidence.
- Node.js 20 Action deprecation remains non-blocking.
- Frequency / `AUDIENCE_FATIGUE` remain deferred until a later additive snapshot column. That is an Architecture slice choice inside FAKE suggestion-only, not a System of Record change.
- Stale `PENDING` cards remain until a human rejects them. That is locked for this Stage; a later slice may add `SUPERSEDED` only with a new specification.
- Approved recommendations still do not execute. Apply/preview-confirm remains a later gated slice.

## Stage Gate decision

- Decision：`REQUEST_CHANGES`
- Decision rationale：Exact-head documentation CI passed and the diff is docs-only inside FAKE LOCAL/TEST suggestion-only (no auto-execute, no credentials, no V16 file). The cycle-1 specification left a false overlap invariant, unnamed V16 evidence columns, unnamed fingerprint bytes, undefined generate counts, stale-PENDING behavior, and generate concurrency/audit holes of the same class as Stage 4D cycle 1. They are fixable in this specification without human escalation.
- Required next action：Keep PR #72 Draft. Do not merge `b58096e`. Land the contract lock-downs, pass complete exact-new-head Push and Pull Request `quality-and-compose` and `secret-scan`, then request cycle-2 Manager Review of that Head. Do not start Stage 06 runtime.
- Human approval required：`No`
- Human approval reason／evidence：Escalation policy checked. No credential, Auth/RBAC/Tenant, production, spend, destructive migration, System of Record, breaking prior API, auto-execute, execute-on-approve, or critical security trigger. Additive V16 remains tables for decision records. Approving a recommendation still must not call platform write/refresh ports.

## Approval record

Not applicable for cycle 1. Decision is `REQUEST_CHANGES`. Merge allowed：No. Next Stage allowed：No.
