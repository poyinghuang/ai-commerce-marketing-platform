# Agent assignments — remaining Stage 04–07

Effective: 2026-08-20  
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V13 stay immutable. Stage 4C may add V14 only. Later slices add V15+.
5. Deterministic `FAKE` in `LOCAL`/`TEST` remains the default execution path. Real Meta, credentials, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen until a recorded human decision.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 4C Ad creative publication **runtime** |
| Branch | `codex/stage-04c-ad-creative-publication-runtime` |
| PR | [#64](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/64) (Draft) |
| Head | `7139b94077a0a55b84e80d4062e7ace97a2a9576` (integrity `fd7e19c`) |
| Manager Decision | Cycle 2 `REQUEST_CHANGES` — remaining `QA-PW-03`, `4C-RT-002`–`005`, `R-4C-01` |
| Base | `c321dc0124375e13fd09785b0c827326e996207f` |
| Stage 4D | **Locked** until 4C `APPROVE`, merge, and post-merge `main` CI |

## Assigned now (Stage 4C runtime)

| Agent | Duty | Status |
| --- | --- | --- |
| Project Manager | Cycle 2 complete: `REQUEST_CHANGES` on `7139b94`. Re-review after a new Head with green exact-head CI | Standby for cycle 3 |
| Review | Cycle 1 findings R-4C-02–10 closed on `fd7e19c`; R-4C-01 still open | Standby |
| QA | Exact-head E2E failed QA-PW-03 (`getByRole('alert')` vs Next.js announcer) | Assigned |
| Backend | Remaining BLOCKING: 4C-RT-002–005 / R-4C-01 (legacy finalize, races, Audit content, provider-outcome + pause UI) | **In progress** |
| Frontend | QA-PW-03 alert locator; 4C-RT-005 pause / malformed If-Match / weak ETag in Playwright | **In progress** |
| Documentation | Update Stage Gate headers after the Manager Decision, not before | Standby |
| Product Owner / Architecture / Research / AI Workflow | Idle for 4C runtime | Idle |

Human review required for this 4C FAKE runtime: **No**, unless the Manager finds an escalation trigger.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 4C runtime closeout | Project Manager | Backend, Frontend, QA, Docs | No, unless escalation | CI green + Manager `APPROVE` |
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

1. Review + QA produce evidence.
2. Project Manager records the Gate Decision on the exact Head.
3. If `REQUEST_CHANGES`: Backend/Frontend fix on this branch; re-review the new Head.
4. If `APPROVE`: Manager may merge after required CI; then verify `main`.
5. Only then may Architecture open Stage 4D specification. Stage 05–07 stay locked.
