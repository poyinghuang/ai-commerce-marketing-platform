# Voice report tooling

Zero-dependency Node 24 generator. Owner-facing docs live in `docs/voice-reports/`.

```text
node tools/voice-reports/cli.mjs generate-stage --input <evidence.json>
node tools/voice-reports/cli.mjs generate-daily --input <evidence.json>
node tools/voice-reports/cli.mjs collect-daily --date YYYY-MM-DD
node tools/voice-reports/cli.mjs write-status --input <status.json>
node tools/voice-reports/cli.mjs speak --file <markdown>
node tools/voice-reports/cli.mjs notify --file <markdown>
node --test tools/voice-reports/test
```

`speak` and `notify` skip when no provider is configured and always exit 0 from the CLI.
