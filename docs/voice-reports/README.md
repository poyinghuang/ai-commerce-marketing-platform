# Voice Reports

Traditional Chinese spoken summaries for the Project Owner. Full technical evidence stays in Stage files, Manager Review reports, PRs, and CI.

## Generate locally

From the repository root, with Node 24:

```powershell
node tools/voice-reports/cli.mjs generate-stage --input docs/voice-reports/evidence/stage-06-specification.json
node tools/voice-reports/cli.mjs generate-daily --input docs/voice-reports/evidence/2026-08-25.json
node tools/voice-reports/cli.mjs write-status --input docs/voice-reports/evidence/project-status.json
node --test tools/voice-reports/test
```

Wrappers:

```powershell
.\scripts\generate-voice-report.ps1 stage --input docs/voice-reports/evidence/stage-06-specification.json
```

```shell
sh ./scripts/generate-voice-report.sh daily --input docs/voice-reports/evidence/2026-08-25.json
```

## Speak and notify

These commands never require an API key. Unconfigured providers skip.

```powershell
$env:VOICE_TTS_PROVIDER="noop"   # default
node tools/voice-reports/cli.mjs speak --file docs/voice-reports/daily/2026-08-25.md
node tools/voice-reports/cli.mjs notify --file docs/voice-reports/daily/2026-08-25.md
```

`VOICE_TTS_PROVIDER=file` writes `artifacts/voice-reports/*.spoken.txt` (gitignored). Cloud TTS is not wired in VR-1.

## Layout

| Path | Purpose |
| --- | --- |
| `VOICE_REPORTING_INTEGRATION_PLAN.md` | Assessment and Manager-approved plan |
| `templates/` | Spoken section order |
| `evidence/` | Auditable inputs (not guessed) |
| `stages/` | Stage completion scripts |
| `daily/` | Daily scripts |
| `.project/status.json` | Generated machine snapshot |

## Policy

`docs/management/voice-reporting.md`
