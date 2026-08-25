# Voice Progress Reporting — Repository Assessment and Integration Plan

> Governance enhancement. This is not a product Stage 01–07 slice and does not unlock Stage 07.
> Assessment date: 2026-08-25
> Assessor: Project Manager / Stage Gate Owner
> Base: `origin/main` at `d77b2e043e97179e5235ee50c68677757f36bd63`

## Manager Decision (plan)

- Decision: `APPROVE`
- Human Review Required: `No`
- Human Escalation: `None`
- Implementation may start without Project Owner confirmation.

Rationale: this work adds Owner-facing spoken summaries and machine-readable progress state. It does not change production business logic, Authentication / RBAC / Tenant, System of Record, Flyway, billing, secrets, or the Manager Gate enum. TTS and notification stay optional adapters. A scheduled GitHub Action, if added, stays read-only and must not auto-commit. Existing `ci.yml` required jobs remain authoritative for product merge.

---

## 1. Existing Project Governance

Repository delivery is Stage / Milestone based. Root `AGENTS.md` is the highest agent instruction. Project Manager is Lead Reviewer, Stage Gate Owner, and Integration Owner.

Authoritative policy:

- `docs/management/manager-policy.md`
- `docs/management/escalation-policy.md`
- `docs/management/stage-gate-template.md`
- `docs/management/agent-assignments.md`
- `docs/agents/AGENTS.md`
- `docs/agents/project-manager.md`

Manager Decision remains exactly:

- `APPROVE`
- `REQUEST_CHANGES`
- `ESCALATE_TO_HUMAN`

There is no `APPROVED WITH CONDITIONS` gate value today. Voice reporting must map spoken language onto the existing enum. It must not invent a fourth merge-authorizing decision.

Current product gate (2026-08-25 evidence):

| Item | Evidence |
| --- | --- |
| Closed on `main` | Stage 01–05 FAKE, Stage 04 tag `stage-04-complete`, Stage 06 **specification** (PR #72 squash-merged at `d77b2e0`; post-merge main CI Run `32804409128` passed) |
| In flight | Stage 06 **runtime** Draft PR [#73](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/73) on `codex/stage-06-decision-engine-runtime` |
| Runtime Manager Decision | `APPROVE` for implementation Head `50fcd8b`; approval-record Head `e65e906`; exact-head CI passed |
| Locked | Stage 07 Expansion; optional Meta paused proof; Decision Engine auto-execute |

Voice Reporting proceeds as a **parallel non-product track** from `origin/main`. It must not modify PR #73, Backend, Frontend product code, Docker Compose product services, or Flyway.

## 2. Existing Git Workflow

Observed, not assumed:

```text
Issue / Stage
  → branch `codex/<slice>`
  → commits
  → push (Push CI on `main` and `codex/**`)
  → Draft PR into `main`
  → Pull Request CI
  → Manager Review
  → Ready for Review
  → exact-head CI
  → Manager Decision APPROVE
  → Squash and Merge
  → post-merge `main` CI
  → Stage / completion docs
  → next Stage only after that
```

- Branch prefix `codex/` is required for Push CI (`.github/workflows/ci.yml`).
- Default merge is **Squash and Merge** (all recent Stage PRs).
- PR template: `.github/pull_request_template.md`.
- Developer must not self-mark Manager Decision as `APPROVE`.
- Automated `manager-gate` Required Check and Branch Protection are **not** enabled. Must not fake them.

Voice Reporting PRs use the same workflow. After merge, generate a Stage Voice Summary. That summary is not a substitute for the Manager Review report.

## 3. Existing Manager Gate

Manual Gate:

1. Draft PR
2. Required CI: `quality-and-compose` and `secret-scan`
3. Independent Manager Review of diff, migrations, contracts, tests, and Remote CI
4. Record in `docs/management/reviews/` plus Stage file fields
5. Merge only after `APPROVE`

Verification baseline includes git identity, Backend tests, Frontend lint/typecheck/tests/build, Flyway/Hibernate when applicable, Compose, Smoke, Playwright, Gitleaks, dependency audit, and actionlint. Unrun checks are `Not verified`, never Passed.

Voice summaries must read that evidence. They must not declare tests passed unless a recorded local run or Remote CI step actually passed.

## 4. Existing Stage Report / Handoff Architecture

There is no file named `handoff`. Handoff is already implemented as:

| Artifact | Location |
| --- | --- |
| Product / architecture specs | `docs/PRD.md`, `docs/Architecture.md`, `docs/Data-Model.md`, `docs/Roadmap.md` |
| Stage contracts | `docs/stages/stage-*.md` |
| Implementation completion | `docs/stages/stage-*-implementation-completion.md` |
| Manager Review reports | `docs/management/reviews/*-manager-review.md` |
| Agent roster / current gate | `docs/management/agent-assignments.md` |
| PR body | `.github/pull_request_template.md` |

There is **no** unified `.project/status.json` today. Progress is scattered across Stage files, review reports, GitHub PRs, and CI. That is the gap Voice Reporting should close without creating a second conflicting source of truth.

Rule: Git / PR / CI / Stage reports / Manager reviews remain System of Record for delivery. `.project/status.json` is a **generated snapshot** of that evidence. Agents must regenerate it from evidence, not hand-edit it to look greener.

## 5. Existing CI

Single workflow: `.github/workflows/ci.yml`.

| Item | Value |
| --- | --- |
| Triggers | `push` to `main` and `codex/**`; `pull_request` to `main`; `workflow_dispatch` |
| Permissions | `contents: read` only |
| Jobs | `quality-and-compose` (timeout 30m), `secret-scan` (Gitleaks 8.28.0) |
| Runner | `ubuntu-24.04` |
| Actions | SHA-pinned checkout, setup-java, setup-node, upload-artifact |
| Secrets | none in workflow files |
| Scheduler | none |

`quality-and-compose` already runs actionlint, Backend Testcontainers tests, Frontend lint/typecheck/test/build/`npm audit --omit=dev`, Playwright E2E, Compose up, Smoke, Compose down.

Implications:

- Adding `contents: write` or a PAT so Actions can commit daily markdown would raise workflow permissions → `ESCALATE_TO_HUMAN`. **Do not do this in VR-1.**
- A new **read-only** scheduled workflow that writes an artifact (not a commit) does not change product required checks and does not need secrets.
- TTS API keys must not be added. Unconfigured TTS must skip.

## 6. What can be reused

- Manager-first governance and escalation policy (no new gate owner).
- Draft PR → CI → Manager Review → Squash and Merge.
- Stage completion reports and Manager Review reports as evidence inputs.
- `codex/**` branch CI.
- Node 24 already installed in CI (for a zero-dependency `node --test` suite).
- `gh` locally and on GitHub-hosted runners for optional live evidence.
- Provider / Adapter pattern already used in Architecture (`TextProvider`, `PlatformAdapter`, …). Voice TTS and Notification follow the same idea, in **tooling**, not in Spring / Next.js product code.

## 7. What must be added

1. Integration plan (this file) and operator README.
2. Voice policy: when to generate, honesty rules, decision mapping, non-blocking failure class.
3. Directory `docs/voice-reports/stages/` and `docs/voice-reports/daily/`.
4. Machine snapshot `.project/status.json` plus JSON Schema.
5. Progress aggregator + spoken-summary generator (Node, no extra npm package).
6. TTS adapter interface + `noop` / `file` implementations. Real cloud TTS deferred.
7. Notification adapter interface + `noop`. LINE / Telegram / Slack / Email deferred.
8. Evidence JSON fixtures so first summaries are auditable.
9. Unit tests for mapping, honesty, and unspeakable-token stripping.
10. Optional scheduled read-only Action for daily **artifact** generation.
11. First Stage Voice Summary and first Daily Voice Summary generated from evidence.
12. Light pointers from `AGENTS.md`, Manager Policy, Project Manager agent doc, and PR template.

Must **not** add:

- Production Java / Next.js / Flyway / Compose product changes.
- Dashboard play buttons in `/dashboard` (Stage 05/06 product UI). Reserve fields only.
- GitHub `contents: write`, PAT, or TTS secrets.
- A fake automated Manager Gate.
- A fourth Manager Decision enum value.

## 8. Files to add or change (VR-1)

### Add

- `docs/voice-reports/VOICE_REPORTING_INTEGRATION_PLAN.md` (this file)
- `docs/voice-reports/README.md`
- `docs/management/voice-reporting.md`
- `docs/management/reviews/voice-reporting-foundation-manager-review.md`
- `docs/voice-reports/templates/stage-voice-summary.md`
- `docs/voice-reports/templates/daily-voice-summary.md`
- `docs/voice-reports/evidence/*.json`
- `docs/voice-reports/stages/*-voice-summary.md`
- `docs/voice-reports/daily/YYYY-MM-DD.md`
- `.project/status.schema.json`
- `.project/status.json`
- `tools/voice-reports/**`
- `scripts/generate-voice-report.sh`
- `scripts/generate-voice-report.ps1`
- `.github/workflows/voice-daily.yml`

### Change (avoid PR #73 conflict files)

- `AGENTS.md` — pointer only
- `docs/management/manager-policy.md` — additive Voice section; Gate enum unchanged
- `docs/agents/project-manager.md` — additive outputs
- `.github/pull_request_template.md` — optional Voice checklist
- `.github/workflows/ci.yml` — fast generator unit-test step (no TTS, no secrets)
- `.gitignore` — ignore generated audio / speak artifacts

### Explicitly not changed in VR-1

- `README.md` (PR #73 also edits the gate sentence)
- `docs/management/agent-assignments.md` (PR #73 owns the current-gate table)
- `backend/**`, `frontend/**`, `docker-compose.yml`, Flyway

## 9. API / Account / Credential requirements

| Item | Needed for VR-1? | Owner action |
| --- | --- | --- |
| OpenAI / Azure / Google / Edge TTS | No | None. Default adapter is `noop`. |
| LINE / Telegram / Slack / Email / Push | No | None. Default adapter is `noop`. |
| New GitHub PAT | No | None. |
| Production secrets / OAuth | No | None. |
| Paid cloud spend | No | None. |

Owner may later supply a TTS key when they want audio files. That is a separate human record. Until then, Markdown / Text is the deliverable.

## 10. Human Decision

Checked against `docs/management/escalation-policy.md` and the Owner intervention list in the request:

| Trigger | Present? |
| --- | --- |
| Destructive / irreversible migration | No |
| Auth / RBAC / Tenant / security model | No |
| Secret / credential / production access | No |
| Billing / paid API / material cost | No |
| System of Record change | No |
| Breaking API contract | No |
| Cross-milestone product direction | No — PM observability only |
| Critical security finding | No |
| Force-push / rewrite `main` | No |
| Raise existing CI to write secrets onto untrusted PRs | No |
| MFA / CAPTCHA / first-party OAuth | No |
| Legal / crawler / ToS | No |

**Human Escalation: No.**

Parallel-track note: Stage 06 runtime is merge-pending on PR #73. Voice Reporting does not depend on that merge, does not unlock Stage 07, and avoids files PR #73 still owns. Manager authorizes this parallel docs/tooling track.

## 11. Implementation Stages

### VR-1 — Foundation (this PR, `codex/voice-reporting-foundation`)

Ship:

- Plan, policy, templates, schema, generator, noop adapters, tests
- First Stage Voice Summary (Stage 06 specification, already merged)
- First Daily Voice Summary (2026-08-25 Asia/Taipei evidence)
- Optional read-only daily workflow (artifact only)
- Fast `node --test` step inside existing `quality-and-compose` (does not call TTS)

Acceptance for VR-1 is listed in section 12.

### VR-2 — Live collector hardening (later, optional)

Improve `gh` collection edge cases. Still no repo write from Actions. Still no secrets.

### VR-3 — Real TTS (later, **human credential**)

Wire one cloud or OS TTS adapter behind the existing interface. Requires Owner key / account. `ESCALATE_TO_HUMAN` at that time. TTS failure must remain non-blocking.

### VR-4 — Notification channels (later, **human credential**)

LINE / Telegram / Slack / Email. Same adapter boundary. Failure must not block merge.

### VR-5 — Manager Dashboard play buttons (later)

Product `/dashboard` must not gain Voice play controls in VR-1. A future gated slice may read `.project/status.json` and last Voice Report paths. That slice needs its own spec if it touches Stage 05 UI.

## 12. Acceptance Criteria

VR-1 is accepted only when all of the following are true:

1. Each completed Stage can generate a Traditional Chinese spoken summary under `docs/voice-reports/stages/`.
2. A Daily Summary can be generated under `docs/voice-reports/daily/YYYY-MM-DD.md`.
3. Summaries are produced from evidence JSON / git / PR / CI / Stage reports, not from guessed progress.
4. Spoken Manager Decision mapping is correct:
   - Gate `APPROVE` with no Owner-binding conditions → 「核准」
   - Gate `APPROVE` with recorded Owner-binding conditions → 「附條件核准」 (voice only; gate enum unchanged)
   - Gate `REQUEST_CHANGES` → 「尚未核准，需要先修正」 (must not be spoken as 否決 unless the Stage is explicitly abandoned)
   - Gate `ESCALATE_TO_HUMAN` → 「需要人工決策」
5. `partial` is never spoken as 「完成」.
6. Failed tests / CI / blockers / deferred blocking items are spoken.
7. When `human_action_required` is false, the script says 「目前不需要人工介入」 (or daily equivalent).
8. TTS provider is replaceable. Unconfigured TTS skips. Speak/notify failure does not fail generator tests or required product checks except for generator **unit** tests (logic only).
9. Existing Git workflow and Manager Gate are unchanged. No fake `manager-gate` check.
10. No production business logic change.
11. Documents listed in section 8 exist.
12. Remote CI `quality-and-compose` and `secret-scan` pass on the PR Head.

## Spoken decision mapping

| Manager Gate (authoritative) | Voice phrase | Allowed in completion summary? |
| --- | --- | --- |
| `APPROVE` | 核准 | Yes |
| `APPROVE` + `conditions[]` | 附條件核准 | Yes; must read the conditions |
| `REQUEST_CHANGES` | 尚未核准，需要先修正 | Not a completion; Status must be 部分完成 or 阻塞 |
| `ESCALATE_TO_HUMAN` | 需要人工決策 | Status 需要人工決策 |
| Explicit abandon / reject record | 否決 | Only with that record |

## Non-blocking failure class

Voice Reporting failures are `non-blocking observability failure` unless they are generator unit-test failures on a Voice Reporting PR.

| Failure | Blocks product merge? |
| --- | --- |
| TTS provider missing or HTTP error | No |
| Notification adapter error | No |
| Scheduled daily workflow error | No (not a required PR check) |
| Generator unit test failure | Yes, on this tooling PR / later PRs that break the generator |
| Product Backend / Playwright / Gitleaks | Unchanged; still required |

## Implementation order for this PR

1. Land this plan (Manager-approved).
2. Land tooling + policy + first evidence-based summaries.
3. Open Draft PR.
4. Wait for exact-head Remote CI.
5. Formal Manager Review of the implementation Head.
6. Squash and Merge only after `APPROVE`.
7. Do not start VR-3/VR-4 (credentials) or Stage 07.
