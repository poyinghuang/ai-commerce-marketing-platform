# Agent assignments — remaining Stage 06–07

Effective: 2026-08-25
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V15 stay immutable. Stage 05 added no migration. Later slices add V16+.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 06 Decision Engine **specification** |
| Branch | Not started |
| PR | — |
| Base | `9e0f4b4` (PR #70 squash merge) |
| Stage 05 | Closed — spec PR [#69](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/69) at `3bbbc69`; runtime PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) at `9e0f4b4`; post-merge main CI Run `32792642634` passed |
| Stage 04 | Closed FAKE 4A–4E; tag `stage-04-complete`; post-merge main CI Run `32754399607` passed |
| Manager Decision | Not started for Stage 06 specification |
| Optional Meta paused proof | **Locked** until a separate human record |

## Assigned now (Stage 06 specification)

| Agent | Duty | Status |
| --- | --- | --- |
| AI Workflow / Architecture | Idle until a Stage 06 specification branch is opened | Idle |
| Backend / Frontend / QA | Idle until the specification `APPROVE` + merge | Idle |
| Documentation | Record Stage 05 close-out; do not invent Decision Engine types yet | Idle |
| Project Manager | Idle until a Stage 06 Draft PR + exact-head CI | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Product Owner | Idle. Do not add auto-execute | Idle |

Human review required for a FAKE suggestion-only Stage 06 specification: **No**, unless the Manager finds an escalation trigger (credentials, Meta smoke, spend, Auth/RBAC/Tenant, Decision Engine auto-execute, V16). **Yes** immediately if any auto-execute path appears.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 05 Dashboard spec | Architecture | Frontend, Documentation, Project Manager | No for ops UI over existing reads | Stage 04 complete (`stage-04-complete`) |
| 05 Dashboard runtime | Frontend | Backend, QA, Review, Project Manager | No for ops UI over existing reads | 05 spec `APPROVE` + merge |
| 4E optional Meta paused proof | Research + Project Manager | Backend, QA | **Yes** — credentials, test-account access | Separate human record; `META_TEST_DELIVERY` stays disabled |
| 06 Decision Engine spec | AI Workflow | Architecture, Documentation, Project Manager | No for suggestion-only; **Yes** if any auto-execute path appears | Stage 05 complete |
| 06 Decision Engine runtime | AI Workflow | Architecture, Backend, Frontend, QA, Project Manager | No for suggestion-only; **Yes** if any auto-execute path appears | 06 spec `APPROVE` + merge |
| 07 Expansion | Architecture | Research, Backend, Project Manager | **Yes** before a second live ads platform or paid provider | Stage 06 complete |

## Human-only list (do not wait for these during FAKE slices)

Escalate immediately; do not merge around them:

- Real Meta / Google / provider credentials, production access, or spend
- Authentication, RBAC, Tenant, or security-model change
- Billing, paid APIs, or material cost
- Destructive or irreversible migration / production data work
- Breaking a merged API contract or changing System of Record
- Force-push of `main`, rewriting history, or deleting official tags
- Critical security finding
- Decision Engine auto-execute (budget change, pause, creative swap without a human confirm)

## Sequence after this assignment

1. Stage 05 runtime PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) is squash-merged at `9e0f4b4`. Post-merge main CI Run `32792642634` passed.
2. Do not start Stage 06 runtime or optional Meta proof until a Stage 06 specification is approved and merged.
3. Stage 06 remains suggestion-only: unapproved recommendations must not execute.
