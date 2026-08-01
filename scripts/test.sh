#!/usr/bin/env sh
set -eu
repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

cd "$repository_root/backend"
sh ./mvnw test

cd "$repository_root/frontend"
npm ci
npm run lint
npm run typecheck
npm test
npm run build
