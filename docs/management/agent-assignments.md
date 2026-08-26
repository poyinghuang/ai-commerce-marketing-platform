# Agent assignments — remaining after Stage 07 FAKE

Effective: 2026-08-26
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V17 stay immutable. 7A, 7B, and 7C-2 added no migration. 7C-1 added additive V17 only. Later slices add V18+.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, Google Ads, LINE, TikTok, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 07 FAKE **close-out** |
| Branch | `codex/stage-07-closeout` |
| PR | Draft [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81) |
| Specification | PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) squash-merged at `2851003`; post-merge main CI Run `32928585609` passed |
| 7A | PR [#77](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/77) squash-merged at `eb2618d`; post-merge main CI Run `32940348609` passed |
| 7B | PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) squash-merged at `da01b13`; post-merge main CI Run `32944155884` passed |
| 7C-1 | PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) squash-merged at `f5faa28`; post-merge main CI Run `32971029793` passed |
| 7C-2 | PR [#80](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/80) squash-merged at `ed3f4cf`; post-merge main CI Run `32974606066` passed |
| Base | `ed3f4cf` (PR #80 squash merge) |
| Stage 06 | Closed — spec PR [#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72) at `d77b2e0`; runtime PR [#73](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/73) at `d22d80a`; close-out PR [#75](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/75) at `771f776`; post-merge main CI Run `32923254447` passed |
| Manager Decision | Not started for this close-out |
| Optional Meta paused proof | **Locked** until a separate human record |
| Next after this close-out | Separate human-gated specification (7D/7E LINE/TikTok or live Sheets/Drive/Meta Insights) |

## Assigned now (Stage 07 FAKE close-out)

| Agent | Duty | Status |
| --- | --- | --- |
| Documentation | Record 7C-2 merge, post-merge `main` CI, and Stage 07 FAKE close | Assigned |
| Project Manager | Idle until exact-head CI on Draft PR [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81) | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Architecture / Research / Backend / Frontend / QA / AI Workflow / Product Owner | Idle. Do not start 7D/7E or live APIs | Idle |

Human review required for this docs-only close-out: **No**, unless credentials, spend, a second live ads platform, paid provider, or Decision Engine auto-execute appears. **Yes** immediately before live Google Ads / LINE / TikTok / paid image provider / live Sheets / Drive / Meta Insights.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 07 Expansion spec | Architecture | Research, Documentation, Project Manager | No for FAKE docs-only; **Yes** if live ads or credentials appear | Stage 06 complete — **merged** PR #76 |
| 07A second image provider runtime | AI Workflow | Backend, QA, Project Manager | No for LOCAL/TEST bean swap | 07 spec merge — **merged** PR #77 |
| 07B second storage provider runtime | Backend | QA, Project Manager | No for LOCAL/TEST bean swap | 7A merged + post-merge CI — **merged** PR #78 |
| 07C-1 FAKE_GOOGLE adapter + V17 | Backend | Architecture, QA, Project Manager | No for FAKE key; **Yes** if Google Ads API/SDK appears | 7B merged + post-merge CI — **merged** PR #79 |
| 07C-2 Google FAKE UI | Frontend | Backend, QA, Project Manager | No for gated FAKE UI | 7C-1 merged + post-merge CI — **merged** PR #80 |
| 07 FAKE close-out | Documentation | Project Manager | No for docs-only close | 7C-2 merged + post-merge CI — **unlocked** |
| 4E optional Meta paused proof | Research + Project Manager | Backend, QA | **Yes** — credentials, test-account access | Separate human record; `META_TEST_DELIVERY` stays disabled |
| 07D/07E LINE / TikTok | Architecture | Research, Backend, Project Manager | **Yes** before a second live ads platform | Separate specification after Stage 07 FAKE close |
| Live Google Sheets / Drive read / Meta Insights | Architecture | Research, Backend, Project Manager | **Yes** — credentials and live APIs | Separate specification after Stage 07 FAKE close |

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

1. Stage 07C-2 runtime PR [#80](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/80) is squash-merged at `ed3f4cf`. Post-merge main CI Run `32974606066` passed.
2. Record Stage 07 FAKE close-out. Do not start 7D/7E, live Google Ads, or live Sheets/Drive/Meta Insights until a separate specification is approved, merged, and post-merge `main` CI has passed. Live ads still need a recorded human decision.
3. Stage 06 remains suggestion-only on `main`: unapproved recommendations must not execute. Approved recommendations also must not execute.
