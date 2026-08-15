#!/usr/bin/env sh
set -u

repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
frontend_root="$repository_root/frontend"
backend_root="$repository_root/backend"
failure_count=0

result() {
  status="$1"
  shift
  printf '[%s] %s\n' "$status" "$*"
}

fail() {
  failure_count=$((failure_count + 1))
  result FAIL "$*"
}

has_tool() {
  tool="$1"
  remediation="$2"
  if command -v "$tool" >/dev/null 2>&1; then
    result PASS "$tool is available"
    return 0
  fi
  fail "$tool is required but was not found"
  result "HUMAN ACTION REQUIRED" "$remediation"
  return 1
}

run_step() {
  name="$1"
  shift
  if "$@"; then
    result PASS "$name"
  else
    code=$?
    fail "$name (exit $code)"
  fi
}

node_required="$(tr -d '[:space:]' < "$frontend_root/.nvmrc")"
npm_required="$(sed -n 's/.*"npm"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$frontend_root/package.json" | head -n 1)"
java_required="$(tr -d '[:space:]' < "$repository_root/.java-version")"

printf 'Development bootstrap\nRepository: %s\nRequired: Node %s, npm %s, Java %s\n' \
  "$repository_root" "$node_required" "$npm_required" "$java_required"

git_ok=0; has_tool git "Install Git with your operating system package manager" || git_ok=$?
node_ok=0; has_tool node "Install Node $node_required through a version manager; do not use an unpinned latest release" || node_ok=$?
npm_ok=0; has_tool npm "Install npm $npm_required after Node is available" || npm_ok=$?
java_ok=0; has_tool java "Install Eclipse Temurin JDK $java_required" || java_ok=$?
javac_ok=0; has_tool javac "Install a full JDK, set JAVA_HOME, and put JAVA_HOME/bin first in PATH" || javac_ok=$?
docker_ok=0; has_tool docker "Install Docker Engine or Docker Desktop. sudo, WSL features, restart, and GUI setup require a human" || docker_ok=$?

if [ "$node_ok" -eq 0 ]; then
  node_actual="$(node --version | sed 's/^v//')"
  if [ "$node_actual" = "$node_required" ]; then
    result PASS "Node $node_actual"
  else
    fail "Node version mismatch: required $node_required, found $node_actual"
    result "HUMAN ACTION REQUIRED" "Install and activate Node $node_required"
  fi
fi

if [ "$npm_ok" -eq 0 ]; then
  npm_actual="$(npm --version)"
  if [ "$npm_actual" = "$npm_required" ]; then
    result PASS "npm $npm_actual"
  else
    fail "npm version mismatch: required $npm_required, found $npm_actual"
    result "HUMAN ACTION REQUIRED" "Install and activate npm $npm_required"
  fi
fi

if [ "$java_ok" -eq 0 ] && [ "$javac_ok" -eq 0 ]; then
  java_output="$(java -version 2>&1)"
  javac_output="$(javac -version 2>&1)"
  case "$java_output" in
    *"$java_required"*)
      case "$javac_output" in
        *"javac 21.0.9"*) result PASS "Java $java_required" ;;
        *) fail "javac version mismatch: required 21.0.9" ;;
      esac
      ;;
    *)
      fail "Java version mismatch: required $java_required"
      result "HUMAN ACTION REQUIRED" "Set JAVA_HOME to Eclipse Temurin JDK $java_required"
      ;;
  esac
fi

if [ "$docker_ok" -eq 0 ]; then
  if compose_version="$(docker compose version 2>&1)"; then
    result PASS "$compose_version"
  else
    fail "Docker Compose is unavailable"
  fi
  if engine_version="$(docker info --format '{{.ServerVersion}}' 2>/dev/null)" && [ -n "$engine_version" ]; then
    result PASS "Docker Engine $engine_version is reachable"
  else
    fail "Docker Engine is not reachable"
    result "HUMAN ACTION REQUIRED" "Start Docker and wait for the Linux Engine"
  fi
fi

if command -v gh >/dev/null 2>&1; then
  result PASS "$(gh --version | head -n 1)"
else
  result WARN "GitHub CLI is optional and was not found"
fi

if [ "$failure_count" -gt 0 ]; then
  printf 'Bootstrap stopped before project installation because %s required system check(s) failed.\n' "$failure_count"
  exit 1
fi

check_maven_wrapper() {
  output="$(cd "$backend_root" && sh ./mvnw --version 2>&1)" || return $?
  printf '%s' "$output" | grep 'Apache Maven 3.9.11' >/dev/null 2>&1
}

backend_dependencies() {
  cd "$backend_root" && sh ./mvnw --batch-mode dependency:go-offline
}

frontend_dependencies() {
  cd "$frontend_root" && npm ci
}

playwright_browser() {
  cd "$frontend_root" && npx --no-install playwright install chromium
}

compose_config() {
  cd "$repository_root" && docker compose config --quiet
}

run_step "Maven Wrapper 3.9.11 is available" check_maven_wrapper
run_step "Backend dependencies resolved through Maven Wrapper" backend_dependencies
run_step "Frontend dependencies installed deterministically with npm ci" frontend_dependencies
run_step "Package-pinned Playwright Chromium installed" playwright_browser
run_step "Docker Compose configuration is valid" compose_config

if [ -f "$repository_root/.env" ]; then
  result PASS ".env is present and remains ignored; values were not read or displayed"
else
  result SKIP ".env was not created; Compose local defaults are sufficient for the deterministic development stack"
fi
result SKIP "Production credentials, Google ADC, and live provider credentials are never created by bootstrap"

if [ "$failure_count" -gt 0 ]; then
  printf 'Bootstrap completed with %s failure(s).\n' "$failure_count"
  exit 1
fi

result WARN "On Linux/WSL, missing Chromium system libraries require a human-approved sudo install: sudo npx playwright install-deps chromium"
printf 'Bootstrap completed successfully. Run ./scripts/verify-env.sh next.\n'
exit 0
