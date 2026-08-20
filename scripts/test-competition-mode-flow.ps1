param(
    [string]$ManagementUrl = $env:XKP_TEST_MANAGEMENT_URL,
    [string]$AgentId = $env:XKP_TEST_AGENT_ID,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$required = @(
    'XKP_TEST_ADMIN_USER', 'XKP_TEST_ADMIN_PASSWORD',
    'XKP_TEST_OPERATIONS_USER', 'XKP_TEST_OPERATIONS_PASSWORD',
    'XKP_TEST_BOUND_USER', 'XKP_TEST_BOUND_PASSWORD',
    'XKP_TEST_UNBOUND_USER', 'XKP_TEST_UNBOUND_PASSWORD',
    'XKP_TEST_FAKE_AGENT_FAIL_URL', 'XKP_TEST_FAKE_AGENT_RECOVER_URL'
)
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Missing required environment variable: $name"
    }
}
if ([string]::IsNullOrWhiteSpace($ManagementUrl) -or
    [string]::IsNullOrWhiteSpace($AgentId)) {
    throw 'ManagementUrl and AgentId are required'
}

$apiRoot = $ManagementUrl.TrimEnd('/') + '/api/'
$script:adminToken = $null
$script:changedMode = $false
$summary = New-Object System.Collections.Generic.List[string]

function Invoke-JsonApi {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Token,
        [object]$Body,
        [switch]$AllowFailure
    )
    $headers = @{}
    if ($Token) { $headers['satoken'] = $Token }
    $arguments = @{
        Method = $Method
        Uri = $apiRoot + $Path.TrimStart('/')
        Headers = $headers
        UseBasicParsing = $true
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $arguments['ContentType'] = 'application/json'
        $arguments['Body'] = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    try {
        $response = Invoke-WebRequest @arguments
        return ($response.Content | ConvertFrom-Json)
    } catch {
        if (-not $AllowFailure) { throw }
        $webResponse = $_.Exception.Response
        $status = if ($webResponse) { [int]$webResponse.StatusCode } else { 0 }
        $content = $null
        if ($webResponse -and $webResponse.GetResponseStream()) {
            $reader = New-Object System.IO.StreamReader($webResponse.GetResponseStream())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
        $body = if ($content) { $content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ httpStatus = $status; body = $body }
    }
}

function Login-Participant {
    param([string]$UserName, [string]$Password)
    $uri = $apiRoot + 'user/login'
    $response = Invoke-WebRequest -Method Post -Uri $uri -UseBasicParsing `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ userName = $UserName; password = $Password }
    $result = $response.Content | ConvertFrom-Json
    if ($result.code -ne 200 -or -not $result.data.tokenValue) {
        throw "Login failed for configured test participant: code=$($result.code)"
    }
    return $result.data
}

function Assert-Value {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Wait-Until {
    param([scriptblock]$Probe, [string]$Description)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $value = & $Probe
        if ($null -ne $value) { return $value }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Description"
}

function Set-PlatformMode {
    param([string]$Target)
    $confirmation = if ($Target -eq 'COMPETITION') { 'ENTER COMPETITION' } else { 'EXIT COMPETITION' }
    $result = Invoke-JsonApi -Method Post -Path 'admin/platform-mode' -Token $script:adminToken `
        -Body @{ targetMode = $Target; confirmation = $confirmation }
    Assert-Value ($result.code -eq 200) "Mode change to $Target failed"
    if ($Target -eq 'COMPETITION') { $script:changedMode = $true }
    if ($Target -eq 'TRAINING') { $script:changedMode = $false }
}

function Wait-TransitionState {
    param([string]$TargetMode, [string[]]$States)
    return Wait-Until -Description "$TargetMode transition state $($States -join ',')" -Probe {
        $result = Invoke-JsonApi -Method Get -Path 'admin/platform-mode/transitions?limit=100' -Token $script:adminToken
        $match = @($result.data | Where-Object { $_.agentId -eq $AgentId -and $_.targetMode -eq $TargetMode } |
            Sort-Object requestedAt -Descending | Select-Object -First 1)
        if ($match.Count -eq 1 -and $States -contains $match[0].state) { return $match[0] }
        return $null
    }
}

function Invoke-FakeAgentHook {
    param([string]$Uri)
    Invoke-WebRequest -Method Post -Uri $Uri -UseBasicParsing -ErrorAction Stop | Out-Null
}

# UNBOUND_PRACTICAL produces the user-visible text from code points so this file stays ASCII for Windows PowerShell 5.1.
$unboundPracticalText = -join @(0x672A,0x5206,0x914D,0x6BD4,0x8D5B,0x73AF,0x5883 | ForEach-Object { [char]$_ })

try {
    $admin = Login-Participant $env:XKP_TEST_ADMIN_USER $env:XKP_TEST_ADMIN_PASSWORD
    Assert-Value ($admin.role -eq 'ADMIN') 'Configured management account is not ADMIN'
    $script:adminToken = $admin.tokenValue
    $operations = Login-Participant $env:XKP_TEST_OPERATIONS_USER $env:XKP_TEST_OPERATIONS_PASSWORD
    Assert-Value ($operations.role -eq 'SUPER_ADMIN') 'Configured operations account is not SUPER_ADMIN'

    $initial = Invoke-JsonApi -Method Get -Path 'admin/platform-mode' -Token $script:adminToken
    Assert-Value ($initial.data.mode -eq 'TRAINING') 'Initial platform mode must be TRAINING'

    $boundBefore = Login-Participant $env:XKP_TEST_BOUND_USER $env:XKP_TEST_BOUND_PASSWORD
    $unboundBefore = Login-Participant $env:XKP_TEST_UNBOUND_USER $env:XKP_TEST_UNBOUND_PASSWORD
    $initialGeneration = [long]$boundBefore.modeGeneration
    Assert-Value ([long]$unboundBefore.modeGeneration -eq $initialGeneration) 'Participant generations differ before transition'

    $slots = Invoke-JsonApi -Method Get -Path 'admin/competition-slots' -Token $script:adminToken
    $boundSlots = @($slots.data | Where-Object { $_.agentId -eq $AgentId -and $null -ne $_.userId })
    Assert-Value ($boundSlots.Count -eq 1) 'Smoke fixture must have exactly one bound slot for the selected Agent'

    Invoke-FakeAgentHook $env:XKP_TEST_FAKE_AGENT_FAIL_URL
    Set-PlatformMode 'COMPETITION'

    foreach ($oldToken in @($boundBefore.tokenValue, $unboundBefore.tokenValue)) {
        $stale = Invoke-JsonApi -Method Get -Path 'user/me' -Token $oldToken -AllowFailure
        Assert-Value ($stale.httpStatus -eq 401 -or $stale.body.code -eq 401) 'Old participant token did not return 401'
        Assert-Value ($stale.body.data.reasonCode -eq 'PLATFORM_MODE_CHANGED') 'Old participant token did not return PLATFORM_MODE_CHANGED'
    }

    $boundAfter = Login-Participant $env:XKP_TEST_BOUND_USER $env:XKP_TEST_BOUND_PASSWORD
    $unboundAfter = Login-Participant $env:XKP_TEST_UNBOUND_USER $env:XKP_TEST_UNBOUND_PASSWORD
    Assert-Value ([long]$boundAfter.modeGeneration -gt $initialGeneration) 'modeGeneration did not advance'

    $paper = Invoke-JsonApi -Method Get -Path 'competition' -Token $unboundAfter.tokenValue
    Assert-Value ($paper.code -eq 200) 'Unbound participant cannot read paper data'
    Assert-Value (-not [string]::IsNullOrWhiteSpace($paper.data.activePaper)) 'Smoke fixture has no active paper'
    $paperType = [uri]::EscapeDataString([string]$paper.data.activePaper)
    $submission = Invoke-JsonApi -Method Get -Path "testPaper/submission?testPaperType=$paperType" -Token $unboundAfter.tokenValue
    Assert-Value ($submission.code -eq 200) 'Unbound participant cannot read participant paper state'
    $practical = Invoke-JsonApi -Method Get -Path 'user/competition-environment' -Token $unboundAfter.tokenValue
    Assert-Value ($practical.data.readiness -eq 'UNBOUND') "$unboundPracticalText was not returned"

    $degraded = Wait-TransitionState 'COMPETITION' @('DEGRADED', 'FAILED')
    $startSteps = @($degraded.steps | Where-Object { $_.actionType -eq 'START_COMPETITION_ENVIRONMENT' })
    Assert-Value ($startSteps.Count -eq 1) 'Only the bound slot may receive START_COMPETITION_ENVIRONMENT'
    Assert-Value ($startSteps[0].environmentId -eq $boundSlots[0].environmentId) 'Competition start targeted the wrong slot'
    Assert-Value ($degraded.state -eq 'DEGRADED' -or $degraded.state -eq 'FAILED') 'Injected failure did not become DEGRADED'
    $trainingSnapshotCount = @($degraded.steps | Where-Object { $_.actionType -eq 'STOP_TRAINING_ENVIRONMENT' }).Count

    Invoke-FakeAgentHook $env:XKP_TEST_FAKE_AGENT_RECOVER_URL
    $retry = Invoke-JsonApi -Method Post -Path "operations/mode-transitions/$($degraded.transitionId)/retry" -Token $operations.tokenValue
    Assert-Value ($retry.code -eq 200) 'Operational retry failed'
    [void](Wait-TransitionState 'COMPETITION' @('SUCCEEDED'))

    $boundCompetitionToken = $boundAfter.tokenValue
    $unboundCompetitionToken = $unboundAfter.tokenValue
    Set-PlatformMode 'TRAINING'
    foreach ($oldToken in @($boundCompetitionToken, $unboundCompetitionToken)) {
        $stale = Invoke-JsonApi -Method Get -Path 'user/me' -Token $oldToken -AllowFailure
        Assert-Value ($stale.httpStatus -eq 401 -or $stale.body.code -eq 401) 'Competition token survived exit to TRAINING'
    }
    $exit = Wait-TransitionState 'TRAINING' @('SUCCEEDED')
    $restoreCount = @($exit.steps | Where-Object { $_.actionType -eq 'RESTORE_TRAINING_ENVIRONMENT' }).Count
    Assert-Value ($restoreCount -eq $trainingSnapshotCount) 'Exit did not restore exactly the captured training snapshot'

    $summary.Add('initial=TRAINING')
    $summary.Add('entered=COMPETITION')
    $summary.Add('failure=DEGRADED')
    $summary.Add('retry=SUCCEEDED')
    $summary.Add('final=TRAINING')
    Write-Host ('competition mode flow passed: ' + ($summary -join '; '))
} finally {
    if ($script:adminToken) {
        try {
            $cleanupMode = Invoke-JsonApi -Method Get -Path 'admin/platform-mode' -Token $script:adminToken
            if ($cleanupMode.data.mode -eq 'COMPETITION') { Set-PlatformMode 'TRAINING' }
        } catch {
            Write-Warning 'Cleanup could not confirm or restore TRAINING mode'
        }
    }
}
