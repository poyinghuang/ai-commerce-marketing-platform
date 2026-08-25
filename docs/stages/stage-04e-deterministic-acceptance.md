# Stage 04E — Deterministic Acceptance

## Gate status

- Status: Specification squash-merged; runtime squash-merged; Stage 04 FAKE closed
- Branch: `codex/stage-04e-deterministic-acceptance-specification` (merged)
- Base: `2c2ab07a77d02d8e2c1d2f4e70010430b0e74cfb` (PR #66 squash merge)
- Stage 4D prerequisite: Passed; PR #66 squash-merged at `2c2ab07`; post-merge main CI Run `32744056926` passed
- Product settings: Stage 04 owner defaults of 2026-08-15 remain authoritative
- Implementation: Runtime PR [#68](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/68) squash-merged at `42515e227aca81fd3a0da51f177df891db6aac7f`
- Manager Decision: Specification and runtime merged on `main`
- Merge: Specification PR [#67](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/67) squash-merged at `031d6575a2d6102af5dd1574ca2c2d74799310f4`; post-merge main CI Run `32754399607` passed
- Tag: `stage-04-complete` peels to `031d657`
- Optional Meta paused proof: **Locked** (separate human record). Stage 05 Dashboard is closed; Stage 06 Decision Engine specification is the current gate.

This document is the Stage 04 deterministic-FAKE acceptance contract. It does not authorize credentials, `META_TEST_READ_WRITE_PAUSED`, `META_TEST_DELIVERY`, real Meta, spend, production, Auth/RBAC/Tenant, Dashboard, or Decision Engine behavior.

## Objective

Close Stage 04 inside the existing FAKE LOCAL/TEST boundary by making the parent acceptance criteria executable as a named suite, not as a documentation checklist. PostgreSQL remains the System of Record. Campaigns, Ad Sets, and Ads remain paused at create. Metrics stay null-safe. AI and the Decision Engine still have no path to a platform write or refresh port.

The slice proves the 4A–4D contracts still hold together. It does not add product APIs, flags, schedulers, or a live ads account.

## Repository-owner and Architecture-locked decisions

1. Provider execution remains deterministic `FAKE` only under explicit `local` or `test` configuration. Default and production profiles expose no usable platform adapter, Stage 4B/4C/4D controller, or credential contract.
2. Stage 4E adds **no** new public HTTP route, BFF path, UI panel, feature flag, or `platform_operations` type. Missing coverage is closed with tests, not with new product surface.
3. V1–V15 remain byte-identical. Stage 4E adds no Flyway version. Recovery from a V15 defect remains forward-only through a later, separately specified V16+.
4. `META_TEST_READ_WRITE_PAUSED` and `META_TEST_DELIVERY` stay disabled. This specification does not collect, store, or exercise credentials. Optional paused Meta proof is a later roster row that requires a separate human record.
5. Tag `stage-04-complete` is created only after Stage 4E **runtime** Manager `APPROVE`, squash-merge, and post-merge `main` CI. This specification PR must not create the tag.
6. Stage 05 Dashboard stayed locked until tag `stage-04-complete` existed on `main`. That tag now peels to `031d657`.
7. The eight parent themes below are the closed acceptance set. A theme is Passed only when the named executable case is green on the reviewed Head. Unrun cases are not Passed.
8. AI generation, review, and any future Decision Engine code must have no compile-time dependency on `PlatformCampaignPort`, `PlatformAdSetPort`, `PlatformAdPort`, `PlatformDeliveryReadPort`, or `PlatformMetricsReadPort`.

These eight decisions do not reopen Stage 04 owner defaults. Credentials, live delivery, or a scheduler still require `ESCALATE_TO_HUMAN`.

## Inherited boundaries

1. Stage 4A operation/attempt/idempotency/reconciliation/optimistic-concurrency/Audit/fake-adapter contracts remain authoritative.
2. Stage 4B fixed FAKE account, actor, ledger ceilings, empty-POST retry/reconcile, and `/platforms/meta` remain authoritative.
3. Stage 4C `APPROVED_IMAGE_ASSET_V1` evidence, V14 integrity, and paused Ad publication remain authoritative.
4. Stage 4D PostgreSQL-only GET, explicit refresh, canonical Taipei window, fingerprint replay, and V15 as-of indexes remain authoritative. Observed-state-only delivery sync continues to use the V15 function replacements; 4E does not reopen that 4D finding as a new product decision.
5. Authentication, RBAC, Tenant authority, credentials, real-provider access, and production remain separately gated.

## Included scope

- A named Backend acceptance class that executes the eight parent themes against Testcontainers.
- An executable proof that the AI package cannot reach platform write or Stage 4D refresh ports.
- Retention of every 4A–4D Playwright spec; one additional Compose-backed 4E case that loads `/platforms/meta` and proves zero automatic platform POST.
- Full CI regression already required by `.github/workflows/ci.yml`: Backend Testcontainers, Frontend lint/typecheck/tests/build/audit, Compose cold start, Playwright, Smoke, actionlint, Gitleaks.
- Documentation closeout of Stage 4D merge/CI and the `stage-04-complete` tagging rule.

## Explicitly excluded

- Real Meta accounts, tokens, Graph/Insights URLs, credentials, network calls, provider payloads, paid delivery, billing, or production.
- `META_TEST_READ_WRITE_PAUSED` smoke, `META_TEST_DELIVERY`, spend, or any live create/pause against Meta.
- New public APIs, BFF routes, UI panels, feature flags, or schedulers.
- Editing V1–V15, adding V16, destructive migration, or snapshot backfill.
- Authentication, RBAC, Tenant, or security-model change.
- Dashboard pages, KPI overviews, lists, CSV export, or Decision Engine behavior.
- Creating git tag `stage-04-complete` from this specification PR.

## Parent themes and current executable evidence

Every row must remain green. Stage 4E runtime may wrap these in `Stage4EDeterministicAcceptanceTest` rather than duplicating fixtures. New tests are required only where the Gap column is not empty.

| Theme | Must remain green on `2c2ab07` | 4E runtime gap |
| --- | --- | --- |
| Idempotency / replay | `Stage4BControllerIntegrationTest#replayReturnsExistingOperationWithoutCapacityChange`; `#adSetReplayPreservesOriginalParentVersionAndChangedIntentHasZeroSideEffects`; `#stateReplayBindsEntityUuidAndTypeWithZeroSideEffects`; `#budgetReplayBindsAdSetUuidWithZeroSideEffects`; `PlatformTypedAuditAcceptanceIntegrationTest#replayAndInvalidEntryEmitNoTypedOrPersistedEvents` | Cite these methods from the 4E class (or `@SelectClasses` equivalent). Do not weaken replay to “same HTTP 202”. |
| Ambiguous reconciliation | `PlatformOperationIntegrationTest#staleReconciliationReturnsToUnknownWithoutAdapterCall`; `#reconciledFoundStaleMutationsUseExactAmbiguousRepresentationAtomically`; `PlatformTypedAuditAcceptanceIntegrationTest#reconciliationOutcomesEmitExactTypedSequenceAndContent`; `frontend/e2e/platform-stage4b.spec.ts` unknown-outcome confirm | Same. 4E must not add automatic reconcile-on-load. |
| Stale ETag | `Stage4BControllerErrorMappingTest` `PLATFORM_STALE_VERSION` → `412 PLATFORM_OPERATION_STALE` / `PLATFORM_ENTITY_STALE`; `Stage4CControllerIntegrationTest#missingAndMalformedIfMatchAndUnknownAdAndParentState`; Playwright 4B stale retry `412` | Add one MockMvc case in the 4E class that a stale entity `If-Match` on pause/resume/budget creates **zero** adapter calls and **zero** ledger rows. |
| Budget rejection | `Stage4BControllerIntegrationTest#canonicalPolicyAmountsUse409AtDailyAndLifetimeCreateAndUpdateBounds`; `#invalidStaleAndCapFailuresPreserveByteEquivalentAuditGraph`; `PlatformOperationIntegrationTest#budgetMutationIncreaseDecreaseAndStaleEvidenceAreBoundedBeforeCall`; `Milestone4BLedgerIntegrationTest` / `Stage4BLedgerConcurrencyIntegrationTest` | Cite. Over-ceiling confirm must remain `409 PLATFORM_BUDGET_CAP_EXCEEDED` or `PLATFORM_POLICY_REJECTED` with no attempt/provider call. |
| Approval enforcement | `Milestone4AAdEvidenceAcceptanceIntegrationTest` (inactive Product, checksum mismatch, TEXT output, missing review); `Stage4CBarrierAndAuditIntegrationTest#resumeRejectsAfterEvidenceDivergenceWhilePauseRemainsAvailable`; `Stage4CDirectSqlIntegrityIntegrationTest` | Cite. 4E must not introduce a path that publishes without `APPROVED_IMAGE_ASSET_V1`. |
| Pause / resume | `Stage4BControllerIntegrationTest#campaignAndAdSetCreateUsePausedFakeOperationsAndLedger`; `Stage4CControllerIntegrationTest#pauseAndResumeProviderOutcomesPersistThroughMockMvc`; `Stage4CBarrierAndAuditIntegrationTest#pauseAndResumeTransactionAEmitOneOperationCreatedEvent`; Playwright 4B/4C | Cite. Create remains `PAUSED`; resume stays a second explicit confirmation. |
| Metrics freshness | `Stage4DControllerIntegrationTest#getIsPostgresOnlyAndRefreshPersistsFingerprintDerivedAndReplay`; `#explainUsesAsOfOrRevisionIndexes`; `Stage4DMetricFingerprintTest`; `Milestone4AMetricAcceptanceIntegrationTest`; Playwright `platform-stage4d.spec.ts` | Cite. GET stays zero-adapter; refresh stays explicit; NULL bases stay omitted; SUCCESS→CORRECTED→SUCCESS stays executable. |
| No AI direct write | `backend/src/main/java/com/aicommerce/platform/ai` currently has **zero** imports of `com.aicommerce.platform.delivery` | **Required new test:** `AiHasNoPlatformWritePathTest` fails if the `ai` package depends on the five ports named in decision 8, or if any `@Service` in `ai` injects those ports. |

## Stage 4E runtime deliverable

After this specification merges, runtime is a QA-led test-and-docs slice on `codex/stage-04e-deterministic-acceptance-runtime`:

1. `Stage4EDeterministicAcceptanceTest` (name exact) under `backend/src/test/java/com/aicommerce/platform/delivery/acceptance/` with one `@Test` method per theme row. Methods may delegate to existing helpers; they must fail the suite if the cited invariant regresses.
2. `AiHasNoPlatformWritePathTest` as specified above. ArchUnit is allowed if it is already a test-scoped dependency; otherwise a package-scan / bytecode assertion is enough. Do not add a production ArchUnit dependency only for this.
3. `frontend/e2e/platform-stage4e.spec.ts`: open `/platforms/meta` with Stage 4B+4D flags as Compose already sets them; assert the page renders; assert the test process recorded **zero** `POST /api/platforms/meta/**` and **zero** `POST /api/platform-entities/**` before any user click. This is Compose-backed, not a route mock that hides a scheduler.
4. Completion report `docs/stages/stage-04e-implementation-completion.md` listing exact-head CI and the method names that passed.
5. No application `src/main` product change unless a cited test cannot be expressed without a test-only package-private hook. Any `src/main` change is a Manager finding.

## Completion tag

Runtime merge to `main` plus post-merge CI is necessary but not sufficient. The Project Manager creates annotated tag `stage-04-complete` on that merge commit after recording `APPROVE`. Agents must not tag from a specification PR, a Draft PR, or a dirty worktree.

## Verification and acceptance

- `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` stays empty on this specification PR and on 4E runtime unless a later approved V16 spec exists.
- Full Backend Testcontainers suite; Frontend lint/typecheck/tests/build; `npm audit --omit=dev`; Compose config/cold health; Smoke; all Playwright specs including `platform-stage4e.spec.ts`; actionlint; pinned Gitleaks; `git diff --check`.
- Exact-head Push and Pull Request `quality-and-compose` and `secret-scan` with no required-step skip other than Playwright artifact upload after an E2E pass.
- Unrun rows in the theme table are Failed or Not verified, never Passed.

## Stage gate

- [x] Stage 4D runtime PR #66 squash-merged at `2c2ab07a77d02d8e2c1d2f4e70010430b0e74cfb`.
- [x] Post-merge `main` CI Run `32744056926` passed `quality-and-compose` and `secret-scan`.
- [x] Independent Manager Review recorded `APPROVE` for the specification content that squash-merged as PR #67.
- [x] Specification squash-merged at `031d6575a2d6102af5dd1574ca2c2d74799310f4`; post-merge `main` CI Run `32754399607` passed.
- [x] Runtime PR #68 squash-merged at `42515e227aca81fd3a0da51f177df891db6aac7f` (merged before the specification PR).
- [x] Tag `stage-04-complete` created on `031d657`.

Optional Meta paused proof stays locked until a separate human record. Stage 05 Dashboard is closed. Stage 06 Decision Engine specification is the current gate.
