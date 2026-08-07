# Milestone 2C-2 — Product Knowledge Vertical Slice

## Gate status

- Status：Approved for implementation
- Branch：`codex/2c-2-product-knowledge`
- Base Commit：`9c2b678a7329c8a8c9519e05cc6bc4eec12d5e61`
- Implementation：Not started
- Local Verification：Not started
- Remote CI：Not started
- Manager Review：Not started
- Manager Decision：Pending
- Human Review Required：No
- Merge：Not started
- Milestone 2C-4：Locked

## Objective

Deliver the Product Knowledge Backend, same-origin BFF, and Product Detail Knowledge Tab on the approved V4 foundation. All mutations must use independent resource concurrency, trusted audit actors, archive-only lifecycle, and the existing Product ownership boundary.

## Included

- Knowledge Create, list, single read, merge-patch, archive, and restore.
- Endpoints exactly as defined by the parent 2C specification under `/api/products/{productUuid}/knowledge`.
- Writable fields only：`knowledgeType`, `title`, `content`, `source`.
- `201 + Location + ETag` on create; resource `ETag` on single reads and successful mutations.
- Strict `If-Match` for patch/archive/restore with 428／400／412 behavior.
- ACTIVE／ARCHIVED／ALL collection filtering, pagination, size ≤ 100, sort allowlist, and UUID secondary ordering.
- Archived Product remains readable but rejects Knowledge mutation with `PRODUCT_ARCHIVED`.
- Archived Knowledge rejects ordinary patch; archive/restore no-op does not increment version or create audit.
- `KnowledgeCommandService` transaction boundary with trusted `AuditActorProvider`; audit records only actual changes.
- Next.js endpoint-specific same-origin Knowledge Route Handlers; no arbitrary URL/path proxy.
- Product Detail `knowledge` Tab with loading, empty, error, create, edit, archive, restore, archived-product read-only, and stale-version reload UX.
- Backend integration/unit tests and Frontend/BFF/component tests.

## Explicitly out of scope

- Creative Plan, Campaign, Campaign Product, Asset, or Aggregate implementation.
- V1–V4 migration changes or a new migration.
- Google, Quality, Workflow, AI, Meta Ads, Dashboard, Decision Engine, or Playwright.
- Authentication／RBAC／Tenant or production actor-provider changes.
- Generic proxy, Cookie／Authorization forwarding, or Browser-controlled Backend origin.

## Required contracts

- Existing Product endpoints and responses remain unchanged.
- Merge patch accepts only `application/merge-patch+json`; missing fields are unchanged, explicit null follows field rules, unknown/immutable fields return `INVALID_MERGE_PATCH`, and empty/no-change patches create no version or audit change.
- Error responses use the existing `ApiError` contract and request ID.
- Path Product ownership mismatch is indistinguishable from not found.
- Mutation and audit commit or roll back together.
- Large content audit values use existing redaction and 4096-character truncation; secrets are never logged.
- BFF forwards only approved query/header/response fields, preserves Backend status/body/ETag/Location/request ID, enforces timeout/body limit, and never forwards Cookie or Authorization.

## Verification requirements

- Backend happy paths and validation for all six endpoints.
- 428, malformed ETag, stale 412, archived resource 409, archived Product 409, owner mismatch 404, empty patch, and rollback tests.
- Audit CREATE／UPDATE／ARCHIVE／RESTORE actor/request ID/actual-change tests; failed/stale/blocked/no-op requests leave no audit.
- Repository queries exclude archived by default and apply stable pagination/sort.
- BFF allowlists, no arbitrary path/URL, header/cookie boundary, timeout/body size, and status/header forwarding tests.
- Knowledge Tab loading/empty/error/CRUD/archive/restore/409/412/428 tests.
- Full Backend suite; Frontend lint/typecheck/tests/build; Compose config/cold start; existing Product smoke plus Knowledge vertical-slice smoke; Gitleaks; npm audit; actionlint.

## Acceptance checklist

- [ ] Knowledge API and UI conform to the approved contract.
- [ ] Product ownership, archive, concurrency, idempotency, and audit boundaries are proven.
- [ ] No hard-delete API or repository operation is introduced.
- [ ] BFF security boundary is preserved.
- [ ] V1–V4 remain unchanged.
- [ ] No out-of-scope resource or later-stage behavior is implemented.
- [ ] Local and Remote CI verification pass.
- [ ] Independent Manager Decision is `APPROVE` before merge.
- [ ] Post-merge main CI passes before downstream integration depends on this slice.

## Mandatory escalation

Stop and escalate if implementation requires changing V1–V4, breaking the Product contract, weakening the BFF or audit actor boundary, adding authentication／RBAC／production credentials, destructive data changes, or extending scope beyond Product Knowledge.
