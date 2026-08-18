# Stage 04C — Ad Creative Publication Slice

## Gate status

- Status: Product settings approved; Independent Manager Review pending
- Branch: `codex/stage-04c-ad-creative-publication-specification`
- Base: `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`
- Stage 4B prerequisite: Passed; PR #62 merged at `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`; post-merge main CI Run `32055963526` passed
- Product specification: Repository-owner settings approved on 2026-08-18; Independent Manager Review pending
- Runtime implementation: Locked; not started
- Manager Decision: Pending
- Merge: Not started
- Stage 4D: Locked

This is a proposed implementation contract, not an approval. It authorizes no migration, runtime, REST, BFF, UI, adapter, provider, credential, network, production, paid-delivery, activation, or Stage 4D change until the specification Gate passes.

## Objective

Deliver a deterministic-FAKE, LOCAL/TEST-only Ad creative publication vertical slice. A user selects one currently valid Stage 03 approved IMAGE output, previews the immutable evidence mapping, explicitly confirms creation of a paused Ad under an eligible paused Ad Set, reads the normalized Ad, and explicitly requests pause/resume. Durable operations retain Stage 4A idempotency, attempt, retry, reconciliation, optimistic concurrency, Audit, and persistence-before-provider-call behavior.

The slice proves approved creative evidence publication. It does not deliver ads, spend money, accept arbitrary creative text, call Meta, or collect metrics.

## Repository-owner approved product decisions

1. The only creative mapping is server-owned `APPROVED_IMAGE_ASSET_V1`.
2. The mapping contains exactly the immutable Product/Asset/IMAGE output/review/checksum identities already present in the Stage 4A `CREATE_AD` payload. No Browser-supplied headline, primary text, description, CTA, destination URL, provider field, or arbitrary JSON is accepted.
3. An Ad is created only while its Campaign and Ad Set are both `PAUSED`, have durable external IDs from successful Stage 4B creation, belong to the fixed account, and are not archived.
4. Every Ad is created `PAUSED`. Ad resume is permitted only after the Campaign and Ad Set are both `ACTIVE`, the Ad has a durable external ID, and the complete current creative evidence chain is revalidated.
5. The repository-owner Stage 4B deterministic-FAKE exception is reused for Ad resume: the same fixed actor may confirm it only under the full LOCAL/TEST FAKE gate. Real paths still require operator/approver separation and new human security approval.
6. Evidence has creation-time snapshot semantics. A later upstream lifecycle/checksum change preserves the historical Ad row but blocks new Ad creation and blocks future submit/resume finalization until a new approved creative is selected; existing evidence is never silently rewritten.
7. Ad create binds the original Ad Set `If-Match` version into canonical idempotency intent. Exact replay uses the original intent before current mutable eligibility checks; a changed parent version under the same client request is an idempotency conflict.
8. No Ad-level budget, targeting, placement, schedule, text-copy, destination, delivery, or metrics configuration is added in 4C.
9. V14 is additive and narrowly updates the V12 request-validation/dispatch-integrity contract; it does not add a new business table or modify V1–V13 files.

The repository owner explicitly approved all nine decisions through the Codex task on 2026-08-18. This approval fixes the product settings for Independent Manager Review; it does not approve runtime implementation, credentials, network access, paid delivery, production, or merge.

## Inherited boundaries

1. Provider execution is deterministic `FAKE` only in `LOCAL` or `TEST`; credentials, real provider network calls, spend, billing, and production remain forbidden.
2. Stage 03 Product, Asset, generated output, review decision, preservation result, and checksum evidence remain authoritative.
3. Stage 4A operation, attempt, idempotency, reconciliation, optimistic-concurrency, evidence, Audit, and fake-adapter contracts remain authoritative.
4. Stage 4B fixed account, Campaign, Ad Set, actor, feature gates, safe web disclosure, and budget boundaries remain authoritative.
5. AI and the Decision Engine cannot call a platform write port directly.
6. Authentication, RBAC, Tenant authority, credentials, real-provider access, delivery, metrics, and production remain separately gated.
7. Flyway V1–V13 are immutable. Recovery from an approved V14 defect is forward-only through V15+.

## Included scope

- Preview and explicit confirmation of one paused Ad from one approved IMAGE evidence chain.
- Normalized Ad read, pause/resume preview, and explicit pause/resume confirmation.
- Safe operation read, eligible explicit retry, and ambiguous-outcome explicit reconciliation through the existing Stage 4A machinery.
- Additive V14 request/evidence/state integrity functions and constraint triggers only.
- Existing deterministic fake Ad creation/state port behavior, with exact contract tests.
- Same-origin fixed-route BFF and an additive Ad section on `/platforms/meta`.
- Exact ETag/If-Match, replay, stable errors, typed Audit, rollback, direct-SQL, migration, concurrency, UI, and Remote CI acceptance.

## Explicitly excluded

- Real Meta accounts, tokens, app secrets, credentials, network calls, provider payloads, paid delivery, billing, or production deployment.
- Arbitrary copy, CTA, URL, audience, placement, pixel, catalog, tracking, schedule, creative JSON, or provider-specific controls.
- Ad Set/Campaign creation or budget-policy changes beyond the approved Stage 4B routes.
- Automatic activation, retry, reconciliation, asset generation/deletion, Product redraw, review override, or evidence substitution.
- Delivery and metrics reads assigned to Stage 4D; optional real-provider proof assigned to Stage 4E.
- Authentication, RBAC, Tenant model, real operator/approver enforcement, or any security-model change.

## Eligibility and immutable creative mapping

Transaction A locks and validates the fixed account, parent Campaign, parent Ad Set, Product, Asset, generation output, and review decision. A new command is eligible only when:

- account is the exact Stage 4B LOCAL/TEST FAKE account and all Backend feature gates are enabled;
- Campaign and Ad Set belong to that account, have nonblank durable external IDs, and are both `PAUSED` for create;
- the supplied Ad Set `If-Match` equals its locked version;
- Product is `ACTIVE`;
- Asset belongs to the Product, is `ACTIVE`, is `IMAGE`, and has a lowercase SHA-256 checksum;
- generation output belongs to the Product, is `IMAGE`, references exactly that generated Asset, is `APPROVED`, has preservation `PASSED`, and its output checksum equals the Asset checksum;
- review decision belongs to that output and is immutable `APPROVED`;
- server-owned creative mapping is exactly `APPROVED_IMAGE_ASSET_V1`.

The Browser supplies only the four evidence UUIDs. The Backend derives the approved checksum and mapping key. Transaction A persists those values into `platform_ads` and the canonical operation payload. V12's deferred Ad snapshot trigger remains authoritative at commit.

For resume, Transaction A additionally locks the Ad and both parents and requires Campaign/Ad Set `ACTIVE`, Ad `PAUSED`, a nonblank Ad external ID, matching Ad `If-Match`, and the same current Product/Asset/output/review/checksum chain. Pause requires only account ownership, a current Ad ETag, Ad `ACTIVE`, and its existing external ID; divergence never prevents a safety-reducing pause.

## Canonical operation and replay

The V12 canonical `CREATE_AD` payload is extended additively by V14 with required `expectedParentVersion`:

```text
schemaVersion = 1
operationType = CREATE_AD
entityType = AD
entityUuid = platformAdUuid
platformAdUuid
platformAdSetUuid
expectedParentVersion
productUuid
assetUuid
generationOutputUuid
reviewDecisionUuid
approvedChecksumSha256
creativeMappingKey = APPROVED_IMAGE_ASSET_V1
desiredState = PAUSED
```

No optional or additional key is permitted. UUIDs are canonical lowercase, strings are NFC, checksum is lowercase hexadecimal, and the canonical JSON/hash/idempotency rules remain Stage 4A exact.

Pause/resume use the existing exact state-mutation payload with `entityType=AD`, path Ad UUID, expected Ad version, and route-derived target state. A request body cannot select its entity type or desired state.

The client generates one `clientRequestUuid` before preview and reuses it for confirmation. Replay resolution occurs before mutable current-state checks:

- exact account/actor/client UUID and complete canonical intent returns the existing operation and creates no row, Audit event, adapter call, or entity mutation;
- a changed parent version, evidence UUID, checksum, mapping, path entity, entity type, expected version, or target state returns `PLATFORM_IDEMPOTENCY_CONFLICT` with zero effects;
- current upstream divergence does not invalidate a previously committed exact replay, but blocks a new command identity.

## Additive V14 migration intent

V14 contains no new table and no backfill. It must:

1. `CREATE OR REPLACE` the V12 immutable request validator so `CREATE_AD` accepts either the legacy V12 exact key set or the new exact key set containing `expectedParentVersion`; it changes no other operation shape. This keeps a physically pre-V14 operation valid when its status later changes.
2. Add a `BEFORE INSERT` operation trigger that requires the new key set and a nonnegative numeric `expectedParentVersion` for every `CREATE_AD` operation physically inserted after V14. Legacy rows are not backfilled and cannot be attached retroactively to a new request identity.
3. Add `verify_platform_ad_dispatch_evidence()` as a narrowly named function used by deferrable constraint triggers on a `CREATE_AD` operation finalizing `SUCCEEDED` and an Ad desired-state transition to `ACTIVE`.
4. At final commit, lock and revalidate the current Product/Asset/output/review/checksum chain, account ownership, parent hierarchy, and operation payload. Create success must still match the immutable Ad snapshot; resume success must have both parents `ACTIVE` and the same evidence chain.
5. Preserve historical Ads when upstream rows later diverge. The trigger runs only for new create success or activation, not archival/history reads or pause.
6. Raise SQLSTATE `23514` for semantic incoherence and retain FK `23503` behavior for missing/mismatched identities.
7. Keep every V1–V13 migration byte-identical and preserve all populated rows during V13→V14 upgrade.

If evidence becomes invalid after adapter success but before Transaction C commits, the success cannot be projected safely. Transaction C rolls back, then a separate bounded recovery transaction records `UNKNOWN_OUTCOME` with `PLATFORM_RESPONSE_AMBIGUOUS`, application-owned normalized evidence, no external ID/entity mutation, and reconcile-only eligibility. A constraint failure is never converted to retryable.

## Transaction and provider contract

The three-transaction Stage 4A boundary is retained:

1. **Transaction A:** resolve exact replay; validate and lock account/parents/evidence; insert pristine paused Ad and pristine `CREATE_AD` operation; write exact Audit; commit.
2. **Outside transaction:** claim operation in the standard short transaction, then call `PlatformAdPort.submitAd` with the exact typed command. No database transaction is active during the adapter call.
3. **Transaction C:** validate the typed outcome, re-lock operation/Ad/current evidence, finalize attempt and operation, apply external ID/observation exactly once, write Audit, and commit atomically.

The existing deterministic fake ID remains `fake-ad-` plus the first 24 lowercase SHA-256 characters of the Stage 4A deterministic identity formula. Both retryable outcomes, both terminal outcomes, ambiguous timeout, success, found/not-found/still-unknown/terminal reconciliation, malformed/null/throwing adapter behavior, replay, and no-call invalid paths remain exact.

Pause/resume use `changeAdState`; mutation success returns no external ID and cannot alter the stored one. Retry remains due-only and reconciliation remains unknown-only. No automatic action exists.

## API contract

All routes are exposed only by the full Stage 4B Backend gate and use the same fixed account/actor resolver before resource lookup.

| Route | Request | Success |
| --- | --- | --- |
| `POST /api/platforms/meta/ad-sets/{adSetUuid}/ads/preview` | `If-Match` Ad Set ETag; exact create request | `200 AdPreview` |
| `POST /api/platforms/meta/ad-sets/{adSetUuid}/ads` | same request/header | `200` replay or `202 Confirmation`; operation `ETag` + `Location` |
| `GET /api/platforms/meta/ads/{adUuid}` | no query/body | `200 PlatformAdApiView` + Ad `ETag` |
| `POST /api/platforms/meta/ads/{adUuid}/state/preview` | Ad `If-Match`; exact target request | `200 StatePreview` |
| `POST /api/platforms/meta/ads/{adUuid}/pause` | Ad `If-Match`; target must be `PAUSED` | `200/202 Confirmation` |
| `POST /api/platforms/meta/ads/{adUuid}/resume` | Ad `If-Match`; target must be `ACTIVE` | `200/202 Confirmation` |

Create request fields, all required and non-null:

```text
clientRequestUuid
productUuid
assetUuid
generationOutputUuid
reviewDecisionUuid
```

`AdPreview` returns those UUIDs, the server-derived checksum fingerprint, mapping key, parent/ad desired states, evidence eligibility, FAKE warning, and a confirmation token-free summary. It returns no raw external ID, provider evidence, account identity, canonical payload, URL, token, cookie, or credential.

`PlatformAdApiView` contains only Ad UUID, parent Ad Set UUID, Product/Asset/output/review UUIDs, checksum fingerprint, mapping key, desired/observed state, external-ID fingerprint when present, timestamps, and version. Raw external ID is forbidden.

Operation reads/retry/reconcile reuse the exact safe Stage 4B operation DTO and headers. The BFF adds only the six fixed Backend paths above, retains the 16 KiB request, 1 MiB response, 10-second timeout, manual redirect refusal, bounded streaming, client-abort composition, content-type checks, header allowlists, and forbidden-field sanitizer.

Validation/error precedence is fixed account/gate, request shape, exact replay/conflict, parent/entity lookup, ETag, eligibility/evidence, policy, claim state. Existing public error contracts are reused; additive codes are:

| HTTP | Public code | Meaning |
| --- | --- | --- |
| 404 | `PLATFORM_AD_NOT_FOUND` | scoped Ad not found |
| 409 | `PLATFORM_AD_EVIDENCE_INVALID` | current evidence chain is not eligible |
| 409 | `PLATFORM_PARENT_STATE_INVALID` | Campaign/Ad Set state blocks create/resume |

All messages are fixed and reveal no account, external ID, provider trace/evidence, checksum, URL, or existence outside the scoped fixed account.

## Audit contract

Stage 4C reuses exact Stage 4A typed Audit records; it does not overload Stage 4B budget events. Required Transaction A subject order is:

- Ad create: `PLATFORM_AD`, `PLATFORM_OPERATION` — 2 events.
- Ad pause/resume: `PLATFORM_OPERATION` — 1 event; successful Transaction C adds attempt, operation result, and effective Ad state event using the Stage 4A matrix.

Ad creation Audit records UUID evidence references and checksum fingerprint only; no raw checksum, external ID, URL, canonical payload, provider evidence, or credential-bearing value is allowed. Exact actor/source/request correlation, actions, subjects, ordered changes, rollback at every append position, post-final-append failure, and replay/invalid/stale/provider-failure no-event behavior are executable acceptance requirements.

## UI behavior

The existing `/platforms/meta` page gains an Ad section only when the server-side Stage 4C flag is enabled. It:

- selects an existing normalized Ad Set and the four logical evidence UUIDs;
- previews evidence/mapping and requires a second explicit confirmation;
- renders the FAKE/no-spend/no-real-provider warning;
- reads the resulting normalized Ad and exposes pause/resume only when eligible;
- shows retry only when due and reconciliation only for unknown outcomes;
- clears pending confirmation and reloads normalized state after any 412;
- warns that upstream evidence divergence preserves history but blocks create/resume;
- performs no automatic submit, retry, reconcile, pause, or resume.

## Verification and acceptance

- Cold V1→V14, populated V13→V14, Hibernate validate, V1–V13 checksum assertions, deliberate V14 collision rollback, and forward-only recovery evidence.
- Direct-SQL negatives for old/new `CREATE_AD` payload shapes, forged parent version, invalid evidence/current divergence, false create success, false activation, external-ID substitution, immutable snapshot mutation, and complete rollback.
- Populated V13 fixture preserves accounts, Campaign/Ad Set/Ad, Stage 03 evidence, operations/attempts, metrics, budget ledger, Audit, and every legacy operation state byte-for-byte.
- Exact replay/conflict, parent version, scoped account, evidence eligibility, create/pause/resume, ETag 428/412, retry/reconciliation, stale finalizer, recovery, and concurrent one-winner tests.
- Deterministic fake contract and real MockMvc/transaction/persistence cases for every legal submit/reconcile outcome with exact safe DTOs.
- Typed Audit exact-content/cardinality/no-event/redaction/rollback cases.
- BFF exact-route, header, duplicate/unknown/null field, canonical UUID/NFC, 16 KiB, 1 MiB, timeout, abort, redirect, transport, content-type, and forbidden-field cases.
- Component and Playwright cases for preview/confirmation, normalized read, pause/resume, stale invalidation, due retry, unknown reconciliation, divergence, and zero automatic action.
- Full Backend regression; Frontend lint/typecheck/tests/build; `npm audit --omit=dev`; Compose config/cold health; Smoke; Playwright; actionlint; pinned Gitleaks history/worktree; `git diff --check`.
- Exact-head Push and Pull Request CI run `quality-and-compose` and `secret-scan` with no required-step skip.

## Stage gate

Stage 4C runtime implementation remains locked until all are true:

- [x] Repository owner explicitly approved the nine product decisions on 2026-08-18.
- [ ] Independent Manager Review records exactly `APPROVE` for a complete exact Head.
- [ ] Any approval-record commit passes full exact-head Push and Pull Request CI.
- [ ] The specification PR merges to `main` and post-merge main CI passes.

Stage 4D remains locked through Stage 4C implementation merge and post-merge verification.
