# Stage 04C — Ad Creative Publication Slice

## Gate status

- Status: Specification drafting unlocked
- Branch: `codex/stage-04c-ad-creative-publication-specification`
- Base: `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`
- Stage 4B prerequisite: Passed; PR #62 merged at `dcfb5e7dcb284bba824c6c81d91ad6ad8b3cd785`; post-merge main CI Run `32055963526` passed
- Product specification: Draft; requires repository-owner approval and Independent Manager Review
- Runtime implementation: Locked; not started
- Manager Decision: Pending
- Merge: Not started
- Stage 4D: Locked

This document unlocks specification work only. It is not an approved implementation contract and authorizes no migration, runtime, REST, BFF, UI, adapter, provider, credential, network, production, paid-delivery, activation, or Stage 4D change.

## Parent objective

Stage 4C will specify the bounded Ad creative publication slice described by the approved parent Stage 04 plan:

- Create paused Ads from approved Stage 03 assets with immutable review and checksum evidence.
- Reject unreviewed, inactive, mismatched, or substituted assets.
- Do not delete assets, redraw Products, or activate Ads automatically.

## Inherited boundaries

The draft must preserve these already-approved boundaries unless a new human decision explicitly changes them:

1. Provider execution is deterministic `FAKE` only in `LOCAL` or `TEST`; credentials, real provider network calls, spend, billing, and production remain forbidden.
2. Every Ad starts `PAUSED`; creation does not authorize activation or delivery.
3. Stage 03 Product, Asset, generated output, review decision, and checksum evidence remain authoritative. Stage 4C cannot mutate or bypass that evidence chain.
4. The Stage 4A operation, attempt, idempotency, reconciliation, optimistic-concurrency, evidence, and Audit contracts remain authoritative.
5. The Stage 4B fixed account, Campaign, Ad Set, actor, feature-gate, safe API/BFF disclosure, and budget-authorization boundaries remain authoritative.
6. AI and the Decision Engine cannot call a platform write port directly.
7. Authentication, RBAC, Tenant authority, credentials, real-provider access, delivery, metrics, and production behavior remain separately gated and out of scope.
8. Existing Flyway migrations V1–V13 are immutable. Any approved schema change must be additive and forward-only.

## Specification work to complete

Before the specification can enter Independent Manager Review, it must define implementation-ready contracts for:

- eligible Product, Asset, generated output, review decision, checksum, lifecycle, ownership, and version evidence;
- Campaign and Ad Set eligibility, account ownership, state, concurrency, and schedule containment;
- Ad identity, immutable creative snapshot, desired/observed state, provider-neutral command payload, canonical idempotency, and deterministic fake ID;
- preview, explicit confirmation, normalized read, pause/resume policy, operation read/retry/reconcile behavior, ETag/If-Match, stable errors, and safe response DTOs;
- exact transaction boundaries, persistence-before-provider-call behavior, reconciliation-only ambiguous recovery, typed Audit records, and rollback/no-event paths;
- additive migration intent, constraints, reciprocal integrity, direct-SQL negatives, concurrency behavior, upgrade compatibility, and forward recovery;
- Backend, Frontend, BFF, Compose, Playwright, Smoke, Audit, security, Gitleaks, dependency, workflow, and exact-head Remote CI acceptance.

## Explicitly out of scope for this unlock

- Runtime code, database migration, REST controller, BFF route, UI, adapter, or test implementation.
- Real Meta accounts, tokens, app secrets, credentials, network calls, paid delivery, billing, or production deployment.
- Automatic activation, automatic retry, automatic reconciliation, asset creation/deletion, Product redraw, or review override.
- Metrics/delivery reads assigned to Stage 4D and optional real-provider proof assigned to Stage 4E.
- Authentication, RBAC, Tenant model, operator/approver enforcement, or other security-model changes.

## Draft decisions requiring explicit approval

The specification author must present exact recommended values and alternatives for repository-owner approval before implementation is unlocked, including at least:

- eligible Ad creative/output type and the exact creative fields exposed to the deterministic fake adapter;
- Ad naming, server-owned policy fields, destination/call-to-action behavior, and forbidden provider fields;
- whether LOCAL/TEST deterministic-FAKE resume may reuse the Stage 4B same-actor exception or remains inert;
- immutable snapshot versus ongoing evidence-coherence semantics after upstream Product/Asset lifecycle changes;
- Ad-level replay identity, parent version binding, schedule/state rules, and reconciliation outcomes;
- any additive persistence required beyond the existing Stage 4A `platform_ads` foundation.

No default in this section is approved merely by appearing in a draft.

## Specification gate

Stage 4C runtime implementation remains locked until all of the following are true:

- [ ] The full specification is complete and contains no unresolved product/security decision.
- [ ] Repository-owner decisions and any required human escalation are recorded.
- [ ] The specification branch contains documentation-only scope and passes local integrity/secret checks.
- [ ] Draft PR Push and Pull Request CI pass at the exact reviewed Head with no required-step skip.
- [ ] Independent Manager Review records exactly `APPROVE` for that Head.
- [ ] Any approval-record commit passes complete exact-head CI.
- [ ] The specification PR merges to `main` and post-merge main CI passes.

Until then, Stage 4C runtime implementation and Stage 4D remain locked.
