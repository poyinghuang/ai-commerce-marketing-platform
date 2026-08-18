# Stage 04C — Ad Creative Publication Slice

## Gate status

- Status: Independent Manager Review `REQUEST_CHANGES`
- Branch: `codex/stage-04c-ad-creative-publication-specification`
- Base: `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`
- Stage 4B prerequisite: Passed; PR #62 merged at `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`; post-merge main CI Run `32055963526` passed
- Product specification: Repository-owner settings approved on 2026-08-18; Independent Manager Re-Review requires further corrections
- Runtime implementation: Locked; not started
- Manager Decision: `REQUEST_CHANGES` for re-reviewed Head `1b7858b2927366df55286683e9d335aba47e6855`
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

## Durable Stage 4C provenance and legacy compatibility

Stage 4C does not add a marker column or business table. Ownership is derived from durable V12 operation payloads and the immutable Ad creation chain:

```text
isStage4COwned(operation) =
  operation.entityType == AD
  AND operation.platformAccountUuid == fixedAccountUuid
  AND (
    operation.operationType == CREATE_AD
      AND normalizedRequest has the exact new CREATE_AD key set
      AND normalizedRequest.expectedParentVersion is present
    OR operation.operationType in {PAUSE, RESUME}
      AND normalizedRequest.entityUuid == operation.entityUuid
      AND EXISTS exactly one SUCCEEDED, same-account, new-shape CREATE_AD operation
          whose entityUuid == operation.entityUuid
          and whose platformAdUuid == operation.entityUuid
  )
```

V14 exposes this as the read-only SQL function `is_stage4c_owned_operation(operation_uuid)` and the application uses the same predicate. It returns false for every non-Ad operation, malformed/mismatched payload, pre-V14 `CREATE_AD` payload without `expectedParentVersion`, and Ad state operation without the qualifying successful new-shape create chain. No V13 batch is created for Stage 4C.

The fixed-account resolver always runs before operation lookup. Operation GET remains account-scoped and may return the safe normalized DTO for a legacy row. Retry and reconciliation require either the existing exact V13 batch ownership or `isStage4COwned`; an unbatched pre-V14 operation returns `409 PLATFORM_LEGACY_OPERATION_INERT` with zero operation/attempt/entity/Audit mutation and zero adapter call. This is evaluated before operation status/due/cap checks, so a legacy row cannot reveal its state through error precedence.

Legacy `CREATE_AD` rows are distinguished only by the physical absence of `expectedParentVersion`; the V14 `BEFORE INSERT` trigger makes that shape impossible for a new row. After upgrade:

- legacy `CREATED`, `FAILED_RETRYABLE`, and `UNKNOWN_OUTCOME` rows are readable but cannot be newly submitted, retried, or reconciled through Stage 4C;
- a legacy row already in `SUBMITTING` with its matching `SUBMIT/STARTED` attempt may finalize any normalized direct outcome; success uses the current evidence/snapshot/parent predicates below but has no parent-version predicate;
- a legacy row already in `RECONCILING` with its matching `RECONCILE/STARTED` attempt may finalize any normalized reconciliation outcome under the same rule;
- existing stale-claim recovery may convert a legacy `SUBMITTING`/`RECONCILING` row to `UNKNOWN_OUTCOME` without an adapter call;
- terminal and successful legacy rows remain immutable.

Populated V13→V14 acceptance creates coherent legacy rows in all seven operation statuses. It byte-compares every operation/attempt/entity/evidence/Audit row after migration, proves every row is GET-readable and HTTP-inert, then separately proves already-claimed `SUBMITTING` and `RECONCILING` rows can finalize or recover with the exact legacy predicates and full atomic rollback.

## Additive V14 migration intent

V14 contains no new table and no backfill. It must:

1. `CREATE OR REPLACE` the V12 immutable request validator so `CREATE_AD` accepts either the legacy V12 exact key set or the new exact key set containing `expectedParentVersion`; it changes no other operation shape. This keeps a physically pre-V14 operation valid when its status later changes.
2. Add a `BEFORE INSERT` operation trigger that requires the new key set and an exact JSON integer `expectedParentVersion` in `0..9223372036854775807` for every `CREATE_AD` operation physically inserted after V14. Legacy rows are not backfilled and cannot be attached retroactively to a new request identity.
3. Add `is_stage4c_owned_operation(operation_uuid)` with the closed predicate above. It is the only non-batch qualification accepted by Stage 4C GET/retry/reconcile orchestration.
4. Add `verify_platform_ad_submit_claim()` on the operation edge `CREATED|FAILED_RETRYABLE -> SUBMITTING`. For new-shape `CREATE_AD` and Stage 4C-owned Ad `RESUME`, it locks and validates the fixed account, parent hierarchy/state/version, Ad, and current evidence before the operation update and matching `SUBMIT/STARTED` attempt can commit. Ad `PAUSE` is explicitly exempt from evidence/parent-state validation but still requires fixed-account ownership, durable Ad external ID, exact expected Ad version, and `ACTIVE -> PAUSED` payload validity. A failed guard raises the named constraint `ct_platform_ad_submit_claim_evidence`, rolls back the entire claim transaction, and creates no attempt/Audit/call.
5. Add `verify_platform_ad_dispatch_evidence()` under the named deferrable constraint `ct_platform_ad_dispatch_result`. It covers both direct and reconciled success: `SUBMITTING -> SUCCEEDED` with matching finalized `SUBMIT/SUCCEEDED` attempt, or `RECONCILING -> SUCCEEDED` with matching finalized `RECONCILE/SUCCEEDED` attempt. It also covers the correlated Ad entity result update in the same transaction.
6. At final commit, lock and revalidate the current Product/Asset/output/review/checksum chain, account ownership, parent hierarchy, operation payload, operation/attempt result, and entity mutation. New create success requires the locked Ad Set version to equal payload `expectedParentVersion`; legacy create success omits only this comparison. Create requires both parent **desired states** `PAUSED`; resume requires both parent **desired states** `ACTIVE`. Parent observed state is informational, may be absent or different, and never changes eligibility. Parent external IDs must remain nonblank. Create success applies exactly Ad external ID/fingerprint plus optional observation with one Ad version increment; resume/pause success applies exactly the payload desired transition plus optional observation with one version increment and never changes external ID.
5. Preserve historical Ads when upstream rows later diverge. The trigger runs only for new create success or activation, not archival/history reads or pause.
6. Raise SQLSTATE `23514` for semantic incoherence and retain FK `23503` behavior for missing/mismatched identities.
7. Keep every V1–V13 migration byte-identical and preserve all populated rows during V13→V14 upgrade.

The final-state predicates are closed:

| Result path | Operation edge and matching attempt | Exact entity edge | Required locked predicates |
| --- | --- | --- | --- |
| New/legacy CREATE_AD direct success | `SUBMITTING -> SUCCEEDED`; current-number `SUBMIT STARTED -> SUCCEEDED`; byte-equal code/trace/evidence | Ad external ID `NULL -> deterministic result`, fingerprint absent→exact SHA-256, optional observation, version `0 -> 1`; desired remains `PAUSED` | same account/hierarchy/snapshot/current evidence; parent desired states `PAUSED`; both parent external IDs nonblank; new shape also requires Ad Set version=`expectedParentVersion` |
| New/legacy CREATE_AD reconciliation found | `RECONCILING -> SUCCEEDED`; current-number `RECONCILE STARTED -> SUCCEEDED` | same create entity edge using FOUND ID/fingerprint | same create predicates; legacy omits only parent-version equality |
| AD RESUME direct success | `SUBMITTING -> SUCCEEDED`; matching `SUBMIT STARTED -> SUCCEEDED` | Ad desired `PAUSED -> ACTIVE`, optional observation, version `expectedEntityVersion -> +1`; external ID unchanged | Stage 4C ownership; same account/hierarchy/current evidence; parent desired states `ACTIVE`; all three external IDs nonblank |
| AD RESUME reconciliation found | `RECONCILING -> SUCCEEDED`; matching `RECONCILE STARTED -> SUCCEEDED` | same resume edge reconstructed from durable payload; FOUND returns no mutation ID/fingerprint | same resume predicates and locked expected Ad version |
| AD PAUSE direct/found success | matching direct or reconcile operation/attempt success | Ad desired `ACTIVE -> PAUSED`, optional observation, one version increment; external ID unchanged | Stage 4C ownership, same account and durable Ad external ID; parent/evidence predicates deliberately absent |

Any different operation/attempt/entity edge, missing/mismatched correlated operation, ID substitution, version jump, external-ID change on mutation, or result/evidence disagreement fails `ct_platform_ad_dispatch_result` at commit. A new-shape create whose parent version changed reports `412 PLATFORM_ENTITY_STALE` before a call when detected by the claim guard; parent desired-state and evidence failures report their exact public errors. A rejected initial claim leaves the already committed pristine Ad and `CREATED` operation unchanged and emits no claim Audit; exact replay returns that inert view without dispatch, and a corrected creative requires a new client request identity. A rejected retry leaves the existing `FAILED_RETRYABLE` operation/due time unchanged so it may be retried only after the current chain is corrected.

All application and trigger paths use one lock order. Transaction A, before an operation exists, locks `account -> campaign -> ad_set -> ad when present -> product -> asset -> generation_output -> review_decision`, then inserts the Ad/operation. Claim, finalization, reconciliation, and recovery lock `operation -> account -> campaign -> ad_set -> ad -> product -> asset -> generation_output -> review_decision -> matching attempt`. A row identity read before locking is rechecked after every lock. PostgreSQL triggers follow the same order after the operation row is already locked by its update. Barrier tests race parent version/state, Product/Asset lifecycle/checksum, output status/preservation/checksum, and review decision against direct and reconciled finalization and prove one deterministic commit or full rollback without deadlock.

If a validated provider success is obtained but Transaction C cannot commit, the coordinator never recalls the adapter. Immediate bounded ambiguity recovery is attempted exactly once only for the named `ct_platform_ad_dispatch_result` violation or SQLSTATE `40001`/`40P01` raised while committing that validated success/found result. An unrelated `23514`, FK violation, programming defect, or malformed outcome is not relabelled by this handler: it propagates, leaves the durable STARTED claim unchanged, performs no adapter recall, and the existing PT5M stale-claim recovery is the only fallback. If the immediate recovery transaction itself fails, the original and recovery failures are logged in bounded/redacted form, no second recovery or adapter call occurs, and stale-claim recovery remains the restart-safe fallback.

The immediate recovery transaction is exact:

- direct path CAS-locks `SUBMITTING` plus the matching current-number `SUBMIT/STARTED` attempt; reconcile path CAS-locks `RECONCILING` plus the matching current-number `RECONCILE/STARTED` attempt;
- it finalizes that attempt to `UNKNOWN_OUTCOME`, sets `completed_at=statement_timestamp()`, increments attempt version `0 -> 1`, and stores `PLATFORM_RESPONSE_AMBIGUOUS` with application-owned evidence `{schemaVersion:1,providerKey:FAKE,attemptKind:SUBMIT|RECONCILE,resultKind:UNKNOWN_OUTCOME}` and no optional evidence fields;
- it retains only a previously validated safe provider trace on both rows when one exists, discards external ID/fingerprint/observation and the provider success/found evidence, and stores byte-equivalent code/trace/evidence on attempt and operation;
- it changes the operation from `SUBMITTING|RECONCILING -> UNKNOWN_OUTCOME`, increments operation version once, preserves identity, payload, idempotency, counters and `claimed_at`, clears `next_attempt_at`, leaves operation `completed_at` null, and makes it reconcile-only within the existing cap;
- it performs no Ad or parent update and emits exactly two Stage 4A events in order: `ATTEMPT_FINALIZED`, then `OPERATION_TRANSITIONED`. Audit failure rolls back both rows and all Audit appends.

Constraint, serialization/deadlock, unrelated-integrity, recovery-failure, concurrent-recovery, and restart tests assert one provider invocation, exact CAS winner, complete graph rollback, and no external-ID/entity projection.

## Transaction and provider contract

The three-transaction Stage 4A boundary is retained:

1. **Transaction A:** resolve exact replay; validate and lock account/parents/evidence; insert pristine paused Ad and pristine `CREATE_AD` operation; write exact Audit; commit.
2. **Claim transaction, then outside transaction:** the Stage 4C claim guard performs the exact current-chain validation and atomically writes the operation transition, STARTED attempt, and two claim Audit events. Only after it commits may `PlatformAdPort.submitAd` or `changeAdState` run. No database transaction is active during the adapter call. Retry uses this same guarded claim; it cannot call the generic unguarded claim path.
3. **Transaction C:** validate the typed outcome, re-lock operation/Ad/current evidence, finalize attempt and operation, apply external ID/observation exactly once, write Audit, and commit atomically.

The existing deterministic fake ID remains `fake-ad-` plus the first 24 lowercase SHA-256 characters of the Stage 4A deterministic identity formula. Both retryable outcomes, both terminal outcomes, ambiguous timeout, success, found/not-found/still-unknown/terminal reconciliation, malformed/null/throwing adapter behavior, replay, and no-call invalid paths remain exact.

Pause/resume use `changeAdState`; mutation success returns no external ID and cannot alter the stored one. Retry remains due-only and reconciliation remains unknown-only. Reconciliation claim does not call a write provider and therefore does not repeat submit-claim evidence validation; a FOUND finalization still runs the full dispatch-result constraint. No automatic action exists.

## Canonicalizer and provider-command compatibility

`PlatformOperationInputCanonicalizer` has two explicit CREATE_AD entry points while retaining one canonical byte algorithm:

- `canonicalizePersisted(String)` accepts exactly the legacy or new CREATE_AD shape so physically existing rows can be read, hashed, finalized, and migration-tested; all other operation types retain their current exact shapes byte-for-byte.
- `canonicalizeNewCreateAd(String)` accepts only the new shape and is mandatory for every Stage 4C preview/confirm/create path. It rejects a missing/additional `expectedParentVersion`, legacy shape, exponent/fraction/string version, boolean/null/array, duplicate/unknown key, noncanonical UUID/checksum, or mapping other than `APPROVED_IMAGE_ASSET_V1` before persistence.

`expectedParentVersion` is a JSON integer and canonicalizes as unsigned plain base-10 digits with no sign, exponent, fraction, leading zero except `0`, or scale. Replay comparison includes its canonical numeric value and the complete canonical JSON bytes/hash. The database dual validator and Java dual canonicalizer must accept/reject the same fixtures. No other operation payload changes.

Provider-command reconstruction requires every CREATE_AD field. `creativeMappingKey` has no fallback: missing or non-`APPROVED_IMAGE_ASSET_V1` throws `PLATFORM_CONTRACT_INVALID` before claim/adapter invocation. `DEFAULT_IMAGE_V1` is removed from the CREATE_AD dispatch path and is forbidden in fixtures, persisted payloads inserted after V14, logs, API DTOs, and adapter commands. Regression tests prove legacy bytes remain readable, new bytes are mandatory for new commands, all non-CREATE_AD canonical bytes/hashes are unchanged, and every rejected/defaulted path has zero attempt/call/Audit.

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

Exact Backend records and JSON declaration order are:

```java
record AdCreateRequest(
    UUID clientRequestUuid,
    UUID productUuid,
    UUID assetUuid,
    UUID generationOutputUuid,
    UUID reviewDecisionUuid) {}

record AdStateRequest(
    UUID clientRequestUuid,
    PlatformDesiredState targetDesiredState) {}

enum Stage4CWarning {
    DETERMINISTIC_FAKE_ONLY,
    NO_REAL_PROVIDER_OR_SPEND,
    EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME
}

record AdPreview(
    UUID clientRequestUuid,
    UUID platformAdSetUuid,
    long expectedParentVersion,
    UUID productUuid,
    UUID assetUuid,
    UUID generationOutputUuid,
    UUID reviewDecisionUuid,
    String approvedChecksumFingerprint,
    String creativeMappingKey,
    PlatformDesiredState parentCampaignDesiredState,
    PlatformDesiredState parentAdSetDesiredState,
    PlatformDesiredState newAdDesiredState,
    boolean evidenceEligible,
    List<Stage4CWarning> warnings,
    boolean confirmable) {}

record StatePreview(
    UUID clientRequestUuid,
    PlatformEntityType entityType,
    UUID entityUuid,
    long expectedEntityVersion,
    PlatformDesiredState previousDesiredState,
    PlatformDesiredState targetDesiredState,
    PlatformDesiredState parentCampaignDesiredState,
    PlatformDesiredState parentAdSetDesiredState,
    boolean evidenceEligible,
    List<Stage4CWarning> warnings,
    boolean confirmable) {}

record PlatformAdApiView(
    UUID platformAdUuid,
    UUID platformAdSetUuid,
    UUID productUuid,
    UUID assetUuid,
    UUID generationOutputUuid,
    UUID reviewDecisionUuid,
    String approvedChecksumFingerprint,
    String creativeMappingKey,
    PlatformDesiredState desiredState,
    Optional<PlatformObservedState> observedState,
    Optional<String> externalIdFingerprint,
    Instant createdAt,
    Instant updatedAt,
    long version) {}

record PlatformOperationApiView(
    UUID operationUuid,
    PlatformOperationType operationType,
    PlatformEntityType entityType,
    UUID entityUuid,
    PlatformOperationStatus status,
    int attemptCount,
    int reconciliationCount,
    int maxAttempts,
    Optional<PlatformStableErrorCode> normalizedErrorCode,
    Optional<Instant> nextAttemptAt,
    Optional<Instant> completedAt,
    Instant createdAt,
    Instant updatedAt,
    long version) {}

record Confirmation(PlatformOperationApiView operation, boolean replay) {}
```

All reference fields and Optional containers are non-null. UUID JSON is canonical lowercase. `Optional.empty()` fields are omitted through `NON_ABSENT`; required fields are never null. Lists are immutable. `entityType` is always `AD`, `newAdDesiredState` is always `PAUSED`, `creativeMappingKey` is always `APPROVED_IMAGE_ASSET_V1`, and warnings are exactly the three enum values above in declaration order. The checksum fingerprint is lowercase SHA-256 of UTF-8 `"stage4c-approved-checksum-v1\n" + approvedChecksumSha256`; raw checksum is never returned. External-ID fingerprint retains the Stage 4A lowercase SHA-256 formula and raw ID is never returned.

Eligibility uses parent **desired state only**: create requires Campaign and Ad Set `PAUSED`; resume requires both `ACTIVE`; pause ignores parent state. Parent/Ad observed state is provider observation only, may be absent or differ, is returned only for the Ad read, and cannot authorize or block a command. Preview echoes the exact locked ETag version. `evidenceEligible` and `confirmable` are both true on `200`; an ineligible preview returns an error rather than a `false` preview. State preview sets `evidenceEligible=true` for pause and to the exact current-chain result for resume.

`AdPreview`/`StatePreview` contain no candidate Ad UUID, confirmation token, account identity, raw checksum/external ID, provider evidence/trace, canonical payload/hash, URL, token, cookie, credential, or arbitrary string. `PlatformAdApiView` and `PlatformOperationApiView` are the complete safe allowlists; unknown response keys fail Backend snapshot and BFF sanitization tests.

Confirmation returns `202` for a newly created operation and `200` only for exact replay. It always returns the exact `PlatformOperationApiView`, strong operation `ETag`, and `Location: /api/platform-operations/{operationUuid}`. Ad GET returns strong Ad ETag. Preview has neither ETag nor Location. Operation GET/retry/reconcile reuse the exact safe DTO; retry/reconcile require no query and an empty body, require operation `If-Match`, and return the same status/header/body rules. All routes reject query parameters.

The BFF adds only the six fixed Backend paths above plus the inherited fixed operation GET/retry/reconcile paths after the Backend has account-scoped and provenance-qualified them. TypeScript types mirror the records exactly; safe-key allowlists include `entityType=AD` and reject any extra/missing/wrong-type key. It retains the 16 KiB request, 1 MiB response, 10-second timeout, manual redirect refusal, bounded streaming, client-abort composition, JSON content-type checks, request/response header allowlists, and forbidden-field sanitizer. Browser input can never choose a Backend origin or operation/entity type.

Validation precedence is exact: feature/profile gate and fixed-account configuration; request content-type/size/JSON/duplicate-or-unknown/null field/path/query shape; canonical lowercase path/body UUID and NFC; exact replay or idempotency conflict; account-scoped parent/entity/operation lookup; `If-Match` presence/parse/value; parent desired-state eligibility; current evidence eligibility; Stage 4C provenance; operation state; retry due/caps or reconciliation cap. The first failure wins and all later layers have zero side effects.

Create field-error order is `clientRequestUuid`, `productUuid`, `assetUuid`, `generationOutputUuid`, `reviewDecisionUuid`, then `If-Match`; state order is `clientRequestUuid`, `targetDesiredState`, then `If-Match`. Path failures use `path`; body syntax uses `body`; query uses `query`. Fixed field messages are `Invalid value`, `Invalid If-Match`, `Invalid request body`, `Invalid path`, and `Query parameters are not allowed` respectively. Existing public contracts are reused; the complete Stage 4C additions/mappings are:

| HTTP | Public code | Fixed message | Internal source / route |
| --- | --- | --- | --- |
| 400 | `PLATFORM_REQUEST_INVALID` | `Platform request is invalid` | strict route boundary |
| 400 | `PLATFORM_CONTRACT_INVALID` | `Platform contract is invalid` | canonicalizer/outcome contract rejection before a valid provider result |
| 404 | `PLATFORM_AD_NOT_FOUND` | `Platform Ad was not found` | account-scoped Ad miss on Ad routes |
| 404 | `PLATFORM_RESOURCE_NOT_FOUND` | `Platform resource was not found` | `PLATFORM_OPERATION_NOT_FOUND` on inherited operation routes |
| 409 | `PLATFORM_AD_EVIDENCE_INVALID` | `The approved Ad evidence is no longer eligible` | `PLATFORM_EVIDENCE_INVALID` on create/resume preview, confirm, initial claim, or retry claim |
| 409 | `PLATFORM_PARENT_STATE_INVALID` | `The parent platform state does not allow this action` | additive internal `PLATFORM_PARENT_STATE_INVALID` on create/resume preview, confirm, initial claim, or retry claim |
| 409 | `PLATFORM_LEGACY_OPERATION_INERT` | `The legacy operation is read-only` | unqualified operation retry/reconcile, before state disclosure |
| 409 | `PLATFORM_IDEMPOTENCY_CONFLICT` | `The request conflicts with an existing operation` | inherited exact identity conflict |
| 409 | `PLATFORM_INVALID_OPERATION_STATE` | `The operation is not eligible for this action` | inherited operation state failure |
| 409 | `PLATFORM_RETRY_NOT_DUE` | `The operation is not yet eligible for retry` | inherited due guard |
| 409 | `PLATFORM_MAX_ATTEMPTS_EXCEEDED` | `The operation has no retry attempts remaining` | inherited cap |
| 409 | `PLATFORM_MAX_RECONCILIATIONS_EXCEEDED` | `The operation has no reconciliation attempts remaining` | inherited cap |
| 409 | `PLATFORM_POLICY_REJECTED` | `Platform policy rejected the request` | inherited fixed-account/local policy guard not represented by a more specific Stage 4C code |
| 412 | `PLATFORM_ENTITY_STALE` | `The platform entity changed; reload and preview again` | Ad/parent ETag/version failure |
| 412 | `PLATFORM_OPERATION_STALE` | `The platform operation changed; reload and retry` | operation ETag/version failure |
| 428 | `PLATFORM_IF_MATCH_REQUIRED` | `If-Match is required` | missing required header |
| 429 | `PLATFORM_PROVIDER_RETRYABLE` | `The fake provider result may be retried later` | persisted `PLATFORM_RATE_LIMITED` or `PLATFORM_TEMPORARILY_UNAVAILABLE` |
| 503 | `PLATFORM_ACCOUNT_CONFIGURATION_INVALID` | `The local platform account is unavailable` | account inactive/environment/provider mismatch |
| 503 | `PLATFORM_ADAPTER_UNAVAILABLE` | `The fake platform adapter is unavailable` | missing fake port |

Normalized provider results return the safe operation body, never `ApiError`, with this exact response matrix:

| Invocation result | HTTP | Operation status / normalized code |
| --- | --- | --- |
| New submit success or reconciliation FOUND | `202` | `SUCCEEDED`; code omitted |
| Submit `PLATFORM_RATE_LIMITED` | `429` | `FAILED_RETRYABLE / PLATFORM_RATE_LIMITED` |
| Submit `PLATFORM_TEMPORARILY_UNAVAILABLE` | `429` | `FAILED_RETRYABLE / PLATFORM_TEMPORARILY_UNAVAILABLE` |
| Submit terminal validation/permission | `202` | `FAILED_TERMINAL / PLATFORM_VALIDATION_FAILED` or `PLATFORM_PERMISSION_DENIED` |
| Submit malformed/null/exception/timeout | `202` | `UNKNOWN_OUTCOME / PLATFORM_RESPONSE_AMBIGUOUS` |
| Reconcile NOT_FOUND | `202` | `UNKNOWN_OUTCOME / PLATFORM_RECONCILIATION_NOT_FOUND` |
| Reconcile STILL_UNKNOWN | `202` | `UNKNOWN_OUTCOME / PLATFORM_RECONCILIATION_INCONCLUSIVE` |
| Reconcile terminal | `202` | `FAILED_TERMINAL / PLATFORM_RECONCILIATION_TERMINAL` |
| Exact committed replay without dispatch | `200` | existing safe operation fields unchanged |

`PLATFORM_STALE_VERSION` maps to entity 412 on Ad create/state routes and operation 412 on operation routes. `PLATFORM_ACCOUNT_INACTIVE`, `PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH`, and `PLATFORM_PROVIDER_UNSUPPORTED` all map to account-configuration 503. `PLATFORM_EVIDENCE_INVALID` maps to the Stage 4C evidence code only for Ad create/resume and retains the inherited evidence code outside Stage 4C. `PLATFORM_RECOVERY_NOT_DUE` is internal to the non-web recovery scheduler and is never exposed by these routes. Account/provider/evidence errors are mapped only after the fixed-account resolver. Every source code and route family has a parameterized status/code/message/path/fieldErrors test; safe JSON snapshots cover create, state, retry, reconciliation, success, retryable, terminal, unknown, replay, stale, and legacy-inert paths.

All error messages are fixed and reveal no account, raw external ID, provider trace/evidence, checksum, URL, canonical payload, or existence outside the scoped fixed account.

## Audit contract

Stage 4C reuses the exact Stage 4A `PlatformAuditEvent`; it does not add fields and does not overload Stage 4B budget events. Evidence UUIDs, checksum/fingerprint, mapping key, canonical payload/hash, and parent state are deliberately **not** Audit changes because the closed Stage 4A record cannot represent them. They remain constrained domain data testable through the Ad/operation rows. Required transaction events are:

- Ad create Transaction A: Ad `CREATE/ENTITY_CREATED` with only new `desiredState=PAUSED`, then operation `CREATE/OPERATION_CREATED` with only new operation status `CREATED` — exactly 2 events.
- Ad pause/resume Transaction A: `PLATFORM_OPERATION CREATE/OPERATION_CREATED` — exactly 1 event.
- Submit/retry claim: operation `UPDATE/OPERATION_TRANSITIONED` then `SUBMIT CREATE/ATTEMPT_CREATED` — exactly 2 events.
- Reconcile claim: operation `UPDATE/OPERATION_TRANSITIONED` then `RECONCILE CREATE/ATTEMPT_CREATED` — exactly 2 events.
- Any claimed normalized submit/reconcile outcome, including retryable, terminal, unknown, not-found, and still-unknown: attempt `UPDATE/ATTEMPT_FINALIZED` then operation `UPDATE/OPERATION_TRANSITIONED` with identical persisted code/trace when present — exactly 2 baseline events.
- Successful/found create: baseline 2 plus Ad `UPDATE/ENTITY_RESULT_APPLIED` carrying only the external-ID fingerprint and optional exact observed-state fields — exactly 3.
- Successful/found pause/resume: baseline 2 plus Ad `UPDATE/ENTITY_RESULT_APPLIED` carrying exact previous/new desired state and optional exact observed-state fields, no external-ID field — exactly 3.
- Immediate commit-failure ambiguity recovery: attempt finalized then operation transitioned with `PLATFORM_RESPONSE_AMBIGUOUS` and retained safe trace — exactly 2; no entity event.

Zero-event paths are limited to pre-claim/local failures: preview, exact replay, request/lookup/ETag/idempotency/provenance/evidence/parent rejection, invalid/no-op state command, not-due/cap/fourth retry, and failed optimistic claimant. A provider outcome after a committed claim is never a no-event path. Exact actor/source/request correlation, actions, subjects, ordered Stage 4A changes, rollback at every append position, post-final-append failure, and sentinel redaction are executable acceptance requirements.

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

- Cold V1→V14, populated V13→V14, Hibernate validate, V1–V13 checksum assertions, deliberate V14 object-name collision rollback, and forward-only V15 recovery evidence.
- Direct-SQL negatives for old/new `CREATE_AD` payload shapes, new-shape insert requirement, provenance false positives, forged parent version, invalid evidence/current divergence, false direct/reconciled create success, false activation, wrong correlated resume operation, external-ID substitution, immutable snapshot mutation, and complete rollback with named SQLSTATE/constraint assertions.
- Populated V13 fixture preserves accounts, Campaign/Ad Set/Ad, Stage 03 evidence, operations/attempts, metrics, budget ledger, Audit, and every legacy operation state byte-for-byte. Legacy `CREATED`, `FAILED_RETRYABLE`, and `UNKNOWN_OUTCOME` remain HTTP-inert; already-claimed `SUBMITTING` and `RECONCILING` exercise every normalized finalization plus stale recovery without a new provider call.
- Exact provenance, replay/conflict, original parent version, scoped account, desired-versus-observed semantics, current evidence, create/pause/resume, ETag 428/412, retry/reconciliation, stale finalizer, and concurrent one-winner tests.
- Claim-race barriers mutate Campaign/Ad Set desired state/version, Product/Asset lifecycle/checksum, output status/preservation/checksum, and review decision after Transaction A and before initial/retry claim. CREATE_AD/RESUME reject with zero attempt/Audit/call; PAUSE remains available.
- Finalization-race barriers perform the same mutations between adapter result and direct/reconciled commit, asserting either exact success or named V14 rollback followed by the exact UNKNOWN recovery graph. Unrelated `23514`, `23503`, `40001`, `40P01`, recovery failure, restart, and concurrent recovery prove no misclassification/re-dispatch.
- Dual Java/SQL canonicalizer fixtures cover legacy/new CREATE_AD, exact unsigned integer versions, every malformed/additional key, forbidden `DEFAULT_IMAGE_V1`, and byte-identical non-CREATE_AD hashes.
- Deterministic fake contract and real MockMvc/transaction/persistence cases for every legal submit/reconcile outcome with exact safe DTOs.
- Typed Audit exact-content/cardinality for Transaction A, guarded submit/retry/reconcile claims, every normalized finalization, create/state entity success, immediate/stale recovery, and attempt-3 conversion. Zero-event tests are limited to the enumerated pre-claim paths. Throw before/between/after each append and after final append before commit proves total rollback; sentinel values prove redaction.
- Backend/BFF exact JSON-key/value/omission snapshots for every record and error row; exact-route/header/query/body/duplicate/unknown/null field, canonical UUID/NFC, field order, 16 KiB, 1 MiB, 10-second timeout, client abort, redirect, transport, content-type, and forbidden-field cases.
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
