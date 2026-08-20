$ErrorActionPreference = 'Stop'

$flowScript = Join-Path $PSScriptRoot 'test-competition-mode-flow.ps1'
if (-not (Test-Path -LiteralPath $flowScript)) {
    throw "Missing competition mode flow script: $flowScript"
}

$source = Get-Content -Raw -LiteralPath $flowScript
@(
    'TRAINING',
    'COMPETITION',
    'modeGeneration',
    'PLATFORM_MODE_CHANGED',
    'DEGRADED',
    'retry',
    'UNBOUND_PRACTICAL',
    'STOP_TRAINING_ENVIRONMENT',
    'START_COMPETITION_ENVIRONMENT',
    'RESTORE_TRAINING_ENVIRONMENT'
) | ForEach-Object {
    if (-not $source.Contains($_)) {
        throw "Missing flow stage: $_"
    }
}

@(
    'XKP_TEST_ADMIN_USER',
    'XKP_TEST_ADMIN_PASSWORD',
    'XKP_TEST_OPERATIONS_USER',
    'XKP_TEST_OPERATIONS_PASSWORD',
    'XKP_TEST_BOUND_USER',
    'XKP_TEST_BOUND_PASSWORD',
    'XKP_TEST_UNBOUND_USER',
    'XKP_TEST_UNBOUND_PASSWORD'
) | ForEach-Object {
    if (-not $source.Contains($_)) {
        throw "Missing environment input: $_"
    }
}

$forbiddenPasswords = @('admin' + '123', 'Feng' + '113147')
if (($forbiddenPasswords | Where-Object { $source.Contains($_) }).Count -gt 0 -or
    $source -match 'Authorization:\s*Bearer\s+[A-Za-z0-9]|tokenValue\s*=') {
    throw 'Embedded credential detected'
}

[void][scriptblock]::Create($source)
Write-Host 'competition mode flow contract passed'
