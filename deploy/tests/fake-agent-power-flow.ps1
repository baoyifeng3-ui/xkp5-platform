param(
    [string]$ManagementBaseUrl = "http://localhost:19241",
    [string]$AgentBaseUrl = "http://localhost:19241/agent/v1",
    [int]$OfflineWaitSeconds = 22
)

$ErrorActionPreference = "Stop"

function Require-EnvironmentValue([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable $Name is not set"
    }
    return $value
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body
    )
    $parameters = @{ Method = $Method; Uri = $Uri; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Assert-HttpStatus {
    param(
        [int]$ExpectedStatus,
        [scriptblock]$Request,
        [string]$FailureMessage
    )
    try {
        & $Request | Out-Null
    } catch {
        $actualStatus = [int]$_.Exception.Response.StatusCode
        if ($actualStatus -eq $ExpectedStatus) {
            return
        }
        throw "$FailureMessage (expected HTTP $ExpectedStatus, received HTTP $actualStatus)"
    }
    throw "$FailureMessage (request unexpectedly succeeded)"
}

$username = Require-EnvironmentValue "XKP_TEST_ADMIN_USERNAME"
$password = Require-EnvironmentValue "XKP_TEST_ADMIN_PASSWORD"
$agentId = $null

try {
    $login = Invoke-RestMethod -Method Post -Uri "$ManagementBaseUrl/user/login" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{ userName = $username; password = $password }
    if ($login.code -ne 200 -or [string]::IsNullOrWhiteSpace($login.data.tokenValue)) {
        throw "Administrator login failed"
    }
    $adminHeaders = @{ satoken = $login.data.tokenValue }

    $tokenResponse = Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/super-admin/processing-agents/registration-tokens" `
        -Headers $adminHeaders -Body @{ label = "fake-agent-power-flow" }
    if ($tokenResponse.code -ne 200 -or [string]::IsNullOrWhiteSpace($tokenResponse.data.token)) {
        throw "Registration token creation failed"
    }

    $suffix = [Guid]::NewGuid().ToString("N")
    $registration = Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/register" -Body @{
        token = $tokenResponse.data.token
        displayName = "Power Flow Test"
        machineDigest = $suffix + $suffix
        hostname = "fake-agent"
        primaryIp = "127.0.0.2"
        macAddress = "02:00:00:00:00:01"
        agentVersion = "integration-test"
    }
    $agentId = $registration.agentId
    $agentHeaders = @{ Authorization = "Bearer $($registration.credential)" }
    if ([string]::IsNullOrWhiteSpace($agentId) -or [string]::IsNullOrWhiteSpace($registration.credential)) {
        throw "Agent registration failed"
    }

    $heartbeat = @{
        agentId = $agentId
        bootId = [Guid]::NewGuid().ToString()
        sequence = 1
        timestamp = [DateTime]::UtcNow.ToString("o")
        agentVersion = "integration-test"
        metrics = @{
            cpuPercent = 1
            ramTotalBytes = 1024
            ramUsedBytes = 256
            ramPercent = 25
            systemDiskTotalBytes = 1024
            systemDiskUsedBytes = 256
            systemDiskPercent = 25
            workspaceDiskTotalBytes = 1024
            workspaceDiskUsedBytes = 256
            workspaceDiskPercent = 25
            dockerAvailable = $true
            dockerVersion = "integration-test"
            runningEnvironmentCount = 0
            runningContainerCount = 0
            collectorErrors = @{}
        }
    }
    Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/heartbeat" -Headers $agentHeaders -Body $heartbeat | Out-Null

    $first = Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/processing-agents/$agentId/shutdown" -Headers $adminHeaders
    $duplicate = Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/processing-agents/$agentId/shutdown" -Headers $adminHeaders
    if ($first.data.commandId -ne $duplicate.data.commandId) {
        throw "Duplicate shutdown created a second command"
    }

    $poll = Invoke-JsonRequest -Method Get -Uri "$AgentBaseUrl/commands/poll?waitSeconds=0" `
        -Headers $agentHeaders
    if ($poll.commands.Count -ne 1) {
        throw "Expected exactly one leased shutdown command"
    }
    $command = $poll.commands[0]
    Assert-HttpStatus -ExpectedStatus 409 -FailureMessage "A stale lease token was accepted" -Request {
        Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/commands/$($command.commandId)/start" `
            -Headers $agentHeaders -Body @{ leaseToken = [Guid]::NewGuid().ToString() }
    }
    Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/commands/$($command.commandId)/start" `
        -Headers $agentHeaders -Body @{ leaseToken = $command.leaseToken } | Out-Null
    $accepted = Invoke-JsonRequest -Method Post `
        -Uri "$AgentBaseUrl/commands/$($command.commandId)/result" `
        -Headers $agentHeaders -Body @{
            leaseToken = $command.leaseToken
            success = $true
            code = "SHUTDOWN_ACCEPTED"
            message = "integration test accepted poweroff"
        }
    if ($accepted.state -ne "RUNNING") {
        throw "Agent success result bypassed offline confirmation"
    }

    Start-Sleep -Seconds $OfflineWaitSeconds
    $history = Invoke-JsonRequest -Method Get `
        -Uri "$ManagementBaseUrl/admin/processing-agents/$agentId/commands" -Headers $adminHeaders
    $completed = @($history.data) | Where-Object { $_.commandId -eq $command.commandId } | Select-Object -First 1
    if ($null -eq $completed -or $completed.state -ne "SUCCEEDED" `
            -or $completed.resultCode -ne "OFFLINE_CONFIRMED") {
        throw "Shutdown was not reconciled to SUCCEEDED/OFFLINE_CONFIRMED"
    }

    $heartbeat.sequence = 2
    $heartbeat.timestamp = [DateTime]::UtcNow.ToString("o")
    Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/heartbeat" `
        -Headers $agentHeaders -Body $heartbeat | Out-Null
    $cancelledRequest = Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/processing-agents/$agentId/shutdown" -Headers $adminHeaders
    Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/super-admin/processing-agents/$agentId/disable" `
        -Headers $adminHeaders | Out-Null
    $cancelledHistory = Invoke-JsonRequest -Method Get `
        -Uri "$ManagementBaseUrl/admin/processing-agents/$agentId/commands" -Headers $adminHeaders
    $cancelled = @($cancelledHistory.data) | Where-Object {
        $_.commandId -eq $cancelledRequest.data.commandId
    } | Select-Object -First 1
    if ($null -eq $cancelled -or $cancelled.state -ne "FAILED" `
            -or $cancelled.resultCode -ne "AGENT_DISABLED") {
        throw "Disabling the Agent did not cancel its active command"
    }
    Assert-HttpStatus -ExpectedStatus 403 -FailureMessage "A disabled Agent was allowed to heartbeat" -Request {
        Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/heartbeat" `
            -Headers $agentHeaders -Body $heartbeat
    }

    Write-Host "fake Agent power flow passed: duplicate-safe, stale-lease-rejected, offline-confirmed, disable-cancelled"
} finally {
    if ($null -ne $agentId -and $null -ne $adminHeaders) {
        try {
            Invoke-JsonRequest -Method Delete `
                -Uri "$ManagementBaseUrl/super-admin/processing-agents/$agentId" `
                -Headers $adminHeaders | Out-Null
        } catch {
            Write-Warning "Could not remove fake Agent $agentId"
        }
    }
}
