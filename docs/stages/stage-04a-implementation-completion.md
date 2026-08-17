# Stage 04A Implementation Completion Report (Draft)

## Delivery identity

- Branch: `codex/stage-04a-platform-foundation-v2`
- Base: `681a51e7cca769e579bddc3f8157f2ab52c19497`
- Scope: internal PostgreSQL/provider-neutral foundation only
- Migration: additive `V12__create_platform_operation_foundation.sql`; V1-V11 unchanged
- Status: Manager re-review cycle 2 finding resolved by Developer; full local verification passed and exact-head Remote CI pending
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

### Case-exact acceptance evidence

- `Milestone4AAdEvidenceAcceptanceIntegrationTest#productAssetOutputReviewAndPreservationFailuresExposeExactSqlStateAndLeaveNoAd` executes mismatched Product/Asset and Product/output foreign keys (`23503`), missing review (`23503`), rejected/pending review, blocked preservation, and approved TEXT output (`23514`). `#inactiveNonImageNullChecksumAndLaterAssetDivergenceKeepHistoricalSnapshotButBlockNewAd` separately executes archived, VIDEO, and null-checksum Asset divergence (`23514`), proves the prior Ad snapshot remains byte-for-byte unchanged, and proves no new Ad is written.
- `Milestone4ASchemaIntegrationTest` exercises all seven table hard-delete guards, pristine inserts, deferred operation/attempt/outcome/evidence matrices, exact due/schedule boundaries, collision rollback, and schema/Hibernate constraints.
- `PlatformOperationIntegrationTest#wrongNonSuccessReusedBudgetOperationsAndPolicyCurrencyBoundsRollbackWithExactInvariant` executes wrong-kind, non-success, reused-success, currency, budget-policy, and lower/upper-bound violations (`23514`) and asserts the complete Ad Set row is unchanged. `#directAndReconciledBudgetSuccessRequireReciprocalAmountAndProvenanceMutation` executes direct and reconciled missing reciprocal mutation/provenance paths (`23514`) and asserts operation, attempt, Ad Set, and Audit post-state are unchanged; `#reconciledFoundSuccessfullyAppliesPauseResumeAndBudgetIncreaseDecreaseAtomically` proves the four valid reconciled mutation paths.
- `Milestone4AMetricAcceptanceIntegrationTest#baseWindowAttributionFreshnessAndFingerprintInvalidCasesExposeExactConstraintAndPreserveState` separately executes negative impressions/reach/clicks/conversions/spend/revenue, equal/reversed window, invalid 7-day click/1-day view attribution, non-current freshness, and uppercase fingerprint (`23514`), plus duplicate fingerprint (`23505`), asserting the snapshot revision set remains unchanged after each failure. The remaining metric methods prove revision coexistence/latest/as-of semantics, nullable metrics, revision/fingerprint ordering, immutability/delete, account coherence, and one-winner concurrency.
- `MigrationCompatibilityTest#populatedV11RowsAndApprovedEvidenceSurviveUpgradeToV12` preserves Product, Campaign Plan, campaign-product, Asset checksum/version, AI output, and review evidence; `#failedV12MigrationRollsBackEveryPartialV12Object` proves collision atomicity and `#canonicalV1ThroughV11ContentRemainsStable` proves V1-V11 byte preservation.
- `PlatformTypedAuditAcceptanceIntegrationTest#successfulCreateEmitsExactTypedSequenceContentAndPersistedChangeOrder` asserts the exact seven-event typed sequence, correlation/entity/operation identities, transition fields, fingerprint, and persisted ordered change content. `#replayAndInvalidEntryEmitNoTypedOrPersistedEvents` proves replay and stale-entry no-event behavior. `PlatformOperationAuditRollbackIntegrationTest#auditFailureBeforeBetweenAndAfterFinalAppendRollsBackAttemptOperationEntityAndAudit` proves exact operation, attempt, entity, typed-event, and persisted-Audit rollback at every append position.
- `DeterministicFakePlatformAdapterTest#everyFixtureReturnsExactNormalizedCodeTraceIdRetryAndEvidenceFields` asserts the exact deterministic Campaign ID, trace ID, fingerprint, normalized code, retry delay, optionals, and evidence schema/provider/kind/result/observed fields for every submit and reconciliation fixture. Other methods prove deterministic Campaign/Ad Set/Ad IDs, canonical money, and Campaign PAUSED. `PlatformAdapterProfileTest` proves the fake exists only with explicit enablement under local/test and is absent under default/production or missing configuration.

## Local verification record

| Check | Result | Evidence |
| --- | --- | --- |
| Backend/Testcontainers | Passed | 344 tests; 0 failures, 0 errors, 0 skipped |
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

Known non-blocking warnings: Mockito/Byte Buddy reports its existing dynamic-agent deprecation warning. Windows Git reports that V12's LF working-tree line endings may be converted to CRLF when Git next rewrites that file. Maven Surefire reported a fork-JVM shutdown timeout after publishing the successful 344-test result; Maven exited successfully and no test failed or was skipped. npm reports the existing `unrs-resolver@1.12.2` allow-scripts warning. GitHub Actions reports that pinned checkout/setup actions target Node.js 20 and are forced onto Node.js 24 by the runner. The first local PowerShell smoke harness attempt passed a multi-value response-header object directly as `If-Match`, so PowerShell rejected the header format before issuing the PATCH; the corrected harness selected the single ETag value and the complete create/read/update/archive/restore chain passed.

## Remote delivery status

- Draft PR: #60 (remains Draft)
- Correction implementation Head: `db752e7ced0a6eff0772c1c657eeed22282dbefa`.
- Push CI: Run `31994807572` passed at the correction implementation Head; `quality-and-compose` and `secret-scan` both passed.
- Pull Request CI: Run `31994812462` passed at the correction implementation Head; `quality-and-compose` and `secret-scan` both passed.
- Required execution: Backend 344/344, frontend lint/typecheck/22 files and 134 tests/build/audit, Compose cold start, Smoke, Playwright 14/14, pinned actionlint, and Gitleaks all executed and passed. Only failure-artifact upload was conditionally skipped because Playwright passed.
- Evidence-only Head CI: Pending after the exact Remote CI results are recorded; that Head will supersede the correction implementation Head for independent review.
- Independent Manager Review: Re-review pending after exact-head CI
- Manager Decision: `REQUEST_CHANGES` preserved
- Merge and post-merge verification: Not started
- Stage 4B: Locked
