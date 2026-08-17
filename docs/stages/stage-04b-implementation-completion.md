# Stage 04B Implementation Completion Report

## Delivery identity

- Branch: `codex/stage-04b-campaign-adset-runtime`
- Base: `b43204bc1b06cd0c34c56540735b2b9694d81043`
- Scope: deterministic-FAKE Campaign and Ad Set vertical slice
- Migration: additive `V13__add_platform_budget_authorization_ledger.sql`; V1-V12 unchanged
- Status: Manager cycle 1 findings corrected; `RESOLVED_PENDING_RE_REVIEW`
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

## Executable acceptance evidence

- `Stage4BControllerIntegrationTest` covers preview and explicit PAUSED Campaign/Ad Set confirmation, deterministic success, normalized/account-scoped reads, Ad Set resume, budget increase, non-releasing decrease, exact ledger/Audit cardinality, Ad Set replay using the original parent version after the parent advances, changed-version zero-side-effect conflict, wrong-account non-disclosure, ambiguous fixed-account fail-closed behavior, and strict size/query/content-type/duplicate/unknown/null/canonical-UUID/NFC/secret boundary rejection.
- `Milestone4BLedgerIntegrationTest` covers cold V13/Hibernate availability, Asia/Taipei midnight boundaries, anchored authorization persistence, SQLSTATE `23514` update/delete rejection, and an arbitrary-account post-V13 Stage 4B operation that cannot commit without its reciprocal batch and leaves no operation or batch.
- `PlatformBudgetAuditEventTest` covers valid non-budget, initial, increase, and non-releasing-decrease event shapes plus exact rejection of mismatched entity/event/action/currency/aggregate contracts. The controller integration path additionally asserts five batch, three reservation, two positive account-day events and secret-sentinel absence.
- `MigrationCompatibilityTest` covers V1-V13 cold migration, populated V11 through V12/V13 preservation, canonical V1-V11 checksums, V12 atomic collision, and V13 atomic collision rollback.
- `Stage4BProfileGateTest` covers the exact enabled test/fake configuration and missing flag, wrong adapter, default, and production fail-closed cases.
- Existing Stage 4A provider contract, three-transaction, retry/reconciliation, concurrency, evidence, direct-SQL, and typed Audit acceptance suites run against their immutable V12 boundary; V13 compatibility tests separately prove the upgrade and post-V13 legacy boundary.
- `platform-stage4b.test.ts`, `platform-meta-manager.test.tsx`, and `platform-stage4b.spec.ts` cover BFF allowlisting/sanitization and bounded streaming, redirect/empty/invalid/forbidden/oversize/network/client-abort failure handling, explicit mutation confirmation, and the browser Campaign-to-Ad-Set happy path. Retry and reconciliation now require an additional explicit UI confirmation and never auto-run.

## Verification record

The implementation commit was pushed and both exact-head runs finished successfully. No unexecuted check is marked passed.

| Check | Current result |
| --- | --- |
| Backend compile | Passed |
| Focused Stage 4B Testcontainers/Hibernate | Passed |
| Frontend lint | Passed |
| Frontend typecheck | Passed |
| Frontend tests | Passed — 24 files / 141 tests |
| Full Backend | Passed — 377 tests, 0 failures/errors/skips |
| Frontend build | Passed |
| `npm audit --omit=dev` | Passed — 0 vulnerabilities |
| Compose cold build/start/health | Passed; isolated host ports 18080/13000 were used because unrelated local software occupied 8080 |
| Smoke | Passed — Backend health, BFF health, Product create/read/update/archive/restore |
| Playwright | Passed — 15/15, including Stage 4B preview/confirm Campaign-to-Ad-Set path |
| actionlint | Passed — 1.7.7 container |
| Gitleaks | Passed — pinned 8.28.0 history and working-tree scans, no leaks |
| `git diff --check` / V1-V12 immutability | Passed |
| Push/PR CI | Passed — Push `32032456950`; Pull Request `32032459611` |

Known local warnings are the existing Mockito/Byte Buddy dynamic-agent deprecation, the existing Maven Surefire fork shutdown-after-success warning, and the package-approved `unrs-resolver` install-script warning. The first Playwright run correctly failed because the runtime-only frontend feature flag had been evaluated during static generation; the page was changed to runtime dynamic rendering, the stack was rebuilt, and the complete 15-test suite then passed without retry. The PR must remain Draft until independent Manager review.

## Remote implementation-head evidence

- Draft PR: `#62`
- Implementation Head: `e25888baa7c6f1b275d9f7ac26a7d7014b108965`
- Push CI Run: `32032456950` — Passed
- Pull Request CI Run: `32032459611` — Passed
- Both runs completed `quality-and-compose` and `secret-scan`. Backend Testcontainers, Frontend verification, Compose build/health, all 15 Playwright scenarios, Smoke, actionlint, and Gitleaks executed successfully; only the conditional failure-artifact upload was correctly skipped.
- GitHub reported the existing non-blocking Actions Node.js 20 deprecation annotation for pinned `actions/checkout`; no Stage 4B check or acceptance step was skipped.

This evidence-only documentation commit creates a superseding Head. Its exact Push and Pull Request CI Run IDs are reported in the Developer handoff after both runs complete successfully; the completion report remains clean rather than creating an infinite evidence-commit cycle.
