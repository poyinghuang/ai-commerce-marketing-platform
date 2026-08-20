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

---

# Cycle 2

> Formal Manager Gate cycle 2. Cycle 1 above is retained. Unrun items are `Not verified`; failures are not recorded as Passed. Developer claims were treated as unverified and checked against `fd7e19c` ancestry plus exact-head CI on the current Head.

## Review identity

- Stage／Milestone：Stage 4C Ad creative publication runtime
- Review date：2026-08-21
- Reviewer：Project Manager / Lead Reviewer / Stage Gate Owner
- Repository：`poyinghuang/ai-commerce-marketing-platform` (`C:\Users\eric\Documents\Git\ai-commerce-marketing-platform`)
- Branch：`codex/stage-04c-ad-creative-publication-runtime`
- Base Commit：`c321dc0124375e13fd09785b0c827326e996207f` (`origin/main`)
- Cycle-1 runtime Head：`887c2c56fba26ba7b7e69842a7a430f4fa07834c`
- Integrity-fix ancestry：`fd7e19c5fb86d0d1eabb93601977c9ac827f706e` (`git merge-base --is-ancestor` of current Head: true)
- Head Commit (PR #64 at cycle-2 close)：`7139b94077a0a55b84e80d4062e7ace97a2a9576`
- Pull Request：https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/64 (Draft; kept Draft)
- Review cycle：2

`7139b94` is `test(stage4c): use exact Load Ad locator in Playwright` (`frontend/e2e/platform-stage4c.spec.ts` one-line `exact: true` on `Load Ad`). Functional Java, V14, BFF allowlist, claim mapping, and barrier/audit tests remain byte-identical to `fd7e19c`. Cycle 2 is **not** closed on `fd7e19c`.

Local worktree at review close tracks origin at `7139b94`. Unrelated dirty file `frontend/next-env.d.ts` is **not** on the PR Head and was not treated as reviewed content.

## Status before review

- Implementation：`fd7e19c` integrity/allowlist/claim-mapping plus `7139b94` Playwright locator
- Local Verification：Developer claimed `fd7e19c` local `mvn` 578/0 and vitest on changed frontend files; Playwright and Compose smoke were not run locally. Manager did not re-run those local commands; exact-head CI on `fd7e19c` and `7139b94` is the executed evidence
- Remote CI：At cycle-2 assignment, `fd7e19c` `secret-scan` had passed and `quality-and-compose` was in progress (`32394435034`, `32394435426`). Those completed as Playwright failures. Locator commit then produced new runs on `7139b94`
- Human Review Required：No
- Merge：Not allowed
- Stage 4D：Locked

## Scope reviewed

- Approved scope：same deterministic-FAKE LOCAL/TEST Ad publication vertical slice as cycle 1
- Explicit out of scope：credentials, real Meta/network, spend, production, Auth/RBAC/Tenant, 4D metrics, copy/CTA/URL/audience, automatic activation, V1–V13 edits
- Files reviewed：`git diff 887c2c56..fd7e19c` (29 files, integrity/tests/BFF/UI) independently, plus `git diff fd7e19c..7139b94` (Playwright locator only)
- Forbidden or unexpected files：none. `git diff --exit-code origin/main --` V1–V13 SQL is empty. Test-only `backend/src/test/resources/db/test-forward-v15/V15__forward_only_name_collision.sql` is not a shipped migration
- Completion report compared：PR #64 body on `fd7e19c`/`7139b94` now describes runtime Head, finding IDs, and that Playwright/Compose were not locally Passed. That stale “implementation has not started” text from cycle 1 is gone

## Architecture and contracts

- Architecture documents reviewed：`docs/stages/stage-04c-ad-creative-publication.md`, cycle-1 manager report, Review Agent report, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`
- Migration reviewed：additive V14 only. `fd7e19c` adds `stage4c_create_ad_canonical_json` / `request_sha256` coherence, split claim exception names (`ct_platform_ad_submit_claim_stale` / `_parent_state` / `_evidence`), and dispatch correlation by matching in-transaction operation (create `0→1`, fingerprint SHA-256, no `ORDER BY updated_at DESC LIMIT 1`)
- Domain／Transaction／Audit boundary：claim/finalize now `SELECT … FOR UPDATE` the operation first (`lockOperation`). `Stage4CCriticalSectionHook` is an empty production seam. Barrier tests exist for a **subset** of spec races. Typed Audit remains cardinality/ordinal, not the full exact-content matrix
- API contract changes：query rejection now `field=query` / `Query parameters are not allowed`. SQL `23514` claim messages map to 412 stale / 409 parent-state / 409 evidence
- Frontend／BFF contract changes：BFF `SAFE_KEYS` allowlist rejects extra keys including `operation` / `replay` / `outcomeEvidence`. Playwright `Preview paused Ad` and `Load Ad` use `exact: true`
- Backward compatibility：legacy `CREATE_AD` without `expectedParentVersion` remains readable; post-V14 inserts require the new key set
- Rollback／forward recovery：V14 collision rollback and test-only forward V15 name-collision remain. Spec-required already-claimed legacy `SUBMITTING`/`RECONCILING` finalize/recovery is still absent

## Impact

- Security impact：LOCAL/TEST FAKE gates retained. No Auth/RBAC/Tenant change. BFF allowlist is stricter than cycle-1 denylist. No credential or production files
- Data impact：additive V14 functions/triggers; no V1–V13 edit; no backfill
- Production impact：none authorized
- External service／cost impact：none

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status -sb`; `git rev-parse HEAD` | Passed for PR identity | Branch tracks origin at `7139b94`. Dirty `frontend/next-env.d.ts` is local-only and not on the Head |
| Diff check | `git diff --check` at review start of `fd7e19c`; locator diff is one line | Passed | No whitespace errors in reviewed diffs |
| Commit history | `git log --oneline --decorate -12` | Passed | `887c2c5` runtime → `fd7e19c` integrity → `7139b94` locator |
| V1–V13 immutability | `git diff --exit-code origin/main --` V1–V13 SQL | Passed | Exit 0 |
| Backend tests | Push `32395393438` and PR `32395399464` step `Test Backend with PostgreSQL Testcontainers` on `7139b94`; same suite succeeded on `fd7e19c` Push `32394435426` | Passed | Surefire on `fd7e19c` Push: **Tests run: 578, Failures: 0, Errors: 0, Skipped: 0**, BUILD SUCCESS. `7139b94` backend step succeeded (locator-only; same Java) |
| Migration tests | Same Surefire; `MigrationCompatibilityTest` 16/0, `Milestone4CSchemaIntegrationTest` 6/0, `Stage4CDirectSqlIntegrityIntegrationTest` 4/0, `Stage4CLegacyOperationIntegrationTest` 1/0 | Passed (executed subset) | Remaining spec holes are recorded as findings, not as unrun-pass |
| Hibernate validation | PersistenceFoundation + Milestone2C/2D/2E/3A/3B version lists through `"14"` | Passed | Cycle-1 4C-RT-001 failures are gone on this ancestry |
| Frontend lint | CI `Verify Frontend` (`npm run lint`) on `7139b94` | Passed | Step succeeded on Push and PR |
| Frontend typecheck | `npm run typecheck` in same step | Passed | |
| Frontend tests | `npm test` in same step | Passed | |
| Production build | `npm run build` in same step | Passed | |
| Docker Compose config | CI `Validate Docker Compose` | Passed | `docker compose config --quiet` succeeded |
| Docker Compose cold start | CI `Start and wait for the full stack` | Passed | Step succeeded; Smoke after E2E did not run |
| Smoke tests | CI `Smoke test health chain and product vertical slice` | Not verified | **Skipped** because Playwright failed. Not marked Passed |
| Playwright E2E | CI `Run Browser E2E` on `7139b94` | Failed | 17 passed, **1 failed** (retried). `platform-stage4c.spec.ts:180` `getByRole('alert')` strict-mode clash with Next.js `#__next-route-announcer__`. See QA-PW-03 |
| Gitleaks | `secret-scan` Gitleaks 8.28.0 | Passed | Push `32395393438` job `96510897475`; PR `32395399464` job `96510918311`. Also passed on `fd7e19c` |
| Dependency audit | CI `npm audit --omit=dev` inside Verify Frontend | Passed | Step succeeded |
| actionlint | CI `Validate GitHub Actions workflow` | Passed | Succeeded before backend |

## Remote CI

- Workflow／Run ID (current Head `7139b94`):
  - Push：`32395393438` — `quality-and-compose` **FAILURE** (9m2s, job `96510897740`), `secret-scan` SUCCESS. https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/32395393438
  - Pull Request：`32395399464` — `quality-and-compose` **FAILURE** (9m36s, job `96510918625`), `secret-scan` SUCCESS. https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/32395399464
- Ancestry Head `fd7e19c` (not the cycle-2 close Head):
  - Pull Request：`32394435034` — `quality-and-compose` FAILURE (Playwright `Load Ad` vs `Load Ad Set` at spec.ts:175), `secret-scan` SUCCESS
  - Push：`32394435426` — `quality-and-compose` FAILURE (same `Load Ad` clash), `secret-scan` SUCCESS. Backend 578/0 on this SHA
- Head SHA matches：both current runs are `7139b94077a0a55b84e80d4062e7ace97a2a9576`, matching local HEAD, `origin`, and PR `headRefOid`
- `quality-and-compose`：Failed on Push and PR at `7139b94`. Backend, Frontend verify, Compose config, and stack start succeeded. Playwright failed. Smoke skipped
- `secret-scan`：Passed on both events at `7139b94`
- Required steps skipped：Yes — Smoke skipped after Playwright failure. Playwright artifact upload ran after E2E fail (allowed upload step; does not make E2E Passed)
- Warnings／annotations：Node.js 20 deprecation on Actions. Non-blocking

`7139b94` Playwright failure:

```text
17 passed, 1 failed
[chromium] e2e/platform-stage4c.spec.ts:95
spec.ts:180 getByRole('alert') strict mode violation:
  1) div.error-banner role=alert  "The approved Ad evidence is no longer eligible"
  2) div#__next-route-announcer__ role=alert
```

`Load Ad` `exact: true` on `7139b94` got past line 175. `Preview paused Ad` `exact: true` (QA-PW-01) passed on both `fd7e19c` and `7139b94` (test at spec.ts:3).

## Cycle-1 / Review finding disposition (inspected, not rubber-stamped)

| ID | Cycle-2 status | Evidence |
| --- | --- | --- |
| 4C-RT-001 | Closed | `782064e` appends `"14"`; CI backend 578/0 on `fd7e19c`; Hibernate/schema tests passed on `7139b94` |
| 4C-RT-002 | Open BLOCKING | Named tests now exist and passed (`Milestone4C` checksums/V14 objects, V15 collision, seven-status V13→V14 HTTP inert, direct-SQL stale/parent/evidence/dispatch). Still missing: populated byte-compare of Audit/metrics/budget ledger; already-claimed legacy `SUBMITTING`/`RECONCILING` finalize/recovery |
| 4C-RT-003 | Open BLOCKING | `Stage4CBarrierAndAuditIntegrationTest` covers parent-version bump, asset checksum claim race, one finalization-race UNKNOWN recovery, concurrent claim. Missing retry-claim races, reconciled finalization races, Product/Asset lifecycle, output status/preservation, review decision, unrelated `23514`/`23503`/`40001`/`40P01`, recovery-failure, restart, concurrent recovery |
| 4C-RT-004 | Open BLOCKING | Cardinality/ordinal/redaction and one post-final-append Tx C failure exist. Missing exact Stage 4A change content, isolated claim/finalize/recovery 2-or-3 event graphs, enumerated zero-event paths, throw at every append position |
| 4C-RT-005 | Open BLOCKING | Query `field=query`, duplicate/null/unknown body, malformed If-Match MockMvc, and mocked error-mapping 92 rows exist. Missing MockMvc+persistence provider-outcome matrix. Playwright second test still does not click pause, send malformed If-Match, or assert weak ETag, and **fails CI** (QA-PW-03) |
| 4C-RT-006 | Closed | PR body on this Head describes actual runtime, finding IDs, and unrun Playwright/Compose honestly |
| 4C-RT-007 | Closed | V14 hashes `stage4c_create_ad_canonical_json`; `mismatchedCreateAdSha256RollsBackAndJavaSqlCanonicalHashesAgree` passed in CI |
| R-4C-01 | Open BLOCKING | Umbrella for remaining 4C-RT-002–005 acceptance matrix |
| R-4C-02 | Closed | Dispatch trigger correlates by matching operation/external ID; create `0→1`; fingerprint SHA-256; no `ORDER BY updated_at DESC LIMIT 1` |
| R-4C-03 | Closed | BFF `SAFE_KEYS` allowlist; tests reject extra/`operation`/`replay`/`outcomeEvidence`. Missing-key/wrong-type BFF tests remain a non-blocking note |
| R-4C-04 | Closed | Filter + MockMvc `field=query` / `Query parameters are not allowed` |
| R-4C-05 | Closed | Distinct SQL messages + controller mapping + `sqlClaimConstraintMessagesMapToDistinctPublicCodes` |
| R-4C-06 | Closed | `insertUnapprovedOperation` is post-V14 new-shape; `legacyCreateAdInsertAfterV14IsRejectedByNewShapeTriggerNotLedger` asserts `expectedParentVersion` |
| R-4C-07 | Closed | `lockOperation` `FOR UPDATE`; `concurrentClaimHasOneWinnerAndNoDeadlock` passed |
| R-4C-08 | Closed | Same as 4C-RT-007 |
| R-4C-09 | Closed | Parameterized mapping expanded (92 tests). Residual provider-snapshot holes stay under 4C-RT-005 |
| R-4C-10 | Closed (accepted NOTE) | Claim Audit ordinal remains Stage 4A `ATTEMPT_CREATED` then `OPERATION_TRANSITIONED` |
| QA-PW-01 | Closed | `Preview paused Ad` `exact: true`; CI test spec.ts:3 passed on `fd7e19c` and `7139b94` |
| QA-PW-02 | Closed on `7139b94` | `Load Ad` `exact: true`; CI progressed past spec.ts:175 |
| QA-PW-03 | Open BLOCKING (new) | spec.ts:180 `getByRole('alert')` matches error banner **and** Next.js route announcer |

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| QA-PW-03 | BLOCKING | Push `32395393438`; PR `32395399464`; `frontend/e2e/platform-stage4c.spec.ts:180` | Exact-head Playwright failed. `getByRole('alert')` is not unique: the evidence-invalid banner exists, but Next.js `#__next-route-announcer__` is also `role="alert"`. Same pattern already filtered in `ai-creative-acceptance.spec.ts` / `product-center.spec.ts`. | Assert `page.getByRole("alert").filter({ hasText: "no longer eligible" })` (or `.error-banner`). Re-run Playwright. Wait for complete exact-new-head Push and PR `quality-and-compose` plus `secret-scan`. |
| 4C-RT-002 | BLOCKING | `Stage4CLegacyOperationIntegrationTest` `GRAPH_TABLES`; no SUBMITTING/RECONCILING finalize tests | Populated V13→V14 HTTP-inert seven-status fixture passed, but it does not byte-compare Audit/metrics/budget ledger, and already-claimed legacy SUBMITTING/RECONCILING finalize/recovery is not executable. | Add those tables to the pre/post graph. Add MockMvc/SQL finalize+stale-recovery cases for legacy SUBMITTING and RECONCILING with named predicates and full rollback. Re-verify the 4C persistence/web tests then full backend. |
| 4C-RT-003 | BLOCKING | `Stage4CBarrierAndAuditIntegrationTest.java` | Claim/finalization barrier coverage is a subset. Retry-claim, reconciled commit, Product/Asset lifecycle, output preservation/status, review decision, unrelated `23514`/`23503`/`40001`/`40P01`, recovery-failure, restart, and concurrent recovery are still absent. | Add the remaining spec barrier rows. Re-verify that class then full backend. |
| 4C-RT-004 | BLOCKING | same Audit test class | Typed Audit exact-content, isolated claim/finalize/recovery cardinality, enumerated zero-event paths, and throw-at-every-append are still incomplete. | Persist Audit/AuditChange assertions by durable ordinal for every required path. Re-verify the Audit suite then full backend. |
| 4C-RT-005 | BLOCKING | `Stage4CControllerIntegrationTest`; `platform-stage4c.spec.ts`; `platform-meta-manager.test.tsx` | Query/duplicate/If-Match MockMvc exist. Complete provider-outcome MockMvc+persistence matrix does not. The second Playwright/UI test names pause, stale 412, weak ETag, malformed If-Match, and zero automatic pause, but never clicks pause or sends a malformed If-Match (`pauses` stays 0). | Add those executable cases. Re-verify 4C web tests, `npm test`, and Playwright after CI backend is green. |
| R-4C-01 | BLOCKING | overlaps 4C-RT-002–005 | Required acceptance matrix remains only partially executable. | Same remaining cases as 4C-RT-002–005. |

Closed this cycle (not blockers): 4C-RT-001, 4C-RT-006, 4C-RT-007, R-4C-02, R-4C-03, R-4C-04, R-4C-05, R-4C-06, R-4C-07, R-4C-08, R-4C-09, R-4C-10, QA-PW-01, QA-PW-02.

Non-blocking notes:

- BFF allowlist still does not type-check missing required keys / wrong JSON types.
- `claimRaceOnParentState` increments Ad Set version, so it asserts `PLATFORM_STALE_VERSION` rather than a pure parent-state race.
- `UNKNOWN_OUTCOME` UI also uses `role="alert"` (`platform-meta-manager.tsx`); keep Playwright alert locators filtered.

## Known limitations

- Stage 4C remains deterministic FAKE in LOCAL/TEST only. Credentials, real Provider, network, paid delivery, production, Auth/RBAC/Tenant, and Stage 4D stay forbidden.
- Compose Smoke was skipped on both `7139b94` runs because Playwright failed. Not Passed.
- Playwright coverage that exists is still route-mocked; the second 4C spec is incomplete versus its own name even before QA-PW-03.
- Repository branch protection still has no automated Manager Gate; this manual Gate is authoritative.
- Node.js 20 Action deprecation remains non-blocking.

## Stage Gate decision

- Decision：`REQUEST_CHANGES`
- Decision rationale：Exact-head Push and Pull Request `quality-and-compose` failed on `7139b94` (Playwright QA-PW-03), so APPROVE is forbidden. Independently, cycle-1 BLOCKING matrix items 4C-RT-002–005 / R-4C-01 remain only partially closed on the `fd7e19c` ancestry. V14 stays additive inside the FAKE LOCAL/TEST boundary; no escalation trigger.
- Required next action：Keep PR #64 Draft. Do not merge. Do not start Stage 4D. Fix QA-PW-03 and the remaining 4C-RT-002–005 / R-4C-01 executable gaps on this branch. Push a new Head and re-request Manager Review only after complete exact-new-head Push **and** Pull Request `quality-and-compose` and `secret-scan`, with no required-step skip other than Playwright artifact upload after an E2E pass.
- Human approval required：`No`
- Human approval reason／evidence：Escalation policy checked. No credential, Auth/RBAC/Tenant, production, spend, destructive migration, System of Record, breaking prior API, or critical security trigger. V14 is additive. Default human review remains No.

### Blocking re-verification (after a new Head)

```text
git status --short
git diff --check
git log --oneline --decorate -10
git rev-parse HEAD
```

Confirm GitHub Push and Pull Request runs on the **new** Head both complete `quality-and-compose` and `secret-scan`. Do not APPROVE on `fd7e19c` or `7139b94`.

## Approval record

Not applicable. Decision is `REQUEST_CHANGES`, not `APPROVE`.

- Manager Review：Failed (cycle 2)
- Manager Decision：REQUEST_CHANGES
- Reviewed Head：`7139b94077a0a55b84e80d4062e7ace97a2a9576`
- Integrity ancestry：`fd7e19c5fb86d0d1eabb93601977c9ac827f706e`
- Approved Commit：n/a
- Approved CI Run：n/a
- Merge allowed：No
- Next Stage allowed：No. Stage 4D remains locked

