# Stage Gate Review Report

> Formal Manager Gate for Stage 4C Ad creative publication **runtime**. Copied from `docs/management/stage-gate-template.md`. Unrun items are `Not verified`; failures are not recorded as Passed.

## Review identity

- Stage／Milestone：Stage 4C Ad creative publication runtime
- Review date：2026-08-20
- Reviewer：Project Manager / Lead Reviewer / Stage Gate Owner
- Repository：`poyinghuang/ai-commerce-marketing-platform` (`C:\Users\eric\Documents\Git\ai-commerce-marketing-platform`)
- Branch：`codex/stage-04c-ad-creative-publication-runtime`
- Base Commit：`c321dc0124375e13fd09785b0c827326e996207f`
- Head Commit (PR #64 at review close)：`a5005d60a4486a07bc68a2de206ad38e8058b9dd`
- Runtime implementation commit (functional diff)：`887c2c56fba26ba7b7e69842a7a430f4fa07834c`
- Pull Request：https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/64 (Draft)
- Review cycle：1

The assignment named expected Head `887c2c56fba26ba7b7e69842a7a430f4fa07834c`. During this review a docs-only commit `a5005d60a4486a07bc68a2de206ad38e8058b9dd` (`docs(stage4c): assign remaining-stage agents and manager gate`) landed on the same Draft PR. `git diff 887c2c5..a5005d6` is only `docs/agents/AGENTS.md` and `docs/management/agent-assignments.md`. Functional Java, V14, BFF, and UI were reviewed at `887c2c5` and remain byte-identical in the current Head.

## Status before review

- Implementation：Developer delivery present at `887c2c5`; later assignment docs at `a5005d6`
- Local Verification：No Stage 4C runtime completion report. PR body still describes the unlock-only Head
- Remote CI：At assignment, `secret-scan` had passed and `quality-and-compose` was in progress on runs `32388544189` (push) and `32388546718` (pull_request) for `887c2c5`
- Human Review Required：No (deterministic FAKE LOCAL/TEST slice; no escalation trigger found)
- Merge：Not allowed
- Stage 4D：Locked

## Scope reviewed

- Approved scope：Deterministic-FAKE LOCAL/TEST Ad publication vertical slice — preview/confirm paused Ad from one approved IMAGE evidence chain (`APPROVED_IMAGE_ASSET_V1`), normalized Ad read, pause/resume, additive V14 request/evidence/state integrity, same-origin BFF and `/platforms/meta` Ad section, reuse of Stage 4A operation machinery and Stage 4B fixed-account gates
- Explicit out of scope：credentials, real Meta/network, spend, production, Auth/RBAC/Tenant, 4D metrics, Ad copy/CTA/URL/audience controls, automatic activation, V1–V13 edits
- Files reviewed：the 30-file `887c2c5` runtime diff versus `c321dc0`, plus the two assignment docs in `a5005d6`. Independently inspected V14, `Stage4C*`, canonicalizer, `PlatformOperationService`/`Transactions`, Stage 4B retry scoping, BFF/UI, and the new tests
- Forbidden or unexpected files：none. `git diff --exit-code origin/main --` V1–V13 migration files is empty. No 4D metrics API. No credential or production files
- Completion report compared：none exists for runtime. PR #64 description still claims implementation has not started and that this Head has no V14/API/BFF/UI change. That text is false for `887c2c5`/`a5005d6`

## Architecture and contracts

- Architecture documents reviewed：`docs/stages/stage-04c-ad-creative-publication.md` (approved product contract), Stage 4B completion/prerequisite, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`
- Migration reviewed：additive `V14__add_stage4c_ad_publication_integrity.sql` only. `CREATE OR REPLACE` of V12 `is_valid_platform_request` to accept legacy or new `CREATE_AD` key sets; `BEFORE INSERT` new-shape requirement; `is_stage4c_owned_operation` / closed LOCAL/TEST account tuples; submit-claim and deferred dispatch-result constraint triggers. No new business table, no backfill, no DROP
- Domain／Transaction／Audit boundary：Transaction A reuses `PlatformOperationTransactions.ensureCreateEntity` to insert a pristine paused Ad; claim calls `Stage4CSupport.validateClaim` before adapter dispatch; immediate `ct_platform_ad_dispatch_result` / `40001` / `40P01` recovery exists on the AD path. Typed Stage 4C Audit exact-content tests are absent
- API contract changes：additive Ad preview/confirm/read/state routes under the Stage 4B+4C FAKE gate. JSON field names for preview/Ad/operation match the spec. Internal type names differ (`Ad` vs `PlatformAdApiView`, `Warning` vs `Stage4CWarning`) but are not serialized as those Java names. `Confirmation` is unwrapped to top-level `PlatformOperationApiView`
- Frontend／BFF contract changes：BFF allows the six Ad paths only when `PLATFORM_STAGE4C_ENABLED=true`; UI Ad section is flag-gated. Safe-key sanitizer, 16 KiB / 1 MiB, 10s timeout, and abort composition are inherited
- Backward compatibility：legacy `CREATE_AD` rows without `expectedParentVersion` remain readable; post-V14 inserts require the new key set and `APPROVED_IMAGE_ASSET_V1`
- Rollback／forward recovery：V14 collision test exists (`failedV14MigrationRollsBackEveryPartialV14Object`). Spec-required forward-only V15 recovery evidence is absent

## Impact

- Security impact：LOCAL/TEST FAKE gates retained (`@Profile("(local | test) & !production")`, adapter=fake, stage4b+stage4c flags). Closed account tuples match the approved non-secret fixtures. No Auth/RBAC/Tenant change. Raw checksum/external ID are fingerprinted on Ad GET. BFF continues to strip forbidden keys
- Data impact：additive functions/triggers only; populated V13 rows are not backfilled
- Production impact：none authorized; production profile does not load `Stage4CController`
- External service／cost impact：none. `DEFAULT_IMAGE_V1` is rejected on the new CREATE_AD dispatch path. No Meta network client added

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status -sb` | Passed for PR identity | Branch tracks origin. Current HEAD = PR Head `a5005d6`. Worktree clean of unpublished functional edits at review close |
| Diff check | `git diff --check c321dc0...887c2c5` and working-tree `git diff --check` | Passed | No whitespace errors |
| Commit history | `git log --oneline --decorate -10` | Passed | `a2d5753` unlock docs; `887c2c5` runtime; `a5005d6` assignment docs. Base `c321dc0` is origin/main |
| V1–V13 immutability | `git diff --exit-code origin/main --` V1–V13 SQL | Passed | Exit 0. Only new file is V14 |
| Backend tests | Push CI `32388544189` job `quality-and-compose` at `887c2c5` | Failed | Surefire: 476 tests, **7 failures**, 0 errors, 0 skipped. BUILD FAILURE. Failures are Flyway version lists still expecting through `13` only (see 4C-RT-001). Manager did not re-run local Maven after this exact-head failure |
| Migration tests | Same Surefire run; `MigrationCompatibilityTest` / `Milestone4CSchemaIntegrationTest` not in the failure list | Passed (narrow) / Failed (required matrix) | V14 presence, legacy insert 23514, JSONB `1e0`, UUID-only forged account, one populated legacy CREATE_AD byte survive, and V14 name-collision rollback exist. Required seven-status populated V13→V14, HTTP-inert, full direct-SQL negatives, V15 recovery are missing (4C-RT-002) |
| Hibernate validation | `PersistenceFoundationIntegrationTest` and sibling schema tests | Failed | Those tests fail at `containsExactly(... "13")` before completing their Hibernate assertions. Other `@SpringBootTest` classes started with `ddl-auto=validate` and were not reported as mapping failures |
| Frontend lint | CI step skipped after backend failure; not run locally | Not verified | Push/PR `887c2c5` skipped Node steps. `a5005d6` jobs still in progress at review close |
| Frontend typecheck | same | Not verified | |
| Frontend tests | same | Not verified | |
| Production build | same | Not verified | |
| Docker Compose config | CI skipped | Not verified | |
| Docker Compose cold start | CI skipped | Not verified | |
| Smoke tests | CI skipped | Not verified | |
| Playwright E2E | CI skipped | Not verified | Added `frontend/e2e/platform-stage4c.spec.ts` is mocked create-only; not executed in this review |
| Gitleaks | `secret-scan` Gitleaks 8.28.0 | Passed | Push `32388544189` and PR `32388546718` at `887c2c5`: SUCCESS. `a5005d6` push `secret-scan` SUCCESS (`32389324674`) |
| Dependency audit | CI skipped (`npm audit --omit=dev`) | Not verified | |
| actionlint | CI step `Validate GitHub Actions workflow` | Passed | Succeeded on `887c2c5` push and PR before backend tests |

## Remote CI

- Workflow／Run ID：
  - Functional Head `887c2c5` Push：`32388544189` — `quality-and-compose` **FAILURE**, `secret-scan` SUCCESS. https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/32388544189
  - Functional Head `887c2c5` Pull Request：`32388546718` — `quality-and-compose` **FAILURE**, `secret-scan` SUCCESS. https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/32388546718
  - Current Head `a5005d6` Push：`32389324674` — `secret-scan` SUCCESS; `quality-and-compose` **IN PROGRESS** at review close
  - Current Head `a5005d6` Pull Request：`32389328410` — `secret-scan` IN PROGRESS; `quality-and-compose` **IN PROGRESS** at review close
- Head SHA matches：`887c2c5` runs match that SHA. Current PR Head is `a5005d6`; its `quality-and-compose` is not a completed pass
- `quality-and-compose`：Failed at `887c2c5`. Backend step failed; Frontend, Compose, Playwright, and Smoke were **skipped**. The skipped Playwright artifact-upload step is not a required-quality skip by itself; the backend failure is
- `secret-scan`：Passed at `887c2c5` (both events). Passed on `a5005d6` push
- Required steps skipped：Yes — all post-backend quality steps skipped because backend tests failed
- Warnings／annotations：Not used to green-light. Node 20 deprecation and Mockito/Surefire warnings from prior stages remain non-blocking if they recur

`887c2c5` failure detail (Surefire):

```text
Tests run: 476, Failures: 7, Errors: 0, Skipped: 0
Milestone2CSchemaIntegrationTest.v4TablesRemainAvailableAndRepositoriesLoadUnderLatestHibernateValidation
Milestone2DQualitySchemaIntegrationTest.v5CreatesOnlyApprovedTablesAndJpaMappingsValidate
Milestone2EDriveSchemaIntegrationTest.v7CreatesOnlyApprovedDriveTablesAndHibernateValidates
Milestone2ESheetsSchemaIntegrationTest.sheetsSchemaAndJpaMappingsRemainValidAfterV7
Milestone3ASchemaIntegrationTest.latestSchemaRetainsApprovedFoundationTablesAndHibernateValidates
Milestone3BSchemaIntegrationTest.textOutputTableRemainsCompatibleAfterV10AndHibernateValidates
PersistenceFoundationIntegrationTest.migrationsRunFromEmptyDatabaseAndHibernateValidatesTheSchema
```

Each expected `["1" … "13"]` and observed extra `"14"`.

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| 4C-RT-001 | BLOCKING | Push CI `32388544189`; PR CI `32388546718`; `PersistenceFoundationIntegrationTest:104`; `Milestone2C/2D/2E/3A/3BSchemaIntegrationTest` version lists | Exact-head `quality-and-compose` failed. Seven schema tests still require Flyway history to end at V13, so adding approved V14 cannot pass CI. Current Head `a5005d6` does not change those tests. | Append `"14"` to every latest-schema version assertion (same pattern used when V13 was added). Re-run `mvn -B test` in `backend` and wait for complete exact-new-head Push and PR `quality-and-compose` plus `secret-scan`. |
| 4C-RT-002 | BLOCKING | `Milestone4CSchemaIntegrationTest.java`; `MigrationCompatibilityTest.populatedLegacyCreateAdBytesSurviveUpgradeToV14`; spec §Verification | Required V14 acceptance is not executable. Missing: cold V1→V14 object assertions beyond a version bump; V1–V12 checksums in the 4C suite; forward-only V15 recovery; populated V13 fixture covering all seven operation statuses with byte-compare of every related table; HTTP GET-allowed / retry-reconcile `PLATFORM_LEGACY_OPERATION_INERT` with zero writes; direct-SQL negatives for forged parent version, mutated LOCAL/TEST fingerprint/reference, invalid evidence, false create/resume success, external-ID substitution, correlated-resume mismatch, complete named-constraint rollback. Current tests cover function presence, one legacy insert 23514, JSONB `1e0`, UUID-only forged account, one CREATE_AD payload survive, and name-collision rollback. | Implement the spec matrix with exact SQLSTATE/constraint names and full pre/post graphs. Tests: populated seven-status V13→V14, HTTP inert, and the listed direct-SQL negatives. Re-verify with `mvn -B -Dtest=Milestone4CSchemaIntegrationTest,MigrationCompatibilityTest test` then full backend. |
| 4C-RT-003 | BLOCKING | spec §Transaction / claim-race / finalization-race; no `barrier`/`claim-race` tests under `backend/src/test` | Claim-race and finalization-race barriers are absent. Spec requires racing parent version/state, Product/Asset lifecycle/checksum, output status/preservation/checksum, and review decision against initial/retry claim and against direct/reconciled commit, plus unrelated `23514`/`23503`/`40001`/`40P01`, recovery-failure, restart, and concurrent recovery. | Add barrier-controlled concurrent tests proving CREATE_AD/RESUME reject with zero attempt/Audit/call, PAUSE remains available, and failed dispatch commit follows the exact UNKNOWN recovery graph with one provider invocation. Re-verify those test classes then full backend. |
| 4C-RT-004 | BLOCKING | spec §Audit contract; no Stage 4C Audit test class | Typed Audit exact-content, cardinality, zero-event, redaction, and writer-failure rollback (including after final append before commit) are not implemented for Ad create Transaction A (2 events), pause/resume Transaction A (1), claim (2), finalization (2 or 3), and immediate recovery (2). | Add persisted Audit/AuditChange assertions by durable ordinal for every required path and the enumerated no-event paths. Re-verify the new Audit suite then full backend. |
| 4C-RT-005 | BLOCKING | `Stage4CControllerIntegrationTest.java`; `Stage4CControllerErrorMappingTest.java`; `platform-meta-manager.test.tsx`; `platform-stage4c.spec.ts`; spec §API / §UI | Vertical-slice evidence is a happy-path create/pause/resume MockMvc plus a mocked mapper 429 subset and a mocked UI create. Missing executable coverage: query rejection and unknown/duplicate/null field order on 4C routes; complete source-to-public matrix including retry/reconcile legacy-inert and all provider outcomes through MockMvc+persistence; UI/Playwright pause/resume, stale 412 invalidation, due retry, unknown reconcile, evidence divergence, weak ETag round-trip, malformed If-Match 400, and zero automatic action. | Add parameterized MockMvc/BFF snapshots and component+Playwright cases named in the spec. Re-verify backend 4C web tests, `npm test`, and Playwright after CI backend is green. |
| 4C-RT-006 | MAJOR | PR #64 body; `docs/management/reviews/stage-04c-specification-manager-review.md` “No V14 runtime evidence exists yet. Implementation has not started.” | Completion/PR evidence is stale and overstates “not started” while V14, API, BFF, and UI are in the Head. | After the blocking tests exist and pass, replace the PR summary with the actual Head, CI run IDs, and case-exact coverage. Do not claim unrun suites Passed. |
| 4C-RT-007 | MAJOR | `V14__add_stage4c_ad_publication_integrity.sql` `protect_platform_create_ad_insert`; spec additive V14 item 2 | The insert trigger checks `request_sha256` is 64 hex and the new key set / mapping, but does not prove stored `request_sha256` equals the Java canonical hash of the persisted payload. | Enforce payload/hash coherence in V14 (or document why PostgreSQL cannot and add a Java-only fixture plus SQL canonical-bytes check). Add shared fixtures for key-set/type/range/canonical hash. Direct-SQL test that a mismatched sha256 rolls back. |

Non-blocking notes (not in the table as blockers):

- `Stage4CViews` uses `Ad` / `Warning` instead of the spec’s Java record names; JSON keys match.
- `Stage4CController` maps every `23514` `ct_platform_ad_submit_claim_evidence` to evidence-invalid. Application `validateClaim` can still emit 412 / parent-state first; SQL remains a single named constraint as specified. Keep Java precedence tests so a bypassed claim cannot disclose the wrong public code without a dedicated mapping.
- `a5005d6` assignment docs are in-scope for the current PR Head but do not change runtime behavior.

若無 Finding，明確寫 `None`。不要刪除本節。 Findings are recorded above; they are not None.

## Known limitations

- Stage 4C remains deterministic FAKE in LOCAL/TEST only. Credentials, real Provider, network, paid delivery, production, Auth/RBAC/Tenant, and Stage 4D metrics stay forbidden.
- Current PR Head `a5005d6` `quality-and-compose` was still running at review close; it cannot be treated as Passed. The immediately prior functional Head already failed the same backend assertions.
- Frontend, Compose, Smoke, Playwright, and dependency audit were skipped on the failed `887c2c5` runs and were not executed locally in this review.
- Playwright coverage that exists is route-mocked create confirmation only.
- Repository branch protection still has no automated Manager Gate; this manual Gate is authoritative.
- Local Mockito/Byte Buddy/Surefire and GitHub Actions Node.js deprecation warnings remain non-blocking when they recur.

## Stage Gate decision

- Decision：`REQUEST_CHANGES`
- Decision rationale：Exact-head Remote CI `quality-and-compose` failed on the runtime commit, so APPROVE is forbidden. Independently, the approved Stage 4C acceptance matrix (migration/direct-SQL/legacy, claim and finalization races, typed Audit, and API/UI evidence) is not present. V14 is additive and stays inside the FAKE LOCAL/TEST boundary, so this is a spec-internal correction, not a human escalation.
- Required next action：Keep PR #64 Draft. Do not merge. Do not start Stage 4D. Backend/Frontend fix 4C-RT-001 through 4C-RT-005 on this branch (4C-RT-006/007 on the same Head). Push a new Head and re-request Manager Review only after complete exact-new-head Push and Pull Request `quality-and-compose` and `secret-scan`.
- Human approval required：`No`
- Human approval reason／evidence：Escalation policy was checked. No credential, Auth/RBAC/Tenant, production, spend, destructive migration, System of Record, breaking prior API, or critical security trigger. V14 is additive. Default human review remains No.

### Blocking re-verification (after a new Head)

```text
git status --short
git diff --check
git log --oneline --decorate -10
git diff --exit-code origin/main -- backend/src/main/resources/db/migration/V1*.sql backend/src/main/resources/db/migration/V2*.sql backend/src/main/resources/db/migration/V3*.sql backend/src/main/resources/db/migration/V4*.sql backend/src/main/resources/db/migration/V5*.sql backend/src/main/resources/db/migration/V6*.sql backend/src/main/resources/db/migration/V7*.sql backend/src/main/resources/db/migration/V8*.sql backend/src/main/resources/db/migration/V9*.sql backend/src/main/resources/db/migration/V10*.sql backend/src/main/resources/db/migration/V11*.sql backend/src/main/resources/db/migration/V12*.sql backend/src/main/resources/db/migration/V13*.sql
```

Backend (Windows PATH must include JDK 21):

```text
cd backend
mvn -B test
```

Frontend:

```text
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
npm audit --omit=dev
```

Then Compose config/cold health, Smoke, Playwright, actionlint, and pinned Gitleaks as required by the Stage 4C spec. Confirm GitHub Push and Pull Request runs on the **new** Head both complete `quality-and-compose` and `secret-scan` with no required-step skip other than the conditional Playwright artifact upload after a Playwright pass.

## Approval record

Not applicable. Decision is `REQUEST_CHANGES`, not `APPROVE`.

- Manager Review：Failed (cycle 1)
- Manager Decision：REQUEST_CHANGES
- Approved Commit：n/a
- Approved CI Run：n/a
- Merge allowed：No
- Next Stage allowed：No. Stage 4D remains locked until a later `APPROVE`, merge under governance, and post-merge `main` CI
