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

## Re-review cycle 2

### Identity and evidence

- Corrected implementation commit: `e25888baa7c6f1b275d9f7ac26a7d7014b108965`
- Reviewed superseding Head: `740be0e89b99ac64f35ab18bbf3ec6d17230dd4d`
- Push CI Run `32033126809` — Passed at the exact reviewed Head.
- Pull Request CI Run `32033130357` — Passed at the exact reviewed Head.
- Both runs executed `quality-and-compose` and `secret-scan`: Backend 377/377; Frontend 24 files / 141 tests, build and audit; Compose, Playwright 15/15, Smoke, actionlint, and pinned Gitleaks passed. Only the conditional failure-artifact upload was skipped.
- Worktree, upstream, PR Head, base, diff check, additive V13 scope, and V1-V12 immutability were independently verified.

The following cycle-1 items materially improved: original Campaign version is now part of Ad Set-create replay; request-time account identity and account-scoped Campaign/Ad Set reads were added; V13 no longer limits post-V13 enforcement to the two fixture accounts; reciprocal reservation/day validation and database-owned timestamps were added; BFF streaming and abort handling improved. These partial resolutions do not close the remaining required findings below.

### 4B-RR-010 — BLOCKING — state and budget replay omit entity identity

State replay compares operation type, expected version, and target state but not the path entity UUID or entity type. Budget replay compares version and amount but not the Ad Set UUID. A reused client request UUID against a different entity with the same intent can return the first operation and disclose its entity identity.

Required correction: compare the persisted entity UUID and entity type as part of immutable replay intent before lookup or write. Add state and budget changed-entity and changed-entity-type tests proving `PLATFORM_IDEMPOTENCY_CONFLICT` and zero database, Audit, disclosure, or adapter side effects.

### 4B-RR-011 — BLOCKING — retry and reconciliation bypass the fixed-account boundary

Operation GET/retry/reconcile checks only that a V13 batch exists and delegates to the generic Stage 4A service; it does not resolve the approved fixed account first or scope the operation and batch to that account. Campaign preview also performs Campaign Plan validation before account resolution, allowing resource-specific errors to precede a fail-closed account error.

Required correction: perform one fixed-account-first resolution for every preview, confirmation, read, retry, and reconciliation route, followed by account-scoped operation/entity lookup. Test wrong-account and misconfigured-account cases for no identifier disclosure, provider call, database write, or Audit event.

### 4B-RR-012 — BLOCKING — Backend error and safe-response acceptance remains incomplete

Validation errors still return empty `fieldErrors`, and the controller suite does not prove the closed route-specific source-to-public status, code, message, precedence, and HTTP 429 matrix or safe success/retryable/terminal/ambiguous/reconciliation JSON shapes.

Required correction: implement the approved route-aware field-error allowlist and exhaustive stable mapping. Add parameterized route/source/precedence tests and forbidden-field snapshots for every exposed outcome.

### 4B-RR-013 — BLOCKING — UI retry/stale behavior and vertical-slice evidence remain incomplete

The UI shows Retry for every `FAILED_RETRYABLE` operation without checking that `nextAttemptAt` is due. An operation 412 only displays an error rather than reloading and invalidating pending confirmation. Component and Playwright suites still cover only the create happy path.

Required correction: compute due-only retry eligibility, reload and invalidate on stale responses, and add component plus Playwright coverage for reads, pause, resume, budget increase/decrease and non-release warning, stale 412, retry, reconciliation, and unknown outcome, proving no automatic mutation or retry.

### 4B-RR-014 / 4B-DB-004 — BLOCKING — typed Audit closure and atomicity matrix remain incomplete

`ACCOUNT_DAY_RESERVED` events are not restricted to `CREATE_AD_SET` + `INITIAL` or `UPDATE_BUDGET` + `INCREASE`; invalid Campaign/state/decrease account-day events can be constructed. Tests do not establish all five command cardinalities, exact persisted AuditChange order/names/types/content, absent/present day paths, redaction/no-event behavior, or writer failure positions across every command and before commit.

Required correction: close the constructor operation-kind-subject-action-presence/arithmetic matrix and add negative constructor plus exact persisted Audit/cardinality/redaction/no-event/all-position rollback tests for all five command types.

### 4B-RR-015 / 4B-DB-005 — BLOCKING — mandatory direct-SQL, real concurrency, and populated-V12 legacy acceptance remains absent

Current V13 acceptance contains only basic availability/date, one happy reservation with simple mutation/delete rejection, and one arbitrary-account missing-batch case. It does not execute the approved direct-SQL identity/date/currency/kind/delta/aggregate/retroactive/unapproved/forged-clock/zero-release rollback matrix, barrier-controlled first-use concurrency below/at/above ceiling and decrease-first, deterministic loser rollback, or stable `40001`/`40P01` no-retry mapping. Migration compatibility does not create the required V12 platform entities, metrics, budget provenance, and coherent operation/attempt/evidence state matrix; it omits the canonical V12 checksum and legacy HTTP-inert proof.

Required correction: implement the complete case-exact Stage 4B specification acceptance matrix, including full post-failure snapshots, actual concurrent transactions, V12 checksum, rich byte-preserved V12 fixtures, allowed GET, and retry/reconcile `LEGACY_OPERATION_INERT` with zero writes, calls, or Audit.

### 4B-RR-016 — MAJOR — BFF exact limits and timeout evidence remain incomplete

The bounded streaming implementation is improved, but deterministic tests do not prove the 10-second timeout/504 case, exact 16 KiB and 1 MiB boundaries, wrong Backend content type/header exposure, and the complete forbidden-field safe DTO set.

Required correction: add deterministic timers, exact-boundary streams, header/content-type, and safe-response cases while retaining client-abort, oversized, network-reset, and redirect coverage.

### 4B-RR-017 — MAJOR — completion report remains broader than executable evidence

The report marks the API, UI, Audit, direct-SQL, concurrency, and legacy findings resolved despite the missing cases above.

Required correction: make every claim case-exact only after the corresponding executable test exists and record only the final superseding Head and its complete exact-head CI.

### Cycle-2 decision

Manager Decision: `REQUEST_CHANGES`

There is no human-escalation trigger. PR #62 remains Draft; merge and Stage 4C remain locked.

## Re-review cycle 3

### Identity and evidence

- Correction commit: `2d7970ead97cefc1e9a81aa6884952b18b073831`
- Reviewed superseding Head: `748ccf4d596aba1fe8d09f937264fc7a1dfa8dc4`
- Exact-head Push CI `32038483322` and Pull Request CI `32038485543` — Passed.
- Both runs executed `quality-and-compose` and `secret-scan`: Backend 413/413; Frontend 24 files / 147 tests, build and audit; Compose, Playwright 16/16, Smoke, actionlint, and Gitleaks passed. Only the conditional failure-artifact upload was skipped.
- Branch, upstream, PR Head, base, clean worktree, diff check, V1-V12 immutability, additive V13 scope, and all forbidden-boundary exclusions were independently verified.

Cycle-2 corrections materially resolved replay entity identity, fixed-account operation scoping, account-day Audit constructor closure, bounded BFF response handling, due-only retry, and several UI flows. The findings below remain required.

### 4B-RR3-001 — BLOCKING — required-field presence and route-aware validation remain incomplete

The Campaign Plan version request field uses a primitive value, is omitted from the explicit required-field set, and can deserialize as zero when absent. Invalid money and other declared fields can still produce empty or generic `body` field errors, and the route/outcome error and safe-response matrix remains incomplete.

Required correction: use presence-aware parsing for every required/non-null field, select the exact declared field in deterministic declaration order, and add exhaustive missing/null/invalid enum/money/UUID, precedence, HTTP 429 outcome, field-error, and safe JSON snapshot tests.

### 4B-RR3-002 — BLOCKING — entity stale handling does not invalidate the preview

After an entity 412, the UI reloads Campaign or Ad Set state but leaves the prior preview and confirmation actionable. A second confirmation can therefore apply stale preview intent using the refreshed ETag.

Required correction: clear preview and pending confirmation state before reload. Test stale Ad Set create, Campaign/Ad Set state, and budget confirmations, requiring a fresh preview and proving no automatic mutation.

### 4B-RR3-003 / 4B-DB-005 — BLOCKING — direct-SQL, real concurrency, and V12 legacy matrix remain incomplete

The labeled direct-SQL matrix updates already-committed immutable rows instead of constructing malformed INSERT/deferred-commit transactions. It therefore does not prove batch-first/retroactive, missing/extra/wrong operation/batch/reservation/day, unapproved operation, forged initial anchors, zero-release, reciprocal rollback, or reused-operation behavior. The concurrency test shares a parent Campaign lock, so transactions serialize before the ledger critical section; exact-at and above-cap are not fresh first-use cases and decrease-first is absent. Legacy evidence covers one failed-retryable row rather than the full coherent V12 state matrix.

Required correction: add malformed batch-first INSERT transactions that reach the intended deferred constraint and assert full rollback; position a database critical-section barrier after separate parent locks for fresh below/exact/above/decrease-first account/day scenarios and assert complete winner/loser state, UUID, version, and timestamps. Build and preserve coherent CREATED, SUBMITTING, FAILED_RETRYABLE, UNKNOWN_OUTCOME, RECONCILING, SUCCEEDED, submit-terminal, and reconciliation-terminal V12 fixtures plus related platform entities/evidence/metric/budget data; verify GET and inert retry/reconcile with zero effects.

### 4B-RR3-004 / 4B-DB-004 — BLOCKING — exact Audit persistence and post-final-append rollback are missing

Persisted tests establish subjects and change names/types but not exact old/new values, UUID equality, action, context, and durable ordering. Rollback failures occur during append positions only and do not cover failure after the final append succeeds but before commit.

Required correction: assert exact persisted event and AuditChange values/context using a durable sequence, add explicit no-event paths, and add a transaction hook proving complete rollback after the final successful append and before commit for the applicable command paths.

### 4B-RR3-005 — MAJOR — BFF timeout and exact-limit evidence is weaker than claimed

The timeout test injects an immediate synthetic error rather than proving the ten-second signal abort. Response tests cover the exact 1 MiB boundary and a larger payload, but not the exact limit-plus-one streaming cancellation.

Required correction: use deterministic fake timers/signals to prove abort at 10,000 ms and add an exact 1 MiB + 1 byte streaming cancellation case, retaining content-type, header, redirect, client-abort, and forbidden-field assertions.

### 4B-RR3-006 — MAJOR — completion evidence remains overstated

The report describes the missing cases above as executed and resolved.

Required correction: make the report case-exact only after the executable evidence exists, and record only the final superseding Head and completed exact-head CI.

### Cycle-3 decision

Manager Decision: `REQUEST_CHANGES`

There is no human-escalation trigger. PR #62 remains Draft; merge and Stage 4C remain locked.

## Re-review cycle 4

### Identity and evidence

- Correction commit: `223b7b484e33ce29bc904c86943bf7956462072e`
- Reviewed superseding Head: `c8789cb778146e4a1a1455c5b016b87e14ba7e2f`
- Exact-head Push CI `32041474659` and Pull Request CI `32041476709` — Passed.
- Both exact-head runs executed Backend 422/422, Frontend verification, Compose, Playwright 16/16, Smoke, actionlint, dependency audit, and Gitleaks; only the conditional failure-artifact upload was skipped.
- Branch, base, upstream, clean worktree, diff check, V1-V12 immutability, additive V13, and all forbidden-boundary exclusions were independently verified.

Required-field presence, stale-preview invalidation, post-final-append rollback, deterministic BFF timeout and limit-plus-one behavior, eight coherent operation status fixtures, and malformed deferred transactions materially improved. The following required gaps remain.

### 4B-RR4-001 — BLOCKING — canonical money field errors and provider-outcome matrix remain incomplete

Exponent, leading-plus, and trailing-zero money can pass the controller and fail only in the service, losing the declared `budgetAmount` or `newBudgetAmount` field. Tests cover excessive scale but not the complete canonical-money set. The required HTTP 429 and safe operation DTO matrix across create, state, budget, retry, and reconciliation routes is not executable.

Required correction: use one strict canonical route-boundary money validator that preserves the declared field, test exponent/plus/trailing-zero/scale/range cases for both money fields, and execute the complete route-family provider-outcome status/header/body/forbidden-field matrix.

### 4B-RR4-002 — BLOCKING — Audit order is not durable

Audit tests order rows using PostgreSQL `ctid`, a physical locator rather than an immutable logical event sequence. This cannot guarantee the approved per-operation Transaction A event order for runtime consumers.

Required correction: within the unmerged additive V13 boundary, add an immutable per-operation Audit ordinal with the required uniqueness and ownership constraints, persist it atomically for the ordered Stage 4B events, and assert exact order, values, context, and correlation by the durable ordinal. Preserve Stage 4A behavior and prove migration/Hibernate/direct-SQL compatibility.

### 4B-RR4-003 / 4B-DB-005 — BLOCKING — concurrency and direct-SQL evidence still does not match the approved cases

Exact-at and above-cap scenarios pre-populate reservations rather than using fresh account/day identities; decrease starts after positive reservations instead of being the first V13 ledger action on an absent row. Snapshot assertions do not compare the complete deterministic entity, operation, batch, reservation, day, attempt, Audit, and AuditChange graph with exact timestamps. The direct-SQL unapproved-operation case uses an invalid V12 CREATE_AD/AD_SET pair, and wrong account/Ad Set, forged day bootstrap, extra aggregate, delta/ceiling, committed zero-release, and database-derived date cases remain incomplete.

Required correction: use fresh isolated account/day identities and separate parent Campaigns for below/exact/above/decrease-first, with the barrier inside the ledger critical section and complete winner/loser snapshots. Exercise a valid V12 CREATE_AD/AD graph and the remaining malformed INSERT/deferred cases. Prove a committed zero reservation leaves the day aggregate unchanged and derive expected business date from the database anchor.

### 4B-RR4-004 — BLOCKING — serialization/deadlock mapping lacks route/transaction evidence

The `40001`/`40P01` test invokes the exception handler directly and does not prove a route calls the service/transaction once, propagates the commit-time failure, performs no automatic retry or Provider call, and leaves all persistent state unchanged.

Required correction: add route-level controlled SQL-state injection or a controlled real database failure and assert exactly one transaction/service invocation, the stable HTTP 409 response, no retry, no Provider call, and complete zero side effects.

### 4B-RR4-005 — BLOCKING — populated V12 byte-preservation and legacy scope remain incomplete

The fixture has eight operation statuses but omits Product, Asset, output, review evidence, Ad, and successful budget provenance. Only a subset of operations/attempts and related rows is snapshotted before and after migration.

Required correction: add the full coherent related V12 platform graph and compare every relevant row/value for all states before and after V13. GET every legacy operation and prove retry/reconciliation inertness with zero writes, calls, or Audit.

### 4B-RR4-006 — BLOCKING — Audit exact values, context, and no-event paths remain incomplete

Persisted tests assert selected subjects, names, and types but not all old/new UUID/date/kind/currency/budget/delta/aggregate values, exact correlation context, and the complete replay/invalid/stale/cap/concurrency/legacy zero-Audit matrix. Durable ordering is also covered by RR4-002.

Required correction: assert the complete five-command persisted event and change content plus explicit zero-Audit cases using durable event order.

### 4B-RR4-007 — MAJOR — completion report remains overstated

The report describes fresh first-use concurrency, a complete direct-SQL matrix, full V12 byte preservation, deterministic money/429 behavior, and durable exact Audit ordering that the current executable evidence does not establish.

Required correction: update the report only after each test exists and record the final superseding Head and its complete exact-head CI.

### Cycle-4 decision

Manager Decision: `REQUEST_CHANGES`

There is no human-escalation trigger. PR #62 remains Draft; merge and Stage 4C remain locked.

## Re-review cycle 5

### Identity and evidence

- Correction commit: `9dd4d12e53e9006a8fb3200215d14241ff27a1bb`
- Reviewed superseding Head: `762066ba237f92adde52e0b3ab1041d176d6db6d`
- Exact-head Push CI `32044180703` and Pull Request CI `32044183082` — Passed.
- Both runs executed Backend 437/437, Frontend 24 files / 149 tests, Compose, Playwright 16/16, Smoke, actionlint, dependency audit, and Gitleaks; only conditional failure-artifact upload was skipped.
- Branch, base, upstream, clean worktree, diff check, V1-V12 immutability, additive V13, and forbidden-boundary exclusions were independently verified.

Durable Audit ordinal, route-level serialization/deadlock propagation, valid V12 CREATE_AD/AD evidence, committed zero release, database-derived date, stale-preview invalidation, BFF limits, fixed-account scope, replay identity, and post-final Audit rollback are materially resolved. The following evidence and contract gaps remain.

### 4B-RR5-001 — BLOCKING — canonical policy amounts are mapped to the wrong error class

The controller now rejects canonical DAILY/LIFETIME amounts above policy bounds as HTTP 400 field validation. The approved contract reserves field validation for malformed canonical money and requires valid-but-out-of-policy amounts to reach policy enforcement and return HTTP 409 `PLATFORM_POLICY_REJECTED`.

Required correction: make route-boundary validation lexical/structural only while preserving exact money field attribution. Test DAILY and LIFETIME create and budget-update boundary and over-bound cases with the approved 400-versus-409 distinction.

### 4B-RR5-002 — BLOCKING — provider outcome and safe DTO matrix is not route-specific

Current tests pass a generic mocked UPDATE_BUDGET/AD_SET operation through the shared response mapper for multiple route labels. They do not execute the applicable Campaign/Ad Set create/state/budget/retry/reconciliation operation/entity combinations, both retryable codes, deterministic outcomes, or exact serialized key/value/absence snapshots.

Required correction: parameterize the actual route-specific operation/entity combinations and deterministic FAKE outcomes; assert exact status, headers, body keys/values, and forbidden-field absence for every approved route family and outcome, including HTTP 429.

### 4B-RR5-003 / 4B-DB-005 — BLOCKING — concurrency graphs remain incompletely asserted

Fresh account/day identities and separate parents are now used, but tests chiefly assert aggregates, counts, IDs returned, and loose timestamp ordering. They do not compare the complete deterministic winner and loser entity, operation, attempt, batch, reservation, day, Audit, and AuditChange graph, exact cross-row UUID relationships, timestamp anchors, or version transitions for below, exact, above, and decrease-first.

Required correction: capture transaction candidates and database time bounds and assert the complete expected persistent graph and byte-equivalent loser rollback for all four scenarios.

### 4B-RR5-004 — BLOCKING — isolated direct-SQL and full rollback matrix remains incomplete

The matrix lacks isolated extra-aggregate-update and distinguishable day/batch ceiling mismatch cases. Some wrong-account and forged-bootstrap failures do not compare the complete pre/post graph, and the forged row can fail as an orphan rather than proving the intended ceiling invariant.

Required correction: add isolated cases that reach the target constraint, assert exact SQLSTATE/invariant, and compare full pre/post state for extra aggregate mutation, ceiling/cap mismatch, and every remaining malformed transaction.

### 4B-RR5-005 — BLOCKING — populated V12 snapshot omits seeded tables and repeated inert-route proof

The rich fixture seeds additional account, Campaign Plan/product, prompt, generation, and related rows, but the before/after snapshot list omits several of them. Post-GET/retry/reconciliation inert snapshots cover a narrower subset.

Required correction: snapshot and compare the complete seeded V12 graph before/after V13 and before/after every inert route family, including accounts, Campaign Plans/products, prompt templates/versions, generation batches/jobs, all platform entities/evidence/metrics/operations/attempts, and applicable Audit data.

### 4B-RR5-006 / 4B-DB-004 — BLOCKING — exact Audit records, context, and no-event matrix remain incomplete

Durable ordinals and budget ledger values are sound, but all five commands are not asserted as complete expected records. Tests do not compare every action, subject UUID/type, actor/source/request correlation, old/new value, and change order; request IDs are only regex-validated. Replay, invalid, stale, cap, concurrency-loser, and legacy zero-Audit evidence is distributed and incomplete.

Required correction: assert complete ordered records for all five commands with exact inbound request correlation and add explicit before/after Audit snapshots for every required no-event path.

### 4B-RR5-007 — MAJOR — completion report remains overstated

The report claims policy field errors, exhaustive provider route snapshots, complete concurrency graphs, full seeded V12 preservation, and exact Audit context beyond the executable evidence.

Required correction: make claims case-exact only after each executable case exists and record final superseding-head CI.

### Cycle-5 decision

Manager Decision: `REQUEST_CHANGES`

There is no human-escalation trigger. PR #62 remains Draft; merge and Stage 4C remain locked.

## Re-review cycle 6

### Identity and evidence

- Correction commit: `8648d1eb8ad7e49337ed070df8055cb6357d88b5`
- Reviewed superseding Head: `9bdb55f644b9ff6e97eb2808d35309d10e59081c`
- Exact-head Push CI `32047314403` and Pull Request CI `32047318371` — Passed.
- Both runs executed Backend 444/444, Frontend 24 files / 149 tests, Compose, Playwright 16/16, Smoke, actionlint, dependency audit, and Gitleaks; only the conditional failure-artifact upload was skipped.
- Branch, base, upstream, clean worktree, diff check, V1-V12 immutability, additive V13, and forbidden-boundary exclusions were independently verified.

Policy 400/409 separation, isolated aggregate/ceiling/cap direct-SQL cases, full seeded V12 graph preservation, repeated legacy inert-route snapshots, durable ordinal, route SQL-state behavior, stale UI, BFF limits, and all prior security/provider boundaries are resolved. Three required evidence gaps remain.

### 4B-RR6-001 — BLOCKING — provider outcome matrix does not cross the deterministic FAKE boundary

The route-specific unit matrix constructs a mocked service and synthesized operation bodies. It validates the controller mapper but does not execute deterministic FAKE modes through MockMvc, transaction/persistence, adapter normalization, and the safe web DTO. A broken adapter-to-service-to-controller propagation could still pass.

Required correction: add end-to-end MockMvc cases whose deterministic FAKE request identities or approved modes produce both `PLATFORM_RATE_LIMITED` and `PLATFORM_TEMPORARILY_UNAVAILABLE` for every applicable Campaign/Ad Set create/state/budget/retry/reconciliation route family. Assert persisted operation/attempt status and evidence plus exact HTTP 429, ETag, Location, allowed JSON keys/values, and forbidden-field absence. Retain the pure mapper unit matrix as supplemental coverage.

### 4B-RR6-002 / 4B-DB-005 — BLOCKING — concurrency evidence is not a complete persistent graph

Tests validate selected operation fields, cross-row IDs, broad time bounds, aggregates, and Audit presence, but omit the actual Ad Set row and complete monetary/kind/currency/ceiling/version/timestamp/AuditChange values. The above-cap loser candidate entity UUID is not captured, so absence of an orphan entity cannot be proven directly.

Required correction: expose or inject a controlled deterministic UUID source for test candidates, construct complete expected row snapshots for below/exact/above/decrease-first, assert every entity/operation/attempt/batch/reservation/day/Audit/AuditChange row and cross-link including exact version/timestamp transitions, and prove every loser-owned row absent with byte-equivalent preservation of the winner graph.

### 4B-RR6-003 / 4B-DB-004 — BLOCKING — all-five Audit comparison is still partial

The test proves durable ordinal, type sequence, selected budget values, and exact request context, but accepts action from a CREATE/UPDATE allowlist, checks many subject UUIDs only for non-null, and does not compare every old/new value and change order for operation, attempt, entity, state, and budget records.

Required correction: build exact expected persisted Audit and AuditChange record lists for all five command shapes, ordered by durable ordinal and change order, and compare exact action, subject UUID/type, actor/source/request correlation, field/value type, and old/new values. Retain the complete no-event snapshot matrix.

### 4B-RR6-004 — MAJOR — completion report remains overstated

The report calls mocked results deterministic provider execution and describes selected concurrency/Audit assertions as complete graphs and exact records.

Required correction: make these claims case-exact after the executable cases exist and record final superseding-head CI.

### Cycle-6 decision

Manager Decision: `REQUEST_CHANGES`

There is no human-escalation trigger. PR #62 remains Draft; merge and Stage 4C remain locked.

## Stage Gate decision

Manager Decision: `REQUEST_CHANGES`

PR #62 must remain Draft. Merge and Stage 4C remain locked. This decision does not authorize credentials, network access, real Provider access, spend, production behavior, or any scope beyond the approved Stage 4B specification.
