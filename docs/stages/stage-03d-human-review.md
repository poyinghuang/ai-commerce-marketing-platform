# Stage 03 Milestone 3D — Human Review and Approval Workflow

## Gate status

- Status: Completed
- Branch: `codex/stage-03-human-review`
- Base Commit: `ae244dfd7b41c6cea13896ae4dfff54159516802`
- Prerequisite: Milestone 3C completed at `fded67da3c539dbeef59f5e5d3a62a35e9041c30`
- Prerequisite tag: `milestone-3c-complete`
- Implementation: Passed
- Migration: V11 created; V1-V10 unchanged
- Local Verification: Passed
- Remote CI: Passed — Push Run `31349895731`; Pull Request Run `31349911642`
- Manager Review: Passed for implementation Head `8b6c1fb4a9ef4328465b19ecf232742808840911`
- Manager Decision: `APPROVE`
- Human Review Required: No
- Approval-record CI: Passed — Push Run `31350263644`; Pull Request Run `31350265943`
- Merge: Passed — PR #52, Squash Commit `965508f146e42b33d98e60abaffe15e65a182717`
- Post-merge CI: Passed — main Run `31350567158`
- Completion Tag: `milestone-3d-complete`
- Milestone 3E: Not started

## Approved scope

- Additive V11 persistence for one immutable human decision per generated output.
- Output lifecycle `PENDING_REVIEW -> APPROVED | REJECTED` with optimistic concurrency.
- Trusted human actor enforcement, same-transaction Audit, blocker evaluation, REST/BFF contracts, and review UI/history.
- Text and background-composite image outputs use the same review contract.
- No publish endpoint, platform write, automatic approval, Product redraw, video, Decision Engine, or Stage 04 behavior.

## V11 migration contract

Create `ai_review_decisions`:

- `review_decision_uuid UUID PRIMARY KEY`
- `generation_output_uuid UUID NOT NULL UNIQUE REFERENCES ai_generation_outputs ON DELETE RESTRICT`
- `decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED'))`
- `reason VARCHAR(2000)`; `APPROVED` requires `NULL`, `REJECTED` requires nonblank trimmed text
- `reviewer_type VARCHAR(32) NOT NULL CHECK (reviewer_type IN ('LOCAL_ADMIN', 'TRUSTED_ACTOR'))`
- `reviewer_id VARCHAR(128) NOT NULL CHECK (BTRIM(reviewer_id) <> '')`
- `request_id VARCHAR(128) NOT NULL` restricted to the existing safe request-ID character set
- `reviewed_output_version BIGINT NOT NULL CHECK (reviewed_output_version >= 0)`
- `decided_at TIMESTAMP WITH TIME ZONE NOT NULL`

V11 replaces only the output review-status constraint so `PENDING_REVIEW`, `APPROVED`, and `REJECTED` are valid. It replaces the existing output protection function to permit only a terminal transition from `PENDING_REVIEW`, while retaining every V10 immutable-field check. Deferred database coherence checks require one matching immutable decision and an output version exactly one greater than `reviewed_output_version`. Direct SQL cannot update/delete a decision, bypass a decision, reverse/change a terminal state, or delete an output.

V1–V10 are immutable and their canonical checksum tests remain active. Empty, populated V10-to-V11, repeat migration, Hibernate validation, and direct SQL protections are mandatory.

## Domain and transaction contract

- `GenerationOutputReviewStatus` contains `PENDING_REVIEW`, `APPROVED`, and `REJECTED`.
- `GenerationOutput.approve()` and `reject()` are the only application transitions; terminal outputs cannot transition again.
- `ReviewDecision` is immutable and append-only. Regeneration creates a new Job/Output and never replaces a decision.
- Review locks the output row, verifies the supplied version, evaluates blockers, creates the decision, updates output status/version, and appends Audit in one transaction.
- Missing output is 404; stale version is 412; terminal output, archived Product, or blocker is 409. Failed requests, stale requests, and repeated decisions write no decision/Audit and do not increment version.
- Human decisions accept only `LOCAL_ADMIN` and `TRUSTED_ACTOR`. `SYSTEM` actors are rejected. Production remains fail closed unless the existing trusted provider supplies a human actor.

## Non-overridable blockers

Approval is rejected with `AI_REVIEW_BLOCKED` when any of these is present:

- non-empty `safety_findings`
- IMAGE preservation is not exactly `PASSED`, or preservation evidence is incomplete
- linked Job is not `SUCCEEDED` or has any failure code, including cost invariant violations
- provider/data-policy validation did not produce a coherent persisted output
- Product or source/generated Asset is archived or no longer belongs to the output Product

Rejection remains allowed for a valid pending output even when approval blockers exist, because rejection cannot publish or weaken a safety gate. Browser input cannot suppress or override blockers.

## REST and BFF contract

- `POST /api/ai-generation-outputs/{outputUuid}/approve`
  - body must be absent or `{}`
  - requires `If-Match: W/"<version>"`
- `POST /api/ai-generation-outputs/{outputUuid}/reject`
  - body `{ "reason": "..." }`, 1–2000 characters after trim
  - requires `If-Match: W/"<version>"`
- `GET /api/ai-generation-outputs/{outputUuid}` returns immutable decision history and current blockers.
- Successful mutation returns 200, the updated weak ETag, output state, blockers, and decision history.
- Missing, malformed, and stale ETags return 428, 400, and 412. Unknown fields or non-object bodies return 400.
- Stable errors include `AI_OUTPUT_NOT_FOUND`, `AI_OUTPUT_ALREADY_DECIDED`, `AI_REVIEW_BLOCKED`, `AI_REVIEW_REASON_REQUIRED`, `AUDIT_ACTOR_UNAVAILABLE`, and existing archived/precondition codes.

Next.js adds exact UUID routes for `/approve` and `/reject` to the existing fixed-origin AI proxy. It forwards only `Content-Type`, safe `X-Request-ID`, and `If-Match`; it never forwards Cookie, Authorization, actor headers, provider URLs, workflow data, or arbitrary targets.

## Frontend contract

- Creative Factory output cards display content/preview, prompt-version reference through the Job, model/provider label, cost, safety findings, preservation evidence, current status, blockers, and immutable decision details.
- Pending eligible output provides explicit Approve and Reject actions. Reject requires a bounded reason.
- Blocked approval is disabled with visible reasons; Reject remains available.
- 409 shows terminal/blocked/archive guidance, 412 requires reload before retry, and 428 reports the missing concurrency token.
- Approved/Rejected output is read-only. No publish or platform action is rendered.

## Audit contract

- Effective review writes `CREATE AI_REVIEW_DECISION` and `UPDATE AI_GENERATION_OUTPUT` using one trusted operation context and transaction.
- Audit changes include output UUID, decision, reviewer type/ID, reason where applicable, and review-status transition. Existing redaction/truncation applies.
- No-op, stale, blocked, invalid-actor, and rollback paths leave no decision or Audit record.
- Logs and API responses never include credentials, headers, provider bodies, full prompts, image bytes, or sensitive request data.

## Verification matrix

### Migration/database

- V1→V11 cold migration, populated V10 upgrade, repeat migration, Hibernate validation, V1–V10 checksum protection.
- Direct JDBC valid approve/reject transactions plus invalid enum/reason/actor/request/version constraints.
- Direct JDBC rejects output-only terminal update, decision-only insert, terminal reversal/change, decision UPDATE/DELETE, output DELETE, and duplicate decisions.

### Backend

- Text and IMAGE approval; rejection reason persistence; immutable history and response ETag.
- Missing/malformed/stale ETag; simultaneous reviewers permit exactly one winner.
- Safety, pixel-preservation, cost/provider/data, archived Product/Asset blockers.
- Rejection of blocked output, repeated approve/reject, trusted actor matrix, transaction rollback, and Audit exact changes.
- Controller schema/status/error tests and all prior regression suites.

### Frontend/BFF

- Exact route/method/body/header allowlists, fixed origin, sanitized upstream failure, and no sensitive-header forwarding.
- Approve/reject success, reason validation, blockers, immutable terminal history, and 409/412/428 recovery.
- Lint, typecheck, Vitest, production build, and production dependency audit.

### Integration

- Compose config/cold start, health/smoke, existing Playwright journeys, Gitleaks, and actionlint.
- Stage 3E will add the complete browser acceptance matrix; 3D must add component-level review coverage now.

## Acceptance checklist

- [x] V11 is additive; V1–V10 are unchanged and migration compatibility passes.
- [x] One immutable human decision and a coherent terminal output state are database-enforced.
- [x] Approve/reject use `If-Match`, trusted human actor, one transaction, and exact Audit.
- [x] Safety, preservation, budget/provider/data, and archive blockers cannot be approved.
- [x] Text and IMAGE review work; regeneration never overwrites a prior output/decision.
- [x] Same-origin BFF and UI provide bounded review actions, blocker explanations, history, and conflict recovery.
- [x] No publish/platform/Ads/video/redraw/Decision Engine/Stage 04 scope exists.
- [x] Local and Remote Backend, Frontend, migration, Compose, Playwright regression, Gitleaks, dependency audit, and actionlint pass.
- [x] Exact-head Manager Review is `APPROVE`, merge/main CI pass, and `milestone-3d-complete` is published before 3E.

## Local verification evidence

- Backend: `./mvnw test` — 287 tests, 0 failures, 0 errors; Hibernate validation and migration suites passed.
- Frontend: lint, typecheck, 22 Vitest files / 134 tests, and Next.js production build passed.
- Migration: cold V1-V11, populated V10-to-V11 upgrade, repeat migration, canonical V1-V10 checksums, and direct SQL protection passed.
- Compose: isolated cold build/start passed; PostgreSQL, Backend, and Frontend were healthy; latest successful Flyway migration was V11.
- Smoke: Backend Actuator and Frontend same-origin health proxy returned only `{"status":"UP"}`; runtime UIDs were Backend 999 and Frontend 1000.
- Browser regression: Playwright 8/8 passed against the real isolated Compose stack.
- Security/tooling: Gitleaks history/worktree, npm production audit, actionlint, and `git diff --check` passed.
- Non-blocking warnings: Byte Buddy dynamic-agent future deprecation and Surefire's delayed fork-JVM shutdown message remain existing test-runtime warnings.

## Escalation boundaries

Escalate before destructive migration, production credentials/deployment, authentication/RBAC/security-model change, budget change, automatic approval/publication, platform write, blocker override, Product redraw, video, Decision Engine, or Stage 04 scope.
