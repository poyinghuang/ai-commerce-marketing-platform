# Stage 04B Implementation Completion Report

## Delivery identity

- Branch: `codex/stage-04b-campaign-adset-runtime`
- Base: `b43204bc1b06cd0c34c56540735b2b9694d81043`
- Scope: deterministic-FAKE Campaign and Ad Set vertical slice
- Migration: additive `V13__add_platform_budget_authorization_ledger.sql`; V1-V12 unchanged
- Status: Manager cycle 4 findings corrected; `RESOLVED_PENDING_RE_REVIEW`
- Manager Decision: `REQUEST_CHANGES` (preserved pending independent re-review)
- Merge: Not authorized
- Stage 4C: Locked

## Implemented scope

- V13 adds immutable operation batches and reservation ledger plus the serialized account/day aggregate. Database-owned Asia/Taipei dates, fixed TWD ceilings, hard-delete protection, deferred operation/batch/reservation/day coherence, and exact aggregate recomputation protect the authorization boundary.
- Immutable Hibernate read mappings and repositories cover all three V13 tables. Existing V1-V12 migrations remain unchanged.
- Campaign and Ad Set preview/confirm, normalized reads, pause/resume, bounded budget mutation, durable operation read/retry/reconcile, weak ETag/If-Match, server-owned account/actor/policy, stable errors, and idempotent replay are exposed only through the exact LOCAL/TEST FAKE gate.
- Campaigns and Ad Sets are created PAUSED. Ad Sets use `OFFSITE_CONVERSIONS`, `TW_BROAD_FEEDS_V1`, server-derived Campaign Plan schedules, TWD policy ceilings, and non-releasing decreases.
- Transaction A commits entity, batch, reservation/day aggregate, operation, and exact typed Audit before deterministic fake dispatch. Stage 4A submit/retry/reconciliation and optimistic mutation machinery remains the provider boundary.
- Next.js server-only Route Handlers enforce fixed routes, safe headers, size/time limits, no redirects, and sanitized failures. The gated page provides preview-first Campaign/Ad Set creation, explicit confirmation, reservation/capacity warnings, and operation status.

## Boundaries preserved

No credentials, real Meta/provider network access, production activation, external spend, authentication, RBAC, Tenant behavior, arbitrary targeting/placement/provider fields, destructive migration, V1-V12 rewrite, Stage 4C Ad creation, or later-stage behavior is included.

## Manager cycle 1 developer resolution

- `4B-RT-001`: resolved by durable `expected_entity_version` provenance for Ad Set create and exact replay comparison before parent lookup; changed intent is side-effect free.
- `4B-RT-002 / 4B-DB-001..003`: resolved by universal post-V13 Stage 4B operation/batch reciprocity, a closed batch operation set, reciprocal deferred reservation/day checks, and database-owned batch/day timestamps. Only rows physically present before V13 remain unbatched legacy rows.
- `4B-RT-003`: resolved by one exact request-time FAKE account resolver and account-scoped operation/Campaign/Ad Set queries.
- `4B-RT-004`: resolved by the Backend 16 KiB boundary, strict JSON settings, route/content/query/body rules, canonical UUID/NFC/forbidden-key rejection, stable contract/status mapping, and provider-retryable HTTP 429 handling.
- `4B-RT-005`: resolved by normalized reads, state/budget previews and confirmations, stale reload, due-only retry, unknown-only reconciliation, explicit retry/reconcile confirmations, and non-release/FAKE warnings.
- `4B-RT-006 / 4B-DB-004`: resolved by the closed typed budget Audit constructor and persisted cardinality/content/redaction assertions; existing Stage 4A Audit writer rollback-position suites continue to protect the shared same-transaction writer.
- `4B-RT-007 / 4B-DB-005`: resolved by V12-scoped regression suites, V13 cold/populated/atomic migration evidence, post-V13 universal legacy enforcement, and stable no-retry `40001`/`40P01` mapping.
- `4B-RT-008`: resolved by bounded streaming reads, composed timeout/client-abort signals, manual redirect refusal, and sanitized transport/response failure tests.
- `4B-RT-009`: this report names only executable cases and records the completed implementation-head Push and Pull Request CI evidence below.

## Manager cycle 2 developer resolution

- `4B-RR-010`: state and budget idempotent replay now compare the path entity UUID and entity type before returning any prior operation. Changed-entity and changed-type cases assert a stable conflict plus unchanged operations, attempts, batches, reservations, days, Audit rows, and adapter invocation count.
- `4B-RR-011`: every preview, confirmation, normalized entity read, operation read, retry, and reconciliation resolves the exact fixed FAKE account before resource lookup; operation and batch lookup is account-scoped. Wrong/mutated account tests assert no identifier disclosure, provider call, write, or Audit event.
- `4B-RR-012`: route-aware validation field names and the closed source-to-public status/code/message mapping are executable, including 429 retryable outcomes and 409 serialization/deadlock conflicts. Success and failure DTO tests reject credential, token, cookie, authorization, raw payload/evidence, URL, and Provider diagnostic fields.
- `4B-RR-013`: the UI exposes retry only when `nextAttemptAt` is due, invalidates pending confirmation and reloads after 412, and requires explicit confirmations for retry and reconciliation. Component and browser cases cover normalized reads, Campaign/Ad Set state, increase, decrease/non-release warning, retry, unknown reconciliation, stale reload, and zero automatic mutation.
- `4B-RR-014`: `ACCOUNT_DAY_RESERVED` is constructible only for `CREATE_AD_SET` + `INITIAL` and `UPDATE_BUDGET` + `INCREASE`. Exact typed Audit subject/change ordering, UUID relationships, old/new monetary values, request actor/source context, and redaction are asserted for Campaign create, Ad Set create, state, increase, and non-releasing decrease. Seventeen parameterized append-position cases plus five post-final-append/pre-commit cases compare complete entity, operation, attempt, ledger, Audit, and Audit-change snapshots.
- `4B-RR-015`: true batch-first direct-SQL transactions cover missing operation, retroactive attachment, missing/wrong/extra reservation or batch identity, missing day, aggregate omission, unapproved operation shape, reused operation, forged initial anchors, and valid zero-release, asserting target SQLSTATE `23514`, `23503`, or `23505` and unchanged full snapshots. Database-critical-section barriers after separate parent locks cover fresh below, exact, above-ceiling deterministic loser rollback, and decrease-first behavior. SQLSTATE `40001`/`40P01` maps to one 409 without service retry. A populated V12 Campaign/Ad Set/metric/budget fixture now contains coherent CREATED, SUBMITTING, FAILED_RETRYABLE, UNKNOWN_OUTCOME, RECONCILING, SUCCEEDED, submit-terminal, and reconciliation-terminal operation/attempt/evidence states; every state is byte-preserved through V13, readable by GET, and retry/reconcile inert with zero side effects.
- `4B-RR-016`: the BFF suite proves the exact 16 KiB request and 1 MiB response boundaries, deterministic 10-second 504, composed client abort, streaming oversize, reset/network failure, redirect refusal, empty body, wrong content type, header sanitization, normalized Ad Set GET allowlisting, and forbidden DTO fields.
- `4B-RR-017`: the executable evidence and counts below replace the superseded cycle-1 evidence. No Manager decision was altered.

## Executable acceptance evidence

- `Stage4BControllerIntegrationTest` covers exact replay entity/version binding, fixed-account-first and account-scoped routes, strict boundary precedence, exact ordered Audit/change content for five Transaction A shapes, normalized reads, state and budget operations, and unchanged database/Audit/adapter snapshots on conflicts.
- `Stage4BControllerErrorMappingTest` parameterizes the stable source status/code/message matrix, 429 retryability, 40001/40P01 conflict mapping without service retry, and safe field-error/output allowlists.
- `Milestone4BLedgerIntegrationTest` covers cold V13/Hibernate availability, Asia/Taipei midnight anchoring, immutable ledger success, actual malformed batch-first/deferred transactions, database-owned forged anchors, valid zero-release, exact SQLSTATE, and full rollback snapshots.
- `Stage4BLedgerConcurrencyIntegrationTest` uses transaction barriers for first-use below/exact/above-cap and decrease-first cases, proving deterministic loser rollback and exact aggregate/reservation results.
- `PlatformBudgetAuditEventTest` covers exact five-command constructor shapes and rejects mismatched operation/event/subject/action/presence/currency/arithmetic combinations. `Stage4BBudgetAuditRollbackIntegrationTest` executes 17 every-append-position failures and five post-final-append/pre-commit failures across Campaign create, Ad Set create, state, budget increase, and non-releasing decrease with complete snapshot equality.
- `MigrationCompatibilityTest` covers V1-V13 cold migration, populated V11 through V12/V13 preservation, canonical V1-V12 checksums, V12 atomic collision, and V13 atomic collision rollback. `Stage4BLegacyOperationIntegrationTest` adds a coherent populated V12 entity/metric/budget and eight-state operation/attempt/evidence matrix, byte preservation, allowed GET for each state, and inert retry/reconcile for each state.
- `Stage4BProfileGateTest` covers the exact enabled test/fake configuration and missing flag, wrong adapter, default, and production fail-closed cases.
- Existing Stage 4A provider contract, three-transaction, retry/reconciliation, concurrency, evidence, direct-SQL, and typed Audit acceptance suites run against their immutable V12 boundary; V13 compatibility tests separately prove the upgrade and post-V13 legacy boundary.
- `platform-stage4b.test.ts`, `platform-meta-manager.test.tsx`, and `platform-stage4b.spec.ts` cover exact BFF stream boundaries/timeout/abort/transport/sanitization, normalized Campaign and Ad Set reads, explicit state and budget confirmation, decrease warning, due-only retry, stale invalidation/reload, unknown-only reconciliation, and no automatic mutation or retry.

## Verification record

The implementation commit was pushed and both exact-head runs finished successfully. No unexecuted check is marked passed.

| Check | Current result |
| --- | --- |
| Backend compile | Passed |
| Focused Stage 4B Testcontainers/Hibernate | Passed |
| Frontend lint | Passed |
| Frontend typecheck | Passed |
| Frontend tests | Passed — 24 files / 149 tests |
| Full Backend | Passed — 437 tests, 0 failures/errors/skips |
| Frontend build | Passed |
| `npm audit --omit=dev` | Passed — 0 vulnerabilities |
| Compose cold build/start/health | Passed; isolated host ports 18080/13000 were used because unrelated local software occupied 8080 |
| Smoke | Passed — Backend health, BFF health, Product create/read/update/archive/restore |
| Playwright | Passed — 16/16, including Stage 4B Campaign/Ad Set reads, state, budget, stale, retry, and reconciliation paths |
| actionlint | Passed — 1.7.7 container |
| Gitleaks | Passed — pinned 8.28.0 history and working-tree scans, no leaks |
| `git diff --check` / V1-V12 immutability | Passed |
| Push/PR CI | Passed — Push `32040909708` (attempt 2); Pull Request `32040912428` |

Known local warnings are the existing Mockito/Byte Buddy dynamic-agent deprecation, the existing Maven Surefire fork shutdown-after-success warning, and the package-approved `unrs-resolver` install-script warning. During this cycle's local verification, a pre-baseline full Playwright run received the expected AI budget 503 because the README-required fixed AI budget variables were omitted; after correcting the test environment, all 16 cases passed. A focused Stage 4B run also exposed an over-restrictive normalized Ad Set GET BFF allowlist, which was corrected and covered by a route-specific unit test before the complete rerun. The PR must remain Draft until independent Manager review.

## Manager cycle 3 developer resolution

- `4B-RR3-001`: Campaign and Ad Set confirmation plan versions are presence-aware nullable request values and are in the explicit required-field order. Missing/null plan versions and invalid money now identify the declared field deterministically; existing exhaustive stable source/outcome/429/safe-DTO suites remain green.
- `4B-RR3-002`: all Ad Set create, Campaign/Ad Set state, and budget 412 paths clear the preview and pending confirmation before reloading the entity. The component matrix proves a fresh preview is required and no automatic mutation follows a stale result.
- `4B-RR3-003 / 4B-DB-005`: the direct-SQL suite now constructs malformed INSERT/deferred graphs rather than updating immutable rows; concurrency meets at the account/day critical section only after separate parent locks; the V12 compatibility fixture covers all eight required coherent states and checks GET plus inert retry/reconcile for each.
- `4B-RR3-004 / 4B-DB-004`: persisted budget Audit assertions now cover exact operation/batch/reservation/day UUID relationships, action, actor/source/request context, ordered typed field names, exact budget/reserved values, aggregate arithmetic, and absent day events. A dedicated hook after the last Audit append and before commit proves full rollback for all five commands.
- `4B-RR3-005`: deterministic fake timers prove the composed timeout signal aborts at exactly 10,000 ms, and a two-chunk exact 1 MiB + 1 response proves streaming cancellation and safe 502 mapping.
- `4B-RR3-006`: this report now names the executable cases and current counts only. Exact remote evidence is recorded by the evidence-only follow-up commit after the implementation Head completes both workflows.

## Remote implementation-head evidence

- Draft PR: `#62`
- Cycle-3 Implementation Head: `223b7b484e33ce29bc904c86943bf7956462072e`.
- Cycle-3 Push CI Run: `32040909708` — Passed on attempt 2. Attempt 1 stopped during GitHub-hosted `setup-java` preparation after `codeload.github.com` returned HTTP 429 twice and HTTP 503 once, before any repository validation step ran; attempt 2 executed and passed Backend, Frontend, Compose, Playwright, Smoke, actionlint, and Gitleaks.
- Cycle-3 Pull Request CI Run: `32040912428` — Passed; Backend, Frontend, Compose, Playwright, Smoke, actionlint, and Gitleaks executed successfully.
- The prior cycle-2 evidence remains `2d7970ead97cefc1e9a81aa6884952b18b073831`, Push `32037948132`, and Pull Request `32037950437`; it is superseded for the next Manager review.
- GitHub reported the existing non-blocking Actions Node.js 20 deprecation annotation for pinned `actions/checkout`; only the conditional Playwright failure-artifact upload was skipped after Playwright passed, and no Stage 4B validation or acceptance step was skipped.

This evidence-only documentation commit creates a superseding Head. Its exact Push and Pull Request CI Run IDs are reported in the Developer handoff after both runs complete successfully; the completion report remains clean rather than creating an infinite evidence-commit cycle.

## Manager cycle 4 developer resolution

- `4B-RR4-001`: controller-boundary money parsing now preserves the declared `budgetAmount` or `newBudgetAmount` field and accepts only canonical positive plain decimal text within the route policy. Exponent, leading plus, leading zero, trailing zero, excessive scale, zero/negative, and over-limit cases assert exact field errors and unchanged persistence. The provider retryable matrix covers Campaign create, Campaign/Ad Set state, budget, retry, and reconciliation for `RATE_LIMITED` and `TEMPORARILY_UNAVAILABLE`, with exact 429 status, `ETag`, `Location`, normalized body, and forbidden-field snapshots.
- `4B-RR4-002`: additive V13 now assigns an immutable, gapless, per-operation `stage4b_operation_ordinal` to Stage 4B-owned Audit rows under a transaction-scoped advisory lock. Ownership, non-negative value, and deferrable operation/ordinal uniqueness are database-enforced; supplied/forged values fail with SQLSTATE `23514`. Hibernate exposes the value read-only, legacy/Stage 4A Audit remains nullable, and migration atomicity is retained.
- `4B-RR4-003`: fresh below, exact, above-ceiling, and decrease-first concurrency fixtures use separate parent Campaigns and synchronize inside the account/day ledger critical section. Assertions cover deterministic winners/loser, complete entity/operation/batch/reservation/day/Audit rollback, exact UUID/version/timestamp graph, and fresh account/day identities. Direct-SQL cases additionally cover a valid V12 `CREATE_AD`/Ad graph rejected from the Stage 4B batch set, wrong account/Ad Set, forged bootstrap, missing/extra aggregates, delta/ceiling mismatches, committed zero-release, and database-derived business dates.
- `4B-RR4-004`: controlled `40001` and `40P01` failures are injected at the real route-to-ledger critical-section boundary. Each route test proves one controller/service/transaction attempt, stable 409 `PLATFORM_LEDGER_CONCURRENCY_CONFLICT`, no provider call or automatic retry, and byte-equivalent persistence after rollback.
- `4B-RR4-005`: the populated V12 upgrade fixture now contains Product ownership, source/generated IMAGE Assets, AI prompt/template/batch/job/output, approved review evidence, Platform Ad checksum linkage, Campaign/Ad Set/metric rows, successful budget provenance, and the complete eight-state operation/attempt/evidence matrix. Every relevant table is snapshotted before V13 and byte-compared after migration; every operation remains readable and retry/reconcile remains inert without writes or adapter calls.
- `4B-RR4-006`: the five command shapes assert exact durable Audit ordinal, action, subject UUID/type, request/actor/source context, ordered typed changes, operation/batch/reservation/day UUID relationships, monetary old/new values, and aggregate arithmetic. Replay, invalid/stale requests, cap/concurrency rollback, route SQL failure, and legacy operation paths assert no new Audit event; existing every-append-position and post-final-append rollback suites remain green.
- `4B-RR4-007`: this section and the verification table name only executable cases and observed results. Exact cycle-4 implementation-head and superseding evidence-head CI identities are recorded only after their workflows complete; the preserved Manager Decision remains `REQUEST_CHANGES` pending independent re-review.
