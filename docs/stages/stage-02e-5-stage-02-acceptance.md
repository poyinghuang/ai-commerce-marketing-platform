# Milestone 2E-5 — Stage 02 Acceptance

## Delivery status

- Branch: `codex/2e-5-stage-02-acceptance`
- Base Commit: `4e12c9f72a9ed7810ee95b96143b51f8c3a31906`
- Implementation: Complete
- Local Verification: Passed
- Commit / Push / Remote CI: Passed — implementation Commit `a67cd052d5ad98f7df0124c33e096143de5486d2`; Push Run `31307627650`; PR Run `31307633427`
- Manager Review / Decision: Passed — `APPROVE`
- Human Review Required: No
- Merge: Pending
- Stage 03: Not started

## Approved scope

- Add a constrained local/test mixed-row Sheet fixture that targets one caller-created immutable Product ID.
- Add one real-Compose Chromium journey covering template download, Preview CREATE/UPDATE/INVALID rows, Execute partial results, persisted reload, Product Aggregate/Quality recalculation, and Drive folder create/reuse.
- Run the complete Stage 02 Backend, Frontend, migration, Hibernate, Compose, smoke, Playwright, dependency, workflow, and secret regression gates.
- Complete exact-head Manager Review, merge, post-merge main verification, and final Stage 02 tags.

## Boundaries

- No new Migration and no modification of V1–V7.
- No production Provider behavior, live Google credential, external API, deployment, System of Record, security model, API contract, or permission change.
- No polling, two-way sync, live Sheet export, file transfer/delete, AI, Meta Ads, Dashboard, Decision Engine, or Stage 03 functionality.
- The mixed fixture exists only under the existing `(local | test) & !production` Provider profile and accepts only `stub-products-mixed_PROD-00000000`-shaped identifiers.

## Acceptance checklist

- [x] Mixed Preview persists exactly one CREATE, one UPDATE, and one INVALID row with two valid rows.
- [x] Execute returns partial completion, creates and updates one Product, skips the invalid row, and reloads the same persisted outcome.
- [x] Imported Product Master fields produce the deterministic 35-point Product Master score through Aggregate and Quality APIs.
- [x] Drive folder ensure returns 201 then idempotent 200 and the UI persists exactly six folder roles across reload.
- [x] All seven existing Playwright scenarios and the new Connector scenario pass against the real Compose stack.
- [x] V1–V7 cold/populated/repeat/checksum/Hibernate tests and all Backend/Frontend regressions pass.
- [x] npm production audit, Gitleaks, actionlint, Docker Compose config/cold start, smoke, Push CI, and PR CI pass.
- [x] Exact-head Manager Decision is `APPROVE`.
- [ ] Merge and post-merge main CI pass.
- [ ] `milestone-2e-complete` and `stage-02-complete` tags point to the verified final main Commit.

## Known limitations

- Acceptance uses deterministic local/test Stub providers; no live Google credential, Shared Drive, or external Google API is exercised.
- Provider file IDs remain opaque and no direct Drive links are constructed.
- Existing Byte Buddy dynamic-agent and GitHub Actions Node.js compatibility warnings remain non-blocking.

## Local verification evidence

- Backend: 228 tests passed with zero failures/errors/skips; V1–V7 migration, canonical checksums, populated upgrade, repeat migration, Hibernate validation, transaction, trigger, actor, Audit, and provider regressions remained green.
- Frontend: lint, typecheck, 21 Vitest files / 127 tests, Next.js production build, and `npm audit --omit=dev` with zero vulnerabilities passed.
- Compose: isolated `aimcp2e5` production-image build and cold health completed in 454.5 seconds; Backend and same-origin health responses were `UP`; Flyway history listed 1, 2, 3, 4, 5, 6, 6.1, and 7 as successful.
- Playwright: the new Connector scenario passed alone, then all eight Chromium scenarios passed together against the real Compose stack in 1.0 minute.
- Security/workflow: actionlint 1.7.7 passed; Gitleaks 8.28.0 at the CI-pinned image digest scanned 63 commits and the working tree with no leaks; `git diff --check` passed.
- Environment note: local Java was 21.0.5 and local Node/npm were 24.14.0/11.9.0. The Repository-pinned Java 21.0.9+10.0.LTS and Node/npm 24.18.0/11.16.0 remain required Remote CI evidence.
- Two initial Connector E2E attempts exposed only assertion precision issues: the canonical validation copy is `productName is required`, and PostgreSQL normalizes timestamps to microsecond precision on reload. Runtime behavior was not changed; the contract assertions were corrected before the final 8/8 pass.
