# Milestone 2E-4 — Connector UI

## Delivery status

- Branch：`codex/2e-4-connector-ui`
- Base Commit：`42b61411edb10c9f52a3c41adc5b345bb5d1d504`
- Implementation：Complete
- Local Verification：Passed
- Commit／Push／Remote CI：Passed — implementation Commit `0cc18426b9ff9bfe451dcd3443c5d8e8449f8293`; Push Run `31304974207`; PR Run `31304976416`
- Manager Review／Decision：Passed — `APPROVE`
- Human Review Required：No
- Merge：Passed — PR #40, Squash Commit `2145764f5dd353a760d74e31972c5874bef5b6a3`
- 2E-5：Not started

## Approved scope

- Fixed same-origin Next.js Route Handlers for the existing Google Sheets template, Preview, query, Execute, and Product storage-folder endpoints.
- `/connectors/google-sheets` source form, immutable Preview result table, counts, validation/execution errors, explicit Execute, ETag conflict recovery, loading/empty/error states, and CSV template link.
- Product Assets tab Drive folder state, idempotent create action, exact six roles, archived Product read-only state, and recovery UI.
- Frontend unit/integration tests, production build, real Compose regression, and existing Playwright regression.

## Security and contract boundaries

- `BACKEND_INTERNAL_URL` is server-only. Browser requests cannot control the upstream origin, path, query, Google credential, actor, Cookie, Authorization, or provider configuration.
- Connector and storage routes use exact UUID/path allowlists and no query forwarding. Only `If-Match` and a validated `X-Request-ID` can be forwarded.
- Request bodies remain bounded to 64 KiB. Backend status/body and only approved response headers are preserved; timeout/network errors are sanitized to `BACKEND_UNAVAILABLE`.
- Execute requires the Preview ETag. HTTP 409/412/428 produce distinct recovery UI and reload the persisted job before retry.
- No new Migration, backend domain behavior, live credential, production access, generic proxy, polling, two-way sync, file transfer/delete, or Stage 03 functionality is included.

## Acceptance checklist

- [x] Exact BFF routes reach only configured Backend paths and reject arbitrary URLs/paths/query/header injection.
- [x] CSV template downloads through same origin with safe response headers.
- [x] Preview renders CREATE/UPDATE/INVALID rows, counts, validation errors, and disables Execute when no valid row exists.
- [x] Execute forwards the current ETag, renders partial/final results, and provides 409/412/428 recovery.
- [x] Provider configuration, permission, quota, timeout, empty Sheet, invalid header, and row failures have actionable UI states.
- [x] Drive state handles absent, connected, create/reuse, archived read-only, and retry states and renders all six folder roles.
- [x] Frontend lint, typecheck, tests, production build, npm audit, Backend regression, Compose, Smoke, existing Playwright, Gitleaks, and actionlint pass locally/remotely.
- [x] Exact-head Manager Decision is `APPROVE`.
- [x] Merge and post-merge main CI pass before 2E-5 (Run `31305543856`).

## Known limitations

- CI and local flows use deterministic Stub providers; no real Google credential or API is exercised.
- The full mixed-row Connector browser journey and final Stage 02 acceptance remain owned by 2E-5.
- Product folder UI displays opaque provider IDs; direct Drive links are intentionally not constructed because provider IDs and deployment/account context are server-controlled.

## Local verification evidence

- Backend：225 tests passed; V1–V7 migration/checksum/Hibernate and all prior Stage 02 regressions remained green.
- Frontend：lint, typecheck, 21 test files / 127 tests, production build, and `npm audit --omit=dev` with 0 vulnerabilities passed on Node 24.18.0 / npm 11.6.0.
- Compose：isolated `aimcp2e4` production-image build and cold start completed healthy in 220 seconds.
- Smoke：Connector page/template 200; Preview 201 with one valid Stub row; Execute 200 and `COMPLETED` with one Product created; Drive ensure returned 201 then 200, GET 200, and six role IDs.
- Playwright：all seven existing Chromium regression scenarios passed against the real Compose stack.
- Exact-head Manager Gate, merge, and post-merge main verification passed. Milestone 2E-4 is complete after this closeout is merged and verified.
