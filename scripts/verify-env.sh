#!/usr/bin/env sh
set -u

repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
frontend_root="$repository_root/frontend"
backend_root="$repository_root/backend"
compose_project="${DEV_VERIFY_COMPOSE_PROJECT_NAME:-ai-commerce-bootstrap-verify}"
frontend_port="${DEV_VERIFY_FRONTEND_PORT:-13000}"
backend_port="${DEV_VERIFY_BACKEND_PORT:-18080}"
failure_count=0
stack_attempted=0

result() {
  status="$1"
  shift
  printf '[%s] %s\n' "$status" "$*"
}

fail() {
  failure_count=$((failure_count + 1))
  result FAIL "$*"
}

run_step() {
  name="$1"
  shift
  if "$@"; then
    result PASS "$name"
    return 0
  fi
  code=$?
  fail "$name (exit $code)"
  return 1
}

valid_port() {
  case "$1" in
    *[!0-9]*|'') return 1 ;;
  esac
  [ "$1" -ge 1024 ] && [ "$1" -le 65535 ]
}

port_available() {
  node -e "const net=require('node:net');const server=net.createServer();server.once('error',()=>process.exit(1));server.listen(Number(process.argv[1]),'127.0.0.1',()=>server.close(()=>process.exit(0)));" "$1"
}

cleanup_stack() {
  if [ "$stack_attempted" -eq 1 ]; then
    if (cd "$repository_root" && docker compose --project-name "$compose_project" down); then
      result PASS "Verification containers stopped; the isolated PostgreSQL volume was preserved"
    else
      result WARN "Verification containers could not be stopped automatically; no volume deletion was attempted"
    fi
    stack_attempted=0
  fi
}

trap cleanup_stack EXIT INT TERM

case "$compose_project" in
  [a-z0-9]* ) ;;
  * ) fail "DEV_VERIFY_COMPOSE_PROJECT_NAME is invalid"; exit 1 ;;
esac
if ! printf '%s' "$compose_project" | grep -Eq '^[a-z0-9][a-z0-9_-]{0,62}$'; then
  fail "DEV_VERIFY_COMPOSE_PROJECT_NAME is invalid"
  exit 1
fi
if ! valid_port "$frontend_port" || ! valid_port "$backend_port"; then
  fail "Verification ports must be numeric values between 1024 and 65535"
  exit 1
fi
if ! port_available "$frontend_port" || ! port_available "$backend_port"; then
  fail "A verification port is already in use; set DEV_VERIFY_FRONTEND_PORT or DEV_VERIFY_BACKEND_PORT"
  exit 1
fi
result PASS "Verification ports $frontend_port/$backend_port are available"

printf 'Development environment verification\nRepository: %s\n' "$repository_root"

run_step "Git working tree has no whitespace errors" git -C "$repository_root" diff --check || true
run_step "Bootstrap prerequisites and deterministic dependencies" sh "$repository_root/scripts/bootstrap.sh" || true

if [ "$failure_count" -gt 0 ]; then
  printf 'Verification stopped because bootstrap prerequisites failed.\n'
  exit 1
fi

if [ -f "$repository_root/.env" ]; then
  result PASS ".env exists and is ignored; contents were not read"
else
  result SKIP ".env is absent; local Compose defaults remain active"
fi

optional_configuration() {
  feature="$1"
  shift
  configured=0
  total=$#
  for variable_name in "$@"; do
    eval "variable_value=\${$variable_name:-}"
    if [ -n "$variable_value" ]; then
      configured=$((configured + 1))
    fi
  done
  if [ "$configured" -eq "$total" ]; then
    result PASS "$feature configuration is present; values were not displayed"
  elif [ "$configured" -eq 0 ]; then
    result SKIP "$feature configuration is absent; the feature remains disabled or uses deterministic local defaults"
  else
    result WARN "$feature configuration is incomplete; configured names were not displayed"
  fi
}

optional_configuration "AI budget" AI_BUDGET_CURRENCY AI_MAX_JOB_COST AI_MAX_BATCH_COST AI_MAX_DAILY_COST
optional_configuration "Live Google Drive" GOOGLE_DRIVE_ROOT_FOLDER_ID GOOGLE_APPLICATION_CREDENTIALS
optional_configuration "Live ComfyUI" COMFYUI_BASE_URL

if git -C "$repository_root" ls-files | grep -Ei '(^|/)(\.env($|\.)|.*credential.*|.*service.?account.*|id_rsa|id_ed25519|.*\.(pem|key|p12|pfx)$)' \
    | grep -Ev '^(\.env\.example|frontend/\.env\.example)$' >/dev/null 2>&1; then
  fail "A sensitive-looking tracked filename was detected; inspect without printing file contents"
else
  result PASS "No tracked credential/private-key filename was detected"
fi

backend_tests() {
  cd "$backend_root" && sh ./mvnw --batch-mode test
}
frontend_lint() {
  cd "$frontend_root" && npm run lint
}
frontend_typecheck() {
  cd "$frontend_root" && npm run typecheck
}
frontend_test() {
  cd "$frontend_root" && npm test
}
frontend_build() {
  cd "$frontend_root" && npm run build
}
frontend_audit() {
  cd "$frontend_root" && npm audit --omit=dev
}
compose_config() {
  cd "$repository_root" && docker compose config --quiet
}

run_step "Backend tests, Testcontainers migrations, and Hibernate validation" backend_tests || true
run_step "Frontend lint" frontend_lint || true
run_step "Frontend typecheck" frontend_typecheck || true
run_step "Frontend unit/component tests" frontend_test || true
run_step "Frontend production build" frontend_build || true
run_step "Frontend production dependency audit" frontend_audit || true
run_step "Docker Compose configuration" compose_config || true

export BACKEND_PORT="$backend_port"
export FRONTEND_PORT="$frontend_port"
export NEXT_PUBLIC_APP_URL="http://localhost:$frontend_port"
export AI_BUDGET_CURRENCY="USD"
export AI_MAX_JOB_COST="5.000000"
export AI_MAX_BATCH_COST="20.000000"
export AI_MAX_DAILY_COST="100.000000"
export PLAYWRIGHT_BASE_URL="http://127.0.0.1:$frontend_port"
export PLAYWRIGHT_AUDIT_DB_ASSERTION="1"
export PLAYWRIGHT_COMPOSE_PROJECT_NAME="$compose_project"

stack_attempted=1
if (cd "$repository_root" && docker compose --project-name "$compose_project" up --detach --build --wait --wait-timeout 240); then
  result PASS "Isolated Docker Compose stack is healthy"

  if node -e "fetch('http://127.0.0.1:$backend_port/actuator/health').then(r=>{if(!r.ok)throw Error(r.status);return r.json()}).then(j=>{if(j.status!=='UP')throw Error('not UP')}).catch(e=>{console.error(e.message);process.exit(1)})"; then
    result PASS "Backend Actuator health"
  else
    fail "Backend Actuator health"
  fi

  if node -e "fetch('http://127.0.0.1:$frontend_port/api/backend-health').then(r=>{if(!r.ok)throw Error(r.status);return r.json()}).then(j=>{if(j.status!=='UP')throw Error('not UP')}).catch(e=>{console.error(e.message);process.exit(1)})"; then
    result PASS "Frontend same-origin Backend health proxy"
  else
    fail "Frontend same-origin Backend health proxy"
  fi

  if (cd "$frontend_root" && npm run test:e2e); then
    result PASS "Real-Compose Playwright E2E"
  else
    fail "Real-Compose Playwright E2E"
  fi
else
  fail "Isolated Docker Compose stack is healthy"
  result SKIP "Health and Playwright checks were skipped because the isolated stack was not healthy"
fi

cleanup_stack

gitleaks_image='zricethezav/gitleaks:v8.28.0@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854'
run_step "Gitleaks full-history scan" docker run --rm --volume "$repository_root:/repo:ro" "$gitleaks_image" detect --source=/repo --redact --no-banner || true
run_step "Gitleaks worktree scan" docker run --rm --volume "$repository_root:/repo:ro" "$gitleaks_image" dir /repo --redact --no-banner || true

if command -v actionlint >/dev/null 2>&1; then
  run_step "GitHub Actions workflow validation" actionlint || true
else
  result SKIP "Local actionlint is not installed; CI owns the pinned actionlint 1.7.7 verification"
fi

run_step "Final Git diff check" git -C "$repository_root" diff --check || true

if [ "$failure_count" -gt 0 ]; then
  printf 'Environment verification failed with %s failure(s).\n' "$failure_count"
  exit 1
fi

printf 'Environment verification passed.\n'
exit 0
