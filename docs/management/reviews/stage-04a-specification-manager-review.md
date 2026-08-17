# Stage 04 Milestone 4A Technical Specification Manager Review

## Review identity

- Stage/Milestone: Stage 04 Milestone 4A — Platform Persistence and Operation Foundation technical specification
- Review date: 2026-08-16
- Reviewer: Codex Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository: `poyinghuang/ai-commerce-marketing-platform`
- Branch: `codex/stage-04a-platform-foundation-specification`
- Base Commit: `a90f6e0bb20d23da10edb66712d85261dafe14e8`
- Reviewed Head Commit: `d129df447f670a9f5b1faab230a06a64c8240438`
- Re-reviewed Candidate Head Commit: `b8781c224748cceac7ec45706382e73c8f592825`
- Latest Re-reviewed Candidate Head Commit: `ddf4d452c1aec09dcbaabe075da13250646a26f9`
- Pull Request: #59

## Status before review

- Implementation: Not started; specification-only review
- Local Verification: Passed for documentation scope, diff, and secret scans
- Remote CI: Passed — Push Run `31954710446`; PR Run `31954749025`
- Human Review Required: No for the current deterministic local/test-only scope; mandatory escalation boundaries remain unchanged
- Merge: Blocked pending required specification corrections and a new exact-head review

## Developer resolution status

- Resolution date: 2026-08-17
- Status: Independent re-review completed for exact Head `ddf4d452c1aec09dcbaabe075da13250646a26f9`; Manager Decision remains `REQUEST_CHANGES`
- Correction Head: `ddf4d452c1aec09dcbaabe075da13250646a26f9`
- Remote CI for correction Head: Passed — Push Run `31981207234`; PR Run `31981211019`
- Developer local validation: Passed — exact two-document scope, Markdown table sanity, `git diff --check`, Gitleaks 8.28.0 history (99 commits) and worktree; no leaks found
- Implementation: Not started
- Migration/runtime/API/UI/adapter changes: None
- Prior Manager Decisions: `REQUEST_CHANGES` for reviewed Head `d129df447f670a9f5b1faab230a06a64c8240438` and re-reviewed candidate `b8781c224748cceac7ec45706382e73c8f592825`; neither is approval of the correction Head
- Merge: Blocked; PR #59 remains Draft through a final contract correction, new Push/PR CI, and another exact-head Independent Manager re-review

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

### Exact-head re-review evidence

- Candidate Head: `b8781c224748cceac7ec45706382e73c8f592825`
- Push Workflow/Run ID: `31979786417` — Passed at the candidate Head
- Pull Request Workflow/Run ID: `31979788160` — Passed at the candidate Head
- `quality-and-compose`: Passed in both runs; Backend 288 tests, Frontend 22 files / 134 tests, lint, typecheck, production build, dependency audit, Compose, Smoke, Playwright 14/14, and actionlint actually executed
- `secret-scan`: Passed in both runs; independent local Gitleaks 8.28.0 history/worktree scans also found no leaks
- Required steps skipped: None. `Upload Playwright failure artifacts` was conditionally skipped because Playwright passed.
- Scope: Candidate diff against `main` remains exactly the Stage 4A specification and this Manager Review document; no runtime, migration, API, UI, adapter, dependency, workflow, or parent Stage 04 specification change exists.
- Local exact-head verification: clean worktree before review; local/upstream/PR Head matched; base and merge-base matched `a90f6e0bb20d23da10edb66712d85261dafe14e8`; `git diff --check` and Gitleaks passed.

## Findings

| ID | Severity | File/Evidence | Finding | Required fix/test |
| --- | --- | --- | --- | --- |
| 4A-SPEC-001 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:193-202`; V10/V11 schema | The proposed Ad FKs prove ownership and decision/output identity, but they do not prove that the referenced output is an IMAGE, its `generated_asset_uuid` equals the Ad `asset_uuid`, the decision/output is APPROVED, preservation is PASSED, or the stored checksum matches immutable output/Asset evidence. Line 202 assigns these guarantees to application validation while also requiring direct-SQL rejection, so direct SQL can create invalid durable Ad evidence and the stated test contract is not implementable as written. | Define exact V12 database-level insert/coherence enforcement using additive composite keys/FKs and/or a deferred constraint trigger. Require direct-SQL tests that reject mismatched Asset/output, TEXT output, REJECTED/PENDING output, blocked preservation, inactive evidence at creation, and checksum mismatch; retain application validation as defense in depth. |
| 4A-SPEC-002 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:149`, `:236`, `:320`, `:386` | `budget_amount` is described as mutable through a later command and `UPDATE_BUDGET`/budget-port behavior is included, but the database trigger contract makes Ad Set policy fields immutable. The exact allowed V12 behavior and later 4B transition are contradictory, so implementation cannot know whether to reject, permit, or audit a budget mutation. | State one exact 4A contract. Recommended: make budget fields immutable in V12/4A, prohibit executable `UPDATE_BUDGET` orchestration in 4A, and explicitly defer a reviewed trigger/command transition to 4B. If V12 is intended to permit mutation, define the exact transition, version/currency/bounds/operation-evidence requirements and direct-SQL plus transaction tests now without exposing a public command. |
| 4A-SPEC-003 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:282-312`, `:458`; approved Stage 04 read-sync contract | The three partial unique indexes allow only one snapshot per entity/window/timezone/attribution/currency, while the table is append-only and recovery says later observations supersede earlier records. A delayed or corrected provider fetch for the same daily window cannot coexist without updating an immutable row or replacing the index in a later migration. | Define an exact append-only revision identity now or defer the metric table to 4D. If retained, permit multiple fetched revisions while rejecting an exact duplicate (for example with `fetched_at`/source fingerprint in the revision key), define the latest-snapshot selection rule, and require direct-SQL tests for delayed revision coexistence, exact-duplicate rejection, NULL preservation, and immutable history. |
| 4A-SPEC-004 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:204-251`, `:379-405` | The specification claims to be the exact implementation contract but leaves operation payload keys per type, normalized error/evidence allowlists, typed command fields, pause/resume/budget method signatures, fake external-ID prefix/hash length, and several port methods as unnamed or marker-only choices. These choices affect persistence hashes, idempotency, compatibility, and contract tests and would otherwise be made during implementation without exact-head approval. | Enumerate the exact command/result/reconciliation DTO fields, payload key schema per operation, normalized error/evidence schema, port method signatures, and deterministic fake ID algorithm. Explicitly defer unused delivery/metrics/credential contracts rather than approving empty marker interfaces. Add a contract-test matrix tied to those exact values. |

### Finding resolution evidence pending re-review

| ID | Developer resolution status | Revised specification evidence |
| --- | --- | --- |
| 4A-SPEC-001 | `RESOLVED_PENDING_RE_REVIEW` | Defines a deferred PostgreSQL Ad-evidence constraint trigger with transaction-boundary locks and exact Product/Asset/IMAGE output/APPROVED decision/PASSED preservation/checksum predicates; enumerates direct-SQL negative cases and SQLSTATE expectations. |
| 4A-SPEC-002 | `RESOLVED_PENDING_RE_REVIEW` | Separates immutable Ad Set policy/configuration from mutable `budget_amount`; requires a successful same-entity `UPDATE_BUDGET` operation pointer, canonical old/new/version evidence, server-owned ceilings, optimistic concurrency, and same-transaction Audit. |
| 4A-SPEC-003 | `RESOLVED_PENDING_RE_REVIEW` | Selects one model: append-only contiguous numbered revisions with per-window revision and source-fingerprint uniqueness, monotonic fetch time, exact duplicate rejection, and deterministic latest/as-of selection. |
| 4A-SPEC-004 | `RESOLVED_PENDING_RE_REVIEW` | Enumerates exact canonical payload keys/formats, typed port methods and record fields, closed write/reconciliation outcomes, normalized evidence/error allowlists, retry/reconcile entry contracts, deterministic fake IDs, idempotency bytes, state mutations, and shared contract tests; unused read/credential marker contracts are deferred. |

These statuses are a Developer Agent completion claim only. An independent reviewer must compare the new exact Head, diff, CI, and all four contracts before changing the Stage Gate decision.

### Exact-head re-review findings

Independent Manager, Software Architect, and Database specialist review of `b8781c224748cceac7ec45706382e73c8f592825` confirmed that the revision materially improves all four original areas, but it does not yet eliminate every implementation choice. Findings 4A-SPEC-003's append-only metric revision model is internally consistent. Findings 4A-SPEC-001, 002, and 004 remain only partially resolved, and the following exact corrections are required.

| ID | Severity | File/Evidence | Finding | Required fix/test |
| --- | --- | --- | --- | --- |
| 4A-SPEC-005 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:91-113`, `:294-306`, `:530`, `:536-544` | Accounts permit `FAKE` and `META`, only a fake adapter exists, and normalized evidence permits `FAKE or future approved provider`. Provider dispatch and persisted provider/evidence coherence therefore have no closed 4A contract. | Define the exact provider/account/environment/adapter matrix, dispatch rejection behavior, and closed V12 evidence-provider allowlist/coherence rule. Add contract and direct-SQL tests for permitted and rejected combinations. |
| 4A-SPEC-006 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:239`, `:296-326`, `:429-448`, `:494-530`, `:541-548`, `:580` | Retry and provider contracts still omit exact Java types/nullability/nesting, `PlatformOperationView`, local orchestration error types/codes, a deterministic 4A `max_attempts` rule, closed outcome-to-error-code sets, and fake fixtures/tests for every declared outcome. | Provide exact typed declarations and required/optional representation for commands, identities, policies, query/view, outcomes, evidence, and local errors. Define exact max attempts, every invalid-entry result/error, closed per-outcome code mappings, and deterministic fixtures/tests including temporary-unavailable and reconciliation-terminal outcomes. |
| 4A-SPEC-007 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:176`, `:272`, `:460`, `:526-530` | Batch and account-business-day budget ceilings do not define the consumed amount, included operation types, decrease behavior, date/timezone derivation, or concurrency-safe reservation. Several incompatible implementations satisfy the text. | Define exact accounting measure, window/date source, included operations, decrease/replay semantics, and concurrency enforcement with deterministic tests, or explicitly defer aggregate ceilings while retaining the exact per-entity bound. |
| 4A-SPEC-008 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:199-216`; existing V1/V4 Product and Asset mutation rules | The Ad constraint trigger revalidates only Ad insert/evidence-column updates. Later direct-SQL Product lifecycle or Asset type/checksum/lifecycle changes can invalidate a previously valid chain without firing it, while the text claims transaction-boundary full-chain integrity. | Choose and state ongoing-current-row coherence or creation-time snapshot semantics. For ongoing coherence, add reciprocal V12 deferred triggers or mutation prevention plus post-insert upstream-mutation negative tests. For snapshot semantics, narrow the invariant and define later divergence deterministically. |
| 4A-SPEC-009 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:174`, `:426-452`, `:574-576` | Reciprocal budget coherence covers operation status updates but does not explicitly reject inserting an `UPDATE_BUDGET` operation already `SUCCEEDED`; a constructed final row can bypass the intended transition evidence. | Require every operation INSERT to be `CREATED` with version/counters zero and claim/result/completion fields null. Require successful operations to have a matching finalized `SUBMIT` attempt, and add direct-SQL negatives for pre-succeeded INSERT and missing/mismatched attempt evidence. |
| 4A-SPEC-010 | MAJOR | `docs/stages/stage-04a-platform-foundation.md:174`, `:460-463`, `:576`, `:585` | Same-transaction Audit is stated, but database budget coherence does not require matching Audit and the matrix does not explicitly test omission. The current wording can be read as database enforcement. | Either define deferred database enforcement for matching Audit content, or label Audit as an application transaction invariant and require a transaction-level missing-Audit rollback/failure test. |
| 4A-SPEC-011 | MAJOR | `docs/stages/stage-04a-platform-foundation.md:357-391`; approved Stage 04 account-currency rule | Revision numbering, uniqueness, monotonic time, latest, and as-of semantics are consistent, but snapshot currency/timezone are not database-coherent with the referenced account. | Define a composite FK/deferred trigger for account currency/timezone coherence, or state the exact limitation and add application/direct-SQL coherence tests. A future as-of performance index is a nonblocking limitation while reads remain inert. |

No finding changes the approved Stage 04 human/security boundaries, and no escalation-policy trigger is present. Stage 4A implementation remains locked.

### Developer resolution evidence for findings 4A-SPEC-005 through 011

| ID | Developer resolution status | Correction evidence in Stage 4A specification |
| --- | --- | --- |
| 4A-SPEC-005 | `RESOLVED_PENDING_RE_REVIEW` | Closes V12/4A to `provider_key=FAKE`, environment `LOCAL`/`TEST`, exact profile/environment dispatch, no fallback, FAKE-only persisted evidence, reciprocal account/evidence DB coherence, and positive/negative contract/direct-SQL tests. |
| 4A-SPEC-006 | `RESOLVED_PENDING_RE_REVIEW` | Defines exact Java enums, nested records, Optional/null rules, sealed outcomes, evidence, `PlatformOperationView`, `PlatformOperationException`, closed stable/local codes, entry errors, `max_attempts=3`, reconciliation cap 3, fake temporary-unavailable and reconciliation-terminal fixtures, and exhaustive tests. |
| 4A-SPEC-007 | `RESOLVED_PENDING_RE_REVIEW` | Chooses explicit aggregate deferral: 4A enforces only current per-entity TWD bounds (`DAILY <= 100`, `LIFETIME <= 300`) and exact increase/decrease/replay/version-concurrency rules. Batch/account-day accounting and its required ledger/date/release semantics are locked to a new 4B review. |
| 4A-SPEC-008 | `RESOLVED_PENDING_RE_REVIEW` | Chooses creation-time snapshot semantics. PostgreSQL validates/locks the evidence chain at Ad INSERT; later allowed upstream divergence preserves the immutable historical row, blocks new Ad creation and future dispatch, and has explicit positive/negative direct-SQL tests. |
| 4A-SPEC-009 | `RESOLVED_PENDING_RE_REVIEW` | Requires every operation INSERT to be pristine `CREATED` with zero counters/version and null result fields; reciprocal deferred triggers require status-consistent latest finalized SUBMIT/RECONCILE attempts, including direct and reconciled success. Direct-SQL bypass cases are enumerated. |
| 4A-SPEC-010 | `RESOLVED_PENDING_RE_REVIEW` | Labels Audit as an application transaction invariant, not DB enforcement; requires a mandatory typed Audit writer, expected Audit per effective transaction, throwing-writer rollback, missing-writer construction/startup failure, and explicit direct-SQL limitation. |
| 4A-SPEC-011 | `RESOLVED_PENDING_RE_REVIEW` | Adds a deferred metric/account trigger that locks the account and requires matching immutable currency/timezone plus ACTIVE lifecycle, with matched/mismatched direct-SQL cases. |

These are Developer completion claims only. They do not alter the Manager findings, Decision, approval record, or Stage 4A lock. The Independent Manager must review the correction exact Head and full Remote CI before changing any decision.

### Re-review of correction Head `ddf4d452c1aec09dcbaabe075da13250646a26f9`

- Scope and identity: local, upstream, and PR Head matched; worktree was clean; base and merge-base remained `a90f6e0bb20d23da10edb66712d85261dafe14e8`; PR diff remained exactly the two Stage 4A documentation files.
- Push Run `31981207234`: Passed at the exact Head; `quality-and-compose` and `secret-scan` passed.
- PR Run `31981211019`: Passed at the exact Head; Backend 288/288, Frontend 22 files / 134 tests, lint, typecheck, build, audit 0 vulnerabilities, Compose, Smoke, Playwright 14/14, actionlint, and Gitleaks actually executed and passed.
- Required-step skips: None. Playwright failure-artifact upload alone was conditionally skipped because the tests passed.
- Local evidence: `git diff --check` and pinned Gitleaks history/worktree passed.
- Database specialist: No unresolved BLOCKING or MAJOR database finding; 4A-SPEC-007 through 011 are implementable and the V12 plan remains additive.
- Architecture specialist: 4A-SPEC-005 and most of 006 are resolved, but two contradictory outcome/retry contracts remain.

| ID | Severity | File/Evidence | Finding | Required fix/test |
| --- | --- | --- | --- | --- |
| 4A-SPEC-012 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:315`, `:665`, `:762` | Mutation success has three incompatible external-ID rules: the outcome table permits it, the typed rule requires it absent, and the fake adapter says mutations return the existing entity ID. | Select one exact provider-neutral rule and align the table, record validation, evidence fingerprint, persisted result, fake behavior, and tests. Recommended: create requires an ID; mutation carries no returned ID and only uses/verifies the existing entity ID from durable state. |
| 4A-SPEC-013 | BLOCKING | `docs/stages/stage-04a-platform-foundation.md:316`, `:326`, `:347`, `:466`, `:665`, `:702-739` | Attempt 3 converts a retryable outcome into terminal `PLATFORM_MAX_ATTEMPTS_EXCEEDED`, but the returned evidence remains retryable with retry seconds. Copying it violates the terminal status/error/evidence coherence rules. A fourth retry also matches both invalid-state and max-attempt local errors. | Define the exact application-generated terminal evidence transformation: finalized attempt/operation status, terminal code/result kind, retry-after removal, trace retention, treatment of the original retryable code, and persistence fields. Define fourth-retry error precedence and add DB/domain/fake tests. |
| 4A-SPEC-014 | MAJOR | `docs/stages/stage-04a-platform-foundation.md:775-790` | The Audit enforcement layer is clear, but `PlatformAuditEvent` is still prose-only and Transaction A may create more than one durable aggregate, leaving event cardinality/content to implementation. | Define the exact typed Audit event record/enum and a transaction-by-transaction event matrix, including entity plus operation creation, claim, result, reconcile, and budget changes; retain rollback, no-false-Audit, and redaction tests. |

No escalation-policy trigger exists. These are ordinary specification corrections within the approved local/test-only Stage 4A boundary; implementation remains locked.

## Known limitations

- Remote CI validates the current repository baseline only; it cannot execute the proposed V12 or future 4A implementation in this documentation-only PR.
- The seven-table model and durable attempt history are within the approved 4A persistence scope, but they are not approved for implementation until the revised exact Head receives Independent Manager `APPROVE`, is merged, and passes post-merge `main` CI.
- Authentication/RBAC/Tenant/security-model work, credentials, external access, spend, production, and real provider behavior remain mandatory human escalation boundaries.
- The repository still uses a manual Manager Gate; no automated `manager-gate` required check or equivalent branch protection exists.
- The npm allow-scripts and GitHub Actions Node runtime warnings above remain non-blocking technical debt.

## Stage Gate decision

- Decision: REQUEST_CHANGES
- Decision rationale: Candidate Head `ddf4d452c1aec09dcbaabe075da13250646a26f9`, exact scope, and all required CI passed; database review is clean and no human escalation trigger exists. However, 4A-SPEC-012 and 013 are mutually incompatible provider/retry result contracts, and 4A-SPEC-014 leaves required same-transaction Audit cardinality to implementation. The specification is not yet one exact executable contract.
- Required next action: Keep PR #59 Draft. The specification Developer Agent must correct only 4A-SPEC-012 through 014 in the Stage 4A specification/pending status, validate scope/diff/Gitleaks, push a new exact Head, and pass full Push/PR CI. Then perform another independent exact-head Manager Review. Do not create V12 or runtime implementation.
- Human approval required: No for the required corrections if they remain within the approved deterministic local/test-only 4A scope.
- Human approval reason/evidence: The findings are technical consistency and verification-contract corrections within the already approved Stage 04 boundaries. Escalate if resolution would change product cardinality, security/tenant authority, credentials/access, spend, production, destructive data behavior, or Stage 4B+ scope.

## Approval record

Not applicable. Decision is `REQUEST_CHANGES`; merge and Stage 4A implementation remain blocked.
