# Stage 04B Specification Manager Review

## Review identity

- Stage／Milestone: Stage 4B Campaign and Ad Set vertical slice specification
- Review date: 2026-08-17
- Reviewer: Independent Project Manager / Lead Reviewer / Stage Gate Owner
- Repository: `ai-commerce-marketing-platform`
- Branch: `codex/stage-04b-campaign-adset-specification`
- Base Commit: `4d89448cb48520c14f6ce991d803a0221503ebeb`
- Head Commit: `2b0dba3bc94b0e9be9d699f23a42880da3e0fee7`
- Pull Request: #61

## Status before review

- Implementation: Blocked; not started
- Repository-owner product settings: Approved on 2026-08-17
- Local Verification: Passed for documentation scope and repository integrity checks
- Remote CI: Passed on the reviewed exact Head
- Human Review Required: Yes — the FAKE/local/test authorization exception requires an explicit repository-owner decision
- Merge: Not started

## Scope reviewed

- Approved scope: Stage 4B specification for Campaign/Ad Set preview-confirm-read-state-budget vertical slice and aggregate budget authorization ledger.
- Explicit out of scope: Runtime implementation, migrations, REST/BFF/UI code, real provider/network, credentials, production, spend, Auth/RBAC/Tenant, Ads, delivery, metrics, Stage 4C+.
- Files reviewed: `docs/stages/stage-04b-campaign-ad-set-vertical-slice.md`; `docs/management/reviews/stage-04b-specification-manager-review.md`
- Forbidden or unexpected files: None on the reviewed Head; the diff contains exactly the two documentation files above
- Completion report compared: Not applicable; specification phase

## Architecture and contracts

- Architecture documents reviewed: `AGENTS.md`, `README.md`, `docs/agents/AGENTS.md`, `docs/Architecture.md`, `docs/Data-Model.md`, `docs/Development-Rules.md`, `docs/Logging-and-Error-Handling.md`, parent Stage 04, approved Stage 4A specification/completion/reviews, and actual V12 migration/application/test contracts
- Migration reviewed: Proposed additive V13 only. The direction is non-destructive, but exact legacy-operation, account-day bootstrap, clock, FK/coherence, and migration-test contracts are blocking
- Domain／Transaction／Audit boundary: Stage 4A operation lifecycle is retained, but the proposed authorization substitution and new Audit event model are not approved/representable as written
- API contract changes: Additive local/test-only routes are proposed, but request/response, per-route concurrency/error, entity-scope, actor/account, Campaign Plan, and schedule contracts are incomplete
- Frontend／BFF contract changes: Same-origin/fail-closed direction is correct; exact limits/errors and the operator/approver boundary remain unresolved
- Backward compatibility: Documentation-only now. Proposed V13 is additive, but pre-V13 unbatched operation behavior is unspecified
- Rollback／forward recovery: Forward-only migration intent is correct; V13 defects would use V14+, but executable recovery evidence is incomplete

## Impact

- Security impact: No runtime change in this PR. Proposed same-actor activation/budget confirmation conflicts with the parent operator/approver separation requirement and requires human resolution
- Data impact: No data change in this PR. Proposed V13 adds immutable authorization evidence; exact integrity/legacy behavior requires correction
- Production impact: None; fail-closed required
- External service／cost impact: None; deterministic fake only

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Repository/branch/Head | `git status --porcelain=v1 --branch`; `git rev-parse`; PR metadata | Passed | Local/upstream/PR Head matched `2b0dba3bc94b0e9be9d699f23a42880da3e0fee7`; base/merge-base matched `4d89448cb48520c14f6ce991d803a0221503ebeb`; worktree was clean before review edits |
| Diff check | `git diff --check origin/main...HEAD` | Passed | No whitespace errors |
| Commit history | `git log --oneline --decorate -8` | Passed | One specification commit on the post-Stage-4A main base |
| PR scope/patch | `git diff --name-status origin/main...HEAD`; PR files | Passed | Exactly two new Markdown files; no runtime, migration, workflow, dependency, credential, or production change |
| Backend tests | Push `32007307611`; PR `32007310578` | Passed | 359 tests; 0 failures, errors, or skips; build succeeded |
| Migration/Hibernate | Same Remote CI runs | Passed for existing V1–V12 | Proposed V13 is documentation only and therefore not executable evidence |
| Frontend lint/typecheck/tests/build | Same Remote CI runs | Passed | 22 files / 134 tests; production build passed |
| Dependency audit | Same Remote CI runs | Passed | 0 vulnerabilities |
| Compose/Smoke/Playwright | Same Remote CI runs | Passed | Compose config/cold health, Product Smoke, and Playwright 14/14 executed |
| Gitleaks/actionlint | Local Gitleaks 8.28.0 and same Remote CI runs | Passed | Local history/worktree found no leaks; Remote secret scans and actionlint passed |

## Remote CI

- Workflow／Run ID: Push `32007307611`; Pull Request `32007310578`
- Head SHA matches: Yes — both ran at `2b0dba3bc94b0e9be9d699f23a42880da3e0fee7`
- `quality-and-compose`: Passed in both runs
- `secret-scan`: Passed in both runs
- Required steps skipped: None. Playwright failure-artifact upload was conditionally skipped because browser tests passed
- Warnings／annotations: Existing GitHub Actions Node 20 to Node 24 runtime-transition annotation; non-blocking and unrelated to this specification

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
| 4B-SPEC-001 | BLOCKING / HUMAN ESCALATION | Parent Stage 04 human-approved decision 6; Stage 4A RESUME contract; Stage 4B lines describing fixed actor/two-step UI | Parent Stage 04 requires `PLATFORM_OPERATOR`/`PLATFORM_APPROVER` separation for activation and budget changes, and Stage 4A keeps RESUME inert pending security-policy approval. This specification substitutes a second confirmation by the same unauthenticated local/test actor. Whether deterministic FAKE/local/test is exempt is an unapproved product/security-boundary decision. | Repository owner must explicitly choose: (A) approve a narrowly defined FAKE/local/test-only exemption with no credential/network/spend/production path, or (B) keep RESUME and budget confirmation inert until the approved authorization/separation model exists. Record the choice and limits before correction or implementation. |
| 4B-SPEC-002 | BLOCKING | Stage 4B Campaign/API/policy sections; Campaign Plan V4 model | Campaign Plan eligibility/mapping and schedule policy are incomplete: ACTIVE/platform/objective/currency compatibility, version/lock behavior, duplicate mapping, LocalDate-to-Instant rules, parent/child schedule containment, and confirmation-time revalidation are not exact. | Define exact eligible Campaign Plan state/fields, immutable mapping, schedule authority/conversion/containment, duplicate/stale errors, and positive/negative transaction tests. |
| 4B-SPEC-003 | BLOCKING | Stage 4B policy, Transaction A, and controller gate | Fixed account/actor selection, zero/multiple-account fail-closed behavior, actor type/ID/source, feature-property conjunction, and optimization goal are unspecified. `platform.fake.enabled` also diverges from the implemented Stage 4A `platform.adapter=fake` gate. | Define the exact account fixture/selection, actor tuple, configuration properties, optimization goal, startup/request failure codes, and profile tests. |
| 4B-SPEC-004 | BLOCKING | Stage 4B API/BFF sections | Exact preview, Campaign, Ad Set, retry, and reconciliation DTOs; required/optional/null rules; route-specific headers/status/body/ETag/replay behavior; stable `ApiError.code` mapping; body limits; timeout/abort; and 413/502/504 behavior are absent. The generic state-preview path is not closed to CAMPAIGN/AD_SET and can admit Stage 4C AD scope. | Define exact records and JSON shapes, restrict entity type, specify every route contract and error precedence, fix numeric limits/timeouts, and add controller/BFF/browser contract tests. |
| 4B-SPEC-005 | BLOCKING | Stage 4B Audit contract; exact Stage 4A `PlatformAuditEvent`; V2 Audit UUID identity | The three new subjects/events require business date, reservation kind, currency and amount/aggregate fields that the closed Stage 4A event cannot represent. `platform_account_budget_days` also has no UUID for required `subjectUuid`. Optionality, action, field ordering, value type, event cardinality, and zero-delta rules are undefined. | Add a stable subject UUID/mapping and exact additive typed event contract, constructors/presence matrix, AuditChange names/order/types, per-operation cardinality, rollback/no-event/redaction tests; retain Audit as an application invariant, not a direct-SQL claim. |
| 4B-SPEC-006 | BLOCKING | V13 batch/operation contract | Batch-first deferred FK is feasible, but retroactive batching of a committed pre-V13 operation is not forbidden, and legacy unbatched V12 operations are not classified for submit/retry/reconcile. | Require batch-insert rejection for an already-visible operation, reciprocal deferred checks for new operations, explicitly keep legacy unbatched rows inert/read-only, and test populated V12 upgrade plus retroactive/missing/orphan/valid batch-first cases. |
| 4B-SPEC-007 | BLOCKING | V13 account-day locking/concurrency | `SELECT ... FOR UPDATE` locks no absent row. Concurrent first reservations can race the PK insert rather than serialize under the ceiling; first zero-decrease behavior is also undefined. | Specify atomic `INSERT ... ON CONFLICT DO NOTHING` bootstrap followed by `SELECT ... FOR UPDATE` (or an equally exact protocol), zero-row/version/timestamp semantics, DB error mapping, and deterministic first-use concurrency tests below/at/above ceiling. |
| 4B-SPEC-008 | BLOCKING | V13 time/ledger/day coherence | A default does not prevent forged caller `created_at`; reservation-to-day FK/coherence and exact account-day insert/update protectors are incomplete. | Require database overwrite/rejection of caller batch time, derive date from that exact anchor, copy it to reservation, define account/FK/reciprocal/deferred aggregate checks, and add forged clock/orphan/wrong-day/aggregate/version/rollback direct-SQL tests. |
| 4B-SPEC-009 | MAJOR | Stage 4B verification matrix | Populated V12 upgrade evidence does not enumerate representative platform entities, budget provenance, or each in-flight/terminal unbatched operation state. | Require exact preservation fixtures and documented no-batch legacy behavior in migration compatibility tests. |

## Known limitations

- Account authorization is conservative and never released in 4B.
- One confirmed batch contains exactly one operation.
- Local/test fixed actor and two-step UI are not authentication or production role separation.
- Real Meta, credentials, spend, production, and Auth/RBAC/Tenant remain separately gated.
- Existing README Stage 04 progress text is stale relative to the merged Stage 4A implementation; this does not authorize changing runtime scope and should be corrected in a later approved documentation update.

## Stage Gate decision

- Decision: `ESCALATE_TO_HUMAN`
- Decision rationale: Exact-head CI and documentation scope passed, but 4B-SPEC-001 is an unresolved conflict between the approved operator/approver security boundary and the proposed same-actor FAKE/local/test mutation flow. The escalation policy requires a human decision when specifications conflict or a security/product boundary has multiple reasonable choices. Findings 002–009 also prevent an implementation-ready approval.
- Required next action: Keep PR #61 Draft. Freeze merge and every Stage 4B runtime/migration/API/BFF/UI implementation. Obtain the explicit repository-owner decision for 4B-SPEC-001; then revise only the Stage 4B specification and review record for findings 001–009, push a new exact Head, wait for full Push/PR CI, and request a fresh independent review.
- Human approval required: `Yes`
- Human approval reason／evidence: Decide whether FAKE/local/test activation and budget commands receive a narrowly scoped same-actor exemption, or remain disabled until operator/approver authorization separation exists.

## Approval record

- Not applicable because Decision is `ESCALATE_TO_HUMAN`.
