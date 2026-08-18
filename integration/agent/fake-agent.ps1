$ErrorActionPreference = 'Stop'

function Invoke-AgentRegistration {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [Parameter(Mandatory = $true)][string]$RegistrationToken,
        [Parameter(Mandatory = $true)][string]$MachineDigest,
        [string]$DisplayName = 'XKP5 integration Agent',
        [string]$Hostname = 'xkp5-fake-agent',
        [string]$PrimaryIp = '192.168.1.250',
        [string]$MacAddress = '02:00:00:00:00:01'
    )

    $body = @{
        token = $RegistrationToken
        displayName = $DisplayName
        machineDigest = $MachineDigest
        hostname = $Hostname
        primaryIp = $PrimaryIp
        macAddress = $MacAddress
        agentVersion = 'integration-test'
    } | ConvertTo-Json

    Invoke-RestMethod -Method Post -Uri "$($BaseUrl.TrimEnd('/'))/register" `
        -ContentType 'application/json' -Body $body
}

function New-AgentHeartbeatBody {
    param(
        [Parameter(Mandatory = $true)][string]$AgentId,
        [Parameter(Mandatory = $true)][string]$BootId,
        [Parameter(Mandatory = $true)][long]$Sequence
    )

    @{
        agentId = $AgentId
        bootId = $BootId
        sequence = $Sequence
        timestamp = [DateTime]::UtcNow.ToString('o')
        agentVersion = 'integration-test'
        metrics = @{
            cpuPercent = 24.5
            ramTotalBytes = 34359738368
            ramUsedBytes = 17179869184
            ramPercent = 50.0
            gpuModel = 'NVIDIA GeForce RTX 2080'
            gpuPercent = $null
            gpuTemperatureCelsius = 62
            gpuMemoryTotalBytes = 8589934592
            gpuMemoryUsedBytes = 2147483648
            gpuMemoryPercent = $null
            systemDiskTotalBytes = 536870912000
            systemDiskUsedBytes = 214748364800
            systemDiskPercent = 40.0
            workspaceDiskTotalBytes = 1073741824000
            workspaceDiskUsedBytes = 322122547200
            workspaceDiskPercent = 30.0
            dockerAvailable = $true
            dockerVersion = '27.5.1'
            runningEnvironmentCount = 2
            runningContainerCount = 4
            collectorErrors = @{ gpu = 'GPU_METRIC_PARTIAL' }
        }
    } | ConvertTo-Json -Depth 6
}

function Invoke-AgentHeartbeat {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [Parameter(Mandatory = $true)][string]$AgentId,
        [Parameter(Mandatory = $true)][string]$Credential,
        [Parameter(Mandatory = $true)][string]$BootId,
        [Parameter(Mandatory = $true)][long]$Sequence
    )

    $headers = @{ Authorization = "Bearer $Credential" }
    $body = New-AgentHeartbeatBody -AgentId $AgentId -BootId $BootId -Sequence $Sequence
    Invoke-RestMethod -Method Post -Uri "$($BaseUrl.TrimEnd('/'))/heartbeat" `
        -Headers $headers -ContentType 'application/json' -Body $body
}

function Get-AgentHttpStatus {
    param([Parameter(Mandatory = $true)]$ErrorRecord)

    $response = $ErrorRecord.Exception.Response
    if ($null -eq $response) { return $null }
    if ($null -ne $response.StatusCode.value__) { return [int]$response.StatusCode.value__ }
    return [int]$response.StatusCode
}
