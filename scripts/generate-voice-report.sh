#!/usr/bin/env sh
set -eu
repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$repository_root"
exec node tools/voice-reports/cli.mjs "$@"
