# Stage 04A Implementation Completion Report (Draft)

## Delivery identity

- Branch: `codex/stage-04a-platform-foundation-v2`
- Base: `681a51e7cca769e579bddc3f8157f2ab52c19497`
- Scope: internal PostgreSQL/provider-neutral foundation only
- Migration: additive `V12__create_platform_operation_foundation.sql`; V1-V11 unchanged
- Status: Manager findings resolved by Developer; full local re-verification passed and Remote CI re-verification is pending
- Manager Decision: `REQUEST_CHANGES` at reviewed Head `85832f0be732bf50f5196fea0994c730fb70e184`; preserved pending independent re-review
- Merge: Not started
- Stage 4B: Locked

## Implemented scope

- Seven FAKE-only Stage 4A tables, including durable operation attempts and append-only metric revisions.
- Additive V12 constraints and deferred reciprocal triggers enforce immutable identities, hard-delete protection, pristine paused entity construction, Ad approval/asset/output/checksum evidence, exact operation input JSON shapes, operation/attempt/evidence/result coherence, bounded state and budget mutations, metric revision identity, and account coherence.
- Exact provider-neutral command, outcome, evidence, reconciliation, policy, stable error, idempotency, and operation-view contracts; no credential or provider read-marker contract exists.
- Three-transaction orchestration persists and claims before the provider call, executes the adapter with no active Spring transaction, and atomically finalizes operation, attempt, entity mutation, and typed Audit afterward.
- Retry, max-attempt conversion, stale-claim recovery, unknown-outcome reconciliation, optimistic claims, and direct/reconciled create, state, and budget success paths follow the approved state machines.
- Deterministic fake adapter is available only when the explicit enable property is true under `local` or `test` and never under `production`; it implements the approved ID algorithm and normalized success, retryable, terminal, ambiguous, and reconciliation fixtures.
- Migration compatibility, Hibernate validation, direct-SQL negative tests, domain/canonicalization, fake contract tests, orchestration, concurrency, transaction-boundary, recovery, reconciliation, and Audit coverage are included.

## Boundaries preserved

No REST, BFF, UI, scheduler, real Meta adapter, HTTP/network client, credential/secret contract, production access, authentication/RBAC/Tenant change, paid operation, external spend, AI write path, or Stage 4B+ behavior is included. Default and production profiles expose no usable platform adapter.

## Developer resolution record

Developer resolution status: `RESOLVED_PENDING_RE_REVIEW`. This is not a Manager approval and does not change the preserved `REQUEST_CHANGES` decision.

| Finding | Developer resolution evidence |
| --- | --- |
| 4A-IMPL-001 | Retry claims atomically clear prior outcome fields; attempts 1-3, terminal conversion, stable identity, exact Audit, and fourth-retry precedence are exercised. |
| 4A-IMPL-002 | Submit and retry use disjoint transactional claims; cross-state entry attempts assert zero attempts, Audit, calls, or operation mutation. |
| 4A-IMPL-003 | Transaction B locks and validates the durable mutation target, external ID, version, state edge, and budget evidence before claim/dispatch. |
| 4A-IMPL-004 | Java outcome validation and deferred V12 operation/attempt/evidence validation enforce the closed matrix; malformed adapter results are normalized to ambiguity outside Transaction C. |
| 4A-IMPL-005 | Stale reconciled PAUSE, RESUME, budget increase, and budget decrease persist exact `RECONCILE/UNKNOWN_OUTCOME` ambiguity with no entity event or mutation. |
| 4A-IMPL-006 | Attempt and operation finalization use checked CAS updates; same and different concurrent finalizers persist one result and one exact Audit pair. |
| 4A-IMPL-007 | V12 collision rollback, direct-SQL matrix/due-time, retry, reconciliation, Audit rollback, and concurrency evidence were added; final counts below will be refreshed only from the completed rerun. |
| 4A-IMPL-008 | V12 rejects early retry claims using the server-controlled claim timestamp and accepts exactly-due/late claims. |
| 4A-IMPL-009 | V12 correlates entity mutations to the exact operation identity/version/provenance rather than timestamp equality. |
| 4A-IMPL-010 | Command references are non-null at construction and persisted actor identity is NFC-normalized; canonical-equivalent replay is tested. |

## Local verification record

| Check | Result | Evidence |
| --- | --- | --- |
| Backend/Testcontainers | Passed | 325 tests; 0 failures, 0 errors, 0 skipped; exact changed-area rerun 35/35 after the final evidence edits |
| V12 migration/direct SQL/Hibernate | Passed | Included in the full Backend suite; only V12 is added and V1-V11 are unchanged |
| Frontend lint | Passed | Existing unchanged frontend |
| Frontend typecheck | Passed | Existing unchanged frontend |
| Frontend tests | Passed | 22 files / 134 tests |
| Frontend production build | Passed | Next.js build completed |
| Frontend dependency audit | Passed | `npm audit --omit=dev`: 0 vulnerabilities |
| Compose config/cold health | Passed | Isolated full stack became healthy |
| Smoke | Passed | Backend/BFF health and Product create/read/update/archive/restore chain |
| Playwright | Passed | 14 Chromium tests |
| actionlint | Passed | Pinned v1.7.7 |
| Gitleaks | Passed | Pinned v8.28.0 history and worktree scans |
| `git diff --check` | Passed | No whitespace errors |

Known non-blocking warnings: Mockito/Byte Buddy reports its existing dynamic-agent deprecation warning. Windows Git reports that V12's LF working-tree line endings may be converted to CRLF when Git next rewrites that file. Maven Surefire reported a fork-JVM shutdown timeout after publishing the successful 325-test result; Maven exited successfully and no test failed or was skipped. The first local Compose attempt could not bind host port 8080 because an unrelated local process owned it; the incomplete stack was removed and the required cold-health, Playwright, and Smoke checks passed with the repository's supported `BACKEND_PORT=18080` and `FRONTEND_PORT=13000` overrides.

## Remote delivery status

- Draft PR: #60 (remains Draft)
- Push CI: Pending
- Pull Request CI: Pending
- Independent Manager Review: Re-review pending after exact-head CI
- Manager Decision: `REQUEST_CHANGES` preserved
- Merge and post-merge verification: Not started
- Stage 4B: Locked
