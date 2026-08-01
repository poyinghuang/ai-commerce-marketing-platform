$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot

Push-Location (Join-Path $repositoryRoot "backend")
try {
    & .\mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { throw "Backend tests failed." }
} finally {
    Pop-Location
}

Push-Location (Join-Path $repositoryRoot "frontend")
try {
    npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed." }
    npm run lint
    if ($LASTEXITCODE -ne 0) { throw "Frontend lint failed." }
    npm run typecheck
    if ($LASTEXITCODE -ne 0) { throw "Frontend typecheck failed." }
    npm test
    if ($LASTEXITCODE -ne 0) { throw "Frontend tests failed." }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." }
} finally {
    Pop-Location
}
