# Stage 04D Specification Manager Review

## Review identity

- Stage／Milestone：Stage 4D delivery and metrics read slice specification
- Review date：2026-08-22
- Reviewer：Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository：`poyinghuang/ai-commerce-marketing-platform`
- Branch：`codex/stage-04d-delivery-metrics-specification`
- Base Commit：`acb833d9622925fa185bf905aeac5bddf93f0d6e`
- Head Commit：cycle-1 reviewed content `ad993cda6e2e804323a2753a4ec3bb71dc077bb9`
- Pull Request：[#65](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/65) (Draft)

## Status before review

- Specification：Developer draft on Draft PR #65
- Runtime implementation：Locked; not started
- Local Verification：Passed for documentation scope (`git diff --check`, docs-only file list, V1–V14 untouched)
- Remote CI：Passed at exact reviewed Head `ad993cd`
- Human Review Required：No escalation trigger on this FAKE specification
- Merge：Not allowed
- Stage 4D runtime：Locked

## Scope reviewed

- Approved scope：Stage 4D deterministic-FAKE LOCAL/TEST entity-level delivery and metrics specification, including proposed additive V15 as-of indexes and local/test API/BFF/UI acceptance
- Explicit out of scope：Runtime, migration implementation, real provider/network, credentials, production, spend, Auth/RBAC/Tenant, scheduler, Dashboard, Decision Engine, Stage 4E+
- Files reviewed：Actual `origin/main...ad993cd` diff, PR #65 description/commits, Stage 4D specification, Stage 4C closeout, agent assignments, parent Stage 04, Stage 4A metric/observed-state contract, Stage 4B clock/BFF empty-POST contract, Stage 4C query/ETag contract
- Forbidden or unexpected files：None; the PR diff is documentation-only. `git diff --exit-code origin/main -- backend/src/main/resources/db/migration/` is empty
- Completion report compared：Stage 4C merge SHA `acb833d` and post-merge CI Run `32504910043` match GitHub. The 4C completion report is a documentation closeout, not runtime evidence for 4D

## Architecture and contracts

- Architecture documents reviewed：`AGENTS.md`, manager/escalation policies, parent Stage 04, approved Stage 4A/4B/4C specifications and 4C completion
- Migration reviewed：Proposed V15 indexes only; V1–V14 remain immutable. Index SQL is named. Runtime EXPLAIN evidence remains a later implementation Gate
- Domain／Transaction／Audit boundary：GET-as-SoR-only and adapter-outside-transaction are consistent with Stage 4A. Cycle-1 draft left fingerprint bytes, delivery-command desired state, duplicate-fingerprint replay, and GET window clock unnamed relative to Stage 4A/4B
- API contract changes：Additive `/api/platform-entities/...` routes match the Stage 04 metrics path. Cycle-1 draft omitted empty-POST `Content-Type` rules that Stage 4C already locked for retry
- Frontend／BFF contract changes：Proposed separate BFF paths and allowlist keys. Cycle-1 draft did not lock empty-POST or `asOf`-only query handling
- Backward compatibility：Documentation-only. Existing 4B/4C mutation routes remain query-free
- Rollback／forward recovery：Forward-only V16+ for a V15 defect is stated. No DROP or backfill

## Impact

- Security impact：No executed security change. Deterministic FAKE LOCAL/TEST boundary is preserved; runtime remains locked
- Data impact：Documentation-only. Proposed V15 is additive indexes with no snapshot rewrite
- Production impact：None authorized
- External service／cost impact：None authorized. Refresh is not a `platform_operations` spend path

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status -sb` | Passed | Branch tracks origin; only unrelated `frontend/next-env.d.ts` is dirty and is excluded |
| Diff check | `git diff --check origin/main...ad993cd` | Passed | No whitespace errors |
| Commit history | `git log origin/main..ad993cd` | Passed | Two documentation commits only |
| PR scope/patch | `git diff --name-only origin/main...HEAD`; `gh pr view 65` | Passed | Five Markdown files; no runtime/migration/workflow/dependency file |
| V1–V14 immutability | `git diff --exit-code origin/main -- backend/src/main/resources/db/migration/` | Passed | Empty |
| Backend tests | Push `32506121991`; PR `32506124961` | Passed | Existing suite on current `main` + docs. Does not execute proposed V15 or 4D ports |
| Migration/Hibernate | Same runs | Passed for current V1–V14 | V15 is specification-only |
| Frontend lint/typecheck/tests/build | Same runs | Passed | Existing frontend verification |
| Production build | Same runs | Passed | Frontend Verify step |
| Docker Compose config | Same runs | Passed | Validate Docker Compose |
| Docker Compose cold start | Same runs | Passed | Start and wait for the full stack |
| Smoke tests | Same runs | Passed | Health chain and product vertical slice |
| Playwright E2E | Same runs | Passed | Run Browser E2E; artifact upload skipped because E2E passed |
| Gitleaks | Same runs `secret-scan` | Passed | Jobs `96846557995` / `96846567232`. Local Gitleaks binary was not executed in this review |
| Dependency audit | Same runs | Passed | Frontend Verify includes `npm audit --omit=dev` |
| actionlint | Same runs | Passed | Validate GitHub Actions workflow |

## Remote CI

- Push Run `32506121991`：`SUCCESS` at `ad993cda6e2e804323a2753a4ec3bb71dc077bb9`; `quality-and-compose` job `96846558087`; `secret-scan` job `96846557995`
- Pull Request Run `32506124961`：`SUCCESS` at the same Head; `quality-and-compose` job `96846566854`; `secret-scan` job `96846567232`
- Head SHA matches：Yes
- Required steps skipped：None other than Playwright artifact upload after an E2E pass
- Warnings／annotations：Existing GitHub Actions Node.js 20 deprecation annotation; non-blocking

Prerequisite post-merge `main` CI Run `32504910043` for PR #64 remains `SUCCESS` at `acb833d9622925fa185bf905aeac5bddf93f0d6e`.

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| 4D-SPEC-001 | BLOCKING | `stage-04d-delivery-metrics-read.md` snapshot selection / metrics persist; V12 unique `(window identity, source_fingerprint)` | Cycle-1 text mapped `23505` to HTTP `200` of the **latest** row. After SUCCESS then CORRECTED, a later SUCCESS fingerprint collides with revision 1, not revision 2. Returning latest would show `spend=26.000000` for a SUCCESS replay. | Map `23505` to the row with that fingerprint. Executable runtime case: SUCCESS→CORRECTED→SUCCESS confirm returns revision 1; GET latest still returns revision 2. |
| 4D-SPEC-002 | BLOCKING | Port contract `DeliveryReadCommand`; fake SUCCESS table | Fake SUCCESS requires current desired `PAUSED`/`ACTIVE`, but the command has no desired-state field. The adapter must not query PostgreSQL. | Add `currentDesiredState` to `DeliveryReadCommand`. Orchestration copies the locked entity value. Fake uses only the command. SUCCESS-family fixtures never return `DELETED`/`PENDING`/`COMPLETED`/`REJECTED`/`ERROR`. |
| 4D-SPEC-003 | BLOCKING | Fingerprint paragraph vs Stage 4A `source_fingerprint` | Cycle-1 deferred to Stage 4A prose. 4A never locked JSON key names, Instant spelling, or whether money is a JSON number or scale-6 string. Duplicate detection and golden hashes cannot be implemented from that text. | Lock compact lexicographic JSON, exact keys, count numbers, money strings scale 6, Instant `Z` seconds, and one golden SUCCESS Campaign hash test. |
| 4D-SPEC-004 | MAJOR | Canonical window vs Stage 4B `platform_taipei_business_date` | Cycle-1 mixed `statement_timestamp()` with “HTTP uses the same clock as other Stage 04 commands,” which would let JVM `Clock` choose the Taipei day near midnight. | GET/preview/refresh must use the named SQL over V13 `platform_taipei_business_date(statement_timestamp())`. |
| 4D-SPEC-005 | MAJOR | Empty POST vs Stage 4C retry BFF | Cycle-1 said “empty body” but did not forbid `Content-Type`. Stage 4C retry rejects any `Content-Type` on empty POST. | Preview/confirm are empty-body routes: no `Content-Type`, zero-byte body, no `If-Match`. |
| 4D-SPEC-006 | MAJOR | Metrics persist vs entity ETag | Cycle-1 said duplicate fingerprint does not bump entity version, but a **new** snapshot insert was not forbidden from `UPDATE`ing the entity. That would change delivery ETags on metrics refresh. | Metrics persist never `UPDATE` Campaign/Ad Set/Ad rows. |

Architecture applied the required contract lock-downs on this branch after `ad993cd`. Those edits change the specification Head and are **not** approved by this cycle.

## Known limitations

- This PR is documentation-only. Proposed V15 and 4D ports are not executed by current CI.
- Local Gitleaks was not run; Remote `secret-scan` is the secret evidence.
- Unrelated working-tree `frontend/next-env.d.ts` is excluded.
- Node.js 20 Action deprecation remains non-blocking.
- No provider cursor is specified for 4D FAKE; Stage 04 cursor opacity is retained by forbidding Browser `cursor` input.
- Refresh is not a `platform_operations` mutation. That is an Architecture slice choice inside Stage 04 read-ingest, not a System of Record change and not a credential/spend trigger.

## Stage Gate decision

- Decision：`REQUEST_CHANGES`
- Decision rationale：Exact-head documentation CI passed and the diff is docs-only inside FAKE LOCAL/TEST, but the cycle-1 specification left fingerprint bytes, delivery-port desired state, duplicate-fingerprint replay, window clock, empty-POST headers, and metrics-versus-entity mutation unnamed. Those are implementation-contract holes of the same class as Stage 4A `4A-SPEC-004`. They are fixable in this specification without human escalation.
- Required next action：Keep PR #65 Draft. Do not merge `ad993cd`. Land the contract lock-downs, pass complete exact-new-head Push and Pull Request `quality-and-compose` and `secret-scan`, then request cycle-2 Manager Review of that Head. Do not start 4D runtime.
- Human approval required：`No`
- Human approval reason／evidence：Escalation policy checked. No credential, Auth/RBAC/Tenant, production, spend, destructive migration, System of Record, breaking prior API, or critical security trigger. V15 remains additive indexes. Forbidding a scheduler and omitting `platform_operations` for FAKE read ingest does not authorize live delivery.

## Approval record

Not applicable for cycle 1. Decision is `REQUEST_CHANGES`. Merge allowed：No. Next Stage allowed：No.

# Cycle 2

## Review identity

- Review date：2026-08-22
- Reviewer：Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Head Commit：reviewed content `8e0705f44ca8d46ad92d521864c6d405f7a5cd26`
- Interval reviewed：`ad993cd..8e0705f`

## Status before cycle 2

- Cycle 1：`REQUEST_CHANGES` on `ad993cd`
- Specification lock-downs：Landed in `8e0705f` together with the cycle-1 report
- Remote CI：Passed at exact `8e0705f`
- Merge：Not started; PR #65 remains Draft

## Cycle 2 verification

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Interval diff | `git diff --stat ad993cd..8e0705f` | Passed | Three Markdown files only; no runtime/migration |
| Diff check | `git diff --check ad993cd..8e0705f` | Passed | |
| Finding 4D-SPEC-001 | Spec snapshot/persist + SUCCESS→CORRECTED→SUCCESS case | Closed | `23505` returns the matching fingerprint row; GET latest remains latest |
| Finding 4D-SPEC-002 | `DeliveryReadCommand.currentDesiredState` | Closed | Adapter uses the command; SUCCESS-family fixtures never return `DELETED` |
| Finding 4D-SPEC-003 | Compact fingerprint JSON keys/types | Closed | Golden hash is a runtime acceptance requirement |
| Finding 4D-SPEC-004 | Canonical window SQL | Closed | V13 `platform_taipei_business_date(statement_timestamp())` |
| Finding 4D-SPEC-005 | Empty POST contract | Closed | No `Content-Type`, empty body, no `If-Match` |
| Finding 4D-SPEC-006 | Metrics persist vs entity | Closed | Never `UPDATE` Campaign/Ad Set/Ad |
| Push CI | `32538799034` | Passed | Head `8e0705f`; `quality-and-compose` `96944535437`; `secret-scan` `96944535631` |
| PR CI | `32538800215` | Passed | Same Head; `quality-and-compose` `96944537726`; `secret-scan` `96944537924` |

Required steps skipped：only Playwright artifact upload after E2E pass. Node.js 20 deprecation annotation remains non-blocking.

## Findings

| ID | Severity | Status |
| --- | --- | --- |
| 4D-SPEC-001 | BLOCKING | Closed |
| 4D-SPEC-002 | BLOCKING | Closed |
| 4D-SPEC-003 | BLOCKING | Closed |
| 4D-SPEC-004 | MAJOR | Closed |
| 4D-SPEC-005 | MAJOR | Closed |
| 4D-SPEC-006 | MAJOR | Closed |

Remaining finding IDs：None.

## Stage Gate decision

- Decision：`APPROVE`
- Decision rationale：Exact-head Push and Pull Request `quality-and-compose` and `secret-scan` succeeded on `8e0705f`. Independently, `git diff ad993cd..8e0705f` closes 4D-SPEC-001 through 4D-SPEC-006 with named SQL, command fields, compact fingerprint JSON, empty-POST rules, and non-latest duplicate replay. The PR remains documentation-only inside FAKE LOCAL/TEST. V15 stays additive indexes. Human Review Required remains No.
- Required next action：This approval-record commit must pass complete exact-head Push and Pull Request CI. Then mark PR #65 Ready and squash-merge. Do not start 4D runtime until merge and post-merge `main` CI. Stage 4E stays locked.
- Human approval required：`No`
- Human approval reason／evidence：Escalation policy re-checked. No credential, Auth/RBAC/Tenant, production, spend, destructive migration, System of Record, breaking prior API, or critical security trigger.

## Approval record

- Manager Review：Passed (cycle 2)
- Manager Decision：APPROVE
- Approved Commit：`8e0705f44ca8d46ad92d521864c6d405f7a5cd26`
- Approved CI Run：Push `32538799034` (job `96944535437`); Pull Request `32538800215` (job `96944537726`); secret-scan jobs `96944535631` / `96944537924`
- Commands actually executed：`git fetch`; `git status -sb`; `git rev-parse HEAD`; `git log origin/main..HEAD`; `git diff --check origin/main...ad993cd` and `ad993cd..8e0705f`; `git diff --exit-code origin/main --` V1–V14 SQL; `gh pr view 65`; `gh run view` on `32506121991`, `32506124961`, `32504910043`, `32538799034`, `32538800215`; independent inspection of Stage 04/4A/4B/4C contracts against the 4D specification
- Merge allowed：Yes under governance after this approval-record Head passes exact-head CI and the PR leaves Draft. **Not executed** in the cycle-2 review commit
- Next Stage allowed：No until merge and post-merge `main` CI. Stage 4D runtime remains locked
