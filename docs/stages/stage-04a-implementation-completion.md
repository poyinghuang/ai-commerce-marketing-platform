# Stage 04A Implementation Completion Report (Draft)

## Delivery identity

- Branch: `codex/stage-04a-platform-foundation-v2`
- Base: `681a51e7cca769e579bddc3f8157f2ab52c19497`
- Scope: internal PostgreSQL/provider-neutral foundation only
- Migration: additive `V12__create_platform_operation_foundation.sql`; V1-V11 unchanged
- Status: Manager re-review findings resolved by Developer; full local verification passed and exact-head Remote CI pending
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
| 4A-IMPL-011 | V12 partial unique provenance indexes permit only one successful CREATE per durable entity and one successful PAUSE/RESUME per entity version. `PlatformOperationIntegrationTest#directSqlCannotReplayCreateOrStateSuccessWithoutItsOwnEntityMutation` proves second-operation replay and success-without-own-mutation fail, while `PlatformOperationSameInstantIntegrationTest` preserves valid sequential same-Instant changes. |
| 4A-IMPL-012 | The executable acceptance matrix is case-exact below: Ad evidence/snapshot, all seven hard-delete guards, direct/reconciled budget reciprocity, metric revisions/account/concurrency, populated V11 preservation, successful reconciled mutation, Audit rollback positions/event cardinality, and deterministic fake/profile fixtures. |
| 4A-IMPL-013 | V12 overwrites caller-supplied claim time with PostgreSQL statement time and checks retry eligibility against server time; `Milestone4ASchemaIntegrationTest#exactOutcomeEvidenceMatrixAndRetryDueTimeAreDatabaseEnforced` covers forged-future, early, due, and late claims. |
| 4A-IMPL-014 | The deferred attempt-coherence trigger proves `next_attempt_at = completed_at + retryAfterSeconds`; `Milestone4ASchemaIntegrationTest#exactOutcomeEvidenceMatrixAndRetryDueTimeAreDatabaseEnforced` covers exact, one-second early, and one-second late schedules. |
| 4A-IMPL-015 | Money canonicalization converts negative stripped scales to scale zero without rounding, and Campaign construction accepts only `PAUSED`; `DeterministicFakePlatformAdapterTest#commandMoneyUsesScaleZeroThroughSixAndCampaignCreateIsPausedOnly` covers integer, trailing-zero, fractional, and rejected ACTIVE cases. |

### Case-exact acceptance evidence

- `Milestone4AAdEvidenceAcceptanceIntegrationTest#adRequiresExactActiveProductGeneratedImageApprovalPreservationAndChecksumSnapshot` exercises a valid immutable Ad snapshot plus wrong Product, archived Product, missing/wrong Asset, non-image Asset, missing/rejected review, wrong output Product, checksum mismatch, immutable evidence, and Ad hard-delete rejection.
- `Milestone4ASchemaIntegrationTest` exercises all seven table hard-delete guards, pristine inserts, deferred operation/attempt/outcome/evidence matrices, exact due/schedule boundaries, collision rollback, and schema/Hibernate constraints.
- `PlatformOperationIntegrationTest#directAndReconciledBudgetSuccessRequireReciprocalAmountAndProvenanceMutation` covers direct and reconciled budget increase/decrease success and missing entity/provenance, amount, currency/pointer negatives; `#reconciledFoundSuccessfullyAppliesPauseResumeAndBudgetIncreaseDecreaseAtomically` covers reconciled PAUSE, RESUME, budget increase, and budget decrease with exact attempt/entity/Audit evidence.
- `Milestone4AMetricAcceptanceIntegrationTest` covers revision 1/2 coexistence, latest/as-of selection, nullable metrics, skipped/repeated/nonmonotonic revisions, fingerprint duplicates, negative metrics, immutability/delete, account currency/timezone/activity coherence, and one-winner concurrent next-revision insertion.
- `MigrationCompatibilityTest#populatedV11RowsAndApprovedEvidenceSurviveUpgradeToV12` preserves Product, Campaign Plan, campaign-product, Asset checksum/version, AI output, and review evidence; `#failedV12MigrationRollsBackEveryPartialV12Object` proves collision atomicity and `#canonicalV1ThroughV11ContentRemainsStable` proves V1-V11 byte preservation.
- `PlatformOperationAuditRollbackIntegrationTest#auditFailureBeforeBetweenAndAfterFinalAppendRollsBackAttemptOperationEntityAndAudit` covers failures before, between, and after final Audit append with operation, attempt, entity, and event rollback. Operation integration tests cover exact successful/failure/no-event cardinality and concurrent finalizer one-winner behavior.
- `DeterministicFakePlatformAdapterTest` covers every submit outcome, reconciliation result, deterministic Campaign/Ad Set/Ad ID prefix, canonical money, and Campaign PAUSED boundary. `PlatformAdapterProfileTest` proves the adapter exists only with explicit enablement under local/test and is absent under default/production or missing configuration.

## Local verification record

| Check | Result | Evidence |
| --- | --- | --- |
| Backend/Testcontainers | Passed | 337 tests; 0 failures, 0 errors, 0 skipped |
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

Known non-blocking warnings: Mockito/Byte Buddy reports its existing dynamic-agent deprecation warning. Windows Git reports that V12's LF working-tree line endings may be converted to CRLF when Git next rewrites that file. Maven Surefire reported a fork-JVM shutdown timeout after publishing the successful 337-test result; Maven exited successfully and no test failed or was skipped. npm reports the existing `unrs-resolver@1.12.2` allow-scripts warning. The first Playwright attempt used an isolated Compose project name while DB-assertion fixtures intentionally address the repository's default project, producing six environment-only DB lookup failures; that stack was removed, the supported default project was started on `BACKEND_PORT=18080` and `FRONTEND_PORT=13000`, and the complete 14-test suite then passed.

## Remote delivery status

- Draft PR: #60 (remains Draft)
- Prior Push/PR evidence is superseded by the current correction set.
- Push CI: Pending the new exact Head.
- Pull Request CI: Pending the new exact Head.
- Independent Manager Review: Re-review pending after exact-head CI
- Manager Decision: `REQUEST_CHANGES` preserved
- Merge and post-merge verification: Not started
- Stage 4B: Locked
