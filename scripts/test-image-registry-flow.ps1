param([switch]$RunReal)

$ErrorActionPreference = 'Stop'
$contract = Join-Path $PSScriptRoot 'test-image-registry-flow-contract.ps1'
& powershell -NoProfile -File $contract
if (-not $RunReal) {
    Write-Output 'Real Registry flow skipped. Use -RunReal only with disposable MySQL, TLS Registry, importer, fake Agent, and dedicated test accounts.'
    exit 0
}
$required = @('XKP_TEST_MANAGEMENT_URL', 'XKP_TEST_REGISTRY_URL', 'XKP_TEST_AGENT_ID', 'XKP_TEST_ADMIN_USER', 'XKP_TEST_ADMIN_PASSWORD')
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Missing real-flow prerequisite: $name" }
}
throw 'Real image registry flow requires an environment-specific harness; no live rollout was attempted by this contract.'
