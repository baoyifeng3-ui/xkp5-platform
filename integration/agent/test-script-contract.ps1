$ErrorActionPreference = 'Stop'

function Assert-Contains([string]$Path, [string]$Pattern) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing integration script: $Path"
    }
    if ((Get-Content -Raw -LiteralPath $Path) -notmatch $Pattern) {
        throw "Expected $Path to contain pattern: $Pattern"
    }
}

function Assert-NotContains([string]$Path, [string]$Pattern) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing integration script: $Path"
    }
    if ((Get-Content -Raw -LiteralPath $Path) -match $Pattern) {
        throw "Expected $Path not to contain pattern: $Pattern"
    }
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$fakeAgent = Join-Path $root 'fake-agent.ps1'
$flow = Join-Path $root 'test-agent-flow.ps1'
$terminalFlow = Join-Path $root 'test-terminal-relay-flow.ps1'

Assert-Contains $fakeAgent 'Invoke-AgentRegistration'
Assert-Contains $fakeAgent 'Invoke-AgentHeartbeat'
Assert-Contains $flow 'duplicate registration token'
Assert-Contains $flow 'duplicate heartbeat sequence'
Assert-Contains $flow 'Start-Sleep -Seconds 16'
Assert-Contains $flow 'reconnect heartbeat'
Assert-Contains $flow 'disabled Agent receives 403'
Assert-Contains $terminalFlow 'https://localhost'
Assert-Contains $terminalFlow 'XKP_TEST_ALLOW_TERMINAL_RELAY'
Assert-Contains $terminalFlow 'deterministic echo PTY adapter'
Assert-Contains $terminalFlow 'non-privileged fake PTY'
Assert-Contains $terminalFlow 'Sec-WebSocket-Protocol'
Assert-Contains $terminalFlow 'xkp-terminal-ticket\.'
Assert-Contains $terminalFlow 'binary echo'
Assert-Contains $terminalFlow '"type":"resize"'
Assert-Contains $terminalFlow 'ticket replay rejection'
Assert-Contains $terminalFlow 'second-session conflict'
Assert-Contains $terminalFlow 'disconnect cleanup'
Assert-Contains $terminalFlow 'XKP_TEST_TERMINAL_IDLE_SECONDS'
Assert-Contains $terminalFlow 'database sentinel absence'
Assert-Contains $terminalFlow 'log sentinel absence'
Assert-Contains $terminalFlow 'unfinished session'
Assert-NotContains $terminalFlow '(?i)(/bin/bash|sudo|runas|Start-Process\s+.*-Verb\s+RunAs)'

Write-Host 'Agent and terminal relay integration script contracts passed.'
