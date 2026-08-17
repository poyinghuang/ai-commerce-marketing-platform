# Stage 4B Manager Review

## Review identity

- Stage／Milestone: Stage 4B Campaign and Ad Set vertical slice
- Repository: `ai-commerce-marketing-platform`
- Branch: `codex/stage-04b-campaign-adset-runtime`
- Base Commit: `b43204bc1b06cd0c34c56540735b2b9694d81043`
- Reviewed Head Commit: `56caf9ac4ee187df298a23d195ef367c0482cc3d`
- Pull Request: `#62`
- Review cycle: 1

## Status before review

- Implementation: Developer delivery complete at the reviewed Head
- Local Verification: Reported complete and independently compared with repository evidence
- Remote CI: Passed at the reviewed Head
- Pull Request: Open and Draft
- Merge: Not allowed
- Stage 4C: Locked

## Scope reviewed

- Additive V13 budget authorization ledger and Hibernate mappings.
- LOCAL/TEST deterministic-FAKE Campaign/Ad Set REST runtime and Stage 4A operation reuse.
- Server-only Next.js BFF and gated Campaign/Ad Set UI.
- Migration, database integrity, transaction, concurrency, Audit, API/security, frontend, Compose, Smoke, and Playwright evidence.
- Explicit exclusions remained credentials, network or real Provider access, production, spend, Auth/RBAC/Tenant changes, destructive migration, V1-V12 edits, and Stage 4C+ work.

The actual PR diff, commits, implementation, migration, tests, completion report, and Remote CI were inspected. V13 is additive and the V1-V12 migration blobs match `main`. No human-escalation boundary was entered.

## Verification reviewed

- Push CI Run `32028395654` — Passed at exact Head `56caf9ac4ee187df298a23d195ef367c0482cc3d`.
- Pull Request CI Run `32028399725` — Passed at the same exact Head.
- Both runs executed `quality-and-compose` and `secret-scan`.
- Backend: 366 tests, 0 failures, 0 errors, 0 skipped.
- Frontend: lint, typecheck, 24 files / 138 tests, production build, and `npm audit --omit=dev` with 0 vulnerabilities.
- Compose cold start and health, Playwright 15/15, Smoke, actionlint, and pinned Gitleaks completed successfully.
- Only the conditional Playwright failure-artifact upload was skipped because Playwright passed.
- `git diff --check` passed and the reviewed worktree was clean and synchronized.

Green CI establishes that the implemented tests passed; it does not resolve the missing reciprocal constraints, unimplemented API/UI contracts, or absent acceptance cases below.

## Findings

### 4B-RT-001 — BLOCKING — Ad Set replay omits the approved parent-version intent

`Stage4BTransactions` compares the durable Ad Set-create replay using parent UUID, budget type, and amount, but does not persist and compare the original Campaign `If-Match` version. Reusing a client request UUID with changed concurrency intent can be treated as an exact replay.

Required correction: include the original parent expected version in the canonical immutable intent and replay equality. Test that replay with the original version succeeds after the parent advances, a changed original version returns `PLATFORM_IDEMPOTENCY_CONFLICT`, and the conflict produces no database, Audit, or adapter side effect.

### 4B-RT-002 / 4B-DB-001..003 — BLOCKING — V13 ledger integrity is not reciprocal or universal

The V13 operation/batch check is limited to two fixture account UUIDs; other post-V13 Stage 4B operations can commit unbatched. Batch validation does not close the allowed operation-type set. Day coherence fires only when the day row changes, so a reservation can commit against an existing day without the matching aggregate update. The day timestamp guard also accepts caller-forged future values.

Required correction: enforce the approved closed Stage 4B operation set for every operation inserted after V13 while preserving only physically pre-V13 rows as inert legacy data; reject unrelated operation types and retroactive attachment. Add reciprocal deferred reservation/day sum, count, delta, version, and timestamp coherence tied to the database-owned batch anchor. Cover valid, zero-decrease, missing/wrong aggregate, arbitrary-account, unapproved-type, forged-time, midnight, and complete rollback cases with exact direct-SQL assertions.

### 4B-RT-003 — BLOCKING — fixed-account boundary is not fail-closed at request time

The runtime resolver does not verify the complete approved fixed-account identity and environment tuple or zero/multiple-candidate ambiguity, and Campaign/Ad Set reads and mutations are fetched by entity UUID without fixed-account ownership scoping. This permits wrong-account lookup or disclosure before a later failure.

Required correction: use one exact request-time fixed-account resolver for every read and operation, validate UUID, reference, provider, environment, lifecycle/archive state, currency, timezone, and fingerprint, and fail closed for zero, multiple, or mutated candidates. Use account-scoped entity queries and prove wrong-account identifiers are neither disclosed nor mutated.

### 4B-RT-004 — BLOCKING — Backend API and stable-error contract is incomplete

The controller does not establish the approved 16 KiB Backend request bound, duplicate/unknown-field rejection, canonical lowercase UUID, NFC and secret-marker validation, or exact query/body/content-type rules. `PLATFORM_CONTRACT_INVALID` falls through to HTTP 409 instead of 400; the closed source-to-public mapping, messages, field errors, precedence, and required HTTP 429 provider-retryable behavior are incomplete.

Required correction: implement strict bounded parsing and the complete route-specific status/code/message/field-error mapping. Add parameterized tests for malformed bodies, duplicates, unknown/null fields, canonical UUIDs, route precedence, retryable/terminal/unknown/reconciliation outcomes, and forbidden response fields.

### 4B-RT-005 — BLOCKING — the approved user-visible vertical slice is incomplete

The UI provides Campaign create, Ad Set create, and a static operation display only. Normalized reads, Campaign/Ad Set pause and resume, budget change, retry, reconcile, stale reload/invalidation, unknown-outcome handling, and their approved confirmation and warning flows are missing. Browser coverage exercises only the create happy path.

Required correction: implement all approved Stage 4B UI flows with preview plus explicit confirmation, state-dependent actions, stale 412 handling, due-only retry, unknown-only reconciliation, and non-releasing budget-decrease warning. Add frontend and Playwright acceptance for success and failure paths without automatic mutation or retry.

### 4B-RT-006 / 4B-DB-004 — BLOCKING — typed Stage 4B Audit contract and atomicity evidence are incomplete

`PlatformBudgetAuditEvent` does not close event-kind/subject/action combinations, UUID relationships, optional-field presence, reservation-kind amount rules, aggregate arithmetic, or positive-only account-day behavior. Exact Stage 4B Audit content, cardinality, redaction, no-event, and writer-failure rollback cases are absent.

Required correction: make the typed constructor/domain contract exhaustive. Test the ordered persisted events and changes for all five command types, invalid/no-event paths, sentinel redaction, and writer failure before, between, and after every append with complete entity, batch, reservation, day, operation, Audit, and Audit-change rollback.

### 4B-RT-007 / 4B-DB-005 — BLOCKING — required migration, concurrency, legacy, and stable-conflict acceptance is missing

Current V13 tests do not execute the approved reciprocal direct-SQL matrix, first-use concurrency below/at/above cap, decrease-first behavior, deterministic winner/loser rollback, rich populated-V12 upgrade, V12 checksum, or legacy HTTP-inert behavior. SQL serialization/deadlock failures are not mapped to stable `PLATFORM_LEDGER_CONCURRENCY_CONFLICT` HTTP 409 without automatic retry.

Required correction: implement the complete case-exact migration/direct-SQL/concurrency/legacy matrix, assert unchanged object snapshots after failed transactions, add stable `40001`/`40P01` mapping and tests, and retain no automatic transaction retry.

### 4B-RT-008 — MAJOR — BFF limits and abort behavior are not enforced during streaming

The BFF reads the full request and response before checking size, and its timeout is not composed with client disconnect.

Required correction: use bounded streaming reads for the 16 KiB request and 1 MiB response caps, combine timeout and client-abort signals, and test timeout, client abort, oversized streaming response, DNS/reset/invalid response, redirect, empty-route bodies, and forbidden response fields.

### 4B-RT-009 — MAJOR — completion evidence overstates executable coverage

The completion report claims exact ledger, Audit, concurrency, API, and UI acceptance that the current tests do not provide, and some exact-head CI fields remain described as pending after completion.

Required correction: after the implementation and tests are corrected, make the report case-exact and record only the final superseding Head and its completed Push and PR CI evidence.

## Known limitations and warnings

- The approved FAKE exception remains limited to deterministic FAKE in LOCAL/TEST behind the approved gates. Credentials, network, real Provider, paid execution, and production remain forbidden; real paths still require operator/approver separation.
- Existing Mockito/Byte Buddy and Maven Surefire shutdown-after-success warnings are non-blocking but must remain recorded.
- The package-approved `unrs-resolver` install-script warning and GitHub Actions Node.js 20-to-24 deprecation annotation are non-blocking.
- Repository branch protection does not provide an automated Manager Gate; the documented manual Gate remains authoritative.

## Required re-verification

After corrections, run focused V13 cold/populated-upgrade, Hibernate, direct-SQL, concurrency, Audit/rollback, API/BFF/frontend, and Playwright suites; then the complete Backend, Frontend, dependency audit, Compose, Smoke, Playwright, actionlint, Gitleaks, migration-immutability, and diff checks. Push the corrected Draft PR and wait for complete exact-head Push and Pull Request CI before requesting a new independent review.

## Stage Gate decision

Manager Decision: `REQUEST_CHANGES`

PR #62 must remain Draft. Merge and Stage 4C remain locked. This decision does not authorize credentials, network access, real Provider access, spend, production behavior, or any scope beyond the approved Stage 4B specification.
