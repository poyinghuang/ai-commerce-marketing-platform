# Agent assignments — remaining Stage 07

Effective: 2026-08-26
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V16 stay immutable. Stage 07 specification is docs-only. 7A and 7B add no migration. 7C-1 may add additive V17 only, as named in `docs/stages/stage-07-expansion.md`.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, Google Ads, LINE, TikTok, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 07 Expansion **specification** |
| Branch | `codex/stage-07-expansion-specification` |
| PR | Draft [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) |
| Base | `771f776` (PR #75 squash merge) |
| Stage 06 | Closed — spec PR [#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72) at `d77b2e0`; runtime PR [#73](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/73) at `d22d80a`; close-out PR [#75](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/75) at `771f776`; post-merge main CI Run `32923254447` passed |
| Stage 05 | Closed — spec PR [#69](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/69) at `3bbbc69`; runtime PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) at `9e0f4b4`; close-out PR [#71](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/71) at `3d3b7b3`; post-merge main CI Run `32795522589` passed |
| Stage 04 | Closed FAKE 4A–4E; tag `stage-04-complete`; post-merge main CI Run `32754399607` passed |
| Manager Decision | Not started for Stage 07 specification |
| Optional Meta paused proof | **Locked** until a separate human record |
| First runtime after spec | **7A** second FAKE image provider; 7B/7C locked |

## Assigned now (Stage 07 specification)

| Agent | Duty | Status |
| --- | --- | --- |
| Architecture | Author the FAKE expansion specification; no runtime | In progress |
| Research / Backend / Frontend / QA | Idle until the specification `APPROVE` + merge | Idle |
| Documentation | Keep Stage 07 spec, assignments, and Architecture aligned | In progress |
| Project Manager | Idle until a Stage 07 Draft PR + exact-head CI | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Product Owner / AI Workflow | Idle. Do not add live ads or auto-execute | Idle |

Human review required for this docs-only FAKE specification: **No**, unless credentials, spend, a second live ads platform, paid provider, or Decision Engine auto-execute appears. **Yes** immediately before live Google Ads / LINE / TikTok / paid image provider.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 07 Expansion spec | Architecture | Research, Documentation, Project Manager | No for FAKE docs-only; **Yes** if live ads or credentials appear | Stage 06 complete |
| 07A second image provider runtime | AI Workflow | Backend, QA, Project Manager | No for LOCAL/TEST bean swap | 07 spec `APPROVE` + merge |
| 07B second storage provider runtime | Backend | QA, Project Manager | No for LOCAL/TEST bean swap | 7A merged + post-merge CI |
| 07C-1 FAKE_GOOGLE adapter + V17 | Backend | Architecture, QA, Project Manager | No for FAKE key; **Yes** if Google Ads API/SDK appears | 7B merged + post-merge CI |
| 07C-2 Google FAKE UI | Frontend | Backend, QA, Project Manager | No for gated FAKE UI | 7C-1 merged + post-merge CI |
| 4E optional Meta paused proof | Research + Project Manager | Backend, QA | **Yes** — credentials, test-account access | Separate human record; `META_TEST_DELIVERY` stays disabled |
| 07D/07E LINE / TikTok | Architecture | Research, Backend, Project Manager | **Yes** before a second live ads platform | Separate specification after 7C-2 |

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

## Sequence after this assignment

1. Stage 06 close-out PR [#75](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/75) is squash-merged at `771f776`. Post-merge main CI Run `32923254447` passed.
2. Complete Stage 07 specification on `codex/stage-07-expansion-specification`. Do not start 7A runtime, V17, or live ads until the specification is approved, merged, and post-merge `main` CI has passed.
3. First runtime is 7A only. Live Google Ads / LINE / TikTok remain locked.
