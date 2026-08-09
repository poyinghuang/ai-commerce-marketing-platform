# Stage 03 Milestone 3B — Text Generation Vertical Slice

## Gate status

- Status: Completed
- Branch: `codex/stage-03-text-generation`
- Base Commit: `4386eed412f0c49f740e4fad17ba781e12afd078`
- Prerequisite: Milestone 3A completed at `b90517a7c5cf9b37c894ceea8beef090a04b149d`
- Prerequisite tag: `milestone-3a-complete`
- Implementation: Complete
- Local Verification: Passed for Backend, Frontend, migration, Compose, smoke, Playwright, dependency audit, Gitleaks, and diff checks; local actionlint not verified
- Remote CI: Passed — Push Run `31336947018`; Pull Request Run `31336949428`
- Manager Review: Passed for implementation Head `5696c68d8c07bb4188c7c4e26a8cf9f20b181941`
- Manager Decision: `APPROVE`
- Approved CI Runs: Push `31336947018`; Pull Request `31336949428`
- Approval-record CI Runs: Push `31337265810`; Pull Request `31337267918`
- Merge: Passed — PR #48, Squash Commit `c1659cf0508e961860d95b13f52db72bfa4dc0c7`
- Post-merge CI: Passed — main Run `31337531564`
- Completion Tag: `milestone-3b-complete`
- Milestone 3C: Not started

## Local implementation evidence

- V9 is additive and creates only `ai_generation_outputs` plus the composite Job relationship key required by its foreign key. V1-V8 are unchanged.
- Empty V1-V9, populated V8-V9, repeat migration, canonical checksums, Hibernate validation, direct JDBC constraints, immutable-field triggers, and delete rejection passed.
- Backend full regression produced 62 Surefire reports and 263 tests with 0 failures, 0 errors, and 0 skipped. The known non-blocking Surefire fork shutdown-after-success warning remains.
- Text integration proves default three variations, server-rendered immutable snapshots, Stub success, partial sibling failure, stale and duplicate execution rejection, archived Product blocking, truthful settlement, cost-invariant blocking, same-operation Audit, and persisted pending-review Output.
- Untrusted Product context escapes markup delimiters before prompt embedding, and a regression test proves stored content cannot close the server-owned prompt boundary.
- Frontend lint and typecheck passed; 22 Vitest files / 130 tests passed; production build passed; `npm audit --omit=dev` found 0 vulnerabilities.
- Fixed-origin AI BFF tests prove exact path/query/header allowlists, body bounds, status/body preservation, and no Cookie, Authorization, or Browser actor forwarding.
- Compose config passed. An isolated cold stack on host ports 18081/13001 became healthy, applied V9, used non-root UIDs 999/1000, and completed Product -> Creative Plan -> text Batch -> Job -> pending-review Output through the same-origin BFF with AI Audit rows.
- All eight existing Stage 02 Playwright scenarios passed against the isolated stack. The ephemeral Compose volume was removed afterward.
- Gitleaks 8.28.0 using the CI-pinned image digest scanned 75 commits and the worktree with no leaks. `git diff --check` passed.
- Local actionlint is not installed and the policy-blocked download was not bypassed. No Workflow file changed; required actionlint evidence remains Pending Remote CI.
- The first Compose start was blocked only because another local repository occupied host port 8080. Compose host ports are now configurable through `BACKEND_PORT` and `FRONTEND_PORT`, with existing defaults unchanged.

## Scope

- Additive V9 text-output persistence.
- Server-side prompt projection and rendering from Product, active Knowledge, and one active Creative Plan.
- Batch creation with three text variations by default and a bounded requested count of 1–3.
- Explicit single-job execution using the provider-neutral `TextGenerationProvider`.
- Deterministic local/test Stub and default/production fail-closed behavior from 3A.
- Job/batch/output query APIs, same-origin Next.js BFF, and Product Creative Factory text UI.
- ETag/If-Match, transactional Audit, budget settlement, partial batch behavior, and recovery-safe provider idempotency.

## Explicit exclusions

- No image generation, ComfyUI, binary storage, pixel-preservation, or V10 schema.
- No approval/rejection mutation or V11 review-decision schema.
- No live/paid provider, credential, provider URL, arbitrary model, arbitrary prompt, or Browser budget input.
- No video, publication, Ads write, Decision Engine, or Stage 04 functionality.

## V9 persistence contract

V9 creates only `ai_generation_outputs`; V1–V8 remain byte-for-byte unchanged.

- `generation_output_uuid UUID PRIMARY KEY`
- `generation_job_uuid UUID NOT NULL UNIQUE REFERENCES ai_generation_jobs ON DELETE RESTRICT`
- `generation_batch_uuid UUID NOT NULL`
- `product_uuid UUID NOT NULL`
- `generation_type VARCHAR(16) NOT NULL CHECK (TEXT)`
- `text_content VARCHAR(16000) NOT NULL` and nonblank
- `model_label VARCHAR(128) NOT NULL`
- `input_units BIGINT NOT NULL CHECK (>= 0)`
- `output_units BIGINT NOT NULL CHECK (>= 0)`
- `actual_cost NUMERIC(19,6) NOT NULL CHECK (>= 0)`
- `currency CHAR(3) NOT NULL` with uppercase ISO format
- `safety_findings JSONB NOT NULL` array with bounded serialized size
- `provider_metadata JSONB NOT NULL` object with bounded serialized size and no credentials/URL/payload body
- `review_status VARCHAR(24) NOT NULL CHECK (PENDING_REVIEW)`
- `created_at`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL CHECK (>= 0)`

Composite foreign keys prove the output Job, Batch, and Product association. Output identity, association, content, usage, cost, currency, model label, safety findings, provider metadata, and creation time are immutable. Direct DELETE is rejected. V11 may add review transitions without modifying V9.

## Prompt and data contract

- Browser selects only a logical active template key, active Creative Plan UUID, logical model profile, and count.
- The Backend loads Product Master plus active Knowledge and the selected active Creative Plan using explicit projections.
- Allowed Product fields: product ID, SKU, name, brand, category, subcategory, short description, prices/currency, stock, and Product URL.
- Allowed Knowledge fields: type, title, content, source, and version; each collection and field is bounded and deterministically ordered.
- Allowed Creative Plan fields are the existing bounded planning fields. Raw entities are never serialized.
- Customer, order, payment, credential, environment, header, cookie, arbitrary document, and server state are never projected.
- Rendering uses the immutable prompt template version and persists the exact normalized allowlisted snapshot plus rendered prompt before provider execution.
- Product content is delimited as untrusted data and cannot select a provider, model, workflow, tool, URL, or instruction hierarchy.

## State and transaction contract

1. Create validates Product/Plan/template/model profile, renders 1–3 immutable Job snapshots, derives server-side cost ceilings, and commits Batch, Jobs, reservations, and Audit in one transaction.
2. Execute requires the current Job `If-Match`. A short transaction locks the Job, validates `CREATED`, and marks it `SUBMITTED` with an immutable provider idempotency key derived from the Job UUID.
3. Provider execution occurs outside a database transaction. No lock or connection is held while calling the provider.
4. Completion locks the Job again. Success persists one output, truthful usage/cost, budget COMMIT/RELEASE, Job success, derived Batch counts/status, and Audit in one transaction.
5. Provider/validation failure records bounded failure state, releases the reservation when no billable usage exists, updates Batch counts/status, and writes Audit in one transaction.
6. A provider-reported actual cost above the reservation is retained, flagged `AI_COST_INVARIANT_VIOLATION`, and prevents automatic execution of remaining Batch jobs.
7. Repeating execute after a terminal state is a conflict and creates no duplicate provider call, output, ledger entry, version increment, or Audit.

## REST contract

- `POST /api/products/{productUuid}/ai-generation-batches`
- `GET /api/products/{productUuid}/ai-generation-batches`
- `GET /api/ai-generation-batches/{batchUuid}`
- `GET /api/ai-generation-jobs/{jobUuid}`
- `POST /api/ai-generation-jobs/{jobUuid}/execute`
- `GET /api/ai-generation-outputs/{outputUuid}`
- `GET /api/ai-budget/status`

Create returns `201`, `Location`, Batch `ETag`, and the created Jobs. Job/Batch/Output GET returns `ETag: W/"<version>"`. Execute requires `If-Match`; missing, malformed, and stale values return 428, 400, and 412. Ownership mismatch is 404. Archived Product/Plan and invalid state return 409.

Request bodies cannot contain raw prompt/template JSON, rendered prompt, snapshot, provider URL, provider/model SDK identifier, cost, currency, credential, actor, workflow, output path, or publish target.

## Frontend and BFF contract

- Product detail adds a `Creative Factory` tab without changing the Stage 02 Dashboard boundary.
- UI supports text-batch creation, 1–3 variation selection, Job execution, reload, partial failure, budget rejection, provider unavailable, archived conflict, and stale ETag recovery.
- Text outputs display content, model label, cost/usage, safety findings, prompt-version reference, and `Pending review`; no Approve/Publish button exists in 3B.
- Route Handlers use fixed Backend origin plus exact UUID/path/method/header/body allowlists.
- Only content type, `If-Match`, `ETag`, `Location`, and request ID are forwarded as required. Cookie, Authorization, actor, arbitrary upstream headers, URLs, and provider metadata are not forwarded.
- Backend unavailable/timeout responses preserve the Repository error contract and never become HTTP 200.

## Error codes

- `AI_BUDGET_NOT_CONFIGURED`
- `AI_JOB_BUDGET_EXCEEDED`
- `AI_BATCH_BUDGET_EXCEEDED`
- `AI_DAILY_BUDGET_EXCEEDED`
- `AI_PROVIDER_NOT_CONFIGURED`
- `AI_PROVIDER_UNAVAILABLE`
- `AI_PROVIDER_RATE_LIMITED`
- `AI_PROVIDER_REJECTED`
- `AI_PROMPT_TEMPLATE_NOT_FOUND`
- `AI_PROMPT_INPUT_INVALID`
- `AI_DATA_POLICY_VIOLATION`
- `AI_OUTPUT_INVALID`
- `AI_GENERATION_BATCH_NOT_FOUND`
- `AI_GENERATION_JOB_NOT_FOUND`
- `AI_GENERATION_STATE_CONFLICT`
- `AI_COST_INVARIANT_VIOLATION`
- `PRODUCT_ARCHIVED`

## Verification and acceptance

- Empty V1→V9, populated V8→V9, repeat migration, Hibernate validation, V1–V8 canonical checksums, and direct SQL trigger/constraint tests pass.
- Create produces three variations by default, with immutable distinct Jobs and reservations derived from server profiles.
- Prompt rendering is deterministic and rejects unknown/sensitive projection fields and injection attempts.
- Stub success persists text Output, usage/cost, settlement, terminal Job and derived Batch state with same-transaction Audit.
- One failed sibling does not roll back successful outputs; Batch finalizes `COMPLETED_WITH_ERRORS`.
- Budget rejection makes no provider call. Missing production provider/budget configuration fails closed.
- Stale/missing/malformed ETag, archived Product/Plan, wrong ownership, duplicate execute, rollback, and cost-invariant cases pass.
- Backend tests, Frontend lint/typecheck/tests/build, npm audit, actionlint, Gitleaks, Compose, existing Playwright regression, Remote CI, exact-head Manager Review, merge, and post-merge main CI pass.
