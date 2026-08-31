# Agent assignments — after Stage 08

Effective: 2026-08-31
Authority: root `AGENTS.md`, `docs/agents/AGENTS.md`, `docs/management/manager-policy.md`, `docs/management/escalation-policy.md`

The Project Manager Agent is the sole Stage Gate Owner. Other agents deliver evidence; they cannot `APPROVE`, merge, or unlock a later Stage. Human intervention is **off by default**. Use `ESCALATE_TO_HUMAN` only when an escalation-policy trigger is actually present.

## Operating rules

1. One Stage or Milestone at a time. No dependent work until merge and post-merge `main` CI pass.
2. Manager Decision is only `APPROVE`, `REQUEST_CHANGES`, or `ESCALATE_TO_HUMAN`.
3. Agents must not lower acceptance, skip required checks, or treat unrun tests as Passed.
4. When Automatic merge conditions in `docs/management/manager-policy.md` hold, Manager squash-merges without waiting for Owner to confirm merge, then starts the next authorized stage after post-merge CI.
5. Flyway V1–V18 stay immutable. Later work may add additive V19+ only after a new approved specification.
6. Deterministic `FAKE` / stub in `LOCAL`/`TEST` remains the default CI path. Opt-in live Sheets, Drive, and Insights stay off in Compose/CI. Real Google Ads, LINE, TikTok, spend, production, Auth/RBAC/Tenant, and System of Record changes stay frozen.

## Current gate

| Item | Value |
| --- | --- |
| Gate | Stage 08 Live Connector Reads **close-out** |
| Branch | `docs/stage-08-closeout` |
| PR | This close-out Draft |
| Specification | PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) squash-merged at `21aca71`; post-merge main CI Run `33090522880` passed |
| 8A | PR [#83](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/83) at `91a8297`; post-merge main CI Run `33143102962` passed |
| 8B | PR [#84](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/84) at `14e03d0`; post-merge main CI Run `33264670377` passed |
| 8C | PR [#85](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/85) at `5a0cf95`; post-merge main CI Run `33326133854` passed |
| Manager authority | PR [#86](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/86) at `93c363e`; post-merge main CI Run `33346538110` passed |
| Manager Decision | Pending for this docs-only close-out |
| Next after close-out | Separate human-gated specification. Live Google Ads / 7D/7E stay locked |

## Assigned now (08 close-out)

| Agent | Duty | Status |
| --- | --- | --- |
| Documentation | Record 8A–8C merge + post-merge CI and close Stage 08 | In progress |
| Project Manager | Idle until this Draft PR exact-head CI, then Manager Review | Idle |
| Review | Idle until Manager Review is requested | Idle |
| Backend / QA / Architecture / Frontend / AI Workflow / Product Owner | Idle. Do not start live ads or 7D/7E | Idle |

Human review required: **No** additional product meeting for this docs-only close-out. **Yes** immediately before live Google Ads / LINE / TikTok / `META_TEST_DELIVERY` / production credentials.

## Roster for later Stages

Do not start a later row until the previous row is merged and post-merge `main` CI has passed.

| Stage | Lead | Supporting agents | Human required | Unlock condition |
| --- | --- | --- | --- | --- |
| 08 Live connector reads spec | Architecture | Research, Documentation, Project Manager | **Yes** — credentials and live APIs | Stage 07 FAKE complete — **merged** PR #82 |
| 08A live Sheets runtime | Backend | QA, Project Manager | **Yes** — Google ADC / test spreadsheet before merge | 08 spec merge + post-merge CI — **merged** PR #83 |
| 08B live Drive folder ensure | Backend | QA, Project Manager | **Yes** — Drive root + ADC | 8A merged + post-merge CI — **merged** PR #84 |
| 08C live Meta Insights | Backend | Architecture, QA, Project Manager | **Yes** — Meta `ads_read` test token | 8B merged + post-merge CI — **merged** PR #85 |
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
4. 8C runtime PR [#85](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/85) is squash-merged at `5a0cf95`. Post-merge main CI Run `33326133854` passed.
5. Manager-authority PR [#86](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/86) is squash-merged at `93c363e`. Post-merge main CI Run `33346538110` passed.
6. Merge this close-out after exact-head CI and Manager `APPROVE`. Do not start live Google Ads, LINE/TikTok, or `META_TEST_DELIVERY`.
7. Compose/CI stay stub/FAKE. Operators opt in locally with ADC / `GOOGLE_DRIVE_ROOT_FOLDER_ID` / `META_TEST_ACCESS_TOKEN` outside the repository.
