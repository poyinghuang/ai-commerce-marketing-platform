# Agent assignments — remaining Stage 05–07

Effective: 2026-08-25
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V15 stay immutable. Stage 05 adds no migration. Later slices add V16+.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 05 Dashboard **runtime** |
| Branch | `codex/stage-05-dashboard-runtime` |
| PR | [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) (Draft) |
| Base | `3bbbc69` (PR #69 squash merge) |
| Stage 05 spec | Closed — PR [#69](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/69) at `3bbbc69`; post-merge main CI Run `32759493087` passed |
| Stage 04 | Closed FAKE 4A–4E; tag `stage-04-complete`; post-merge main CI Run `32754399607` passed |
| Manager Decision | Pending runtime review |
| Stage 06 / optional Meta proof | **Locked** until this runtime `APPROVE` + merge + post-merge `main` CI |

## Assigned now (Stage 05 runtime)

| Agent | Duty | Status |
| --- | --- | --- |
| Frontend | `/dashboard` workbench, fail-closed BFF, home link, Playwright zero-POST + Stage 03D confirm | In progress |
| Backend | Read-only `com.aicommerce.platform.dashboard` GETs, campaign-grain KPI, profile gates | In progress |
| QA | GET/KPI/zero-adapter/zero-POST/approve-reject proof on the runtime PR | In progress |
| Documentation | Gate, assignments, README, implementation completion | In progress |
| Project Manager | Idle until Draft PR + exact-head CI | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Product Owner / AI Workflow | Idle. Do not open Decision Engine | Idle |

Human review required for this FAKE Dashboard runtime: **No**, unless the Manager finds an escalation trigger (credentials, Meta smoke, spend, Auth/RBAC/Tenant, Decision Engine auto-execute, V16).

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 05 Dashboard spec | Architecture | Frontend, Documentation, Project Manager | No for ops UI over existing reads | Stage 04 complete (`stage-04-complete`) |
| 05 Dashboard runtime | Frontend | Backend, QA, Review, Project Manager | No for ops UI over existing reads | 05 spec `APPROVE` + merge |
| 4E optional Meta paused proof | Research + Project Manager | Backend, QA | **Yes** — credentials, test-account access | Separate human record; `META_TEST_DELIVERY` stays disabled |
| 06 Decision Engine | AI Workflow | Architecture, Backend, Frontend, QA, Project Manager | No for suggestion-only; **Yes** if any auto-execute path appears | Stage 05 complete |
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

## Sequence after this assignment

1. Draft PR [#70](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/70) is open on `codex/stage-05-dashboard-runtime`.
2. Exact-head Push and Pull Request `quality-and-compose` plus `secret-scan` must pass before Manager Review.
3. Do not start optional Meta proof or Stage 06 from this PR.
