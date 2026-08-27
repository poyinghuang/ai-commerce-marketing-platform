# Agent assignments — remaining Stage 08

Effective: 2026-08-28
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V17 stay immutable. 8A and 8B add no migration. 8C may add additive V18 only, as named in `docs/stages/stage-08-live-connector-reads.md`.
5. Deterministic `FAKE` / stub in `LOCAL`/`TEST` remains the default CI path. Opt-in live Sheets requires `platform.sheets.provider=google` plus operator ADC outside git. Real Google Ads, LINE, TikTok, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 08 **8A** opt-in LOCAL live Sheets |
| Branch | `codex/stage-08a-live-sheets` |
| PR | Not opened |
| Specification | PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) squash-merged at `21aca71`; post-merge main CI Run `33090522880` passed |
| Base | `21aca71` (PR #82 squash merge) |
| Stage 07 | Closed FAKE — close-out PR [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81) at `49b73d6`; post-merge main CI Run `32996644069` passed |
| Manager Decision | Not started for 8A runtime |
| Optional Meta paused proof | **Locked** until a separate human record |
| Next after 8A | 8B live Drive folder ensure. 8C stays locked |

## Assigned now (8A runtime)

| Agent | Duty | Status |
| --- | --- | --- |
| Backend | Deliver `platform.sheets.provider=google` on LOCAL/TEST without changing Preview/Execute | In progress |
| QA | Profile matrix, MockRest sanitization, existing 2E execute tests on stub | In progress |
| Documentation | Keep Stage 08 gate headers and 8A completion report aligned | In progress |
| Project Manager | Idle until a 8A Draft PR + exact-head CI | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Architecture / Frontend / AI Workflow / Product Owner | Idle. Do not start 8B/8C or live ads | Idle |

Human review required for this opt-in LOCAL Sheets flag: **Yes** before merge if a live spreadsheet/ADC record is still missing, or if credentials appear in git/CI. **No** additional product meeting for the stub-default CI path itself. **Yes** immediately before live Google Ads / LINE / TikTok / Drive 8B / Insights 8C / production credentials.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 08 Live connector reads spec | Architecture | Research, Documentation, Project Manager | **Yes** — credentials and live APIs | Stage 07 FAKE complete — **merged** PR #82 |
| 08A live Sheets runtime | Backend | QA, Project Manager | **Yes** — Google ADC / test spreadsheet before merge | 08 spec merge + post-merge CI — **unlocked** |
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

1. Stage 08 specification PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) is squash-merged at `21aca71`. Post-merge main CI Run `33090522880` passed.
2. Complete 8A runtime. Do not start 8B/8C, live Google Ads, or LINE/TikTok until 8A is approved, merged, and post-merge `main` CI has passed.
3. Compose/CI stay `platform.sheets.provider=stub`. Operators opt in locally with ADC outside the repository.
