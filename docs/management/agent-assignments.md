# Agent assignments — remaining Stage 04–07

Effective: 2026-08-24
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V15 stay immutable. Stage 4E adds no migration. Later slices add V16+.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 4E deterministic acceptance **specification** |
| Branch | `codex/stage-04e-deterministic-acceptance-specification` |
| PR | [#67](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/67) (Draft) |
| Base | `2c2ab07` (PR #66 squash merge) |
| Stage 4D runtime | Merged at `2c2ab07`; post-merge main CI Run `32744056926` passed |
| Stage 4D spec | Merged at `aa90804`; post-merge main CI Run `32540462993` passed |
| Manager Decision | Pending specification review |
| Optional Meta proof / 05–07 | **Locked** |

## Assigned now (Stage 4E specification)

| Agent | Duty | Status |
| --- | --- | --- |
| QA | Lock the eight parent acceptance themes, named tests, and 4E runtime gaps | In progress |
| Architecture | Confirm no new API/flag/migration and no credential path | Support |
| Documentation | 4D closeout, parent Stage 04 header, this assignment | In progress |
| Project Manager | Idle until Draft PR + exact-head CI | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Backend / Frontend | Idle for 4E runtime until this specification merges | Idle |
| Product Owner / AI Workflow | Idle. Do not open Dashboard or Decision Engine | Idle |

Human review required for this 4E FAKE specification: **No**, unless the Manager finds an escalation trigger (credentials, Meta smoke, spend, Auth/RBAC/Tenant).

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 4D spec | Architecture | Research, Project Manager | No for FAKE read-only metrics spec | 4C merged |
| 4D runtime | Backend | Frontend, QA, Review, Project Manager | No for FAKE entity-level reads | 4D spec `APPROVE` + merge |
| 4E spec | QA | Architecture, Documentation, Project Manager | No for FAKE acceptance spec | 4D merged + post-merge CI |
| 4E runtime | QA | Backend, Frontend, Project Manager | No | 4E spec `APPROVE` + merge |
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

1. Draft PR [#67](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/67) is open on `codex/stage-04e-deterministic-acceptance-specification`. Wait for exact-head Push and Pull Request CI.
2. Exact-head Push and Pull Request `quality-and-compose` plus `secret-scan` must pass before Manager Review.
3. Do not start Stage 4E runtime until this specification merges and post-merge `main` CI passes. Do not start optional Meta proof or Stage 05.
