# Stage 03 — AI Creative Factory

## Gate status

- Status: Human architecture decision approved; detailed specification in progress
- Branch: `codex/stage-03-ai-creative-spec`
- Base Commit: `d039478d477c97e0f7cd658bd46cca2719b4cc33`
- Stage 02 prerequisite: Passed — `stage-02-complete` at `73f20fe75ef64da8add771087a2035a773d905af`
- Implementation: Not started
- Migration: Not created
- Local Verification: Pending
- Remote CI: Pending
- Manager Review / Decision: Pending
- Human Review Required: No for the approved specification; Yes only if an escalation boundary below is crossed
- Merge: Pending
- Stage 04: Not started

## Human-approved decisions

1. Stage 03 generates text and images only. Video generation is deferred.
2. Domain and application layers depend only on provider-neutral ports; vendor SDK types never cross an adapter boundary.
3. Image generation is ComfyUI-first. The default workflow preserves Product source pixels and generates the environment/background; Product redraw is outside Stage 03.
4. Every job, batch, and UTC day is protected by mandatory monetary budget guards. Budget values are human-controlled server configuration and have no Browser mutation API.
5. Credentials come only from environment/secret infrastructure. Developer tasks and normal CI receive no production credentials.
6. Only allowlisted Product and Creative Plan fields are sent to providers. Secrets, customer PII, orders, and payment information are prohibited.
7. Every generated output requires a trusted human approval before it can be considered publishable.
8. Stage 03 contains no Meta Ads automation, automatic publication, Decision Engine, or production video generation.

## Architecture boundaries

- PostgreSQL remains the System of Record for templates, versions, jobs, budget reservations/ledger, output metadata, and human decisions.
- Generated binaries live behind an `AssetBinaryStore` port. PostgreSQL stores IDs, checksums, sizes, media types, preservation evidence, and bounded provider metadata; it never stores image bytes.
- `TextGenerationProvider` and `ImageGenerationProvider` are application ports. Local/test uses deterministic Stub providers. Default/production profiles fail closed until a separately configured trusted adapter is available.
- The ComfyUI adapter uses a fixed server-side `COMFYUI_BASE_URL`; Browser input cannot control scheme, host, port, workflow path, credentials, output URL, or arbitrary node graph.
- Repository-versioned ComfyUI workflows use API-format JSON plus a manifest of allowlisted node IDs/input names. Browser requests select a logical workflow key, never submit workflow JSON.
- Provider calls occur outside long database transactions. A persisted job and budget reservation exist before submission; provider idempotency uses immutable `generation_job_uuid`; recovery resumes only known nonterminal jobs.
- Existing `StorageProvider` continues to own Product folder creation. Binary read/write is a separate narrow port so Stage 03 does not expand folder-management semantics or introduce file deletion.

## Logical agent roles

- Creative Planner: selects the approved Creative Plan and constructs a bounded generation batch.
- Copywriter: produces three text variations per approved text batch unless a lower bounded count is explicitly requested.
- Image Planner: selects the approved background-composite workflow, source image, mask/alpha evidence, and bounded visual parameters.
- QA: validates schema, safety, cost, pixel preservation, and review blockers. QA never approves or publishes an output.

These are application responsibilities, not autonomous identities or trusted actors. They cannot bypass the Backend state machine, budget guard, provider allowlist, Audit, or human review.

## Delivery milestones

No dependent milestone starts until the preceding milestone receives exact-head Manager `APPROVE`, merges, and passes post-merge `main` CI.

### 3A — Generation persistence, prompt versioning, budget and audit foundation

- Additive V8 migration and JPA/domain foundation only.
- Prompt template identity and immutable versions.
- Generation batches/jobs, explicit state machine, optimistic locking, Archive where applicable, and append-only budget ledger.
- Server-only `AiBudgetPolicyProvider`, deterministic UTC budget guard, reservation/commit/release behavior, and concurrent overspend protection.
- Provider ports, local/test deterministic stubs, default/production fail-closed profiles, trusted actor/request ID, Audit, migration compatibility, and Testcontainers.
- No generation REST API or Frontend.

### 3B — Text generation vertical slice

- Additive V9 output persistence required by text results.
- Prompt renderer using allowlisted Product/Knowledge/Creative Plan fields and immutable prompt-version snapshots.
- Text provider submit/complete/failure contract, deterministic Stub, production fail-closed adapter selection, and bounded usage/cost metadata.
- Generate three persisted copy variations by default, each independently reviewable and traceable to the same immutable prompt/template snapshot.
- Text generation API, same-origin BFF, Product Creative area UI, ETag/status/error recovery, Audit, Quality-neutral behavior, and tests.
- No paid provider is enabled by normal CI. A live provider adapter/configuration is a separately reviewed operational change.

### 3C — ComfyUI background image generation

- Additive V10 image-specific source/mask/preservation fields only.
- `AssetBinaryStore` port, local/test binary store, and ComfyUI adapter using fixed `/prompt`, `/history/{prompt_id}`, and `/view` routes with bounded polling/timeouts.
- Require an approved Product source image with an alpha channel or explicit mask. Default mode is `BACKGROUND_COMPOSITE`; redraw mode is rejected.
- Upload only bounded source/mask bytes, inject only manifest-allowlisted workflow inputs, retrieve only provider-returned output identifiers, validate PNG/JPEG type/size/dimensions, and store through the binary port.
- Compute and persist exact protected-region pixel evidence. A changed protected Product region blocks the output from approval.
- Create generated IMAGE Asset metadata only after binary persistence succeeds; use deterministic recovery for external-write/database-commit gaps.

### 3D — Human review and approval workflow

- Additive V11 append-only review-decision persistence.
- Output lifecycle: `PENDING_REVIEW -> APPROVED | REJECTED`. Regeneration creates a new job/output; it never overwrites an approved output.
- Approve/reject requires current `If-Match`, a trusted actor, and a reason for rejection. No Browser actor header is accepted.
- Safety, preservation, budget, provider, and data-minimization blockers cannot be overridden by approval.
- Review UI shows prompt version, source assets, provider/model label, cost, safety findings, preservation result, output preview/text, and immutable decision history.
- There is no publish endpoint and no external platform write.

### 3E — E2E and Stage 03 acceptance

- Real Compose, deterministic Stub text/image providers, synthetic non-PII Product data, full existing regression, and committed Playwright journeys.
- Prove budget rejection, partial batch behavior, retry/recovery, text approval/rejection, background image pixel preservation, changed-pixel blocking, archived Product blocking, stale ETag recovery, Audit, and no publication path.
- Exact-head Manager Gate, merge, post-merge `main` CI, documentation closeout, and completion tags `milestone-3e-complete` plus `stage-03-complete`.

## V8 persistence contract (owned by 3A)

V8 is additive; V1–V7 remain byte-for-byte unchanged.

### `ai_prompt_templates`

- `prompt_template_uuid UUID PRIMARY KEY`
- `template_key VARCHAR(128) NOT NULL UNIQUE`
- `generation_type VARCHAR(16) NOT NULL CHECK (TEXT, IMAGE)`
- `display_name VARCHAR(256) NOT NULL`
- `lifecycle_status VARCHAR(16) NOT NULL CHECK (ACTIVE, ARCHIVED)`
- `created_at`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`, `version BIGINT NOT NULL`

Template UUID/key/type are immutable. Archive is idempotent; hard delete is rejected.

### `ai_prompt_template_versions`

- `prompt_template_version_uuid UUID PRIMARY KEY`
- `prompt_template_uuid UUID NOT NULL REFERENCES ai_prompt_templates ON DELETE RESTRICT`
- `version_number INTEGER NOT NULL CHECK (> 0)` and unique per template
- `template_text VARCHAR(16000) NOT NULL`
- optional `negative_prompt VARCHAR(8000)`
- `input_schema JSONB NOT NULL` containing only the bounded logical placeholder contract
- `content_sha256 CHAR(64) NOT NULL`
- `created_by VARCHAR(128) NOT NULL`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

Versions are immutable and append-only. Provider model IDs, secrets, tokens, URLs, and credentials are not template content.

### `ai_generation_batches`

- `generation_batch_uuid UUID PRIMARY KEY`
- `product_uuid UUID NOT NULL REFERENCES products ON DELETE RESTRICT`
- optional `creative_plan_uuid UUID REFERENCES creative_plans ON DELETE RESTRICT`
- `status VARCHAR(24) NOT NULL CHECK (CREATED, RUNNING, COMPLETED, COMPLETED_WITH_ERRORS, BUDGET_REJECTED, CANCELLED)`
- `currency CHAR(3) NOT NULL` with uppercase ISO format
- `estimated_cost`, `reserved_cost`, `actual_cost NUMERIC(19,6) NOT NULL CHECK (>= 0)`
- `requested_job_count INTEGER NOT NULL CHECK (> 0)` and coherent success/failure/rejection counts
- `created_by VARCHAR(128) NOT NULL`, `created_at`, `updated_at`, `version`

Identity, Product/Plan association, currency, creator, and requested count are immutable. Delete is rejected.

### `ai_generation_jobs`

- `generation_job_uuid UUID PRIMARY KEY`
- `generation_batch_uuid UUID NOT NULL REFERENCES ai_generation_batches ON DELETE RESTRICT`
- `product_uuid UUID NOT NULL REFERENCES products ON DELETE RESTRICT`
- optional `creative_plan_uuid UUID REFERENCES creative_plans ON DELETE RESTRICT`
- `prompt_template_version_uuid UUID NOT NULL REFERENCES ai_prompt_template_versions ON DELETE RESTRICT`
- `generation_type VARCHAR(16) NOT NULL CHECK (TEXT, IMAGE)`
- `provider_key VARCHAR(64) NOT NULL`, `model_key VARCHAR(128) NOT NULL`
- `status VARCHAR(24) NOT NULL CHECK (CREATED, SUBMITTED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BUDGET_REJECTED)`
- immutable `rendered_prompt VARCHAR(16000) NOT NULL`, optional `negative_prompt VARCHAR(8000)` and bounded `input_snapshot JSONB`
- optional immutable `provider_job_id VARCHAR(256)` once submitted; no credential or provider URL
- `estimated_cost`, `reserved_cost`, `actual_cost NUMERIC(19,6) NOT NULL CHECK (>= 0)`, `currency CHAR(3) NOT NULL`
- bounded failure code/message, attempt count, submitted/started/completed timestamps, created/updated timestamps, and `version`

Jobs cannot change Product, plan, prompt snapshot, provider/model selection, generation type, estimate, or currency after creation. State transitions and cost finalization use explicit methods and optimistic locking. Delete is rejected.

### `ai_budget_ledger`

- `budget_ledger_uuid UUID PRIMARY KEY`
- `generation_job_uuid UUID NOT NULL REFERENCES ai_generation_jobs ON DELETE RESTRICT`
- `budget_date DATE NOT NULL` in UTC
- `entry_type VARCHAR(16) NOT NULL CHECK (RESERVE, COMMIT, RELEASE)`
- `amount NUMERIC(19,6) NOT NULL CHECK (> 0)`, `currency CHAR(3) NOT NULL`
- `entry_order INTEGER NOT NULL CHECK (>= 0)`, unique per job
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

The ledger is database-protected append-only. A job can reserve once, then commit actual cost and release unused reservation, or release the whole reservation on pre-provider cancellation/failure. Direct UPDATE/DELETE is rejected.

## Budget guard contract

- Required server configuration: `AI_BUDGET_CURRENCY`, `AI_MAX_JOB_COST`, `AI_MAX_BATCH_COST`, `AI_MAX_DAILY_COST`.
- Missing, malformed, zero/negative, mixed-currency, or internally inconsistent values fail closed for generation mutations.
- No REST/BFF mutation can change limits. A human-controlled configuration change and process restart are required.
- The application validates per-job and per-batch estimates before persistence, then serializes the UTC-day reservation decision in PostgreSQL so concurrent requests cannot overspend the daily limit.
- Daily usage is `committed actual cost + active reservations`. Released amounts no longer consume the limit. Actual cost above reservation is committed only if the additional atomic daily-budget check succeeds; otherwise the job is blocked for manual operational review and no further provider call is made.
- Rejected jobs/batches produce a bounded error and Audit record but no provider call. Budget values and usage are observable; credentials and provider payloads are not.
- Stage 03 never auto-increases a budget, changes currency, or retries past a budget rejection.

## Provider-neutral contracts

### `TextGenerationProvider`

- Accepts a domain-owned immutable request: job UUID/idempotency key, rendered prompt, model key, output schema, maximum output length, and timeout.
- Returns provider-neutral text, usage counters, estimated/actual monetary cost, model label, safety findings, and bounded opaque metadata.
- Provider exceptions map to stable codes such as `AI_PROVIDER_NOT_CONFIGURED`, `AI_PROVIDER_UNAVAILABLE`, `AI_PROVIDER_RATE_LIMITED`, `AI_PROVIDER_REJECTED`, and `AI_OUTPUT_INVALID`.

### `ImageGenerationProvider`

- Accepts job UUID/idempotency key, repository workflow key/version, allowlisted workflow inputs, bounded source/mask handles, requested dimensions/format, and timeout.
- Returns provider-neutral job state and output handle/metadata. It never exposes arbitrary download URLs to the Browser.
- The ComfyUI adapter validates configured origin, workflow manifest, prompt ID, output filenames/subfolders/types, response size, media type, and image dimensions.
- ComfyUI workflows are Repository assets and use API-format JSON. Stage 03 relies on the documented queue/history/view behavior but wraps it behind the provider port because the external contract may evolve.

### `AssetBinaryStore`

- Reads only the explicitly authorized source/mask Asset and writes generated bytes to an application-selected Product path.
- Uses bounded streaming; no unbounded byte arrays, arbitrary filesystem path, traversal, Browser URL, overwrite, delete, or rename operation is exposed.
- Returns opaque provider ID, media type, size, checksum, and bounded metadata. Database compensation never deletes external data automatically; deterministic idempotency/reconciliation handles commit gaps.

Official ComfyUI references:

- <https://docs.comfy.org/development/overview>
- <https://docs.comfy.org/development/comfyui-server/comms_routes>
- <https://docs.comfy.org/development/core-concepts/workflow>

## Prompt rendering and data minimization

- Allowed context is an explicit projection of Product Master, active Product Knowledge, the selected active Creative Plan, and optional Campaign purpose. No raw entity serialization is permitted.
- Orders, customers, addresses, emails, phones, payment fields, cookies, headers, tokens, environment variables, provider credentials, arbitrary uploaded documents, and free-form server state are never included.
- Each field is length-bounded, normalized, delimited as untrusted data, and rendered into a versioned template. Product content cannot introduce system/provider instructions or select tools/workflows.
- The persisted input snapshot contains only the allowlisted normalized projection required to reproduce the prompt. Audit records hashes/IDs and effective state changes, not entire prompts, generated images, secrets, or provider error bodies.
- Logs contain job/batch/product/template IDs, state, duration, and bounded cost metadata. Prompt/output bodies and binary contents are excluded.

## Product-pixel preservation contract

- Source Asset must be an active IMAGE belonging to the Product and must have a verified checksum. An explicit mask Asset must belong to the same Product, or the source PNG must provide a usable alpha channel.
- The protected Product region is the nontransparent/masked source region. The ComfyUI workflow generates only outside that region and composites the original protected pixels over the generated background.
- Before persistence, the application decodes source, mask, and output with bounded dimensions/pixels and compares the exact protected-region RGBA values. It stores source checksum, mask checksum, protected-pixel checksum, output checksum, algorithm version, and result.
- Any dimension mismatch, malformed/decompression-bomb image, missing mask/alpha, changed protected pixel, unsupported format, or absent evidence produces a blocker. A blocked output cannot be human-approved.
- The Stage 03 API has no redraw option. A future Product redraw mode requires a new human architecture decision.

## State, transaction and recovery rules

- Batch/job creation, budget reservation, trusted Audit, and initial state commit in one transaction before any provider call.
- Provider submission happens outside the transaction with job UUID as the idempotency key. A short transaction then records provider ID/state. Ambiguous submission is recovered by provider lookup/idempotency, never blind duplicate submission.
- Completion downloads/validates output before the database transaction that stores output metadata, generated Asset metadata, cost ledger entries, final state, and Audit.
- Each job is isolated. One failed or budget-rejected job does not roll back successful siblings; batch counts/status are derived and finalized deterministically.
- Retry is bounded and only for idempotent lookup/read or documented transient provider failures. Validation, safety, credential, preservation, and budget failures are not retried automatically.
- SYSTEM recovery uses an explicit SYSTEM actor, a server-generated request ID, and one operation context. No Browser actor is trusted.

## REST and BFF contract

Planned Backend routes:

- `POST /api/products/{productUuid}/ai-generation-batches`
- `GET /api/products/{productUuid}/ai-generation-batches`
- `GET /api/ai-generation-batches/{batchUuid}`
- `GET /api/ai-generation-jobs/{jobUuid}`
- `POST /api/ai-generation-jobs/{jobUuid}/execute`
- `POST /api/ai-generation-jobs/{jobUuid}/cancel`
- `GET /api/ai-generation-outputs/{outputUuid}`
- `POST /api/ai-generation-outputs/{outputUuid}/approve`
- `POST /api/ai-generation-outputs/{outputUuid}/reject`
- `GET /api/ai-budget/status`

Create requests select Product/Plan, logical template/workflow key, generation type, model profile, count, and bounded creative parameters. They cannot provide provider URL, credential, raw workflow JSON, arbitrary prompt template, budget override, actor, output path, or publish target.

- Resource GET responses return `ETag: W/"<version>"`.
- Execute/cancel/approve/reject require `If-Match`; missing, malformed, and stale tokens return 428, 400, and 412.
- Archived Product/Plan/source Asset conflicts return 409. Ownership mismatch returns 404 without disclosing another Product's resource.
- Same-origin Next.js Route Handlers use exact UUID/path/method/query/header allowlists, bounded bodies, fixed Backend origin, sanitized timeout errors, and no Cookie/Authorization/actor forwarding.

## Frontend scope

- Product detail `Creative Factory` tab with batch creation and job/output history.
- Template/model profile selection from server-provided allowlists; no arbitrary endpoint/workflow/prompt JSON input.
- Text result and image result review cards with source/preservation comparison, cost, safety findings, prompt version, status, and failure recovery.
- Explicit Generate/Execute, Cancel, Approve, Reject, Reload, and Regenerate actions with concurrency conflict UX.
- Budget status and rejection explanations without exposing secrets or internal provider payloads.
- Loading, empty, partial success, provider unavailable, archived, validation, preservation-blocked, stale, and retry states; responsive and accessible behavior.
- No publish button, Ads action, automatic approval, video control, or Decision Engine recommendation.

## Audit contract

- Entity types: `AI_PROMPT_TEMPLATE`, `AI_GENERATION_BATCH`, `AI_GENERATION_JOB`, `AI_GENERATION_OUTPUT`, `AI_REVIEW_DECISION`, and `AI_BUDGET_LEDGER` where appropriate.
- Every effective mutation uses trusted actor/request ID and Audit in the same database transaction. SYSTEM recovery is explicit.
- Audit stores IDs, states, hashes, cost/currency, model/provider labels, blocker codes, and decision reason. It never stores credentials, headers, raw provider payloads, complete prompts/outputs, image bytes, or PII.
- No-op state transitions and idempotent retries create no version increment or duplicate Audit.
- Review and budget-ledger database records are append-only and protected by direct SQL triggers.

## Error codes

- `AI_BUDGET_NOT_CONFIGURED`, `AI_JOB_BUDGET_EXCEEDED`, `AI_BATCH_BUDGET_EXCEEDED`, `AI_DAILY_BUDGET_EXCEEDED`
- `AI_PROVIDER_NOT_CONFIGURED`, `AI_PROVIDER_UNAVAILABLE`, `AI_PROVIDER_RATE_LIMITED`, `AI_PROVIDER_REJECTED`
- `AI_PROMPT_TEMPLATE_NOT_FOUND`, `AI_PROMPT_INPUT_INVALID`, `AI_DATA_POLICY_VIOLATION`, `AI_OUTPUT_INVALID`
- `AI_GENERATION_BATCH_NOT_FOUND`, `AI_GENERATION_JOB_NOT_FOUND`, `AI_GENERATION_STATE_CONFLICT`
- `AI_SOURCE_ASSET_REQUIRED`, `AI_SOURCE_ASSET_INVALID`, `AI_MASK_REQUIRED`, `AI_PRODUCT_PIXELS_CHANGED`
- `AI_REVIEW_BLOCKED`, `AI_OUTPUT_NOT_FOUND`, `AI_OUTPUT_ALREADY_DECIDED`, `PRODUCT_ARCHIVED`

Errors retain the Repository `code`, `message`, `requestId`, `timestamp`, `path`, and optional `fieldErrors` contract. Stack traces, prompt/output bodies, provider responses, credentials, internal URLs, workflow graphs, and filesystem paths are not returned.

## Test strategy

### Migration/database

- Empty V1→V8/V9/V10/V11, populated Stage 02 upgrade, repeat migration, Hibernate validation, and canonical V1–V7 checksum protection.
- Direct JDBC constraints, FKs, enum/range/currency/count coherence, identity immutability, prompt-version/review/ledger append-only triggers, and delete rejection.
- Concurrent budget tests prove no daily overspend without assuming sequence/commit order.

### Backend/domain

- State machine valid/invalid transitions, optimistic locking, Product/Plan/Asset ownership/lifecycle, prompt snapshot determinism, data allowlist and injection delimiting.
- Per-job/batch/day budget accept/reject, concurrency, reservation/commit/release, actual-cost delta, rollback, idempotency, and config fail-closed profiles.
- Provider profile matrix, fixed-origin ComfyUI requests, workflow manifest injection, bounded polling/retry, response/path validation, sanitized errors, and no credential logging.
- Pixel-preservation exact match, changed pixel, alpha/mask, dimensions, decompression limits, media type/checksum, binary-store rollback/recovery, and generated Asset linkage.
- Trusted actor/request ID, Audit same-transaction rollback, no-op behavior, partial batch isolation, recovery, and approval blockers.

### Frontend/BFF

- Exact route/path/method/query/header allowlists, body limits, ETag/Location/request ID forwarding, fixed origin, no arbitrary provider/workflow/output URL, and no Cookie/Authorization/actor forwarding.
- Batch/job/output states, cost/budget, partial failure, provider errors, preservation evidence, approve/reject, archived and 409/412/428 recovery UI.
- Lint, typecheck, component tests, production build, dependency audit, and responsive/accessibility assertions.

### Compose/Playwright

- Preserve all eight Stage 02 scenarios.
- Deterministic text batch with success/failure/budget rejection and persisted reload.
- Background image generation with source pixels unchanged, six-folder/Asset linkage, and approval.
- Changed-pixel output is blocked; stale review loses; rejection reason persists; archived Product cannot generate.
- Database-backed Audit/review/ledger assertions use only ephemeral Compose PostgreSQL.
- CI receives no production credential and makes no live AI/Google/Ads call.

## Security, cost and operational limits

- Request bodies, prompt fields, output text, image bytes, dimensions/pixels, batch/job counts, retries, polling duration, concurrency, metadata, and error payloads are bounded.
- Provider origins and workflow assets are server configuration/repository resources. SSRF, arbitrary workflow execution, custom-node selection, filesystem traversal, and arbitrary output retrieval are rejected.
- No secret appears in Git, Docker build args/context, logs, Audit, database prompt snapshots, API responses, Playwright artifacts, or generated metadata.
- Generated text/image is untrusted content and is never interpreted as code, HTML, URL, workflow, instruction, or publish command.
- Production credentials, enabling a paid text provider, production ComfyUI deployment, GPU capacity, budget-limit changes, model-license approval, and real Product data runs are operational changes requiring human control outside normal development/CI.

## Stage 03 acceptance checklist

- [ ] Human decisions above remain unchanged and all Milestones receive exact-head Manager `APPROVE`.
- [ ] V8–V11 migrate empty/populated databases without modifying V1–V7; Hibernate validates and direct SQL protections pass.
- [ ] Provider-neutral text and ComfyUI image ports have deterministic Stub tests and production fail-closed profiles.
- [ ] Job/batch/day budget guards are atomic, concurrent-safe, observable, and not Browser mutable.
- [ ] Prompt versions/snapshots are reproducible and data minimization prevents secret/PII/order/payment transmission.
- [ ] Text and background-image generation complete with bounded cost/usage metadata and deterministic recovery.
- [ ] Protected Product pixels remain exact; changed/unknown preservation evidence blocks approval.
- [ ] Every output requires trusted human approval/rejection with ETag, Audit, and immutable history.
- [ ] No publication, Ads write, Decision Engine, video generation, Product redraw, live credential, or production deployment is introduced.
- [ ] Backend, Frontend, migration, Hibernate, Compose, smoke, Playwright, npm audit, Gitleaks, and actionlint pass locally and remotely.
- [ ] Final acceptance merges, post-merge main CI passes, and `milestone-3e-complete` plus `stage-03-complete` tags are created.

## Escalation boundaries

Stop with `ESCALATE_TO_HUMAN` before any of the following:

- Modifying V1–V7, destructive migration, production data/backfill, hard delete, or System of Record change.
- Selecting/enabling a paid text provider, accepting model/license terms, production ComfyUI/GPU deployment, production credential/access, or budget-limit change.
- Authentication/RBAC/Tenant/security-model change or sending customer/order/payment/secret data to a provider.
- Allowing Product redraw, video, automatic approval/publication, Ads/platform write, Decision Engine, or other Stage 04+ scope.
- Weakening pixel preservation, budget, Audit, provider-origin/workflow allowlist, BFF, or human-review gates.
