$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot
& node tools/voice-reports/cli.mjs @args
if ($LASTEXITCODE -ne 0) { throw "Voice report command failed." }
