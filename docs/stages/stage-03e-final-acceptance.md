# Stage 03 Milestone 3E — Final Acceptance

## Gate status

- Status: Approved for implementation
- Branch: `codex/stage-03-final-acceptance`
- Base Commit: `1a2e78d6cfecf6862d54d69243ee8db3f8efd036`
- Prerequisite: Milestone 3D completed at `965508f146e42b33d98e60abaffe15e65a182717`
- Prerequisite tag: `milestone-3d-complete`
- Implementation: Not started
- Migration: Not required; V1-V11 remain unchanged
- Local Verification: Not started
- Remote CI: Not started
- Manager Review: Not started
- Manager Decision: Pending
- Human Review Required: No
- Merge: Not started
- Stage 04: Not started

## Approved scope

Milestone 3E is an acceptance and hardening slice for the already-approved Stage 03 architecture. It adds committed real-Compose Playwright journeys, deterministic local/test-only fixtures needed to make safety failure paths observable, CI budget configuration for non-production fixtures, and documentation. Runtime fixes are allowed only when an acceptance test exposes a defect inside the existing Stage 03 contract.

No new persistence schema, provider architecture, production provider, credential, budget mutation API, publication path, Meta Ads behavior, Product redraw, video generation, Decision Engine, or Stage 04 functionality is allowed.

## Deterministic acceptance fixtures

- CI/local acceptance supplies explicit non-production budget configuration: USD, per-job 5, per-batch 20, per-day 100.
- Existing `STANDARD`, `LOW_COST`, `PARTIAL_FAILURE_FIXTURE`, and `COST_INVARIANT_FIXTURE` text profiles remain deterministic.
- Add a local/test-only `OVER_JOB_BUDGET_FIXTURE` selection mapped to the existing allowlisted `stub-text-over-job` ceiling. It must be rejected before provider execution and must not weaken production fail-closed behavior.
- Add a second local/test source handle whose source bytes are valid but whose deterministic Stub result changes one protected opaque Product pixel. The preservation verifier, not the Browser or provider, must produce `BLOCKED` and `AI_PRODUCT_PIXELS_CHANGED`.
- Fixture identifiers are server-controlled and never accepted as arbitrary provider origins, workflows, credentials, URLs, or paths.

## Playwright acceptance matrix

1. Text generation can be created/executed and one output approved while another is rejected with a required reason; terminal decisions remain immutable after reload.
2. Two Browser contexts reviewing the same output prove stale ETag recovery: exactly one decision succeeds and the stale request receives 412 with reload guidance.
3. `OVER_JOB_BUDGET_FIXTURE` is rejected with `AI_JOB_BUDGET_EXCEEDED` before any Job/output/Audit execution state is created; the configured budget status remains read-only.
4. `PARTIAL_FAILURE_FIXTURE` produces a deterministic mixed batch (success/failure), preserves failed Job evidence, and allows successful outputs to be reviewed without retrying or replacing failed Jobs.
5. A normal IMAGE background-composite output preserves protected pixels, records evidence, can be approved, and retains source/generated Asset relationships.
6. The changed-pixel IMAGE fixture is `BLOCKED`, visibly disables approval, returns 409 if approval is attempted directly, and remains rejectable with a reason.
7. Archiving a Product prevents new generation and prevents approval of an already-pending output; restore does not silently approve or replace it.
8. Database assertions prove effective review Audit entries and the absence of publication/platform entity types or endpoints. UI and route manifests contain no Publish action.

The suite may use API setup through the same-origin BFF, but all end-user review, conflict, blocker, and terminal-history behavior must be asserted in the Browser UI. Database assertions use the existing guarded fixed Compose PostgreSQL helper pattern and validated UUIDs only.

## Verification requirements

- Backend full tests, including deterministic fixture/profile tests and all V1-V11 migration/checksum/Hibernate/direct-SQL regressions.
- Frontend lint, typecheck, Vitest, production build, and production dependency audit.
- Playwright full suite against an isolated cold Compose stack with explicit test budget configuration and guarded Audit DB assertion.
- Actuator/BFF health, latest Flyway V11, non-root runtime, actionlint, Gitleaks history/worktree, and `git diff --check`.
- Remote Push and Pull Request `quality-and-compose` and `secret-scan` must execute without required-step skips.

## Acceptance checklist

- [ ] V1-V11 are unchanged; no V12 or other migration is introduced.
- [ ] Text approval/rejection, immutable history, and stale-review recovery pass in real Browser journeys.
- [ ] Per-job budget rejection and partial-batch evidence pass without provider/network access.
- [ ] Valid image preservation can be approved; changed protected pixels cannot be approved and can be rejected.
- [ ] Archived Product and Asset/safety/preservation blockers remain non-overridable.
- [ ] Effective decisions have same-operation Audit; failed/stale/blocked attempts leave no decision Audit.
- [ ] No publication, platform write, Meta Ads, Product redraw, video, Decision Engine, or Stage 04 path exists.
- [ ] Full local and Remote verification pass at the exact implementation Head.
- [ ] Manager Decision is `APPROVE`, merge and post-merge main CI pass, and tags `milestone-3e-complete` and `stage-03-complete` are published.

## Escalation boundaries

Escalate before any migration, authentication/RBAC/security-model change, production credential/deployment, paid provider use, human-controlled production budget change, provider architecture change, automatic approval/publication, platform write, blocker override, Product redraw, video, Decision Engine, destructive data action, or Stage 04 scope.
