# Stage 04C Specification Manager Review

## Review identity

- Stage／Milestone: Stage 4C Ad creative publication slice specification
- Review date: 2026-08-18
- Reviewer: Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository: `ai-commerce-marketing-platform`
- Branch: `codex/stage-04c-ad-creative-publication-specification`
- Base Commit: `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`
- Head Commit: `73a9a9f874f0bda44a477f2316a0f76d03e3b7ac`
- Pull Request: #63

## Status before review

- Specification: Complete developer draft; repository-owner settings approved on 2026-08-18
- Runtime implementation: Locked; not started
- Local Verification: Passed for documentation scope and repository integrity
- Remote CI: Passed at the exact reviewed Head after one disclosed infrastructure retry
- Human Review Required: Satisfied for the nine recorded product decisions
- Merge: Not started; PR remains Draft
- Stage 4D: Locked

## Repository-owner decision

- Decision date: 2026-08-18
- Decision: Approved all nine proposed Stage 4C product decisions without modification
- Boundary retained: deterministic `FAKE` in `LOCAL`/`TEST` only; no credential, network, real Provider, spend, billing, production, Auth/RBAC/Tenant, delivery, metrics, or Stage 4D behavior
- Review effect: Owner approval allowed Independent Manager Review; it did not pre-approve the implementation contract or runtime

## Scope reviewed

- Approved scope: Stage 4C deterministic-FAKE Ad creative publication specification, including proposed additive V14 integrity changes and local/test API/BFF/UI acceptance
- Explicit out of scope: Runtime, migration implementation, real provider/network, credentials, production, spend, Auth/RBAC/Tenant, delivery/metrics, and Stage 4D+
- Files reviewed: Stage 4C specification/review record, Stage 4B completion integration update, PR description, commits, and actual diff
- Forbidden or unexpected files: None; the PR diff is documentation-only
- Completion report compared: Stage 4B merge/post-merge evidence matched repository and CI state; Stage 4C has no implementation completion report

## Architecture and contracts

- Architecture documents reviewed: `AGENTS.md`, `README.md`, Manager/escalation policies, parent Stage 04, approved Stage 4A/4B specifications and completion records
- Migration reviewed: Proposed V14 only; actual V12/V13 functions, constraints, legacy state handling, and V1–V13 immutability were compared
- Domain／Transaction／Audit boundary: Stage 4A three-transaction and typed Audit contracts were compared with proposed create/state/recovery behavior
- API contract changes: Proposed Ad preview/confirm/read/state and inherited operation routes reviewed against actual Stage 4B controllers/services
- Frontend／BFF contract changes: Proposed fixed routes and safe DTO boundary reviewed against actual Stage 4B proxy/UI contracts
- Backward compatibility: Unresolved for pre-V14 unbatched Ad operations and Stage 4B legacy-inert routing
- Rollback／forward recovery: Forward-only intent is correct, but named commit-failure recovery graphs remain incomplete

## Impact

- Security impact: No executed security change. Deterministic FAKE LOCAL/TEST boundary is preserved; runtime remains locked
- Data impact: Documentation-only. Proposed V14 has no backfill or new business table, but exact legacy/constraint semantics require correction
- Production impact: None authorized
- External service／cost impact: None authorized

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Repository/branch/Head | Git and PR metadata | Passed | Local/upstream/PR Head matched `73a9a9f874f0bda44a477f2316a0f76d03e3b7ac`; base/merge-base matched `dcfb5e7...`; worktree clean before review-record edits |
| Diff check | `git diff --check origin/main...HEAD` | Passed | No whitespace errors |
| Commit history | `git log origin/main..HEAD` | Passed | Three specification/governance commits only |
| PR scope/patch | Git diff and PR files | Passed | Two new Markdown files and one Stage 4B completion documentation update; no runtime/migration/workflow/dependency file |
| Backend tests | Push `32091975723`; PR `32091978079` attempt 2 | Passed | Existing Backend/Testcontainers suite passed; this does not execute proposed V14 behavior |
| Migration/Hibernate | Same runs | Passed for current V1–V13 | V14 is specification-only and therefore not verified by runtime tests |
| Frontend lint/typecheck/tests/build | Same runs | Passed | Existing frontend verification completed |
| Dependency audit | Same runs | Passed | No production dependency vulnerability reported |
| Compose/Smoke/Playwright | Same runs | Passed | Push completed; PR attempt 2 completed Compose, E2E, and Smoke |
| Gitleaks/actionlint | Local Gitleaks 8.28.0 and same runs | Passed | History/worktree and Remote secret scans passed |

## Remote CI

- Push Run `32091975723`: `SUCCESS` at exact reviewed Head; `quality-and-compose` and `secret-scan` passed
- Pull Request Run `32091978079` attempt 1: `CANCELLED` when pinned Chromium download stalled until the 30-minute job timeout; downstream Compose/E2E/Smoke were skipped, so attempt 1 is not a pass
- Pull Request Run `32091978079` attempt 2: `SUCCESS` at the same exact Head; Backend, frontend, Chromium, Compose, Playwright, Smoke, cleanup, actionlint, and secret scan executed successfully
- Required steps skipped in successful runs: None. Playwright failure-artifact upload was conditionally skipped because E2E passed
- Warnings／annotations: Existing GitHub Actions Node.js runtime-transition annotation; non-blocking. Attempt-1 download timeout is retained as superseded infrastructure evidence

## Findings

Developer correction status: all findings below are `RESOLVED_PENDING_RE_REVIEW` in the specification working copy. This does not change the recorded `REQUEST_CHANGES` decision for reviewed Head `73a9a9f874f0bda44a477f2316a0f76d03e3b7ac`; a new exact Head, complete CI, and independent re-review are required.

### 4C-SPEC-001 — BLOCKING — Stage 4C ownership conflicts with legacy-inert operation routing

- Evidence: The specification reuses Stage 4B operation GET/retry/reconcile but creates no V13 operation batch. Actual `Stage4BService` requires a batch before retry/reconcile and otherwise returns `PLATFORM_LEGACY_OPERATION_INERT`.
- Impact: Every new CREATE_AD and AD PAUSE/RESUME operation can be treated as legacy, while broadly allowing unbatched AD rows would reactivate pre-V14 V12 operations.
- Required correction: Define a durable exact Stage 4C provenance/discriminator and account/type/entity/payload rules. Prove pre-V14 operations remain readable/inert while only Stage 4C-owned operations can retry/reconcile.
- Developer disposition: `RESOLVED_PENDING_RE_REVIEW` — new-shape CREATE_AD payload presence is the durable discriminator; Ad state operations require exactly one successful same-account new-shape create chain. GET remains scoped/readable while retry/reconcile rejects every legacy row before state disclosure. No V13 batch, marker column, or table is added.

### 4C-SPEC-002 — BLOCKING — Pre-dispatch current-evidence enforcement is incomplete

- Evidence: Evidence is checked during command Transaction A and success finalization, but initial/retry claim can dispatch after evidence or parent state diverges. Retry bypasses the proposed command Transaction A entirely.
- Impact: An invalid CREATE_AD or AD RESUME can reach the adapter before the deferred trigger rejects projection, contradicting no-call invalid-path requirements.
- Required correction: Specify exact current evidence/parent/account revalidation at every initial and retry claim, lock order, stable errors and precedence, zero attempt/call/Audit behavior, race tests, and the explicit PAUSE safety exception.
- Developer disposition: `RESOLVED_PENDING_RE_REVIEW` — one Stage 4C claim transaction revalidates the locked chain before `CREATED|FAILED_RETRYABLE -> SUBMITTING` and STARTED-attempt insertion. Initial and retry use it; PAUSE is explicitly safety-exempt. The specification fixes one application/trigger lock order and exact zero-effect race matrix.

### 4C-SPEC-003 — BLOCKING — V14 trigger and commit-failure recovery graphs are not exact

- Evidence: The draft does not enumerate direct versus reconciled success predicates, correlated AD RESUME operation, parent-version equality, deterministic lock ordering, legacy payload behavior, or full SUBMIT/RECONCILE commit-failure recovery records.
- Impact: Implementations can disagree under concurrent evidence/parent changes or misclassify an unrelated constraint/serialization defect as provider ambiguity.
- Required correction: Define exact operation/entity OLD→NEW edges, payload/row equality, parent desired/observed semantics, shared row-lock order, named constraint classification, byte-equivalent normalized evidence, counters/timestamps/versions, two-event recovery Audit, no adapter recall, one bounded recovery attempt, and fallback when recovery fails. Add direct/reconciled barrier tests and unrelated `23514`/`40001`/`40P01` tests.
- Developer disposition: `RESOLVED_PENDING_RE_REVIEW` — direct/reconciled create and state predicates, legacy finalization, desired-only parent eligibility, lock order, named constraints, and exact SUBMIT/RECONCILE UNKNOWN graphs are specified. Named dispatch violations and post-success `40001`/`40P01` receive one bounded recovery; unrelated integrity errors propagate and stale-claim recovery is the no-recall fallback.

### 4C-SPEC-004 — BLOCKING — API/BFF/UI contract is not implementation-exact

- Evidence: `AdPreview`, `StatePreview`, `Confirmation`, and `PlatformAdApiView` lack exact typed records, optionality/omission rules, safe field values, fingerprint formula, warning order, fixed messages, field-error order, source mapping, and route precedence. Parent `PAUSED`/`ACTIVE` does not say desired state, observed state, or both.
- Impact: Backend, BFF, and UI can implement incompatible disclosure, eligibility, and error behavior.
- Required correction: Publish exact Java/JSON/TypeScript records and route status/header/replay behavior; define desired-versus-observed semantics, safe mapping, field/error precedence and snapshot tests. If owner approval did not cover the state semantic, return that exact choice to the repository owner.
- Developer disposition: `RESOLVED_PENDING_RE_REVIEW` — exact ordered Backend records, Optional omission rules, fingerprint/warning formulas, safe allowlists, status/ETag/Location behavior, field order/messages, source mapping, and BFF snapshots are specified. Campaign/Ad Set desired state alone governs eligibility; observed state is informational and cannot authorize or block.

### 4C-SPEC-005 — BLOCKING — Audit requirements conflict with Stage 4A typed Audit

- Evidence: The draft says it reuses Stage 4A records but requires Ad creation Audit to contain evidence UUIDs/checksum fingerprint, fields absent from `PlatformAuditEvent`. It also says provider-failure no-event, while Stage 4A requires two baseline Transaction C events for every claimed normalized outcome.
- Impact: The required event cannot be represented and provider outcomes would violate the inherited Audit matrix.
- Required correction: Either retain the exact Stage 4A event shape and remove unsupported fields, or define one closed additive typed Stage 4C event. Distinguish zero-event pre-claim paths from claimed provider outcomes; define exact direct/reconcile/recovery cardinality, field order/types, redaction, and rollback tests.
- Developer disposition: `RESOLVED_PENDING_RE_REVIEW` — the correction retains the unmodified Stage 4A typed event and removes evidence UUID/checksum claims from Audit. It enumerates Transaction A, guarded claims, every claimed normalized outcome, entity success, immediate recovery, exact zero-event paths, ordering, rollback, and redaction.

### 4C-SPEC-006 — MAJOR (required) — Java canonicalizer/provider-command compatibility is missing

- Evidence: The proposed DB validator accepts a new `expectedParentVersion`, but the actual Java canonicalizer accepts only the legacy CREATE_AD key set. `PlatformOperationService` also defaults a missing creative mapping to `DEFAULT_IMAGE_V1`, conflicting with approved `APPROVED_IMAGE_ASSET_V1`.
- Impact: The approved new payload is rejected or silently mapped to the wrong creative contract.
- Required correction: Define dual legacy/new canonicalizer behavior, mandatory new-command shape, exact numeric canonicalization/replay comparison, and removal/rejection of provider-command defaults with regression tests for all non-CREATE_AD payloads.
- Developer disposition: `RESOLVED_PENDING_RE_REVIEW` — `canonicalizePersisted` accepts the two exact historical shapes, `canonicalizeNewCreateAd` requires the new shape, and expected parent version has one unsigned integer encoding. Provider reconstruction requires `APPROVED_IMAGE_ASSET_V1`; `DEFAULT_IMAGE_V1` is removed/rejected with zero-call tests.

## Known limitations

- Current CI verifies the documentation Head and current V1–V13 runtime only; it cannot prove proposed V14 behavior.
- Deterministic FAKE LOCAL/TEST is the maximum authorized provider boundary.
- No human-escalation trigger is currently present. The desired-versus-observed parent-state choice requires owner confirmation only if it was not included in the recorded product approval.
- Corrections are documentation-only and have not been independently re-reviewed. No V14 runtime evidence exists yet.

## Required retest

After correcting only the specification/review documents:

- run `git diff --check` and pinned Gitleaks history/worktree scans;
- push a new exact Head and wait for complete Push and Pull Request `quality-and-compose` plus `secret-scan`;
- repeat independent Architecture/API/Security and Database/Migration/Concurrency/Audit review;
- keep PR #63 Draft and keep runtime/Stage 4D locked.

## Manager Decision

`REQUEST_CHANGES`

PR #63 must remain Draft. Do not merge and do not start Stage 4C runtime implementation.
