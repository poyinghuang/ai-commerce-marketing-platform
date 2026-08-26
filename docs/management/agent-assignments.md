# Agent assignments — remaining Stage 07

Effective: 2026-08-26
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V15 stay immutable. Stage 05 added no migration. Stage 06 specification was docs-only. Stage 06 runtime added additive V16 recommendation tables only. Stage 07 specification is docs-only. Later slices add V17+.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 07 Expansion **specification** |
| Branch | Not started |
| PR | Draft [#75](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/75) |
| Base | `d22d80a` (PR #73 squash merge) |
| Stage 06 | Closed — spec PR [#72](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/72) at `d77b2e0`; runtime PR [#73](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/73) at `d22d80a`; post-merge main CI Run `32914761189` passed |
| Stage 05 | Closed — spec PR [#69](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/69) at `3bbbc69`; runtime PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) at `9e0f4b4`; close-out PR [#71](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/71) at `3d3b7b3`; post-merge main CI Run `32795522589` passed |
| Stage 04 | Closed FAKE 4A–4E; tag `stage-04-complete`; post-merge main CI Run `32754399607` passed |
| Manager Decision | Not started for Stage 07 specification |
| Optional Meta paused proof | **Locked** until a separate human record |

## Assigned now (Stage 07 specification)

| Agent | Duty | Status |
| --- | --- | --- |
| Architecture | Idle until a Stage 07 specification branch is opened | Idle |
| Research / Backend / QA | Idle until the specification `APPROVE` + merge | Idle |
| Documentation | Record Stage 06 close-out; do not invent a second ads platform | Idle |
| Project Manager | Idle until a Stage 07 Draft PR + exact-head CI | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Product Owner / AI Workflow | Idle. Do not add auto-execute or live ads | Idle |

Human review required for a docs-only Stage 07 specification: **No**, unless credentials, spend, a second live ads platform, or Decision Engine auto-execute appears. **Yes** immediately before a second live ads platform or paid provider.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 05 Dashboard spec | Architecture | Frontend, Documentation, Project Manager | No for ops UI over existing reads | Stage 04 complete (`stage-04-complete`) |
| 05 Dashboard runtime | Frontend | Backend, QA, Review, Project Manager | No for ops UI over existing reads | 05 spec `APPROVE` + merge |
| 4E optional Meta paused proof | Research + Project Manager | Backend, QA | **Yes** — credentials, test-account access | Separate human record; `META_TEST_DELIVERY` stays disabled |
| 06 Decision Engine spec | AI Workflow | Architecture, Documentation, Project Manager | No for suggestion-only; **Yes** if auto-execute or execute-on-approve appears | Stage 05 complete |
| 06 Decision Engine runtime | AI Workflow | Architecture, Backend, Frontend, QA, Project Manager | No for suggestion-only; **Yes** if auto-execute or execute-on-approve appears | 06 spec `APPROVE` + merge |
| 07 Expansion spec | Architecture | Research, Documentation, Project Manager | **Yes** before a second live ads platform or paid provider | Stage 06 complete |
| 07 Expansion runtime | Architecture | Research, Backend, Project Manager | **Yes** before live ads | 07 spec `APPROVE` + merge |

## Human-only list (do not wait for these during FAKE slices)

Escalate immediately; do not merge around them:

- Real Meta / Google / provider credentials, production access, or spend
- Authentication, RBAC, Tenant, or security-model change
- Billing, paid APIs, or material cost
- Destructive or irreversible migration / production data work
- Breaking a merged API contract or changing System of Record
- Force-push of `main`, rewriting history, or deleting official tags
- Critical security finding
- Decision Engine auto-execute, or Stage 06 approve/reject that calls pause, resume, budget, refresh, publication, or AI execute

## Sequence after this assignment

1. Stage 06 runtime PR [#73](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/73) is squash-merged at `d22d80a`. Post-merge main CI Run `32914761189` passed.
2. Do not start Stage 07 runtime, live ads, or optional Meta proof until a Stage 07 specification is approved and merged. Live ads still need a recorded human decision.
3. Stage 06 remains suggestion-only on `main`: unapproved recommendations must not execute. Approved recommendations also must not execute.
