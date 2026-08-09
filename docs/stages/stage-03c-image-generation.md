# Stage 03 Milestone 3C — ComfyUI Background Image Generation

## Gate status

- Status: Approved for implementation
- Branch: `codex/stage-03-image-generation`
- Base Commit: `c5d1e18233c2a2b3760bb16118edaca32084d200`
- Prerequisite: Milestone 3B completed at `c1659cf0508e961860d95b13f52db72bfa4dc0c7`
- Prerequisite tag: `milestone-3b-complete`
- Implementation: Not started
- Migration: Not created
- Local Verification: Not started
- Remote CI: Not started
- Manager Review: Not started
- Manager Decision: Pending
- Merge: Not started
- Milestone 3D: Not started

## Scope

- Additive V10 image-output persistence and TEXT/IMAGE coherence on the existing output aggregate.
- One-image background-composite batch/job flow using an active Product source IMAGE Asset and optional active mask IMAGE Asset.
- Provider-neutral `ImageGenerationProvider` orchestration and `AssetBinaryStore` binary boundary.
- Repository-owned ComfyUI API workflow plus manifest; fixed-origin adapter with bounded `/prompt`, `/history/{prompt_id}`, and `/view` requests.
- Deterministic local/test image provider and binary store; default/production remain fail closed.
- Exact protected-region RGBA verification before output and generated Asset metadata commit.
- Image create/execute/query REST, same-origin BFF, Creative Factory UI, Audit, cost settlement, recovery reconciliation, and tests.

## Explicit exclusions

- No Product redraw mode, arbitrary workflow JSON, custom node selection, Browser provider URL, Browser output path, or arbitrary download URL.
- No live production ComfyUI/GPU deployment, production credential, paid provider, or budget change.
- No approve/reject mutation, V11 decision history, publication, Ads write, Decision Engine, video, or Stage 04 behavior.
- No binary hard delete or automatic external-data compensation.

## V10 migration contract

V10 is forward-only and does not edit V1–V9. Existing TEXT rows remain valid.

The migration expands `ai_generation_outputs` for coherent IMAGE rows:

- `text_content` becomes nullable; a replacement constraint requires nonblank text only for `TEXT`.
- `generation_type` allows `TEXT` or `IMAGE`.
- optional `source_asset_uuid`, `mask_asset_uuid`, and `generated_asset_uuid` reference `assets` with `ON DELETE RESTRICT`.
- optional `generation_mode VARCHAR(32)` allows only `BACKGROUND_COMPOSITE`.
- optional `workflow_key VARCHAR(128)` and `workflow_version VARCHAR(64)`.
- optional `image_width`, `image_height INTEGER` constrained to `1..4096`; total decoded pixels are application-bounded to 16,777,216.
- optional `media_type VARCHAR(64)` allows `image/png` or `image/jpeg`.
- optional `size_bytes BIGINT` constrained to `1..16777216`.
- optional `source_checksum_sha256`, `mask_checksum_sha256`, `output_checksum_sha256`, and `protected_pixels_sha256 CHAR(64)` use lower-case SHA-256 format.
- optional `preservation_algorithm VARCHAR(64)` allows `RGBA_MASK_EXACT_V1`.
- optional `preservation_status VARCHAR(16)` allows `PASSED` or `BLOCKED`.
- optional bounded `preservation_details JSONB` is an object without bytes, paths, URLs, provider bodies, credentials, or source content.
- `generated_asset_uuid` is unique when present.

Coherence constraints require:

- TEXT: nonblank `text_content`; every image-specific column is null.
- IMAGE: `text_content` is null; source/generated Assets, mode, workflow identity, dimensions, media type, size, source/output/protected checksums, algorithm, and status are present.
- `mask_asset_uuid` and mask checksum are both null or both present.
- IMAGE output relationship matches the immutable Job/Batch/Product association already protected by V9.

V9's output-protection function is replaced only through V10 so all new immutable image evidence and Asset relationships are protected. Review status, updated time, and version remain the only fields reserved for V11 review transitions. Direct DELETE remains rejected.

## Source and binary contract

- Source and optional mask are active IMAGE Assets owned by the Product. Source requires media type, size, checksum, and an opaque provider file identifier understood by `AssetBinaryStore`.
- Source bytes must decode as PNG or JPEG. Without an explicit mask, the source must be PNG with a nonempty alpha-protected region.
- Explicit mask must decode at identical dimensions. Its nonzero alpha/luminance selects the protected Product region.
- Maximum binary size is 16 MiB; maximum dimension is 4096; maximum decoded pixels is 16,777,216. Metadata is validated before allocation and again after decode.
- `AssetBinaryStore` exposes only authorized `read(asset)` and idempotent `writeGenerated(jobUuid, productUuid, bytes, mediaType)` operations. It exposes no arbitrary path, URL, overwrite, rename, delete, or listing API.
- Local/test storage is deterministic and process-local for tests. The production/default implementation denies use until a separately reviewed trusted binary adapter is configured.
- Generated Asset metadata is created with `assetType=IMAGE`, purpose `AI_BACKGROUND_COMPOSITE`, bounded provider metadata, checksum, media type, size, and the same Product/Creative Plan relationship.

## Pixel-preservation contract

1. Decode source, mask/alpha, and provider output with bounded image readers.
2. Require identical width and height.
3. Normalize decoded pixels to RGBA.
4. For every protected pixel, require output RGBA to equal source RGBA exactly.
5. Hash the ordered protected source RGBA bytes using SHA-256.
6. Persist source/mask/output checksums, protected hash, algorithm version, and `PASSED` or `BLOCKED` evidence.

Changed pixels, missing/empty mask, invalid alpha, malformed image, decompression limits, dimension mismatch, unsupported media type, checksum mismatch, or absent evidence is `AI_PRODUCT_PIXELS_CHANGED` or `AI_SOURCE_ASSET_INVALID`. A blocked output remains `PENDING_REVIEW` and cannot become approvable in 3D.

## Workflow and ComfyUI adapter contract

- Browser selects only logical workflow key `background-composite-v1` and image profile `STANDARD_IMAGE`.
- Repository resources contain API-format workflow JSON and a manifest with exact node IDs and mutable input names. All other nodes/inputs are immutable.
- Allowed injected values are opaque uploaded source/mask handles, bounded prompt text, width, height, output prefix derived from Job UUID, and deterministic seed where required.
- `COMFYUI_BASE_URL` is parsed once from server configuration, must be an absolute HTTP(S) origin without user info/query/fragment/path traversal, and is never returned to Browser or logs.
- Prompt IDs, filenames, subfolders, and provider output types use strict length/character allowlists. `/view` is called only with identifiers returned by the validated `/history/{prompt_id}` response.
- Poll interval, attempts, response bytes, redirects, and total timeout are bounded. Redirects to another origin are rejected.
- Local/test CI never calls ComfyUI. Explicit adapter tests use a mock HTTP server only. Default and every production profile combination select the deny provider.

## State, transaction and recovery contract

1. Create loads Product/Plan/source/mask/template, validates ownership/lifecycle, stores bounded immutable snapshots, reserves the server-derived ceiling, and commits Batch/Job/ledger/Audit atomically.
2. Execute requires current Job `If-Match`; a short transaction moves the Job to running using its UUID as provider idempotency key.
3. Source/mask read, provider submission/poll/download, image validation, and generated binary write occur outside database transactions.
4. Completion transaction creates IMAGE output and generated Asset metadata, settles budget, updates Job/Batch, and writes Audit atomically.
5. The binary write key is deterministic from Job UUID. If database commit fails after external write, retry reads/reuses the same stored checksum and never creates a second binary.
6. Provider or validation failure releases unbilled reservation and persists bounded failure state/Audit. Billable cost is retained truthfully.
7. Duplicate terminal execution creates no provider call, binary, Asset, output, ledger record, version increment, or Audit.

## REST and BFF contract

Existing 3B routes remain backward compatible. Image creation uses:

- `POST /api/products/{productUuid}/ai-generation-batches` with `generationType: IMAGE`, `creativePlanUuid`, logical `templateKey`, `workflowKey`, `modelProfile`, `sourceAssetUuid`, optional `maskAssetUuid`, and count fixed to one.
- Existing Batch/Job/Output GET and Job execute routes expose bounded image metadata, source/mask/generated Asset UUIDs, preservation evidence, and `PENDING_REVIEW`.

Requests cannot include raw bytes, URLs, provider origin, workflow JSON/node IDs, handles, output paths, redraw mode, seed override, cost/currency, credential, actor, or publish target. BFF path/header/body protections from 3B remain in force.

## Frontend contract

- Creative Factory adds an Image mode without creating a Dashboard.
- User selects an active Creative Plan, eligible source Asset, optional eligible mask Asset, and the single server-advertised workflow/profile.
- UI displays source/generated image metadata, cost, state, preservation result/reason, loading/empty/error/archived/provider/budget/stale states, and `Pending review`.
- No raw provider URL, filesystem handle, workflow graph, Approve, Publish, redraw, or video control is shown.

## Verification and acceptance

- Empty V1→V10, populated V9→V10, repeat/no-pending, Hibernate validation, V1–V9 canonical checksums, and direct JDBC constraints/triggers pass.
- Existing TEXT rows and text generation behavior remain unchanged.
- Alpha-source and explicit-mask success preserve every protected pixel and persist exact evidence, generated binary, IMAGE Asset, output, cost/state, and Audit.
- Changed pixel, empty/mismatched mask, malformed/decompression-bomb/oversized binary, bad media type/checksum/dimensions, wrong ownership, archived resources, duplicate execution, rollback, and reconciliation cases pass.
- ComfyUI mock tests prove fixed origin, exact routes, manifest-only mutation, strict returned identifiers, bounded polling/body/timeout, no redirect/SSRF/path traversal, and sanitized failures.
- Provider/binary-store profile matrices prove deterministic local/test and production fail closed, including mixed production profiles.
- Frontend BFF/component tests, lint, typecheck, tests, production build, npm audit, Compose, existing Playwright, Gitleaks, actionlint, Remote CI, exact-head Manager Review, merge, and post-merge main CI pass.
- No production credential/call, publication, approval mutation, Product redraw, video, Ads, Decision Engine, or Stage 04 code exists.
