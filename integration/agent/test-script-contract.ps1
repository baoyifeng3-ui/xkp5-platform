$ErrorActionPreference = 'Stop'

function Assert-Contains([string]$Path, [string]$Pattern) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing integration script: $Path"
    }
    if ((Get-Content -Raw -LiteralPath $Path) -notmatch $Pattern) {
        throw "Expected $Path to contain pattern: $Pattern"
    }
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$fakeAgent = Join-Path $root 'fake-agent.ps1'
$flow = Join-Path $root 'test-agent-flow.ps1'

Assert-Contains $fakeAgent 'Invoke-AgentRegistration'
Assert-Contains $fakeAgent 'Invoke-AgentHeartbeat'
Assert-Contains $flow 'duplicate registration token'
Assert-Contains $flow 'duplicate heartbeat sequence'
Assert-Contains $flow 'Start-Sleep -Seconds 16'
Assert-Contains $flow 'reconnect heartbeat'
Assert-Contains $flow 'disabled Agent receives 403'

Write-Host 'Agent integration script contract passed.'
