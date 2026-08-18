param(
    [string]$AgentBaseUrl = 'http://localhost:19141/agent/v1',
    [string]$ApiBaseUrl = 'http://localhost:19140/api',
    [string]$RegistrationToken,
    [string]$AdminToken,
    [string]$SuperAdminToken
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'fake-agent.ps1')

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-ExpectedFailure([scriptblock]$Action, [int]$Status, [string]$Scenario) {
    try {
        & $Action | Out-Null
        throw "$Scenario unexpectedly succeeded"
    } catch {
        if ($_.Exception.Message -eq "$Scenario unexpectedly succeeded") { throw }
        $actual = Get-AgentHttpStatus -ErrorRecord $_
        if ($actual -ne $Status) {
            throw "$Scenario returned HTTP $actual instead of $Status"
        }
    }
}

function Invoke-ManagementApi {
    param([string]$Method, [string]$Path, [string]$Token, $Body)
    $parameters = @{
        Method = $Method
        Uri = "$($ApiBaseUrl.TrimEnd('/'))/$($Path.TrimStart('/'))"
        Headers = @{ satoken = $Token }
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json
    }
    Invoke-RestMethod @parameters
}

if ([string]::IsNullOrWhiteSpace($RegistrationToken)) {
    if ([string]::IsNullOrWhiteSpace($SuperAdminToken)) {
        throw 'Provide RegistrationToken or SuperAdminToken.'
    }
    $issued = Invoke-ManagementApi -Method Post `
        -Path 'super-admin/processing-agents/registration-tokens' `
        -Token $SuperAdminToken -Body @{ label = 'integration-flow' }
    $RegistrationToken = $issued.data.token
    Assert-True (-not [string]::IsNullOrWhiteSpace($RegistrationToken)) 'Token issuance returned no token.'
}

$machineDigest = ([System.BitConverter]::ToString(
    [System.Security.Cryptography.SHA256]::Create().ComputeHash(
        [System.Text.Encoding]::UTF8.GetBytes([Guid]::NewGuid().ToString())
    )
)).Replace('-', '').ToLowerInvariant()
$bootId = [Guid]::NewGuid().ToString()

$registration = Invoke-AgentRegistration -BaseUrl $AgentBaseUrl `
    -RegistrationToken $RegistrationToken -MachineDigest $machineDigest
Assert-True (-not [string]::IsNullOrWhiteSpace($registration.agentId)) 'Registration returned no Agent ID.'
Assert-True (-not [string]::IsNullOrWhiteSpace($registration.credential)) 'Registration returned no credential.'

# duplicate registration token
Invoke-ExpectedFailure -Status 400 -Scenario 'duplicate registration token' -Action {
    Invoke-AgentRegistration -BaseUrl $AgentBaseUrl -RegistrationToken $RegistrationToken `
        -MachineDigest ('f' * 64)
}

$first = Invoke-AgentHeartbeat -BaseUrl $AgentBaseUrl -AgentId $registration.agentId `
    -Credential $registration.credential -BootId $bootId -Sequence 1
Assert-True $first.accepted 'First heartbeat was not accepted.'

# duplicate heartbeat sequence
$duplicate = Invoke-AgentHeartbeat -BaseUrl $AgentBaseUrl -AgentId $registration.agentId `
    -Credential $registration.credential -BootId $bootId -Sequence 1
Assert-True (-not $duplicate.accepted) 'Duplicate heartbeat sequence was accepted.'

$second = Invoke-AgentHeartbeat -BaseUrl $AgentBaseUrl -AgentId $registration.agentId `
    -Credential $registration.credential -BootId $bootId -Sequence 2
Assert-True $second.accepted 'Increasing heartbeat sequence was not accepted.'

$readToken = if ([string]::IsNullOrWhiteSpace($AdminToken)) { $SuperAdminToken } else { $AdminToken }
if (-not [string]::IsNullOrWhiteSpace($readToken)) {
    Start-Sleep -Seconds 16
    $offline = Invoke-ManagementApi -Method Get `
        -Path "admin/processing-agents/$($registration.agentId)" -Token $readToken
    Assert-True (-not $offline.data.online) 'Agent did not become offline after 15 seconds.'

    # reconnect heartbeat
    $reconnect = Invoke-AgentHeartbeat -BaseUrl $AgentBaseUrl -AgentId $registration.agentId `
        -Credential $registration.credential -BootId $bootId -Sequence 3
    Assert-True $reconnect.accepted 'Reconnect heartbeat was not accepted.'
    $online = Invoke-ManagementApi -Method Get `
        -Path "admin/processing-agents/$($registration.agentId)" -Token $readToken
    Assert-True $online.data.online 'Agent did not return online after reconnect.'
}

if (-not [string]::IsNullOrWhiteSpace($SuperAdminToken)) {
    Invoke-ManagementApi -Method Post `
        -Path "super-admin/processing-agents/$($registration.agentId)/disable" `
        -Token $SuperAdminToken -Body $null | Out-Null

    # disabled Agent receives 403
    Invoke-ExpectedFailure -Status 403 -Scenario 'disabled Agent receives 403' -Action {
        Invoke-AgentHeartbeat -BaseUrl $AgentBaseUrl -AgentId $registration.agentId `
            -Credential $registration.credential -BootId $bootId -Sequence 4
    }
}

Write-Host "Agent integration flow passed for $($registration.agentId)."
