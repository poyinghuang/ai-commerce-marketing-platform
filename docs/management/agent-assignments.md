# Agent assignments — remaining Stage 08

Effective: 2026-08-30
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. Flyway V1–V17 stay immutable. 8C may add additive V18 only, as named in `docs/stages/stage-08-live-connector-reads.md`.
5. Deterministic `FAKE` / stub in `LOCAL`/`TEST` remains the default CI path. Opt-in live Insights requires `platform.stage8.insights.live=true` plus `META_TEST_ACCESS_TOKEN` outside git. Real Google Ads, LINE, TikTok, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 08 **8C** opt-in LOCAL live Meta Insights + delivery read |
| Branch | `codex/stage-08c-live-insights` |
| PR | Draft [#85](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/85) |
| Specification | PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) squash-merged at `21aca71`; post-merge main CI Run `33090522880` passed |
| Base | `14e03d0` (PR [#84](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/84) squash merge); 8B post-merge main CI Run `33264670377` passed |
| Stage 07 | Closed FAKE — close-out PR [#81](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/81) at `49b73d6`; post-merge main CI Run `32996644069` passed |
| Manager Decision | Pending for 8C runtime |
| Optional Meta paused proof | **Locked** until a separate human record |
| Next after 8C | Live Google Ads stays locked. 7D/7E stay locked |

## Assigned now (8C runtime)

| Agent | Duty | Status |
| --- | --- | --- |
| Backend | Deliver `LiveMetaInsightsReadAdapter` + additive V18 + META account initializer; keep writes on FAKE | In progress |
| QA | MockRest mapping, profile matrix, V18 cold/upgrade/collision, META write reject, FAKE 4D on default path | In progress |
| Documentation | Keep Stage 08 gate headers and 8C completion report aligned | In progress |
| Project Manager | Idle until Draft PR exact-head CI, then Manager Review | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Architecture / Frontend / AI Workflow / Product Owner | Idle. Do not start live ads | Idle |

Human review required for this opt-in LOCAL Insights flag: **Yes** before using a live Meta `ads_read` token. **No** additional product meeting for the stub-default CI path itself. **Yes** immediately before live Google Ads / LINE / TikTok / `META_TEST_DELIVERY` / production credentials.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 08 Live connector reads spec | Architecture | Research, Documentation, Project Manager | **Yes** — credentials and live APIs | Stage 07 FAKE complete — **merged** PR #82 |
| 08A live Sheets runtime | Backend | QA, Project Manager | **Yes** — Google ADC / test spreadsheet before merge | 08 spec merge + post-merge CI — **merged** PR #83 |
| 08B live Drive folder ensure | Backend | QA, Project Manager | **Yes** — Drive root + ADC | 8A merged + post-merge CI — **merged** PR #84 |
| 08C live Meta Insights | Backend | Architecture, QA, Project Manager | **Yes** — Meta `ads_read` test token | 8B merged + post-merge CI — **unlocked** |
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
2. 8A runtime PR [#83](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/83) is squash-merged at `91a8297`. Post-merge main CI Run `33143102962` passed.
3. 8B runtime PR [#84](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/84) is squash-merged at `14e03d0`. Post-merge main CI Run `33264670377` passed.
4. Open 8C Draft PR. Wait exact-head CI. Do not merge without Manager `APPROVE`. Do not start live Google Ads, LINE/TikTok, or `META_TEST_DELIVERY`.
5. Compose/CI stay `platform.stage8.insights.live=false`. Operators opt in locally with `META_TEST_ACCESS_TOKEN` outside the repository.
