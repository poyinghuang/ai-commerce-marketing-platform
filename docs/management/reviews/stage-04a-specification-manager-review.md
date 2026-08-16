# Stage 04 Milestone 4A Technical Specification Manager Review

## Review identity

- Stage/Milestone: Stage 04 Milestone 4A — Platform Persistence and Operation Foundation technical specification
- Review date: 2026-08-16
- Reviewer: Codex Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository: `poyinghuang/ai-commerce-marketing-platform`
- Branch: `codex/stage-04a-platform-foundation-specification`
- Base Commit: `a90f6e0bb20d23da10edb66712d85261dafe14e8`
- Reviewed Head Commit: `d129df447f670a9f5b1faab230a06a64c8240438`
- Pull Request: #59

## Status before review

- Implementation: Not started; specification-only review
- Local Verification: Passed for documentation scope, diff, and secret scans
- Remote CI: Passed — Push Run `31954710446`; PR Run `31954749025`
- Human Review Required: No for the current deterministic local/test-only scope; mandatory escalation boundaries remain unchanged
- Merge: Blocked pending required specification corrections and a new exact-head review

## Scope reviewed

- Approved scope: Exact Stage 4A technical specification for the additive V12 persistence foundation, provider-neutral contracts, deterministic fake-adapter boundary, transaction/reconciliation behavior, verification, and governance.
- Explicit out of scope: Runtime implementation, actual migration, REST/UI/BFF, network, Meta access, credentials, authentication/RBAC/Tenant, production, spend, and Stage 4B+ behavior.
- Files reviewed: `docs/stages/stage-04a-platform-foundation.md` and `docs/management/reviews/stage-04a-specification-manager-review.md`.
- Forbidden or unexpected files: None. The reviewed PR patch contains exactly the two documentation files above.
- Completion report compared: Passed for branch, commit, file scope, no-implementation claim, local validation, and Remote CI. Technical-contract findings remain unresolved.

## Architecture and contracts

- Architecture documents reviewed: `AGENTS.md`, `README.md`, `docs/agents/AGENTS.md`, `docs/Architecture.md`, `docs/Data-Model.md`, `docs/Development-Rules.md`, `docs/Logging-and-Error-Handling.md`, the approved Stage 04 specification/review, prior Stage 03 acceptance, and relevant V1–V11 migration/domain/Audit/provider/test patterns.
- Management documents reviewed: `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`, and `docs/management/stage-gate-template.md`.
- Migration reviewed: Specification intent only. V1–V11 are unchanged and no V12 implementation exists in this PR.
- Domain/Transaction/Audit boundary: Persistence-before-call, durable attempts, optimistic claim, ambiguous reconciliation, bounded Audit, and fake-only profile boundaries are directionally correct. The exact database and port contracts have blocking inconsistencies listed below.
- API contract changes: None.
- Frontend/BFF contract changes: None.
- Backward compatibility: Documentation-only PR has no runtime impact; proposed V12 compatibility cannot be approved until the findings are corrected.
- Rollback/forward recovery: Forward-only migration recovery is correctly stated, but the proposed metric and mutation contracts need correction before implementation can safely rely on it.

## Impact

- Security impact: No runtime security change. The proposal keeps credentials, network, production, spend, authentication/RBAC/Tenant, and real Meta access out of scope.
- Data impact: None in this PR. The future V12 proposal would create seven additive tables and one redundant-but-additive uniqueness key on `ai_review_decisions`.
- Production impact: None.
- External service/cost impact: None.

## Verification executed

| Verification | Command/Run | Result | Evidence/Notes |
| --- | --- | --- | --- |
| Repository/branch/exact Head | `git status --short --branch`; `git rev-parse`; GitHub PR metadata | Passed | Local, upstream, and PR Head matched `d129df447f670a9f5b1faab230a06a64c8240438`; base and merge-base matched `a90f6e0bb20d23da10edb66712d85261dafe14e8`. |
| Git status | `git status --porcelain=v1` before review edits | Passed | Clean. |
| Diff check | `git diff --check origin/main...HEAD` | Passed | No whitespace errors. |
| Commit history | `git log --oneline --decorate -8` | Passed | One specification commit on the approved post-PR-#56 main base. |
| PR scope/actual patch | GitHub changed filenames/patch; `git diff --name-status origin/main...HEAD` | Passed | Exactly two new Markdown files; no runtime, migration, workflow, dependency, or approved parent-spec edit. |
| Backend tests | Runs `31954710446`, `31954749025` | Passed | 288 tests, 0 failures, 0 errors, 0 skipped; Maven build succeeded. |
| Migration tests | Same Remote CI runs | Passed | Existing V1–V11 cold/upgrade/hash suite passed; no V12 exists in this PR. |
| Hibernate validation | Same Remote CI runs | Passed | Existing schema validation passed; proposed V12 is documentation only and therefore not executable evidence. |
| Frontend lint | Same Remote CI runs | Passed | Executed in `Verify Frontend`. |
| Frontend typecheck | Same Remote CI runs | Passed | Executed in `Verify Frontend`. |
| Frontend tests | Same Remote CI runs | Passed | 22 files / 134 tests passed. |
| Production build | Same Remote CI runs | Passed | Frontend production build succeeded. |
| Docker Compose config | Same Remote CI runs | Passed | Step executed successfully in both runs. |
| Docker Compose cold start | Same Remote CI runs | Passed | Full stack build/start/readiness succeeded in both runs. |
| Smoke tests | Same Remote CI runs | Passed | Health chain and product vertical slice executed successfully in both runs. |
| Playwright E2E | Same Remote CI runs | Passed | 14 tests passed in both runs. |
| Gitleaks | Local Gitleaks 8.28.0 history/worktree; Remote `secret-scan` | Passed | Independent local review scanned 96 commits and current worktree; no leaks found. Both Remote runs passed. |
| Dependency audit | Same Remote CI runs | Passed | `npm audit --omit=dev` reported 0 vulnerabilities. |
| actionlint | Same Remote CI runs | Passed | Workflow validation executed successfully in both runs. |

## Remote CI

- Push Workflow/Run ID: `31954710446` — Passed at reviewed Head `d129df447f670a9f5b1faab230a06a64c8240438`
- Pull Request Workflow/Run ID: `31954749025` — Passed at reviewed Head `d129df447f670a9f5b1faab230a06a64c8240438`
- `quality-and-compose`: Passed in both runs
- `secret-scan`: Passed in both runs
- Required steps skipped: None. `Upload Playwright failure artifacts` was conditionally skipped because Playwright passed.
- Warnings/annotations: npm reported the existing `unrs-resolver@1.12.2` allow-scripts warning. GitHub Actions reported the existing Node.js 20 action-runtime deprecation/forced Node.js 24 transition. Both are non-blocking for this documentation PR.

## Findings

| ID | Severity | File/Evidence | Finding | Required fix/test |
| --- | --- | --- | --- | --- |
| 4A-SPEC-001 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:193-202`; V10/V11 schema | The proposed Ad FKs prove ownership and decision/output identity, but they do not prove that the referenced output is an IMAGE, its `generated_asset_uuid` equals the Ad `asset_uuid`, the decision/output is APPROVED, preservation is PASSED, or the stored checksum matches immutable output/Asset evidence. Line 202 assigns these guarantees to application validation while also requiring direct-SQL rejection, so direct SQL can create invalid durable Ad evidence and the stated test contract is not implementable as written. | Define exact V12 database-level insert/coherence enforcement using additive composite keys/FKs and/or a deferred constraint trigger. Require direct-SQL tests that reject mismatched Asset/output, TEXT output, REJECTED/PENDING output, blocked preservation, inactive evidence at creation, and checksum mismatch; retain application validation as defense in depth. |
| 4A-SPEC-002 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:149`, `:236`, `:320`, `:386` | `budget_amount` is described as mutable through a later command and `UPDATE_BUDGET`/budget-port behavior is included, but the database trigger contract makes Ad Set policy fields immutable. The exact allowed V12 behavior and later 4B transition are contradictory, so implementation cannot know whether to reject, permit, or audit a budget mutation. | State one exact 4A contract. Recommended: make budget fields immutable in V12/4A, prohibit executable `UPDATE_BUDGET` orchestration in 4A, and explicitly defer a reviewed trigger/command transition to 4B. If V12 is intended to permit mutation, define the exact transition, version/currency/bounds/operation-evidence requirements and direct-SQL plus transaction tests now without exposing a public command. |
| 4A-SPEC-003 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:282-312`, `:458`; approved Stage 04 read-sync contract | The three partial unique indexes allow only one snapshot per entity/window/timezone/attribution/currency, while the table is append-only and recovery says later observations supersede earlier records. A delayed or corrected provider fetch for the same daily window cannot coexist without updating an immutable row or replacing the index in a later migration. | Define an exact append-only revision identity now or defer the metric table to 4D. If retained, permit multiple fetched revisions while rejecting an exact duplicate (for example with `fetched_at`/source fingerprint in the revision key), define the latest-snapshot selection rule, and require direct-SQL tests for delayed revision coexistence, exact-duplicate rejection, NULL preservation, and immutable history. |
| 4A-SPEC-004 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:204-251`, `:379-405` | The specification claims to be the exact implementation contract but leaves operation payload keys per type, normalized error/evidence allowlists, typed command fields, pause/resume/budget method signatures, fake external-ID prefix/hash length, and several port methods as unnamed or marker-only choices. These choices affect persistence hashes, idempotency, compatibility, and contract tests and would otherwise be made during implementation without exact-head approval. | Enumerate the exact command/result/reconciliation DTO fields, payload key schema per operation, normalized error/evidence schema, port method signatures, and deterministic fake ID algorithm. Explicitly defer unused delivery/metrics/credential contracts rather than approving empty marker interfaces. Add a contract-test matrix tied to those exact values. |

## Known limitations

- Remote CI validates the current repository baseline only; it cannot execute the proposed V12 or future 4A implementation in this documentation-only PR.
- The seven-table model and durable attempt history are within the approved 4A persistence scope, but they are not approved for implementation until the blocking contract findings are resolved.
- Authentication/RBAC/Tenant/security-model work, credentials, external access, spend, production, and real provider behavior remain mandatory human escalation boundaries.
- The repository still uses a manual Manager Gate; no automated `manager-gate` required check or equivalent branch protection exists.
- The npm allow-scripts and GitHub Actions Node runtime warnings above remain non-blocking technical debt.

## Stage Gate decision

- Decision: REQUEST_CHANGES
- Decision rationale: Exact Head and all required CI passed, scope is documentation-only, and no human escalation trigger is present. However, four blocking specification contradictions/omissions would permit invalid durable evidence or force unapproved design decisions during implementation. The technical specification is not yet an executable exact contract.
- Required next action: Keep PR #59 Draft. The Developer Agent must correct only the Stage 4A technical specification and pending review scaffold as necessary, run documentation scope/diff/Gitleaks checks, push a new Head, and pass full Push/PR CI. Then start a fresh independent exact-head Manager Review. Do not create V12 or any runtime implementation.
- Human approval required: No for the required corrections if they remain within the approved deterministic local/test-only 4A scope.
- Human approval reason/evidence: The findings are technical consistency and verification-contract corrections within the already approved Stage 04 boundaries. Escalate if resolution would change product cardinality, security/tenant authority, credentials/access, spend, production, destructive data behavior, or Stage 4B+ scope.

## Approval record

Not applicable. Decision is `REQUEST_CHANGES`; merge and Stage 4A implementation remain blocked.
