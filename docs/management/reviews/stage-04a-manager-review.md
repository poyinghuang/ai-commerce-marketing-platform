# Stage 4A Manager Review

## Review identity

- Stage／Milestone: Stage 4A Platform Foundation implementation
- Review date: 2026-08-17
- Reviewer: Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository: `ai-commerce-marketing-platform`
- Branch: `codex/stage-04a-platform-foundation-v2`
- Base Commit: `681a51e7cca769e579bddc3f8157f2ab52c19497`
- Head Commit: `85832f0be732bf50f5196fea0994c730fb70e184`
- Pull Request: #60

## Status before review

- Implementation: Developer reported complete
- Local Verification: Reported passed; evidence compared with repository and CI
- Remote CI: Passed at the reviewed Head
- Human Review Required: No escalation-only boundary was triggered
- Merge: Not allowed; PR remains Draft

## Scope reviewed

- Approved scope: Additive V12 Stage 4A persistence foundation, provider-neutral contracts, deterministic fake provider, durable attempts/reconciliation/recovery, optimistic concurrency, typed Audit, and acceptance verification.
- Explicit out of scope: REST, UI, real Meta/network clients, credentials, production access, spend, authentication/RBAC/Tenant changes, schedulers, and Stage 4B+.
- Files reviewed: Actual PR diff, V12 migration, delivery domain/application/ports/fake adapter, persistence mappings, Stage 4A tests, completion report, and configuration changes.
- Forbidden or unexpected files: None. V1-V11 are unchanged; V12 is the only new migration.
- Completion report compared: Failed. Several claimed contract and acceptance matrices are not represented by executable tests.

## Architecture and contracts

- Architecture documents reviewed: `AGENTS.md`, `README.md`, management policies, approved Stage 04 and Stage 4A specifications, and prior approval records.
- Migration reviewed: Additive V12 reviewed independently at the database-contract level.
- Domain／Transaction／Audit boundary: Failed due to claim, finalization, evidence-coherence, and Audit cardinality defects listed below.
- API contract changes: Internal provider-neutral orchestration only; no REST contract change.
- Frontend／BFF contract changes: None.
- Backward compatibility: V1-V11 remain byte-for-byte unchanged; no destructive migration was found.
- Rollback／forward recovery: Recovery foundation exists, but the reconciled stale-mutation evidence contract is inconsistent with the approved specification.

## Impact

- Security impact: No credential, real-provider, production, auth, RBAC, Tenant, or secret boundary expansion found.
- Data impact: Blocking integrity defects can create invalid operation evidence, strand STARTED attempts, or emit Audit events for results that did not win persistence.
- Production impact: None authorized; Stage 4A remains LOCAL/TEST FAKE-only.
- External service／cost impact: None; no network-capable provider or paid call is in scope.

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status --short --branch` | Passed | Clean and synchronized before this review record |
| Diff check | `git diff --check origin/main...HEAD` | Passed | No whitespace errors |
| Commit history | `git log` and PR commit inspection | Passed | Six implementation/evidence commits reviewed |
| Backend tests | Push/PR CI | Passed | 310 tests reported; required acceptance coverage is incomplete |
| Migration tests | Testcontainers suite and code review | Failed | V12 atomicity and approved direct-SQL matrix are incomplete |
| Hibernate validation | Backend suite | Passed | V12 schema validates |
| Frontend lint | Push/PR CI | Passed | Unchanged frontend |
| Frontend typecheck | Push/PR CI | Passed | Unchanged frontend |
| Frontend tests | Push/PR CI | Passed | 22 files / 134 tests |
| Production build | Push/PR CI | Passed | Frontend build passed |
| Docker Compose config | Push/PR CI | Passed | Executed |
| Docker Compose cold start | Push/PR CI | Passed | Executed |
| Smoke tests | Push/PR CI | Passed | Executed |
| Playwright E2E | Push/PR CI | Passed | 14 tests passed |
| Gitleaks | Push/PR CI | Passed | Pinned Gitleaks 8.28.0 history scan |
| Dependency audit | Push/PR CI | Passed | 0 vulnerabilities reported |
| actionlint | Push/PR CI | Passed | Executed |

## Remote CI

- Workflow／Run ID: Push `31988028845`; PR `31988047202`
- Head SHA matches: Yes, both ran at `85832f0be732bf50f5196fea0994c730fb70e184`
- `quality-and-compose`: Passed in both runs
- `secret-scan`: Passed in both runs
- Required steps skipped: None. Failure-artifact upload was conditionally skipped because Playwright passed.
- Warnings／annotations: Existing Node action-runtime transition warning; reported Mockito/Byte Buddy and post-success Surefire fork shutdown warnings are non-blocking.

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| 4A-IMPL-001 | BLOCKING | `PlatformOperation.java:53`; `PlatformOperationTransactions.java:216`; V12 around line 349 | A due retry changes `FAILED_RETRYABLE` to `SUBMITTING` without clearing prior error, trace, and evidence, so V12 rejects attempt 2. | Clear prior result fields atomically during retry claim; test attempt 1 retryable through attempts 2/3, terminal conversion, identity reuse, Audit, and fourth-retry precedence. |
| 4A-IMPL-002 | BLOCKING | `PlatformOperationService.java:9-12`; `PlatformOperationTransactions.java:122-157` | `submit` and `retry` share a claim that accepts both CREATED and FAILED_RETRYABLE. Each public method therefore accepts the other's forbidden state. | Separate entry eligibility while preserving the common transaction; test submit-on-retryable and retry-on-created as zero-side-effect `PLATFORM_INVALID_OPERATION_STATE`. |
| 4A-IMPL-003 | BLOCKING | `PlatformOperationTransactions.java:121-157`; `PlatformOperationService.java:18-29` | Mutation external ID, expected entity version, desired-state edge, and prior budget evidence are not validated before claim/dispatch. Missing ID is discovered after STARTED is committed and is incorrectly caught as ambiguous. | Lock and validate the exact durable mutation target in Transaction B before claim. Parameterize missing-ID, stale/no-op state, stale budget, type/currency/bound cases across PAUSE, RESUME, and UPDATE_BUDGET; assert zero call/attempt/Audit/entity mutation. |
| 4A-IMPL-004 | BLOCKING | Outcome records; `PlatformOperationTransactions.java:200-300`; V12 evidence/coherence functions | Java and PostgreSQL do not enforce the closed variant/status/error/resultKind/retry/ID/fingerprint/provider matrix. Malformed provider results can roll back Transaction C and strand a claim instead of becoming ambiguous; direct SQL can pair valid but contradictory evidence with an operation. | Add exhaustive write/reconciliation validators and deferred DB matrix enforcement. Convert null/exception/malformed results to the exact ambiguous result. Add constructor/port and direct-SQL negatives for every mapping. |
| 4A-IMPL-005 | BLOCKING | Approved spec lines 481-484; `NormalizedPlatformEvidence.java`; V12 evidence validation; `PlatformOperationTransactions.java:254-260` | Reconciled-found mutation with stale entity version requires `RECONCILE/UNKNOWN_OUTCOME`, but Java/V12 reject it and implementation persists `STILL_UNKNOWN`. | Permit only the approved application-generated combination and test stale reconciled PAUSE, RESUME, and budget increase/decrease with exact attempt/operation/entity/Audit atomicity. |
| 4A-IMPL-006 | BLOCKING | `PlatformOperationTransactions.java:220-227` | Submit finalization ignores attempt and operation update counts and is not CAS. A losing concurrent finalizer can reload the winner and write false duplicate Audit. | CAS exact STARTED attempt and claimed operation, check row counts before Audit, and test same/different concurrent outcomes with one persisted result and exact events. |
| 4A-IMPL-007 | BLOCKING | Stage 4A completion report; `Milestone4ASchemaIntegrationTest`; `MigrationCompatibilityTest`; focused operation/fake tests | Completion evidence overstates retry, mutation, outcome, Audit rollback, concurrency, migration atomicity, and direct-SQL coverage. V12 collision rollback and the approved Stage 4A matrices are absent. | Implement the full approved acceptance matrix and revise the completion report to cite actual tests/results and the new exact-head CI. |
| 4A-IMPL-008 | MAJOR | V12 operation transition trigger around line 344 | Direct SQL can move FAILED_RETRYABLE to SUBMITTING before `next_attempt_at`; application-only due checking is bypassable. | Enforce due time using the server-controlled claim timestamp and add early/exactly-due/late direct-SQL tests. |
| 4A-IMPL-009 | MAJOR | V12 entity/result coherence around line 415 | Entity/result correlation relies on `completed_at = updated_at`; fixed/equal timestamps can make provenance ambiguous across sequential operations. | Correlate with exact operation identity rather than time equality; test two valid same-Instant state changes on one entity. |
| 4A-IMPL-010 | MAJOR | `PlatformOperationService.java:8`; `CreatePlatformOperationCommand.java`; approved identity formula | Requested actor ID is not NFC-normalized for persisted identity/idempotency, and the create command does not enforce the specification's non-null typed input contract. | Normalize/validate actor identity and all command fields at the boundary; test canonically equivalent actor IDs, nulls, and malformed inputs with stable local errors and zero writes. |

## Known limitations

- Account-day and batch budget aggregation remains intentionally deferred to Stage 4B; only approved per-entity limits belong here.
- Ad evidence is an immutable creation-time snapshot; later upstream divergence remains historical and must block a new eligible dispatch according to the approved Stage 4A boundary.
- Audit is an application transaction invariant and is intentionally not enforced for arbitrary direct SQL.
- The metric as-of performance index remains deferred pending Stage 4D query evidence.

## Stage Gate decision

- Decision: `REQUEST_CHANGES`
- Decision rationale: Exact-head CI passed and scope is correct, but unresolved BLOCKING findings affect retry viability, local-before-call safety, provider-result integrity, reconciliation semantics, concurrent finalization, and required acceptance evidence.
- Required next action: Keep PR #60 Draft. Correct only Stage 4A implementation and evidence, run full local validation, push a new exact Head, wait for complete Push and PR CI, and request fresh independent Code, Database, and Manager re-review. Do not merge or start Stage 4B.
- Human approval required: `No`
- Human approval reason／evidence: No escalation-policy trigger was found; these are correctable Stage 4A implementation defects.

## Approval record

- Not applicable. Manager Decision is not `APPROVE`.

## Re-review cycle 1 — 2026-08-17

### Exact-head evidence

- Candidate Head: `9d06823f3f999e78259569a69828b0dfb1a0329d`
- Implementation correction commit: `a6add8534e2d8dbc45c5c6b12b1aef1e47e60a96`
- Evidence-only commit: `9d06823f3f999e78259569a69828b0dfb1a0329d`
- Push CI: `31990824278` — Passed at the exact candidate Head
- Pull Request CI: `31990826957` — Passed at the exact candidate Head
- Scope: Stage 4A only; PR remained Draft/Open and V1-V11 remained unchanged.
- Independent Code Review: `REQUEST_CHANGES`
- Independent Database Review: `REQUEST_CHANGES`

Developer changes materially resolved the principal defects recorded as 4A-IMPL-001 through 006 and 010: retry result clearing, disjoint submit/retry entry eligibility, locked mutation validation before claim, typed outcome validation and ambiguous-result handling, stale reconciled-found representation, CAS finalization, and input/NFC boundary validation are present. The following exact-head findings remain; prior Manager findings and Decision remain authoritative until a later independent review explicitly approves a new exact Head.

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| 4A-IMPL-011 | BLOCKING | V12 operation/entity coherence around lines 436-473 | Create and desired-state success checks prove only that the entity currently has a matching result/version. A second distinct operation can reuse the result already applied by the first operation and commit SUCCEEDED without its own entity mutation. Timestamp correlation was removed, but exact-operation provenance is still absent. | Add an exact operation-identity/provenance or uniqueness mechanism for CREATE and PAUSE/RESUME equivalent to the budget provenance guarantee. Add direct-SQL negatives for second CREATE and state operations reusing an already-consumed result/version, plus success without an entity tuple change by that operation. Retain the valid same-Instant sequential test. |
| 4A-IMPL-012 | BLOCKING | Stage 4A completion report; Stage 4A schema, migration, Audit, fake/profile and orchestration tests | The approved acceptance matrix is still materially incomplete, while the completion report marks broad coverage resolved. Missing evidence includes the full Ad Product/Asset/output/review/checksum/snapshot matrix; all seven DELETE guards; direct/reconciled budget reciprocity negatives; metric revisions/coherence/concurrency; V11 fixture preservation for Campaign Plan/Asset/checksum/version; full successful reconciled mutation matrix; Audit failure before/between/after append positions; exact event/no-event matrix; and exhaustive fake/profile/fixture cases. | Implement the approved matrix with parameterized tests where appropriate. Exercise actual deferred triggers, not only helper functions. Revise the completion report to enumerate actual test classes/cases without unsupported broad claims. |
| 4A-IMPL-013 | MAJOR | V12 retry claim transition around line 362 | Due-time enforcement compares the old schedule with caller-controlled `NEW.claimed_at`; direct SQL can forge a future claim timestamp while PostgreSQL server time is still before due. | Enforce eligibility against PostgreSQL server time independently of the supplied timestamp; test forged-future, early, exact-due, and late cases. |
| 4A-IMPL-014 | MAJOR | V12 retry result/coherence functions | PostgreSQL does not prove `next_attempt_at = finalized SUBMIT attempt completed_at + evidence.retryAfterSeconds`; valid retry evidence can be paired with an arbitrary schedule. | Add deferred operation/attempt schedule coherence and negative tests for earlier/later schedules plus the exact valid schedule. |
| 4A-IMPL-015 | MAJOR | `PlatformContractSupport.money`; `PlatformCampaignCommand` | `stripTrailingZeros()` can return negative BigDecimal scale for integer amounts, violating the declared 0..6 money contract. Campaign create commands do not enforce `desiredState=PAUSED`. | Normalize negative scale to zero without rounding and test integer/trailing-zero/fractional values. Enforce PAUSED for campaign create and test invalid states. |

### Re-review decision

- Decision: `REQUEST_CHANGES`
- Decision rationale: Both exact-head CI runs passed and most initial findings improved, but exact-operation database provenance and the required acceptance evidence remain BLOCKING. Retry schedule integrity and typed money/create-state contracts also retain unresolved MAJOR findings.
- Required next action: Keep PR #60 Draft. Correct 4A-IMPL-011 through 015 within Stage 4A, run full local verification, push a new exact Head, wait for complete Push and PR CI, and repeat independent Code, Database, and Manager review. Do not merge or begin Stage 4B.
- Human approval required: `No`; no escalation-policy trigger exists.

## Re-review cycle 2 — 2026-08-17

### Exact-head evidence

- Candidate Head: `9d395100719899273460120d0275e6405cb42c3c`
- Push CI: `31992960900` — Passed
- Pull Request CI: `31992963434` — Passed
- Required jobs and steps: `quality-and-compose` and `secret-scan` passed; Backend 337 tests, frontend lint/typecheck/134 tests/build/audit, Compose, Playwright 14 tests, Smoke, actionlint, cleanup, and Gitleaks executed. Only failure-artifact upload was conditionally skipped after Playwright success.
- Scope and worktree: Clean, synchronized, Draft/Open, and Stage 4A-only; no forbidden provider, credential, production, security-boundary, spend, REST/UI, or Stage 4B change.
- Independent Code Review: `REQUEST_CHANGES`
- Independent Database Review: `REQUEST_CHANGES`

The exact-operation provenance uniqueness, database-server-time retry eligibility, deferred retry schedule equality, normalized money scale, and PAUSED Campaign create contract resolve 4A-IMPL-011 and 013 through 015. Acceptance coverage also improved materially. One required finding remains.

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| 4A-IMPL-016 | BLOCKING | Stage 4A completion report lines 52-58 and the Ad, operation/budget, metric, fake-provider, and Audit acceptance tests | The completion report's case-exact claims still exceed executable evidence. Missing independent cases include existing mismatched Product/output and TEXT output; pending/missing review; blocked preservation; inactive/non-image/null-checksum Asset and Asset lifecycle/type/checksum divergence; wrong/reused/non-success budget operation and currency/policy/bound/deferred rollback cases; metric base/window/attribution/freshness/fingerprint invalidity; exact fake outcome code/evidence/retry/trace/ID fields; and exact typed Audit sequence/content plus required no-event/rollback paths. Several broad RuntimeException/count assertions do not prove the intended invariant or unchanged post-state. | Add case-specific fixtures and tests that exercise actual deferred constraints, assert the intended SQLState/invariant and unchanged state, and verify exact normalized fake and Audit records. Cover the approved Stage 4A matrix or make the completion report strictly enumerate only actual cases; the approved acceptance scope itself is not reduced. |

### Re-review decision

- Decision: `REQUEST_CHANGES`
- Decision rationale: Exact-head CI and implementation integrity findings passed, but the remaining required acceptance evidence is BLOCKING under the approved specification and completion report claims.
- Required next action: Keep PR #60 Draft. Resolve 4A-IMPL-016, run the complete local baseline, push a new exact Head, wait for full Push and PR CI, and repeat independent Code, Database, and Manager review. Do not merge or start Stage 4B.
- Human approval required: `No`; this is a correctable Stage 4A verification/evidence gap.
