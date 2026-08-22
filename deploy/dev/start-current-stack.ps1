[CmdletBinding()]
param(
    [string]$EnvFile = '.env.prod',
    [string]$ComposeFile = 'compose.prod.yml',
    [int]$HealthTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$envPath = (Resolve-Path (Join-Path $repoRoot $EnvFile) -ErrorAction Stop).Path
$composePath = (Resolve-Path (Join-Path $repoRoot $ComposeFile) -ErrorAction Stop).Path

docker info | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop is not available.' }

$composeArgs = @('--env-file', $envPath, '-f', $composePath)
Push-Location $repoRoot
try {
    docker compose @composeArgs build java vue
    if ($LASTEXITCODE -ne 0) { throw 'Application image build failed.' }
    docker compose @composeArgs up -d java vue
    if ($LASTEXITCODE -ne 0) { throw 'Application stack startup failed.' }

    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    $backendPort = if ($env:MATCH_BACKEND_PORT) { $env:MATCH_BACKEND_PORT } else { '19141' }
    $health = $null
    do {
        try {
            $health = Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/health" -UseBasicParsing -TimeoutSec 5
            if ($health.StatusCode -ge 200 -and $health.StatusCode -lt 500) { break }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if (-not $health) { throw "Backend health check did not respond within $HealthTimeoutSeconds seconds." }
    docker compose @composeArgs logs --tail 80 java
} finally {
    Pop-Location
}
