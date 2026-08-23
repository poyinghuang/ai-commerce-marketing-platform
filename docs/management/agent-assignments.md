# Agent assignments — remaining Stage 04–07

Effective: 2026-08-23
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V14 stay immutable. Stage 4D may add V15. Later slices add V16+.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 4D delivery and metrics **runtime** |
| Branch | `codex/stage-04d-delivery-metrics-runtime` |
| PR | [#66](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/66) (Draft) |
| Base | `aa90804` (PR #65 squash merge) |
| Stage 4D spec | Merged at `aa90804`; post-merge main CI Run `32540462993` passed |
| Stage 4C | Merged at `acb833d`; post-merge main CI Run `32504910043` passed |
| Manager Decision | Pending runtime review |
| Stage 4E / 05–07 | **Locked** |

## Assigned now (Stage 4D runtime)

| Agent | Duty | Status |
| --- | --- | --- |
| Backend | V15, ports, FAKE read adapter, GET/refresh services, public errors | In progress |
| Frontend | BFF `/api/platform-entities/...` and additive `/platforms/meta` panel | In progress |
| QA | Backend/BFF/UI/Playwright acceptance for GET, fingerprint replay, empty POST, zero auto-refresh | In progress |
| Documentation | Runtime completion report and gate headers | In progress |
| Project Manager | Idle until Draft PR + exact-head CI | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Product Owner / AI Workflow | Idle. Do not open Dashboard or Decision Engine | Idle |

Human review required for this 4D FAKE runtime: **No**, unless the Manager finds an escalation trigger (credentials, scheduler, spend, Auth/RBAC/Tenant).

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 4D spec | Architecture | Research, Project Manager | No for FAKE read-only metrics spec | 4C merged |
| 4D runtime | Backend | Frontend, QA, Review, Project Manager | No for FAKE entity-level reads | 4D spec `APPROVE` + merge |
| 4E deterministic acceptance | QA | Backend, Frontend, Project Manager | No | 4D merged |
| 4E optional Meta paused proof | Research + Project Manager | Backend, QA | **Yes** — credentials, test-account access | Separate human record; `META_TEST_DELIVERY` stays disabled |
| 05 Dashboard | Frontend | Backend, QA, Project Manager | No for ops UI over existing reads | Stage 04 complete (`stage-04-complete`) |
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

1. Draft PR [#66](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/66) is open on `codex/stage-04d-delivery-metrics-runtime`. Wait for exact-head Push and Pull Request CI.
2. Exact-head Push and Pull Request `quality-and-compose` plus `secret-scan` must pass before Manager Review.
3. Do not start Stage 4E until this runtime merges and post-merge `main` CI passes.
