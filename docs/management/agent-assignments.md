# Agent assignments — remaining Stage 08

Effective: 2026-08-27
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V17 stay immutable. Stage 08 specification is docs-only. 8A and 8B add no migration. 8C may add additive V18 only, as named in `docs/stages/stage-08-live-connector-reads.md`.
5. Deterministic `FAKE` / stub in `LOCAL`/`TEST` remains the default CI path. Opt-in live Sheets / Drive / Meta Insights require the flags in the Stage 08 spec plus a recorded human secret record. Real Google Ads, LINE, TikTok, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 08 Live Connector Reads **specification** |
| Branch | `codex/stage-08-live-connector-reads-specification` |
| PR | Not opened |
| Stage 07 | Closed FAKE — spec PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) at `2851003`; 7A PR [#77](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/77) at `eb2618d`; 7B PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) at `da01b13`; 7C-1 PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) at `f5faa28`; 7C-2 PR [#80](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/80) at `ed3f4cf`; close-out PR [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81) at `49b73d6`; post-merge main CI Run `32996644069` passed |
| Base | `49b73d6` (PR #81 squash merge) |
| Manager Decision | `ESCALATE_TO_HUMAN` until a recorded human test-resource and secret-handling decision; then `APPROVE` / `REQUEST_CHANGES` |
| Optional Meta paused proof | **Locked** until a separate human record |
| Next after this spec | 8A live Sheets runtime. 8B/8C stay locked. 7D/7E stay locked |

## Assigned now (Stage 08 specification)

| Agent | Duty | Status |
| --- | --- | --- |
| Architecture | Name 8A/8B/8C live-read contracts without production credentials | Assigned |
| Documentation | Keep Stage 08 gate headers and assignments aligned | Assigned |
| Research | Idle until 8A runtime; Graph/Sheets/Drive contracts are named in the spec | Idle |
| Backend / Frontend / QA | Idle until specification `APPROVE` + merge | Idle |
| Project Manager | Idle until a Draft PR + exact-head CI, then `ESCALATE_TO_HUMAN` | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Product Owner / AI Workflow | Idle. Do not add auto-execute or live ads writes | Idle |

Human review required for this specification: **Yes** — it authorizes live network paths and operator-supplied credentials (outside git). **Yes** immediately before live Google Ads / LINE / TikTok / paid image provider / production credentials / `META_TEST_DELIVERY`.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 07 FAKE close-out | Documentation | Project Manager | No for docs-only close | 7C-2 merged + post-merge CI — **merged** PR #81 |
| 08 Live connector reads spec | Architecture | Research, Documentation, Project Manager | **Yes** — credentials and live APIs | Stage 07 FAKE complete — **unlocked** |
| 08A live Sheets runtime | Backend | QA, Project Manager | **Yes** — Google ADC / test spreadsheet | 08 spec `APPROVE` + merge + human secret record |
| 08B live Drive folder ensure | Backend | QA, Project Manager | **Yes** — Drive root + ADC | 8A merged + post-merge CI |
| 08C live Meta Insights | Backend | Architecture, QA, Project Manager | **Yes** — Meta `ads_read` test token | 8B merged + post-merge CI |
| 4E optional Meta paused proof | Research + Project Manager | Backend, QA | **Yes** — credentials, test-account access | Separate human record; `META_TEST_DELIVERY` stays disabled |
| 07D/07E LINE / TikTok | Architecture | Research, Backend, Project Manager | **Yes** before a second live ads platform | Separate specification |
| Live Google Ads | Architecture | Research, Backend, Project Manager | **Yes** — credentials and spend risk | Separate specification |

## Human-only list (do not wait for these during FAKE slices)

Escalate immediately; do not merge around them:

- Real Meta / Google / LINE / TikTok / provider credentials, production access, or spend
- Authentication, RBAC, Tenant, or security-model change
- Billing, paid APIs, or material cost
- Destructive or irreversible migration / production data work
- Breaking a merged API contract or changing System of Record
- Force-push of `main`, rewriting history, or deleting official tags
- Critical security finding
- Decision Engine auto-execute, or Stage 06 approve/reject that calls pause, resume, budget, refresh, publication, or AI execute
- Putting Google or Meta secrets into git or GitHub Actions for this Stage

## Sequence after this assignment

1. Stage 07 FAKE close-out PR [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81) is squash-merged at `49b73d6`. Post-merge main CI Run `32996644069` passed.
2. Complete the Stage 08 specification. Do not start 8A runtime, 7D/7E, or live Google Ads until this spec is approved, merged, and post-merge `main` CI has passed, and the human secret record exists.
3. Stage 06 remains suggestion-only on `main`. Compose/CI stay stub/FAKE until an operator opts into 8A/8B/8C flags locally.
