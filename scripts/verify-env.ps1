[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:FailureCount = 0
$script:LastStepPassed = $false
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repositoryRoot "frontend"
$backendRoot = Join-Path $repositoryRoot "backend"
$configuredComposeProject = [Environment]::GetEnvironmentVariable("DEV_VERIFY_COMPOSE_PROJECT_NAME", "Process")
$configuredFrontendPort = [Environment]::GetEnvironmentVariable("DEV_VERIFY_FRONTEND_PORT", "Process")
$configuredBackendPort = [Environment]::GetEnvironmentVariable("DEV_VERIFY_BACKEND_PORT", "Process")
$composeProject = if ($configuredComposeProject) { $configuredComposeProject } else { "ai-commerce-bootstrap-verify" }
$frontendPort = 13000
$backendPort = 18080

function Write-Result {
    param(
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Message
    )

    Write-Host ("[{0}] {1}" -f $Status, $Message)
}

function Add-Failure {
    param([Parameter(Mandatory = $true)][string]$Message)

    $script:FailureCount++
    Write-Result "FAIL" $Message
}

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    $script:LastStepPassed = $false
    try {
        $global:LASTEXITCODE = 0
        & $Action
        if ($LASTEXITCODE -ne 0) {
            throw ("command exited with code {0}" -f $LASTEXITCODE)
        }
        Write-Result "PASS" $Name
        $script:LastStepPassed = $true
    } catch {
        Add-Failure ("{0}: {1}" -f $Name, $_.Exception.Message)
    }
}

function Test-PortAvailable {
    param([Parameter(Mandatory = $true)][int]$Port)

    if ($Port -lt 1024 -or $Port -gt 65535) {
        throw ("Port {0} must be between 1024 and 65535" -f $Port)
    }
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if ($listener) {
        throw ("Port {0} is already in use; set DEV_VERIFY_FRONTEND_PORT or DEV_VERIFY_BACKEND_PORT" -f $Port)
    }
}

function Test-OptionalConfiguration {
    param(
        [Parameter(Mandatory = $true)][string[]]$Names,
        [Parameter(Mandatory = $true)][string]$Feature
    )

    $configured = @($Names | Where-Object { -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, "Process")) })
    if ($configured.Count -eq $Names.Count) {
        Write-Result "PASS" ("{0} configuration is present; values were not displayed" -f $Feature)
    } elseif ($configured.Count -eq 0) {
        Write-Result "SKIP" ("{0} configuration is absent; the feature remains disabled or uses deterministic local defaults" -f $Feature)
    } else {
        Write-Result "WARN" ("{0} configuration is incomplete; configured names were not displayed" -f $Feature)
    }
}

Set-Location $repositoryRoot
Write-Host "Development environment verification"
Write-Host ("Repository: {0}" -f $repositoryRoot)

if ($configuredFrontendPort -and -not [int]::TryParse($configuredFrontendPort, [ref]$frontendPort)) {
    Add-Failure "DEV_VERIFY_FRONTEND_PORT must be a numeric value"
    exit 1
}
if ($configuredBackendPort -and -not [int]::TryParse($configuredBackendPort, [ref]$backendPort)) {
    Add-Failure "DEV_VERIFY_BACKEND_PORT must be a numeric value"
    exit 1
}

if ($composeProject -notmatch "^[a-z0-9][a-z0-9_-]{0,62}$") {
    Add-Failure "DEV_VERIFY_COMPOSE_PROJECT_NAME is invalid"
    exit 1
}

try {
    Test-PortAvailable $frontendPort
    Test-PortAvailable $backendPort
    Write-Result "PASS" ("Verification ports {0}/{1} are available" -f $frontendPort, $backendPort)
} catch {
    Add-Failure $_.Exception.Message
    exit 1
}

Invoke-Step "Git working tree has no whitespace errors" {
    & git diff --check
}

Invoke-Step "Bootstrap prerequisites and deterministic dependencies" {
    & (Join-Path $PSScriptRoot "bootstrap.ps1")
}

if ($script:FailureCount -gt 0) {
    Write-Host "Verification stopped because bootstrap prerequisites failed."
    exit 1
}

Test-OptionalConfiguration @("AI_BUDGET_CURRENCY", "AI_MAX_JOB_COST", "AI_MAX_BATCH_COST", "AI_MAX_DAILY_COST") "AI budget"
Test-OptionalConfiguration @("GOOGLE_DRIVE_ROOT_FOLDER_ID", "GOOGLE_APPLICATION_CREDENTIALS") "Live Google Drive"
Test-OptionalConfiguration @("COMFYUI_BASE_URL") "Live ComfyUI"

if (Test-Path (Join-Path $repositoryRoot ".env")) {
    Write-Result "PASS" ".env exists and is ignored; contents were not read"
} else {
    Write-Result "SKIP" ".env is absent; local Compose defaults remain active"
}

$trackedSensitive = @(& git ls-files | Select-String -Pattern '(^|/)(\.env($|\.)|.*credential.*|.*service.?account.*|id_rsa|id_ed25519|.*\.pem$|.*\.key$|.*\.p12$|.*\.pfx$)' | ForEach-Object { $_.Line })
$unexpectedSensitive = @($trackedSensitive | Where-Object { $_ -notin @(".env.example", "frontend/.env.example") })
if ($unexpectedSensitive.Count -eq 0) {
    Write-Result "PASS" "No tracked credential/private-key filename was detected"
} else {
    Add-Failure "A sensitive-looking tracked filename was detected; inspect without printing file contents"
}

Invoke-Step "Backend tests, Testcontainers migrations, and Hibernate validation" {
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode test
    } finally {
        Pop-Location
    }
}

Invoke-Step "Frontend lint" {
    Push-Location $frontendRoot
    try { & npm run lint } finally { Pop-Location }
}

Invoke-Step "Frontend typecheck" {
    Push-Location $frontendRoot
    try { & npm run typecheck } finally { Pop-Location }
}

Invoke-Step "Frontend unit/component tests" {
    Push-Location $frontendRoot
    try { & npm test } finally { Pop-Location }
}

Invoke-Step "Frontend production build" {
    Push-Location $frontendRoot
    try { & npm run build } finally { Pop-Location }
}

Invoke-Step "Frontend production dependency audit" {
    Push-Location $frontendRoot
    try { & npm audit --omit=dev } finally { Pop-Location }
}

Invoke-Step "Docker Compose configuration" {
    & docker compose config --quiet
}

$environmentNames = @(
    "BACKEND_PORT", "FRONTEND_PORT", "NEXT_PUBLIC_APP_URL",
    "AI_BUDGET_CURRENCY", "AI_MAX_JOB_COST", "AI_MAX_BATCH_COST", "AI_MAX_DAILY_COST",
    "PLAYWRIGHT_BASE_URL", "PLAYWRIGHT_AUDIT_DB_ASSERTION", "PLAYWRIGHT_COMPOSE_PROJECT_NAME"
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$stackReady = $false
try {
    [Environment]::SetEnvironmentVariable("BACKEND_PORT", [string]$backendPort, "Process")
    [Environment]::SetEnvironmentVariable("FRONTEND_PORT", [string]$frontendPort, "Process")
    [Environment]::SetEnvironmentVariable("NEXT_PUBLIC_APP_URL", ("http://localhost:{0}" -f $frontendPort), "Process")
    [Environment]::SetEnvironmentVariable("AI_BUDGET_CURRENCY", "USD", "Process")
    [Environment]::SetEnvironmentVariable("AI_MAX_JOB_COST", "5.000000", "Process")
    [Environment]::SetEnvironmentVariable("AI_MAX_BATCH_COST", "20.000000", "Process")
    [Environment]::SetEnvironmentVariable("AI_MAX_DAILY_COST", "100.000000", "Process")
    [Environment]::SetEnvironmentVariable("PLAYWRIGHT_BASE_URL", ("http://127.0.0.1:{0}" -f $frontendPort), "Process")
    [Environment]::SetEnvironmentVariable("PLAYWRIGHT_AUDIT_DB_ASSERTION", "1", "Process")
    [Environment]::SetEnvironmentVariable("PLAYWRIGHT_COMPOSE_PROJECT_NAME", $composeProject, "Process")

    Invoke-Step "Isolated Docker Compose stack is healthy" {
        & docker compose --project-name $composeProject up --detach --build --wait --wait-timeout 240
    }
    $stackReady = $script:LastStepPassed

    if ($stackReady) {
        Invoke-Step "Backend Actuator health" {
            $health = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/actuator/health" -f $backendPort) -TimeoutSec 15
            if ($health.status -ne "UP") { throw "Backend health did not return UP" }
        }

        Invoke-Step "Frontend same-origin Backend health proxy" {
            $health = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/api/backend-health" -f $frontendPort) -TimeoutSec 15
            if ($health.status -ne "UP") { throw "Frontend health proxy did not return UP" }
        }

        Invoke-Step "Real-Compose Playwright E2E" {
            Push-Location $frontendRoot
            try { & npm run test:e2e } finally { Pop-Location }
        }
    } else {
        Write-Result "SKIP" "Health and Playwright checks were skipped because the isolated stack was not healthy"
    }
} finally {
    try {
        & docker compose --project-name $composeProject down
        if ($LASTEXITCODE -eq 0) {
            Write-Result "PASS" "Verification containers stopped; the isolated PostgreSQL volume was preserved"
        } else {
            Write-Result "WARN" "Verification container shutdown returned a non-zero code; no volume deletion was attempted"
        }
    } catch {
        Write-Result "WARN" "Verification containers could not be stopped automatically; no destructive cleanup was attempted"
    }
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
}

$gitleaksImage = "zricethezav/gitleaks:v8.28.0@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854"
$repositoryMount = ("{0}:/repo:ro" -f $repositoryRoot)
Invoke-Step "Gitleaks full-history scan" {
    & docker run --rm --volume $repositoryMount $gitleaksImage detect --source=/repo --redact --no-banner
}

Invoke-Step "Gitleaks worktree scan" {
    & docker run --rm --volume $repositoryMount $gitleaksImage dir /repo --redact --no-banner
}

if (Get-Command "actionlint" -ErrorAction SilentlyContinue) {
    Invoke-Step "GitHub Actions workflow validation" {
        & actionlint
    }
} else {
    Write-Result "SKIP" "Local actionlint is not installed; CI owns the pinned actionlint 1.7.7 verification"
}

Invoke-Step "Final Git diff check" {
    & git diff --check
}

if ($script:FailureCount -gt 0) {
    Write-Host ("Environment verification failed with {0} failure(s)." -f $script:FailureCount)
    exit 1
}

Write-Host "Environment verification passed."
exit 0
