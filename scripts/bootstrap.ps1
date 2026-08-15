[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:FailureCount = 0
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repositoryRoot "frontend"
$backendRoot = Join-Path $repositoryRoot "backend"

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

function Test-Tool {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Remediation
    )

    if (Get-Command $Name -ErrorAction SilentlyContinue) {
        Write-Result "PASS" ("{0} is available" -f $Name)
        return $true
    }

    Add-Failure ("{0} is required but was not found" -f $Name)
    Write-Result "HUMAN ACTION REQUIRED" $Remediation
    return $false
}

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    try {
        $global:LASTEXITCODE = 0
        & $Action
        if ($LASTEXITCODE -ne 0) {
            throw ("command exited with code {0}" -f $LASTEXITCODE)
        }
        Write-Result "PASS" $Name
    } catch {
        Add-Failure ("{0}: {1}" -f $Name, $_.Exception.Message)
    }
}

function Read-RequiredVersions {
    $nodeVersion = (Get-Content -Raw (Join-Path $frontendRoot ".nvmrc")).Trim()
    $package = Get-Content -Raw (Join-Path $frontendRoot "package.json") | ConvertFrom-Json
    $javaVersion = (Get-Content -Raw (Join-Path $repositoryRoot ".java-version")).Trim()

    return [PSCustomObject]@{
        Node = $nodeVersion
        Npm = [string]$package.engines.npm
        Java = $javaVersion
    }
}

Set-Location $repositoryRoot
$required = Read-RequiredVersions

Write-Host "Development bootstrap"
Write-Host ("Repository: {0}" -f $repositoryRoot)
Write-Host ("Required: Node {0}, npm {1}, Java {2}" -f $required.Node, $required.Npm, $required.Java)

if ($PSVersionTable.PSVersion -lt [Version]"5.1") {
    Add-Failure ("PowerShell 5.1 or newer is required; current version is {0}" -f $PSVersionTable.PSVersion)
} else {
    Write-Result "PASS" ("PowerShell {0}" -f $PSVersionTable.PSVersion)
}

$null = Test-Tool "git" "Install Git for Windows, for example: winget install --source winget --id Git.Git --exact"
$nodeAvailable = Test-Tool "node" "Install Volta, then run: volta install node@$($required.Node)"
$npmAvailable = Test-Tool "npm" "After Node is available, run: volta install npm@$($required.Npm)"
$javaAvailable = Test-Tool "java" "Install Temurin with: winget install --source winget --id EclipseAdoptium.Temurin.21.JDK --exact --version 21.0.9.10"
$javacAvailable = Test-Tool "javac" "Ensure JAVA_HOME points to the Temurin JDK and place %JAVA_HOME%\bin first in PATH"
$dockerAvailable = Test-Tool "docker" "Install and start Docker Desktop. UAC, WSL2 setup, restart, and Docker Desktop GUI actions require a human"

if ($nodeAvailable) {
    $actualNode = ((& node --version).Trim()).TrimStart("v")
    if ($actualNode -eq $required.Node) {
        Write-Result "PASS" ("Node {0}" -f $actualNode)
    } else {
        Add-Failure ("Node version mismatch: required {0}, found {1}" -f $required.Node, $actualNode)
        Write-Result "HUMAN ACTION REQUIRED" ("Run: volta install node@{0}" -f $required.Node)
    }
}

if ($npmAvailable) {
    $actualNpm = (& npm --version).Trim()
    if ($actualNpm -eq $required.Npm) {
        Write-Result "PASS" ("npm {0}" -f $actualNpm)
    } else {
        Add-Failure ("npm version mismatch: required {0}, found {1}" -f $required.Npm, $actualNpm)
        Write-Result "HUMAN ACTION REQUIRED" ("Run: volta install npm@{0}" -f $required.Npm)
    }
}

if ($javaAvailable -and $javacAvailable) {
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Java reports its version on stderr. PowerShell 5.1 otherwise turns the
        # successful native command into a terminating NativeCommandError.
        $ErrorActionPreference = "Continue"
        $javaOutput = (& java -version 2>&1 | Out-String)
        $javacOutput = (& javac -version 2>&1 | Out-String)
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($javaOutput -match [Regex]::Escape($required.Java) -and $javacOutput -match "javac 21\.0\.9") {
        Write-Result "PASS" ("Java {0}" -f $required.Java)
    } else {
        Add-Failure ("Java version mismatch: required {0}" -f $required.Java)
        Write-Result "HUMAN ACTION REQUIRED" "Set JAVA_HOME to the Temurin 21.0.9+10 JDK and reopen PowerShell"
    }
}

if ($dockerAvailable) {
    try {
        $composeVersion = (& docker compose version 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw "Docker Compose is unavailable" }
        Write-Result "PASS" $composeVersion

        $engineVersion = (& docker info --format "{{.ServerVersion}}" 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($engineVersion)) {
            throw "Docker Engine is not reachable"
        }
        Write-Result "PASS" ("Docker Engine {0} is reachable" -f $engineVersion)
    } catch {
        Add-Failure $_.Exception.Message
        Write-Result "HUMAN ACTION REQUIRED" "Start Docker Desktop and wait until the Linux Engine is ready"
    }
}

if (Get-Command "gh" -ErrorAction SilentlyContinue) {
    Write-Result "PASS" ((& gh --version | Select-Object -First 1) -join "")
} else {
    Write-Result "WARN" "GitHub CLI is optional and was not found; install it before creating Pull Requests"
}

if ($script:FailureCount -gt 0) {
    Write-Host ("Bootstrap stopped before project installation because {0} required system check(s) failed." -f $script:FailureCount)
    exit 1
}

Invoke-Step "Maven Wrapper 3.9.11 is available" {
    Push-Location $backendRoot
    try {
        $mavenOutput = (& .\mvnw.cmd --version 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0 -or $mavenOutput -notmatch "Apache Maven 3\.9\.11") {
            throw "Maven Wrapper did not report version 3.9.11"
        }
    } finally {
        Pop-Location
    }
}

Invoke-Step "Backend dependencies resolved through Maven Wrapper" {
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode dependency:go-offline
    } finally {
        Pop-Location
    }
}

Invoke-Step "Frontend dependencies installed deterministically with npm ci" {
    Push-Location $frontendRoot
    try {
        & npm ci
    } finally {
        Pop-Location
    }
}

Invoke-Step "Package-pinned Playwright Chromium installed" {
    Push-Location $frontendRoot
    try {
        & npx --no-install playwright install chromium
    } finally {
        Pop-Location
    }
}

Invoke-Step "Docker Compose configuration is valid" {
    & docker compose config --quiet
}

if (Test-Path (Join-Path $repositoryRoot ".env")) {
    Write-Result "PASS" ".env is present and remains ignored; values were not read or displayed"
} else {
    Write-Result "SKIP" ".env was not created; Compose local defaults are sufficient for the deterministic development stack"
}

Write-Result "SKIP" "Production credentials, Google ADC, and live provider credentials are never created by bootstrap"

if ($script:FailureCount -gt 0) {
    Write-Host ("Bootstrap completed with {0} failure(s)." -f $script:FailureCount)
    exit 1
}

Write-Host "Bootstrap completed successfully. Run .\scripts\verify-env.ps1 next."
exit 0
