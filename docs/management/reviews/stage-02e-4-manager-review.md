# Stage Gate Review — Milestone 2E-4 Connector UI

## Review identity

- Review date: 2026-08-09
- Reviewer: Codex Project Manager / Stage Gate Owner
- Repository: `poyinghuang/ai-commerce-marketing-platform`
- Branch: `codex/2e-4-connector-ui`
- Base Commit: `42b61411edb10c9f52a3c41adc5b345bb5d1d504`
- Reviewed Head Commit: `0cc18426b9ff9bfe451dcd3443c5d8e8449f8293`
- Pull Request: #40

## Scope reviewed

- Fixed same-origin BFF routes for Google Sheets template, Preview, query, Execute, and Product storage-folder operations.
- Google Sheets import UI and Product Assets-tab Drive folder UI, including loading, empty, error, conflict, archived, create, and reuse states.
- All 19 changed files and the complete Base-to-Head diff were reviewed.
- No backend, migration, workflow, dependency, credential, production access, generic proxy, or Stage 03 change is present.

## Architecture and contracts

- `BACKEND_INTERNAL_URL` is read only on the Next.js server. Browser-controlled origin, path, query, credential, actor, Cookie, Authorization, and provider configuration are not forwarded.
- Connector and storage-folder paths use exact UUID/path allowlists. Request bodies are bounded to 64 KiB, request IDs are validated, only approved response headers are exposed, and network failures are sanitized.
- Preview persists and renders the Backend snapshot contract. Execute forwards the current weak ETag and preserves Backend 409/412/428 status semantics with explicit reload recovery.
- Drive folder UI uses the idempotent existing GET/POST contract and renders the exact six managed folder roles. Archived Products cannot start folder creation.

## Impact

- Security: fixed upstream origin/path, header allowlists, bounded bodies, no Browser credential forwarding, and sanitized failures were verified.
- Data: no migration or backend mutation behavior changed. UI mutations use existing transactional API contracts.
- Production/external cost: no deployment, live Google credential, or live provider call is included.
- Contract: additive same-origin BFF routes and UI only; existing Product and Connector API contracts are unchanged.

## Verification executed

| Verification | Result | Evidence |
| --- | --- | --- |
| Git status/diff/log | Passed | Clean implementation Head; Base-to-Head diff check passed; expected 19 files |
| Backend | Passed | `mvnw.cmd test`: 225 tests, 0 failures/errors/skips |
| Migration/Hibernate | Passed | Existing V1–V7 migration/checksum/Hibernate regression remained green; no migration diff |
| Frontend | Passed | lint, typecheck, 21 test files / 127 tests, Next.js production build |
| Dependency audit | Passed | `npm audit --omit=dev`: 0 vulnerabilities |
| Compose/Smoke | Passed | isolated production-image cold start healthy; template, Preview, Execute, and Drive create/reuse/query smoke passed |
| Playwright | Passed | all seven existing Chromium scenarios against real Compose |
| Gitleaks/actionlint | Passed | pinned local tools and both Remote CI events |

## Remote CI

- Push Run `31304974207`: `quality-and-compose` and `secret-scan` passed at the reviewed Head.
- PR Run `31304976416`: `quality-and-compose` and `secret-scan` passed at the reviewed Head.
- Backend, Frontend, actionlint, Docker build/start, Browser E2E, Smoke, and Gitleaks actually executed.
- Non-blocking annotation: pinned GitHub Actions that target the Node.js 20 compatibility layer are forced by the runner to Node.js 24.

## Findings

None open. No CRITICAL, BLOCKING, or necessary MAJOR finding was identified.

## Known limitations

- CI and local flows use deterministic Stub providers and no real Google credential or API.
- The mixed create/update/error browser journey and final Stage 02 acceptance remain owned by 2E-5.
- Product folder UI intentionally shows opaque provider IDs rather than constructing direct Drive links.
- Existing Byte Buddy dynamic-agent and GitHub Actions compatibility warnings remain non-blocking.

## Stage Gate decision

- Decision: `APPROVE`
- Rationale: the approved additive UI/BFF scope is complete, required local and exact-head Remote CI evidence passed, security and API boundaries conform to the specification, and no blocking finding remains.
- Required next action: commit this approval record, pass Push and PR CI at the documentation Head, mark PR #40 Ready, squash merge, and verify post-merge `main` CI before starting 2E-5.
- Human approval required: `No`
- Merge allowed: `Yes`, after the approval-record Head passes required CI.
- Next Stage allowed: only after merge and post-merge `main` verification.

## Approval record

- Manager Review: Passed
- Manager Decision: APPROVE
- Approved Commit: `0cc18426b9ff9bfe451dcd3443c5d8e8449f8293`
- Approved CI Runs: Push `31304974207`; PR `31304976416`
