# Stage 04C — Ad Creative Publication Slice

## Gate status

- Status: Specification merged; post-merge main CI passed; runtime implementation in progress
- Branch: `codex/stage-04c-ad-creative-publication-runtime`
- Base: `c321dc0124375e13fd09785b0c827326e996207f`
- Stage 4B prerequisite: Passed; PR #62 merged at `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`; post-merge main CI Run `32055963526` passed
- Product specification: Repository-owner settings approved on 2026-08-18; specification PR #63 squash-merged at `c321dc0124375e13fd09785b0c827326e996207f`
- Runtime implementation: In progress on `codex/stage-04c-ad-creative-publication-runtime`
- Manager Decision: `APPROVE` for reviewed content Head `c19f4d4d9e8366865c3d011fb54e672b19c3cbb6`
- Merge: Squash merge `c321dc0124375e13fd09785b0c827326e996207f`; post-merge main CI Run `32204183690` passed
- Stage 4D: Locked

The approved specification contract is now on `main`. Runtime implementation may begin on this separate branch from that merge SHA. It remains inside the deterministic-FAKE LOCAL/TEST boundary and still authorizes no credential, network, real Provider, production, paid-delivery, activation, Auth/RBAC/Tenant, or Stage 4D change.

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
- Exact weak ETag/If-Match, replay, stable errors, typed Audit, rollback, direct-SQL, migration, concurrency, UI, and Remote CI acceptance.

## Explicitly excluded

- Real Meta accounts, tokens, app secrets, credentials, network calls, provider payloads, paid delivery, billing, or production deployment.
- Arbitrary copy, CTA, URL, audience, placement, pixel, catalog, tracking, schedule, creative JSON, or provider-specific controls.
- Ad Set/Campaign creation or budget-policy changes beyond the approved Stage 4B routes.
- Automatic activation, retry, reconciliation, asset generation/deletion, Product redraw, review override, or evidence substitution.
- Delivery and metrics reads assigned to Stage 4D; optional real-provider proof assigned to Stage 4E.
- Authentication, RBAC, Tenant model, real operator/approver enforcement, or any security-model change.

## Eligibility and immutable creative mapping

Transaction A locks and validates the fixed account, parent Campaign, parent Ad Set, Product, Asset, generation output, and review decision. A new command is eligible only when:

- account is the exact Stage 4B FAKE account resolved for the active `local` or `test` profile and all Backend feature gates are enabled;
- Campaign and Ad Set belong to that account, have nonblank durable external IDs, and are both `PAUSED` for create;
- the supplied Ad Set `If-Match` equals its locked version;
- Product is `ACTIVE`;
- Asset belongs to the Product, is `ACTIVE`, is `IMAGE`, and has a lowercase SHA-256 checksum;
- generation output belongs to the Product, is `IMAGE`, references exactly that generated Asset, is `APPROVED`, has preservation `PASSED`, and its output checksum equals the Asset checksum;
- review decision belongs to that output and is immutable `APPROVED`;
- server-owned creative mapping is exactly `APPROVED_IMAGE_ASSET_V1`.

The Browser supplies only the four evidence UUIDs. The Backend derives the approved checksum and mapping key. Transaction A persists those values into `platform_ads` and the canonical operation payload. V12's deferred Ad snapshot trigger remains authoritative at commit.

For resume, Transaction A additionally locks the Ad and both parents and requires Campaign/Ad Set `ACTIVE`, Ad `PAUSED`, a nonblank Ad external ID, matching Ad `If-Match`, and the same current Product/Asset/output/review/checksum chain. Pause requires only account ownership, a current weak Ad ETag, Ad `ACTIVE`, and its existing external ID; divergence never prevents a safety-reducing pause.

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

Stage 4C does not add a marker column or business table. Ownership is derived from durable V12 operation payloads, the immutable Ad creation chain, and the closed Stage 4B LOCAL/TEST account tuples. SQL never reads the active Spring profile, `current_setting`, `application_name`, or any other forgeable session value, and it never compares against a single `fixedAccountUuid`.

The closed durable approved account tuples are exactly the Stage 4B server-owned fixtures. Local uses UUID `00000000-0000-4000-8000-00000000004b`, reference `stage4b-local`, environment `LOCAL`, and fingerprint `4f1eee978e5efed2d42ac62995484b642870cda74dea26cd2d2f63653d51cf36`; test uses UUID `00000000-0000-4000-8000-00000000005b`, reference `stage4b-test`, environment `TEST`, and fingerprint `9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2`. SQL ownership also requires `provider_key=FAKE`, `currency=TWD`, and `timezone=Asia/Taipei` on that locked row. These are non-secret FAKE fixtures.

`isApprovedStage4CAccount(account)` is true only when the locked `platform_accounts` row equals one of those tuples field-for-field. A UUID-only match, mutated reference/fingerprint/environment/provider/currency/timezone, extra candidate, missing row, or unknown account is false.

```text
isStage4COwnedSql(operation) =
  operation.entityType == AD
  AND isApprovedStage4CAccount(operation.account)
  AND (
    operation.operationType == CREATE_AD
      AND normalizedRequest has the exact new CREATE_AD key set
      AND expectedParentVersion is a JSON number whose stored integral
          value is in 0..9223372036854775807
    OR operation.operationType in {PAUSE, RESUME}
      AND normalizedRequest.entityUuid == operation.entityUuid
      AND EXISTS exactly one SUCCEEDED, same-account, new-shape CREATE_AD operation
          whose entityUuid == operation.entityUuid
          and whose platformAdUuid == operation.entityUuid
  )
```

V14 exposes this as the read-only SQL function `is_stage4c_owned_operation(operation_uuid)`. The function joins `platform_operations` to `platform_accounts` and evaluates `isStage4COwnedSql`. It accepts only that UUID argument. It returns false for every non-Ad operation, malformed/mismatched payload, pre-V14 `CREATE_AD` payload without `expectedParentVersion`, Ad state operation without the qualifying successful new-shape create chain, and every account that is not one of the two closed tuples. No V13 batch is created for Stage 4C.

The application predicate is stricter and always runs after the existing Stage 4B request-time fixed-account resolver:

```text
isStage4COwned(operation, resolvedAccount) =
  operation.platformAccountUuid == resolvedAccount.platformAccountUuid
  AND isApprovedStage4CAccount(resolvedAccount)
  AND isStage4COwnedSql(operation)
```

`resolvedAccount` is exactly one of the two tuples, selected by the active `local` or `test` profile, and must also satisfy the Stage 4B `ACTIVE`/non-archived resolver rules. HTTP GET/retry/reconcile never call the SQL function in isolation: they use `isStage4COwned`. Direct-SQL and V14 triggers use `is_stage4c_owned_operation` because they have no trustworthy profile.

Cross-profile, wrong-account, and forged-account HTTP requests fail at the resolver or account-scoped lookup with no identifier disclosure, even when `is_stage4c_owned_operation` is true for a same-database TEST row while the LOCAL profile is active. Direct-SQL tests assert SQL true/false independently of profile; application tests assert the additional resolver equality and zero disclosure.

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
2. Add a `BEFORE INSERT` operation trigger that requires the new key set for every `CREATE_AD` operation physically inserted after V14. After JSONB parse, PostgreSQL enforces JSON number type, integral value in `0..9223372036854775807`, `->>` matching `^(0|[1-9][0-9]*)$`, exact keys, and stored `request_sha256` coherence with the persisted Java canonical hash. PostgreSQL does not inspect or reject original numeric token spelling. Legacy rows are not backfilled and cannot be attached retroactively to a new request identity.
3. Add `is_stage4c_owned_operation(operation_uuid)` with `isStage4COwnedSql` above. HTTP GET/retry/reconcile orchestration uses the stricter application `isStage4COwned` after the active-profile resolver. Triggers and direct-SQL use the SQL function.
4. Add `verify_platform_ad_submit_claim()` on the operation edge `CREATED|FAILED_RETRYABLE -> SUBMITTING`. For new-shape `CREATE_AD` and SQL Stage 4C-owned Ad `RESUME`, it locks and validates the operation account against the closed tuple set, parent hierarchy/state/version, Ad, and current evidence before the operation update and matching `SUBMIT/STARTED` attempt can commit. Ad `PAUSE` is explicitly exempt from evidence/parent-state validation but still requires closed-tuple account ownership, durable Ad external ID, exact expected Ad version, and `ACTIVE -> PAUSED` payload validity. A failed guard raises the named constraint `ct_platform_ad_submit_claim_evidence`, rolls back the entire claim transaction, and creates no attempt/Audit/call.
5. Add `verify_platform_ad_dispatch_evidence()` under the named deferrable constraint `ct_platform_ad_dispatch_result`. It covers both direct and reconciled success: `SUBMITTING -> SUCCEEDED` with matching finalized `SUBMIT/SUCCEEDED` attempt, or `RECONCILING -> SUCCEEDED` with matching finalized `RECONCILE/SUCCEEDED` attempt. It also covers the correlated Ad entity result update in the same transaction.
6. At final commit, lock and revalidate the current Product/Asset/output/review/checksum chain, closed-tuple account ownership, parent hierarchy, operation payload, operation/attempt result, and entity mutation. New create success requires the locked Ad Set version to equal payload `expectedParentVersion`; legacy create success omits only this comparison. Create requires both parent **desired states** `PAUSED`; resume requires both parent **desired states** `ACTIVE`. Parent observed state is informational, may be absent or different, and never changes eligibility. Parent external IDs must remain nonblank. Create success applies exactly Ad external ID/fingerprint plus optional observation with one Ad version increment; resume/pause success applies exactly the payload desired transition plus optional observation with one version increment and never changes external ID.
7. Preserve historical Ads when upstream rows later diverge. The trigger runs only for new create success or activation, not archival/history reads or pause.
8. Raise SQLSTATE `23514` for semantic incoherence and retain FK `23503` behavior for missing/mismatched identities.
9. Keep every V1–V13 migration byte-identical and preserve all populated rows during V13→V14 upgrade.

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
- `canonicalizeNewCreateAd(String)` accepts only the new shape and is mandatory for every Stage 4C preview/confirm/create path. It inspects the raw JSON source before any JSONB conversion and rejects a missing/additional `expectedParentVersion`, legacy shape, exponent/sign/fraction/leading-zero/quoted/boolean/null/array version spelling, a duplicate or unknown key, noncanonical UUID/checksum, or mapping other than `APPROVED_IMAGE_ASSET_V1` before persistence.

`expectedParentVersion` canonicalizes as unsigned plain base-10 digits with no sign, exponent, fraction, leading zero except `0`, or scale. Replay comparison includes its canonical numeric value and the complete canonical JSON bytes/hash. HTTP `If-Match` is parsed only by existing `ResourceEtag` and is written into the payload as that canonical integer; the Browser never supplies the payload number directly.

Numeric validation is split because PostgreSQL JSONB normalizes numeric tokens before any V14 function runs and therefore cannot distinguish an original `1e0` from integral `1` without raw lexical storage:

- Java/HTTP, on the raw JSON source and `If-Match` token, reject `1e0`, `1E0`, `1.0`, `01`, `+1`, `-1`, `-0`, quoted `"1"`, boolean, null, array, and any other non-canonical spelling, and never persist them.
- PostgreSQL, after JSONB parse, enforces JSON number type, integral value in `0..9223372036854775807`, `->>` matching `^(0|[1-9][0-9]*)$`, exact keys, and stored canonical/`request_sha256` coherence. Direct-SQL of a JSONB-normalized `1e0` that became integral `1` is accepted by SQL and is not an HTTP success path.
- Shared fixtures cover key-set, JSON type, integral range, and canonical stored bytes/hash. Lexical-only fixtures are Java/HTTP. JSONB-normalized numeric fixtures are SQL. The two layers are not claimed to accept/reject the same lexical tokens.

No other operation payload changes.

Provider-command reconstruction requires every CREATE_AD field. `creativeMappingKey` has no fallback: missing or non-`APPROVED_IMAGE_ASSET_V1` throws `PLATFORM_CONTRACT_INVALID` before claim/adapter invocation. `DEFAULT_IMAGE_V1` is removed from the CREATE_AD dispatch path and is forbidden in fixtures, persisted payloads inserted after V14, logs, API DTOs, and adapter commands. Regression tests prove legacy bytes remain readable, new bytes are mandatory for new commands, all non-CREATE_AD canonical bytes/hashes are unchanged, and every rejected/defaulted path has zero attempt/call/Audit.

## API contract

All routes are exposed only by the full Stage 4B Backend gate and use the same fixed account/actor resolver before resource lookup.

| Route | Request | Success |
| --- | --- | --- |
| `POST /api/platforms/meta/ad-sets/{adSetUuid}/ads/preview` | Ad Set `If-Match`; exact create request | `200 AdPreview` |
| `POST /api/platforms/meta/ad-sets/{adSetUuid}/ads` | same request/header | `200` replay or `202` new `PlatformOperationApiView`; operation weak `ETag` + `Location` |
| `GET /api/platforms/meta/ads/{adUuid}` | no query/body | `200 PlatformAdApiView` + Ad weak `ETag` |
| `POST /api/platforms/meta/ads/{adUuid}/state/preview` | Ad `If-Match`; exact target request | `200 StatePreview` |
| `POST /api/platforms/meta/ads/{adUuid}/pause` | Ad `If-Match`; target must be `PAUSED` | `200` replay or `202` new `PlatformOperationApiView` |
| `POST /api/platforms/meta/ads/{adUuid}/resume` | Ad `If-Match`; target must be `ACTIVE` | `200` replay or `202` new `PlatformOperationApiView` |

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
```

`Confirmation(PlatformOperationApiView operation, boolean replay)` is an internal application record only. It is never a JSON type, never appears in OpenAPI/TypeScript, and is never serialized. Controllers unwrap it exactly as Stage 4B: HTTP `200` if and only if `replay` is true, otherwise `202`, except persisted `PLATFORM_RATE_LIMITED` / `PLATFORM_TEMPORARILY_UNAVAILABLE` which remain `429`; the body is always the nested `PlatformOperationApiView`. Replay is represented only by that status difference. Top-level JSON keys are exactly the `PlatformOperationApiView` declaration order above; `operation` and `replay` are forbidden keys.

All reference fields and Optional containers are non-null. UUID JSON is canonical lowercase. `Optional.empty()` fields are omitted through `NON_ABSENT`; required fields are never null. Lists are immutable. `entityType` is always `AD`, `newAdDesiredState` is always `PAUSED`, `creativeMappingKey` is always `APPROVED_IMAGE_ASSET_V1`, and warnings are exactly the three enum values above in declaration order. The checksum fingerprint is lowercase SHA-256 of UTF-8 `"stage4c-approved-checksum-v1\n" + approvedChecksumSha256`; raw checksum is never returned. External-ID fingerprint retains the Stage 4A lowercase SHA-256 formula and raw ID is never returned.

Eligibility uses parent **desired state only**: create requires Campaign and Ad Set `PAUSED`; resume requires both `ACTIVE`; pause ignores parent state. Parent/Ad observed state is provider observation only, may be absent or differ, is returned only for the Ad read, and cannot authorize or block a command. Preview echoes the exact locked ETag version. `evidenceEligible` and `confirmable` are both true on `200`; an ineligible preview returns an error rather than a `false` preview. State preview sets `evidenceEligible=true` for pause and to the exact current-chain result for resume.

`AdPreview`/`StatePreview` contain no candidate Ad UUID, confirmation token, account identity, raw checksum/external ID, provider evidence/trace, canonical payload/hash, URL, token, cookie, credential, or arbitrary string. `PlatformAdApiView` and `PlatformOperationApiView` are the complete safe allowlists; unknown response keys fail Backend snapshot and BFF sanitization tests.

Stage 4C reuses `ResourceEtag` and emits/accepts exactly one weak token `W/"<non-negative decimal version>"` where the decimal matches `0|[1-9][0-9]*` and is in `0..9223372036854775807`. Mutation and operation responses use `W/"<operationVersion>"`. Ad GET uses `W/"<adVersion>"`. `Location` is `/api/platform-operations/{operationUuid}`. Required `If-Match` is absent → `428 PLATFORM_IF_MATCH_REQUIRED`. Every other rejected form is `400 PLATFORM_REQUEST_INVALID` with field `If-Match` and message `Invalid If-Match`, including strong `"1"`, quoted-without-`W/`, wildcard `*`, list `W/"1", W/"2"`, padded `W/"01"`, negative `W/"-1"`, lowercase `w/"1"`, surrounding whitespace, empty, overflow `W/"9223372036854775808"`, and any other malformed token. Preview has neither ETag nor Location. Operation GET/retry/reconcile reuse the exact safe DTO; retry/reconcile require no query and an empty body, require operation `If-Match`, and return the same status/header/body rules. All routes reject query parameters. Backend, BFF, and UI snapshots cover emitted weak tags, 200-versus-202 top-level keys, and each malformed 400 vector.

The BFF adds only the six fixed Backend paths above plus the inherited fixed operation GET/retry/reconcile paths after the Backend has account-scoped and provenance-qualified them. TypeScript types mirror the public records exactly and have no `Confirmation` type; safe-key allowlists include `entityType=AD` and reject any extra/missing/wrong-type key, including wrapper keys `operation` and `replay`. It retains the 16 KiB request, 1 MiB response, 10-second timeout, manual redirect refusal, bounded streaming, client-abort composition, JSON content-type checks, request/response header allowlists, and forbidden-field sanitizer. Browser input can never choose a Backend origin or operation/entity type.

Validation precedence is exact: feature/profile gate and fixed-account configuration; request content-type/size/JSON/duplicate-or-unknown/null field/path/query shape; canonical lowercase path/body UUID and NFC; exact replay or idempotency conflict; account-scoped parent/entity/operation lookup; `If-Match` presence/parse/value; parent desired-state eligibility; current evidence eligibility; Stage 4C provenance; operation state; retry due/caps or reconciliation cap. The first failure wins and all later layers have zero side effects.

Create field-error order is `clientRequestUuid`, `productUuid`, `assetUuid`, `generationOutputUuid`, `reviewDecisionUuid`, then `If-Match`; state order is `clientRequestUuid`, `targetDesiredState`, then `If-Match`. Path failures use `path`; body syntax uses `body`; query uses `query`. Fixed field messages are `Invalid value`, `Invalid If-Match`, `Invalid request body`, `Invalid path`, and `Query parameters are not allowed` respectively. Existing public contracts are reused; the complete Stage 4C additions/mappings are:

| HTTP | Public code | Fixed message | Internal source / route |
| --- | --- | --- | --- |
| 400 | `PLATFORM_REQUEST_INVALID` | `Platform request is invalid` | strict route boundary, including malformed `If-Match` |
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

`PLATFORM_STALE_VERSION` maps to entity 412 on Ad create/state routes and operation 412 on operation routes. `PLATFORM_ACCOUNT_INACTIVE`, `PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH`, and `PLATFORM_PROVIDER_UNSUPPORTED` all map to account-configuration 503. `PLATFORM_EVIDENCE_INVALID` maps to the Stage 4C evidence code only for Ad create/resume and retains the inherited evidence code outside Stage 4C. `PLATFORM_RECOVERY_NOT_DUE` is internal to the non-web recovery scheduler and is never exposed by these routes. Account/provider/evidence errors are mapped only after the fixed-account resolver. Every source code and route family has a parameterized status/code/message/path/fieldErrors test; safe JSON snapshots cover create, state, retry, reconciliation, success, retryable, terminal, unknown, replay, stale, and legacy-inert paths, assert top-level keys never include `operation` or `replay`, and assert emitted `ETag` is exactly one weak `W/"<version>"` token.

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
- Direct-SQL negatives for old/new `CREATE_AD` payload shapes, new-shape insert requirement, provenance false positives, forged parent version, account-tuple mismatch, UUID-only forged account, mutated LOCAL/TEST fingerprint/reference, invalid evidence/current divergence, false direct/reconciled create success, false activation, wrong correlated resume operation, external-ID substitution, immutable snapshot mutation, and complete rollback with named SQLSTATE/constraint assertions. Direct-SQL also proves JSONB-normalized `1e0` that stored as integral `1` is a SQL-layer numeric case, not an HTTP lexical success.
- Populated V13 fixture preserves accounts, Campaign/Ad Set/Ad, Stage 03 evidence, operations/attempts, metrics, budget ledger, Audit, and every legacy operation state byte-for-byte. Legacy `CREATED`, `FAILED_RETRYABLE`, and `UNKNOWN_OUTCOME` remain HTTP-inert; already-claimed `SUBMITTING` and `RECONCILING` exercise every normalized finalization plus stale recovery without a new provider call.
- Exact provenance, replay/conflict, original parent version, scoped account, desired-versus-observed semantics, current evidence, create/pause/resume, weak ETag 428/412, malformed ETag 400, retry/reconciliation, stale finalizer, and concurrent one-winner tests.
- LOCAL versus TEST resolver, cross-profile operation UUID access, wrong-account, and forged-account HTTP cases assert no identifier disclosure, zero writes/Audit/adapter calls, and that `is_stage4c_owned_operation` is independent of profile while `isStage4COwned` requires resolver equality. Session-setting/`current_setting` forgeries cannot change SQL ownership.
- Claim-race barriers mutate Campaign/Ad Set desired state/version, Product/Asset lifecycle/checksum, output status/preservation/checksum, and review decision after Transaction A and before initial/retry claim. CREATE_AD/RESUME reject with zero attempt/Audit/call; PAUSE remains available.
- Finalization-race barriers perform the same mutations between adapter result and direct/reconciled commit, asserting either exact success or named V14 rollback followed by the exact UNKNOWN recovery graph. Unrelated `23514`, `23503`, `40001`, `40P01`, recovery failure, restart, and concurrent recovery prove no misclassification/re-dispatch.
- Dual Java/SQL fixtures are partitioned: shared key-set/type/range/canonical-hash cases; Java/HTTP-only lexical rejects (`1e0`, `1E0`, `1.0`, `01`, `+1`, `-1`, `-0`, quoted `"1"`); SQL-only JSONB-normalized numeric cases. Legacy/new CREATE_AD, forbidden `DEFAULT_IMAGE_V1`, and byte-identical non-CREATE_AD hashes remain required.
- Deterministic fake contract and real MockMvc/transaction/persistence cases for every legal submit/reconcile outcome with exact safe DTOs.
- Typed Audit exact-content/cardinality for Transaction A, guarded submit/retry/reconcile claims, every normalized finalization, create/state entity success, immediate/stale recovery, and attempt-3 conversion. Zero-event tests are limited to the enumerated pre-claim paths. Throw before/between/after each append and after final append before commit proves total rollback; sentinel values prove redaction.
- Backend/BFF exact JSON-key/value/omission snapshots for every public record and error row, including create/state/retry/reconcile `200` versus `202` top-level `PlatformOperationApiView` keys and absence of `operation`/`replay`; exact-route/header/query/body/duplicate/unknown/null field, canonical UUID/NFC, field order, weak ETag emit/parse, 16 KiB, 1 MiB, 10-second timeout, client abort, redirect, transport, content-type, and forbidden-field cases.
- Component and Playwright cases for preview/confirmation, normalized read, pause/resume, stale invalidation, due retry, unknown reconciliation, divergence, weak ETag round-trip, malformed If-Match 400, and zero automatic action.
- Full Backend regression; Frontend lint/typecheck/tests/build; `npm audit --omit=dev`; Compose config/cold health; Smoke; Playwright; actionlint; pinned Gitleaks history/worktree; `git diff --check`.
- Exact-head Push and Pull Request CI run `quality-and-compose` and `secret-scan` with no required-step skip.

## Stage gate

Stage 4C specification gates are complete. Runtime implementation is in progress on `codex/stage-04c-ad-creative-publication-runtime` from merge SHA `c321dc0124375e13fd09785b0c827326e996207f` after post-merge main CI Run `32204183690`.

- [x] Repository owner explicitly approved the nine product decisions on 2026-08-18.
- [x] Independent Manager Review records exactly `APPROVE` for reviewed content Head `c19f4d4d9e8366865c3d011fb54e672b19c3cbb6`.
- [x] The approval-record commit passed full exact-head Push CI `32201581792` and Pull Request CI `32201584294`.
- [x] Specification PR #63 squash-merged to `main` at `c321dc0124375e13fd09785b0c827326e996207f`; post-merge main CI Run `32204183690` passed.

Stage 4D remains locked through Stage 4C implementation merge and post-merge verification.
