# Voice Progress Reporting Policy

Voice summaries are an Owner-facing management layer. They do not replace Stage specifications, completion reports, Manager Review reports, PR bodies, or CI logs.

Authority remains `AGENTS.md` and `docs/management/manager-policy.md`. This file only describes how spoken summaries are produced from that evidence.

## When to generate

### Stage Completion Voice Summary

Generate `docs/voice-reports/stages/<stage-id>-voice-summary.md` after a Stage / sub-stage has a recorded Manager Decision **and** the corresponding evidence (PR, CI, completion report) is available.

Typical moment: squash-merge plus post-merge `main` CI, or a specification-only merge that closes that specification gate.

Do not generate a completion summary that says 「完成」 while the Stage is still Draft, failing CI, or `REQUEST_CHANGES`.

### Daily Project Voice Summary

Generate `docs/voice-reports/daily/YYYY-MM-DD.md` using **Asia/Taipei** calendar dates.

Daily summaries may be written by the Manager Agent during a working session, or produced as a GitHub Actions **artifact** by the read-only scheduled workflow. Actions must not commit to `main`.

## Honesty

- Progress comes from git, GitHub PR/CI, Stage files, and Manager reviews.
- `.project/status.json` is a generated snapshot of that evidence.
- Unrun tests are not 「通過」.
- `partial` is not 「完成」.
- `REQUEST_CHANGES` is not 「核准」 and is not 「否決」 unless the Stage is explicitly abandoned.
- `APPROVE` with Owner-binding `conditions[]` is spoken as 「附條件核准」.
- Blockers, known issues that block the next Stage, security findings, and human escalation must be spoken.
- Do not read commit SHA, UUID, or full URL aloud. Keep those in the 證據索引 section only.

## Spoken Manager mapping

See `docs/voice-reports/VOICE_REPORTING_INTEGRATION_PLAN.md`. The Gate enum stays `APPROVE` / `REQUEST_CHANGES` / `ESCALATE_TO_HUMAN`.

## Adapters

```text
Project Evidence
      → Progress Aggregator
      → Voice Summary Generator
      → Markdown / Text
      → TTS Adapter (optional)
      → Audio (optional)
      → Notification Adapter (optional)
```

Default TTS: `noop`. Default notification: `noop`.

Missing credentials, TTS HTTP errors, and notification errors are `non-blocking observability failure`. They must not fail required product CI and must not block squash-merge.

Generator **unit tests** (spoken mapping, honesty, schema) may fail `quality-and-compose`. They do not call TTS providers.

## Dashboard reservation

`.project/status.json` reserves fields a future Manager Dashboard may show:

- project, milestone, current_stage, stage_status
- active_pr, ci_status, manager_gate
- blockers, human_action_required
- last_voice_report, next_stage

VR-1 does not add play buttons to product `/dashboard`.
