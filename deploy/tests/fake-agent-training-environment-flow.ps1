param(
    [string]$ManagementBaseUrl = "http://localhost:19241",
    [string]$AgentBaseUrl = "http://localhost:19241/agent/v1",
    [string]$LicenseFile = $env:XKP_TEST_LICENSE_FILE,
    [int]$UserId = [int]$env:XKP_TEST_USER_ID,
    [int]$CourseId = [int]$env:XKP_TEST_COURSE_ID
)

$ErrorActionPreference = "Stop"

function Require-EnvironmentValue([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable $Name is not set"
    }
    return $value
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
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
        $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Assert-ApiOk([object]$Response, [string]$Action) {
    $message = if ($null -ne $Response) { $Response.msg } else { "empty response" }
    Assert-True ($null -ne $Response -and $Response.code -eq 200) "$Action failed: $message"
    return $Response.data
}

function Login([string]$Username, [string]$Password) {
    $response = Invoke-RestMethod -Method Post -Uri "$ManagementBaseUrl/user/login" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{ userName = $Username; password = $Password }
    Assert-True ($response.code -eq 200 -and -not [string]::IsNullOrWhiteSpace($response.data.tokenValue)) `
        "Login failed for $Username"
    return @{ satoken = $response.data.tokenValue }
}

function Import-TestLicense([hashtable]$Headers) {
    Assert-True (Test-Path -LiteralPath $LicenseFile -PathType Leaf) "Test license file does not exist"
    $expected = Get-Content -Raw -LiteralPath $LicenseFile | ConvertFrom-Json
    $statusResponse = Invoke-JsonRequest -Method Get -Uri "$ManagementBaseUrl/admin/license/status" `
        -Headers $Headers
    $status = Assert-ApiOk $statusResponse "Read current license status"
    if ($status.licenseId -eq $expected.payload.licenseId -and
            $status.state -in @("ACTIVE", "EXPIRING")) {
        Write-Host "matching active test license is already installed"
        return
    }
    $raw = & curl.exe --silent --show-error -X POST `
        -H "satoken: $($Headers.satoken)" -F "file=@$LicenseFile" `
        "$ManagementBaseUrl/admin/license/import"
    if ($LASTEXITCODE -ne 0) {
        throw "Test license import transport failed"
    }
    Assert-ApiOk ($raw | ConvertFrom-Json) "Test license import" | Out-Null
}

function Poll-OneCommand([hashtable]$AgentHeaders, [string]$ExpectedType) {
    $poll = Invoke-JsonRequest -Method Get -Uri "$AgentBaseUrl/commands/poll?waitSeconds=0" `
        -Headers $AgentHeaders
    $commands = @($poll.commands)
    Assert-True ($commands.Count -eq 1) "Expected one $ExpectedType command, got $($commands.Count)"
    Assert-True ($commands[0].type -eq $ExpectedType) `
        "Expected $ExpectedType, got $($commands[0].type)"
    return $commands[0]
}

function Complete-Command {
    param(
        [hashtable]$AgentHeaders,
        [object]$Command,
        [bool]$Success,
        [string]$Code,
        [string]$Message,
        [hashtable]$Details
    )
    Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/commands/$($Command.commandId)/start" `
        -Headers $AgentHeaders -Body @{ leaseToken = $Command.leaseToken } | Out-Null
    return Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/commands/$($Command.commandId)/result" `
        -Headers $AgentHeaders -Body @{
            leaseToken = $Command.leaseToken
            success = $Success
            code = $Code
            message = $Message
            details = $Details
        }
}

function Environment-State([hashtable]$Headers, [string]$EnvironmentId) {
    $response = Invoke-JsonRequest -Method Get -Uri "$ManagementBaseUrl/admin/training-environments" `
        -Headers $Headers
    $items = @(Assert-ApiOk $response "List training environments")
    return $items | Where-Object { $_.environmentId -eq $EnvironmentId } | Select-Object -First 1
}

function Pair-Details([string]$AnnotationState, [string]$EditorState,
                      [string]$Workspace, [bool]$SentinelPresent) {
    return @{
        pair = @{
            annotation = @{ state = $AnnotationState; code = "FAKE_AGENT" }
            editor = @{ state = $EditorState; code = "FAKE_AGENT" }
        }
        workspaceRelativePath = $Workspace
        sentinelPresent = $SentinelPresent
    }
}

$managementUri = [Uri]$ManagementBaseUrl
Assert-True ($managementUri.Host -in @("localhost", "127.0.0.1")) `
    "This destructive test is restricted to a local disposable platform"
Assert-True ($env:XKP_TEST_ALLOW_LICENSE_REPLACE -eq "YES") `
    "Set XKP_TEST_ALLOW_LICENSE_REPLACE=YES for the disposable test platform"
Assert-True ($UserId -gt 0) "XKP_TEST_USER_ID must identify an existing test user"
Assert-True ($CourseId -gt 0) "XKP_TEST_COURSE_ID must identify an existing test course"

$adminUsername = Require-EnvironmentValue "XKP_TEST_ADMIN_USERNAME"
$adminPassword = Require-EnvironmentValue "XKP_TEST_ADMIN_PASSWORD"
$superUsername = Require-EnvironmentValue "XKP_TEST_SUPER_ADMIN_USERNAME"
$superPassword = Require-EnvironmentValue "XKP_TEST_SUPER_ADMIN_PASSWORD"
$adminHeaders = Login $adminUsername $adminPassword
$superHeaders = Login $superUsername $superPassword
$agentId = $null

try {
    Import-TestLicense $adminHeaders

    $registrationToken = Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/super-admin/processing-agents/registration-tokens" `
        -Headers $superHeaders -Body @{ label = "fake-training-environment-flow" }) `
        "Create Agent registration token"

    $suffix = [Guid]::NewGuid().ToString("N")
    $registration = Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/register" -Body @{
        token = $registrationToken.token
        displayName = "Training Environment Flow"
        machineDigest = $suffix + $suffix
        hostname = "fake-training-agent"
        primaryIp = "127.0.0.3"
        macAddress = "02:00:00:00:00:02"
        agentVersion = "integration-test"
    }
    $agentId = $registration.agentId
    Assert-True (-not [string]::IsNullOrWhiteSpace($agentId)) "Agent registration failed"
    $agentHeaders = @{ Authorization = "Bearer $($registration.credential)" }

    $heartbeat = @{
        agentId = $agentId
        bootId = [Guid]::NewGuid().ToString()
        sequence = 1
        timestamp = [DateTime]::UtcNow.ToString("o")
        agentVersion = "integration-test"
        metrics = @{
            cpuPercent = 1; ramTotalBytes = 1024; ramUsedBytes = 256; ramPercent = 25
            systemDiskTotalBytes = 1024; systemDiskUsedBytes = 256; systemDiskPercent = 25
            workspaceDiskTotalBytes = 1024; workspaceDiskUsedBytes = 256; workspaceDiskPercent = 25
            dockerAvailable = $true; dockerVersion = "integration-test"
            runningEnvironmentCount = 0; runningContainerCount = 0; collectorErrors = @{}
        }
    }
    $ack = Invoke-JsonRequest -Method Post -Uri "$AgentBaseUrl/heartbeat" `
        -Headers $agentHeaders -Body $heartbeat
    Assert-True ($ack.operationGrant.allowed -eq $true) "Active license did not issue an operation grant"

    $templateSuffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
    $annotation = Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/super-admin/container-templates" -Headers $superHeaders -Body @{
            templateName = "Fake Annotation $templateSuffix"; componentType = "ANNOTATION"
            imageReference = "zy-anno:latest"; runtimeName = "sysbox-runc"; restartPolicy = "always"
            ports = @(@{ containerPort = 8080; protocol = "tcp" }); mountTarget = "/root/data"
            cpuLimitMillis = 2000; memoryLimitBytes = 2147483648; gpuEnabled = $false
        }) "Publish annotation template"
    $editor = Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/super-admin/container-templates" -Headers $superHeaders -Body @{
            templateName = "Fake Editor $templateSuffix"; componentType = "EDITOR"
            imageReference = "zy-contestv2:latest"; runtimeName = "nvidia"; restartPolicy = "always"
            ports = @(
                @{ containerPort = 9090; protocol = "tcp" },
                @{ containerPort = 8887; protocol = "tcp" },
                @{ containerPort = 5000; protocol = "tcp" }
            )
            mountTarget = "/home/student/data"; command = @("/bin/bash")
            cpuLimitMillis = 4000; memoryLimitBytes = 6442450944
            gpuEnabled = $true; gpuComputePercent = 50
        }) "Publish editor template"

    $created = Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/super-admin/training-environments" -Headers $superHeaders -Body @{
            userId = $UserId; courseId = $CourseId; agentId = $agentId; slotNumber = 1
            annotationTemplateId = $annotation.templateId
            annotationTemplateVersion = $annotation.templateVersion
            editorTemplateId = $editor.templateId; editorTemplateVersion = $editor.templateVersion
        }) "Create training environment"
    $environmentId = $created.environmentId
    $createCommand = Poll-OneCommand $agentHeaders "CREATE_TRAINING_ENVIRONMENT"
    Assert-True ($createCommand.payload.components.Count -eq 2) "Create did not contain a component pair"
    $workspace = $createCommand.payload.workspaceRelativePath
    Assert-True (-not [string]::IsNullOrWhiteSpace($workspace)) "Create workspace was empty"
    Assert-True (($createCommand.payload.components.mountTarget -contains "/root/data") -and
        ($createCommand.payload.components.mountTarget -contains "/home/student/data")) `
        "Component mount targets do not form the required shared pair"
    Complete-Command $agentHeaders $createCommand $true "ENVIRONMENT_CREATED" "fake create complete" `
        (Pair-Details "STOPPED" "STOPPED" $workspace $false) | Out-Null
    $sentinelPresent = $true

    $startFirst = Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/training-environments/$environmentId/start" `
        -Headers $adminHeaders) "Start training environment"
    $startDuplicate = Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/training-environments/$environmentId/start" `
        -Headers $adminHeaders) "Repeat start training environment"
    Assert-True ($startFirst.operationId -eq $startDuplicate.operationId) `
        "Repeated start created a second operation"
    $startCommand = Poll-OneCommand $agentHeaders "START_TRAINING_ENVIRONMENT"
    $startResult = Complete-Command $agentHeaders $startCommand $true "ENVIRONMENT_STARTED" `
        "fake start complete" (Pair-Details "RUNNING" "RUNNING" $workspace $sentinelPresent)
    $startReplay = Invoke-JsonRequest -Method Post `
        -Uri "$AgentBaseUrl/commands/$($startCommand.commandId)/result" -Headers $agentHeaders -Body @{
            leaseToken = $startCommand.leaseToken; success = $true; code = "ENVIRONMENT_STARTED"
            message = "fake start complete"
            details = Pair-Details "RUNNING" "RUNNING" $workspace $sentinelPresent
        }
    Assert-True ($startResult.state -eq $startReplay.state) "Terminal result replay was not idempotent"

    Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/training-environments/$environmentId/stop" `
        -Headers $adminHeaders) "Stop training environment" | Out-Null
    $stopCommand = Poll-OneCommand $agentHeaders "STOP_TRAINING_ENVIRONMENT"
    Complete-Command $agentHeaders $stopCommand $true "ENVIRONMENT_STOPPED" "fake stop complete" `
        (Pair-Details "STOPPED" "STOPPED" $workspace $sentinelPresent) | Out-Null

    Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/training-environments/$environmentId/restore" `
        -Headers $adminHeaders) "Restore training environment" | Out-Null
    $restoreCommand = Poll-OneCommand $agentHeaders "RESTORE_TRAINING_ENVIRONMENT"
    Assert-True ($restoreCommand.payload.workspaceRelativePath -eq $workspace) `
        "Restore changed the shared workspace"
    Complete-Command $agentHeaders $restoreCommand $true "ENVIRONMENT_RESTORED" "fake restore complete" `
        (Pair-Details "STOPPED" "STOPPED" $workspace $sentinelPresent) | Out-Null
    $restored = Environment-State $adminHeaders $environmentId
    Assert-True ($restored.actualState -eq "STOPPED") "Restore did not finish stopped"

    Assert-ApiOk (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/admin/training-environments/$environmentId/restore" `
        -Headers $adminHeaders) "Start partial-failure restore" | Out-Null
    $failedCommand = Poll-OneCommand $agentHeaders "RESTORE_TRAINING_ENVIRONMENT"
    Complete-Command $agentHeaders $failedCommand $false "ENVIRONMENT_EXECUTION_FAILED" `
        "fake editor create failed" (Pair-Details "STOPPED" "MISSING" $workspace $sentinelPresent) | Out-Null
    $degraded = Environment-State $adminHeaders $environmentId
    Assert-True ($degraded.actualState -eq "DEGRADED") "Partial failure did not become DEGRADED"
    Assert-True ($degraded.annotationContainerState -eq "STOPPED" -and
        $degraded.editorContainerState -eq "MISSING") "Component failure details were not preserved"

    Write-Host "fake training environment flow passed: shared-workspace, sentinel-preserved, idempotent, degraded"
} finally {
    if ($null -ne $agentId) {
        try {
            Invoke-JsonRequest -Method Delete `
                -Uri "$ManagementBaseUrl/super-admin/processing-agents/$agentId" `
                -Headers $superHeaders | Out-Null
        } catch {
            Write-Warning "Could not remove fake Agent $agentId from the disposable database"
        }
    }
}
