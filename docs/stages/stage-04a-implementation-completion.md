# Stage 04A Implementation Completion Report (Draft)

## Delivery identity

- Branch: `codex/stage-04a-platform-foundation-v2`
- Base: `681a51e7cca769e579bddc3f8157f2ab52c19497`
- Scope: internal PostgreSQL/provider-neutral foundation only
- Migration: additive `V12__create_platform_operation_foundation.sql`; V1-V11 unchanged
- Status: Manager re-review cycle 3 finding resolved by Developer; full local verification and correction implementation-head Remote CI passed; evidence-only exact-head CI pending
- Manager Decision: `REQUEST_CHANGES`; preserved pending independent re-review
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
| 4A-IMPL-016 | Case-specific acceptance tests now execute each requested Ad, budget, metric, fake-provider, and typed-Audit invariant. Every negative database case asserts its exact SQLState and exact unchanged durable post-state; the methods and cases are enumerated below. |
| 4A-IMPL-017 | Budget lower/upper bounds and operation/policy failures now use isolated fixtures with exact SQLState, named constraint or invariant, and full operation/attempt/entity/Audit snapshots. Ad evidence and reciprocal failures have the same exact checks. Fake submit/reconcile outcomes and replayed IDs are exact, while the parameterized typed-Audit suite covers submit failures through attempt 3, reconciliation outcomes, state/budget mutations, stale recovery, and no-event entry failures. |

### Case-exact acceptance evidence

- `Milestone4AAdEvidenceAcceptanceIntegrationTest#adRequiresExactActiveProductGeneratedImageApprovalPreservationAndChecksumSnapshot` executes generated-Asset/checksum/Product/review failures with exact SQLState and trigger invariant, including inactive Product and mismatched checksum, and asserts the full Ad/evidence source snapshot is unchanged. `#productAssetOutputReviewAndPreservationFailuresExposeExactSqlStateAndLeaveNoAd` executes mismatched Product/Asset and Product/output foreign keys (`23503`), missing review (`23503`), rejected/pending review, blocked preservation, and approved TEXT output (`23514`). `#inactiveNonImageNullChecksumAndLaterAssetDivergenceKeepHistoricalSnapshotButBlockNewAd` separately executes archived, VIDEO, and null-checksum Asset divergence (`23514`), proves the prior Ad snapshot remains byte-for-byte unchanged, and proves no new Ad is written.
- `Milestone4ASchemaIntegrationTest` exercises all seven table hard-delete guards, pristine inserts, deferred operation/attempt/outcome/evidence matrices, exact due/schedule boundaries, collision rollback, and schema/Hibernate constraints.
- `PlatformOperationIntegrationTest#wrongNonSuccessReusedBudgetOperationsAndPolicyCurrencyBoundsRollbackWithExactInvariant` uses a fresh otherwise-valid fixture for explicit zero, negative, and upper-bound budget values; wrong-kind, non-success, reused-success, currency, and budget-policy cases are independently executed. Each failure asserts SQLState `23514`, the exact named bound constraint or trigger invariant, and unchanged full operation, attempt, Ad Set, Audit, and Audit-change snapshots. `#directAndReconciledBudgetSuccessRequireReciprocalAmountAndProvenanceMutation` separately executes raw amount-only and provenance-only entity updates plus direct and reconciled success-without-entity paths, with the same exact failure and complete unchanged-state proof. `#reconciledFoundSuccessfullyAppliesPauseResumeAndBudgetIncreaseDecreaseAtomically` proves the four valid reconciled mutation paths.
- `Milestone4AMetricAcceptanceIntegrationTest#baseWindowAttributionFreshnessAndFingerprintInvalidCasesExposeExactConstraintAndPreserveState` separately executes negative impressions/reach/clicks/conversions/spend/revenue, equal/reversed window, invalid 7-day click/1-day view attribution, non-current freshness, and uppercase fingerprint (`23514`), plus duplicate fingerprint (`23505`), asserting the snapshot revision set remains unchanged after each failure. The remaining metric methods prove revision coexistence/latest/as-of semantics, nullable metrics, revision/fingerprint ordering, immutability/delete, account coherence, and one-winner concurrency.
- `MigrationCompatibilityTest#populatedV11RowsAndApprovedEvidenceSurviveUpgradeToV12` preserves Product, Campaign Plan, campaign-product, Asset checksum/version, AI output, and review evidence; `#failedV12MigrationRollsBackEveryPartialV12Object` proves collision atomicity and `#canonicalV1ThroughV11ContentRemainsStable` proves V1-V11 byte preservation.
- `PlatformTypedAuditAcceptanceIntegrationTest#successfulCreateEmitsExactTypedSequenceContentAndPersistedChangeOrder` asserts the exact seven-event typed sequence, correlation/entity/operation identities, transition fields, fingerprint, and persisted ordered change content. Its parameterized `#submitFailureAndAttemptThreeEmitExactTypedSequenceAndContent`, `#reconciliationOutcomesEmitExactTypedSequenceAndContent`, `#successfulStateAndBudgetMutationsEmitExactTypedSequenceAndContent`, and `#staleRecoveryEmitsExactTypedSequenceAndContent` methods assert ordered typed content for retryable, terminal, ambiguous, max-attempt, found, not-found, still-unknown, reconciliation-terminal, state, budget, submit-recovery, and reconciliation-recovery paths. `#replayAndInvalidEntryEmitNoTypedOrPersistedEvents` and parameterized `#rejectedEntryMatrixEmitsNoTypedOrPersistedAudit` prove replay, stale-version, invalid reconciliation, and not-due recovery have no typed or persisted Audit side effect. `PlatformOperationAuditRollbackIntegrationTest#auditFailureBeforeBetweenAndAfterFinalAppendRollsBackAttemptOperationEntityAndAudit` proves exact operation, attempt, entity, typed-event, and persisted-Audit rollback at every append position.
- `DeterministicFakePlatformAdapterTest#everyFixtureReturnsExactNormalizedCodeTraceIdRetryAndEvidenceFields` asserts exact normalized codes, trace IDs, retry delay, optionals, and evidence schema/provider/attempt/result/observed/ID/fingerprint fields for every submit fixture including `RECONCILE_FOUND` and every reconciliation fixture including `SUCCESS`. `#allCreateEntityPrefixesUseTheSameDeterministicIdentityAlgorithm` asserts the exact replayed Campaign, Ad Set, and Ad IDs. Other methods prove canonical money and Campaign PAUSED. `PlatformAdapterProfileTest` proves the fake exists only with explicit enablement under local/test and is absent under default/production or missing configuration.

## Local verification record

| Check | Result | Evidence |
| --- | --- | --- |
| Backend/Testcontainers | Passed | 359 tests; 0 failures, 0 errors, 0 skipped |
| V12 migration/direct SQL/Hibernate | Passed | Included in the full Backend suite; only V12 is added and V1-V11 are unchanged |
| Frontend lint | Passed | Existing unchanged frontend |
| Frontend typecheck | Passed | Existing unchanged frontend |
| Frontend tests | Passed | 22 files / 134 tests |
| Frontend production build | Passed | Next.js build completed |
| Frontend dependency audit | Passed | `npm audit --omit=dev`: 0 vulnerabilities |
| Compose config/cold health | Passed | Repository default Compose project became healthy on local ports 18080/13000 |
| Smoke | Passed | Backend/BFF health and Product create/read/update/archive/restore chain |
| Playwright | Passed | 14 Chromium tests |
| actionlint | Passed | Pinned v1.7.7 |
| Gitleaks | Passed | Pinned v8.28.0 history and worktree scans |
| `git diff --check` | Passed | No whitespace errors |

Known non-blocking warnings: Mockito/Byte Buddy reports its existing dynamic-agent deprecation warning. Windows Git reports that V12's LF working-tree line endings may be converted to CRLF when Git next rewrites that file. Maven Surefire reported a fork-JVM shutdown timeout after publishing the successful 359-test result; Maven exited successfully and no test failed or was skipped. npm reports the existing `unrs-resolver@1.12.2` allow-scripts warning. GitHub Actions reports that pinned checkout/setup actions target Node.js 20 and are forced onto Node.js 24 by the runner. The first two local Playwright runs selected a misspelled isolated Compose project/container name, so only the database assertion subprocesses failed to locate PostgreSQL; after selecting the running isolated container from Docker metadata, the complete 14-test suite passed. The existing PowerShell smoke harness used the single ETag value and the complete create/read/update/archive/restore chain passed.

## Remote delivery status

- Draft PR: #60 (remains Draft)
- 4A-IMPL-017 correction implementation Head: `db53f3e4975dc52137c3f3376ddcdd8c6201d609`.
- Push CI: Run `31997125161` passed at the correction implementation Head. `quality-and-compose` and `secret-scan` passed; Backend 359/359, frontend lint/typecheck/22 files and 134 tests/build/audit, Compose, Playwright 14/14, Smoke, actionlint, and Gitleaks executed. The failure-artifact upload step was correctly skipped because Playwright passed.
- Pull Request CI: Run `31997127676` passed at the same correction implementation Head with the same required jobs and execution evidence; the failure-artifact upload step was correctly skipped because Playwright passed.
- Required local execution: Backend 359/359, frontend lint/typecheck/22 files and 134 tests/build/audit, isolated Compose cold start, Smoke, Playwright 14/14, pinned actionlint 1.7.7, and pinned Gitleaks 8.28.0 history/worktree scans all executed and passed.
- Evidence-only Head CI: Pending after the exact correction-head Remote CI results are recorded; that Head will supersede the correction implementation Head for independent review.
- Independent Manager Review: Re-review pending after exact-head CI
- Manager Decision: `REQUEST_CHANGES` preserved
- Merge and post-merge verification: Not started
- Stage 4B: Locked
