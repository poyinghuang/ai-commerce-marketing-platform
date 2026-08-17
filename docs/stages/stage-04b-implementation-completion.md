# Stage 04B Implementation Completion Report

## Delivery identity

- Branch: `codex/stage-04b-campaign-adset-runtime`
- Base: `b43204bc1b06cd0c34c56540735b2b9694d81043`
- Scope: deterministic-FAKE Campaign and Ad Set vertical slice
- Migration: additive `V13__add_platform_budget_authorization_ledger.sql`; V1-V12 unchanged
- Status: Developer local verification complete; Remote CI pending
- Manager Decision: `PENDING`
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

## Executable acceptance evidence

- `Stage4BControllerIntegrationTest` covers preview and explicit PAUSED Campaign/Ad Set confirmation, deterministic success, normalized reads, Ad Set resume, budget increase, non-releasing decrease, exact account/day aggregate, durable reservations, and replay without a duplicate batch.
- `Milestone4BLedgerIntegrationTest` covers cold V13/Hibernate availability, Asia/Taipei midnight boundaries, exact anchored authorization persistence, and SQLSTATE `23514` update/delete rejection for batch, reservation, and day rows with unchanged aggregate state.
- `MigrationCompatibilityTest` covers V1-V13 cold migration, populated V11 through V12/V13 preservation, canonical V1-V11 checksums, V12 atomic collision, and V13 atomic collision rollback.
- `Stage4BProfileGateTest` covers the exact enabled test/fake configuration and missing flag, wrong adapter, default, and production fail-closed cases.
- Existing Stage 4A provider contract, three-transaction, retry/reconciliation, concurrency, evidence, direct-SQL, and typed Audit acceptance suites run unchanged except for a server-clock-based wait that removes host/database clock timing from retry eligibility.
- `platform-stage4b.test.ts`, `platform-meta-manager.test.tsx`, and `platform-stage4b.spec.ts` cover BFF allowlisting/sanitization, explicit confirmation, and the browser Campaign-to-Ad-Set happy path.

## Verification record

Remote CI Run IDs will be recorded only after the implementation commit is pushed and both exact-head runs finish. No unexecuted check is marked passed.

| Check | Current result |
| --- | --- |
| Backend compile | Passed |
| Focused Stage 4B Testcontainers/Hibernate | Passed |
| Frontend lint | Passed |
| Frontend typecheck | Passed |
| Frontend tests | Passed — 24 files / 138 tests |
| Full Backend | Passed — 366 tests, 0 failures/errors/skips |
| Frontend build | Passed |
| `npm audit --omit=dev` | Passed — 0 vulnerabilities |
| Compose cold build/start/health | Passed; isolated host ports 18080/13000 were used because unrelated local software occupied 8080 |
| Smoke | Passed — Backend health, BFF health, Product create/read/update/archive/restore |
| Playwright | Passed — 15/15, including Stage 4B preview/confirm Campaign-to-Ad-Set path |
| actionlint | Passed — 1.7.7 container |
| Gitleaks | Passed — pinned 8.28.0 history and working-tree scans, no leaks |
| `git diff --check` / V1-V12 immutability | Passed |
| Push/PR CI | Pending |

Known local warnings are the existing Mockito/Byte Buddy dynamic-agent deprecation, the existing Maven Surefire fork shutdown-after-success warning, and the package-approved `unrs-resolver` install-script warning. The first Playwright run correctly failed because the runtime-only frontend feature flag had been evaluated during static generation; the page was changed to runtime dynamic rendering, the stack was rebuilt, and the complete 15-test suite then passed without retry. The PR must remain Draft until independent Manager review.
