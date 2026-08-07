# Milestone 2C-3 — Creative Plan Vertical Slice

## Gate status

- Status：Approved for implementation
- Branch：`codex/2c-3-creative-plans`
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

Deliver the Creative Plan Backend, same-origin BFF, and Product Detail Creative Plans Tab on the approved V4 foundation. The slice uses independent resource ETags, archive-only lifecycle, Product ownership, and transactional audit without introducing AI generation or approval workflow.

## Included

- Creative Plan Create, list, single read, merge-patch, archive, and restore.
- Endpoints exactly as defined by the parent 2C specification under `/api/products/{productUuid}/creative-plans`.
- Writable V4 business fields：`planName`, `primaryAudience`, `secondaryAudience`, `painPoint`, `coreBenefit`, `creativeAngle`, `emotionalDirection`, `brandTone`, `visualStyle`, `mainColor`, `characterSetting`, `cta`.
- `201 + Location + ETag` on create; resource `ETag` on single reads and successful mutations.
- Strict `If-Match` with 428／400／412 behavior.
- ACTIVE／ARCHIVED／ALL filtering, pagination, size ≤ 100, sort allowlist, and UUID secondary ordering.
- Archived Product remains readable but rejects Creative Plan mutation with `PRODUCT_ARCHIVED`.
- Archived Plan rejects ordinary patch; archive/restore no-op does not increment version or create audit.
- `CreativePlanCommandService` transaction boundary with trusted `AuditActorProvider`; audit records only actual changes.
- Next.js endpoint-specific same-origin Creative Plan Route Handlers.
- Product Detail `creative-plans` Tab with loading, empty, error, create, edit, archive, restore, archived-product read-only, and stale-version reload UX.
- Backend integration/unit tests and Frontend/BFF/component tests.

## Explicitly out of scope

- Product Knowledge, Campaign, Campaign Product, Asset, or Aggregate implementation.
- V1–V4 migration changes or a new migration.
- AI-generated fields, prompts, LLM calls, approval actor/time, Quality, or Workflow.
- Google, Meta Ads, Dashboard, Decision Engine, or Playwright.
- Authentication／RBAC／Tenant or production actor-provider changes.
- Generic proxy, Cookie／Authorization forwarding, or Browser-controlled Backend origin.

## Required contracts

- Existing Product endpoints and responses remain unchanged.
- Merge patch accepts only `application/merge-patch+json`; missing fields are unchanged, explicit null clears optional fields but cannot clear `planName`, unknown/immutable fields fail, and empty/no-change patches produce no audit/version change.
- Error responses use existing `ApiError` and request ID contracts.
- Path Product ownership mismatch returns not found without disclosing another Product relationship.
- Mutation and audit share one transaction.
- Audit uses approved value types, redaction, truncation, and actual-change ordering; no sensitive request body is logged.
- BFF uses fixed endpoint/query/header allowlists, preserves Backend status/body/ETag/Location/request ID, enforces timeout/body size, and never forwards Cookie or Authorization.

## Verification requirements

- Backend happy paths and validation for all six endpoints and every writable field.
- 428, malformed ETag, stale 412, archived Plan 409, archived Product 409, owner mismatch 404, empty patch, and rollback tests.
- Audit CREATE／UPDATE／ARCHIVE／RESTORE actor/request ID/actual-change tests; failed/stale/blocked/no-op requests leave no audit.
- Repository queries exclude archived by default and apply stable pagination/sort.
- BFF arbitrary path/URL rejection, query/header/cookie boundary, timeout/body size, and response forwarding tests.
- Creative Plans Tab loading/empty/error/CRUD/archive/restore/409/412/428 tests.
- Full Backend suite; Frontend lint/typecheck/tests/build; Compose config/cold start; existing Product smoke plus Creative Plan vertical-slice smoke; Gitleaks; npm audit; actionlint.

## Acceptance checklist

- [ ] Creative Plan API and UI conform to the approved contract.
- [ ] Product ownership, archive, concurrency, idempotency, and audit boundaries are proven.
- [ ] No hard-delete API or repository operation is introduced.
- [ ] BFF security boundary is preserved.
- [ ] V1–V4 remain unchanged.
- [ ] No AI, approval workflow, or other out-of-scope behavior is implemented.
- [ ] Local and Remote CI verification pass.
- [ ] Independent Manager Decision is `APPROVE` before merge.
- [ ] Post-merge main CI passes before Campaign or Asset depends on this slice.

## Mandatory escalation

Stop and escalate if implementation requires changing V1–V4, breaking the Product contract, weakening BFF/audit boundaries, adding AI/authentication/production credentials, destructive data changes, or extending scope beyond Creative Plans.
