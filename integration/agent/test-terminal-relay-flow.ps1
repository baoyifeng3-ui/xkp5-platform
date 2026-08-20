param(
    [string]$ManagementBaseUrl = 'https://localhost:19241',
    [string]$RelayBaseUrl = 'wss://localhost:19247',
    [string]$BrowserOrigin = 'https://localhost:19246',
    [string]$LicenseFile = $env:XKP_TEST_LICENSE_FILE,
    [string]$DatabaseContainer = $env:XKP_TEST_MYSQL_CONTAINER,
    [string]$ManagementContainer = $env:XKP_TEST_MANAGEMENT_CONTAINER,
    [string]$DatabaseName = 'match',
    [string]$DatabaseUser = 'root',
    [string]$DatabasePassword = $env:XKP_TEST_DB_PASSWORD,
    [int]$InjectedIdleSeconds = [int]$env:XKP_TEST_TERMINAL_IDLE_SECONDS
)

$ErrorActionPreference = 'Stop'

# Non-privileged fake PTY: this process is a deterministic echo PTY adapter only.
# Sec-WebSocket-Protocol carries the stable protocol and xkp-terminal-ticket.* value.
# The flow opens exactly one Agent peer and one browser peer; it never starts an OS shell.

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Require-Environment([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Required environment variable $Name is not set" }
    return $value
}

function Assert-LoopbackTls([string]$Value, [string]$Scheme, [string]$Name) {
    $uri = [Uri]$Value
    Assert-True ($uri.Scheme -eq $Scheme) "$Name must use $Scheme"
    Assert-True ($uri.Host -in @('localhost', '127.0.0.1', '::1')) "$Name must use loopback"
    Assert-True ([string]::IsNullOrEmpty($uri.Query) -and [string]::IsNullOrEmpty($uri.Fragment)) `
        "$Name must not contain query or fragment data"
}

function Get-HttpStatus($ErrorRecord) {
    $response = $ErrorRecord.Exception.Response
    if ($null -eq $response) { return $null }
    if ($null -ne $response.StatusCode.value__) { return [int]$response.StatusCode.value__ }
    return [int]$response.StatusCode
}

function Invoke-JsonRequest {
    param([string]$Method, [string]$Uri, [hashtable]$Headers = @{}, $Body)
    $parameters = @{ Method = $Method; Uri = $Uri; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    Invoke-RestMethod @parameters
}

function Get-ApiData($Response, [string]$Action) {
    Assert-True ($null -ne $Response -and $Response.code -eq 200) "$Action failed"
    return $Response.data
}

function Login([string]$Username, [string]$Password) {
    $response = Invoke-RestMethod -Method Post -Uri "$ManagementBaseUrl/user/login" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ userName = $Username; password = $Password }
    Assert-True ($response.code -eq 200 -and -not [string]::IsNullOrWhiteSpace($response.data.tokenValue)) `
        'Super-administrator login failed'
    return @{ satoken = $response.data.tokenValue }
}

function Import-DisposableLicense([hashtable]$Headers) {
    Assert-True (Test-Path -LiteralPath $LicenseFile -PathType Leaf) 'Disposable license file is missing'
    $raw = & curl.exe --silent --show-error -X POST -H "satoken: $($Headers.satoken)" `
        -F "file=@$LicenseFile" "$ManagementBaseUrl/admin/license/import"
    Assert-True ($LASTEXITCODE -eq 0) 'Disposable license upload failed'
    Get-ApiData ($raw | ConvertFrom-Json) 'Import disposable license' | Out-Null
}

function New-TerminalWebSocket {
    param([string]$Uri, [string[]]$Protocols, [hashtable]$Headers = @{})
    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    foreach ($protocol in $Protocols) { $socket.Options.AddSubProtocol($protocol) }
    foreach ($entry in $Headers.GetEnumerator()) { $socket.Options.SetRequestHeader($entry.Key, $entry.Value) }
    $timeout = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(10))
    try {
        $socket.ConnectAsync([Uri]$Uri, $timeout.Token).GetAwaiter().GetResult()
    } finally {
        $timeout.Dispose()
    }
    Assert-True ($socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) 'WebSocket did not open'
    Assert-True ($socket.SubProtocol -eq 'xkp-terminal-v1') 'Server did not select the stable terminal subprotocol'
    return $socket
}

function Send-WebSocket {
    param($Socket, [byte[]]$Bytes, [System.Net.WebSockets.WebSocketMessageType]$Type)
    $segment = [ArraySegment[byte]]::new($Bytes)
    $Socket.SendAsync($segment, $Type, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
}

function Receive-WebSocket {
    param($Socket, [int]$TimeoutSeconds = 10)
    $buffer = New-Object byte[] 65536
    $stream = [IO.MemoryStream]::new()
    $timeout = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSeconds))
    try {
        do {
            $segment = [ArraySegment[byte]]::new($buffer)
            $result = $Socket.ReceiveAsync($segment, $timeout.Token).GetAwaiter().GetResult()
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                return @{ Type = $result.MessageType; Bytes = [byte[]]@(); Text = '' }
            }
            $stream.Write($buffer, 0, $result.Count)
        } while (-not $result.EndOfMessage)
        $bytes = $stream.ToArray()
        return @{ Type = $result.MessageType; Bytes = $bytes; Text = [Text.Encoding]::UTF8.GetString($bytes) }
    } finally {
        $timeout.Dispose()
        $stream.Dispose()
    }
}

function Close-WebSocket($Socket) {
    if ($null -eq $Socket) { return }
    try {
        if ($Socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            $timeout = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(5))
            try {
                $Socket.CloseOutputAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                    'integration disconnect cleanup', $timeout.Token).GetAwaiter().GetResult()
            } finally { $timeout.Dispose() }
        }
    } catch { }
    $Socket.Dispose()
}

function Poll-TerminalCommand([hashtable]$AgentHeaders) {
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        $poll = Invoke-JsonRequest -Method Get `
            -Uri "$ManagementBaseUrl/agent/v1/commands/poll?waitSeconds=0" -Headers $AgentHeaders
        $command = @($poll.commands) | Where-Object { $_.type -eq 'OPEN_ROOT_TERMINAL' } | Select-Object -First 1
        if ($null -ne $command) { return $command }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Terminal command was not delivered'
}

function Start-TerminalCommand([hashtable]$AgentHeaders, $Command) {
    Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/agent/v1/commands/$($Command.commandId)/start" `
        -Headers $AgentHeaders -Body @{ leaseToken = $Command.leaseToken } | Out-Null
}

function Complete-TerminalCommand([hashtable]$AgentHeaders, $Command, [string]$Code) {
    Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/agent/v1/commands/$($Command.commandId)/result" `
        -Headers $AgentHeaders -Body @{
            leaseToken = $Command.leaseToken; success = $true; code = $Code
            message = 'terminal session closed'; details = @{}
        } | Out-Null
}

function Create-TerminalSession([hashtable]$SuperHeaders, [string]$AgentId) {
    return Get-ApiData (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/operations/processing-agents/$AgentId/terminal-sessions" `
        -Headers $SuperHeaders -Body @{ confirmation = 'OPEN_ROOT_TERMINAL' }) 'Create terminal session'
}

function Get-TerminalSession([hashtable]$SuperHeaders, [string]$SessionId) {
    return Get-ApiData (Invoke-JsonRequest -Method Get `
        -Uri "$ManagementBaseUrl/operations/terminal-sessions/$SessionId" `
        -Headers $SuperHeaders) 'Read terminal session'
}

function Issue-BrowserTicket([hashtable]$SuperHeaders, [string]$SessionId) {
    return Get-ApiData (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/operations/terminal-sessions/$SessionId/browser-ticket" `
        -Headers $SuperHeaders) 'Issue browser ticket'
}

function Issue-AgentTicket([hashtable]$AgentHeaders, $Command) {
    return Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/agent/v1/terminal-sessions/$($Command.payload.sessionId)/agent-ticket" `
        -Headers $AgentHeaders -Body @{ commandId = $Command.commandId; leaseToken = $Command.leaseToken }
}

function Assert-ReplayRejected([string]$Uri, [string]$Ticket, [hashtable]$Headers, [string]$Scenario) {
    $replay = $null
    try {
        $replay = New-TerminalWebSocket -Uri $Uri `
            -Protocols @('xkp-terminal-v1', "xkp-terminal-ticket.$Ticket") -Headers $Headers
        throw "$Scenario unexpectedly succeeded"
    } catch {
        if ($_.Exception.Message -eq "$Scenario unexpectedly succeeded") { throw }
    } finally { Close-WebSocket $replay }
}

function Wait-TerminalState([hashtable]$Headers, [string]$SessionId, [string[]]$States) {
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $session = Get-TerminalSession $Headers $SessionId
        if ($session.state -in $States) { return $session }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "unfinished session $SessionId"
}

function Invoke-MySql([string]$Sql) {
    $output = & docker exec --env "MYSQL_PWD=$DatabasePassword" $DatabaseContainer `
        mysql --batch --skip-column-names -u $DatabaseUser $DatabaseName -e $Sql
    Assert-True ($LASTEXITCODE -eq 0) 'Database assertion command failed'
    return ($output -join "`n").Trim()
}

function Assert-ShortIdleClockConfigured {
    $expectedOffset = 600 + $InjectedIdleSeconds
    $configuredOffset = [int]$env:MATCH_TERMINAL_TEST_CLOCK_OFFSET_SECONDS
    Assert-True ($configuredOffset -eq $expectedOffset) `
        "Development test clock offset must be $expectedOffset seconds"
}

function Assert-SentinelAbsent([string]$Sentinel) {
    $escaped = $Sentinel.Replace("'", "''")
    $databaseMatches = Invoke-MySql "SELECT (SELECT COUNT(*) FROM processing_agent_terminal_session WHERE INSTR(CONCAT_WS('|', COALESCE(end_message,''), COALESCE(end_reason,'')), '$escaped') > 0) + (SELECT COUNT(*) FROM processing_agent_command WHERE INSTR(CONCAT_WS('|', CAST(payload_json AS CHAR), COALESCE(result_message,''), COALESCE(CAST(result_json AS CHAR),'')), '$escaped') > 0);"
    Assert-True ($databaseMatches -eq '0') 'database sentinel absence assertion failed'
    $logs = & docker logs $ManagementContainer 2>&1
    Assert-True ($LASTEXITCODE -eq 0) 'Management log assertion command failed'
    Assert-True (-not (($logs -join "`n").Contains($Sentinel))) 'log sentinel absence assertion failed'
}

Assert-True ($env:XKP_TEST_ALLOW_TERMINAL_RELAY -eq 'YES') `
    'Set XKP_TEST_ALLOW_TERMINAL_RELAY=YES for this destructive disposable-stack test'
Assert-LoopbackTls $ManagementBaseUrl 'https' 'ManagementBaseUrl'
Assert-LoopbackTls $RelayBaseUrl 'wss' 'RelayBaseUrl'
Assert-LoopbackTls $BrowserOrigin 'https' 'BrowserOrigin'
Assert-True ($InjectedIdleSeconds -ge 1 -and $InjectedIdleSeconds -le 30) `
    'XKP_TEST_TERMINAL_IDLE_SECONDS must be between 1 and 30'
Assert-True (-not [string]::IsNullOrWhiteSpace($env:MATCH_TERMINAL_TEST_CLOCK_OFFSET_SECONDS)) `
    'MATCH_TERMINAL_TEST_CLOCK_OFFSET_SECONDS is required for the development test clock'
Assert-True (-not [string]::IsNullOrWhiteSpace($DatabaseContainer)) 'XKP_TEST_MYSQL_CONTAINER is required'
Assert-True (-not [string]::IsNullOrWhiteSpace($ManagementContainer)) 'XKP_TEST_MANAGEMENT_CONTAINER is required'
Assert-True (-not [string]::IsNullOrWhiteSpace($DatabasePassword)) 'XKP_TEST_DB_PASSWORD is required'

$superUsername = Require-Environment 'XKP_TEST_SUPER_ADMIN_USERNAME'
$superPassword = Require-Environment 'XKP_TEST_SUPER_ADMIN_PASSWORD'
$superHeaders = Login $superUsername $superPassword
$agentId = $null
$agentSocket = $null
$browserSocket = $null

try {
    Import-DisposableLicense $superHeaders
    $registrationToken = Get-ApiData (Invoke-JsonRequest -Method Post `
        -Uri "$ManagementBaseUrl/super-admin/processing-agents/registration-tokens" `
        -Headers $superHeaders -Body @{ label = 'terminal-relay-integration' }) 'Create registration token'
    $suffix = [Guid]::NewGuid().ToString('N')
    $registration = Invoke-JsonRequest -Method Post -Uri "$ManagementBaseUrl/agent/v1/register" -Body @{
        token = $registrationToken.token; displayName = 'Terminal Relay Fake Agent'
        machineDigest = $suffix + $suffix; hostname = 'terminal-relay-fake-agent'
        primaryIp = '127.0.0.8'; macAddress = '02:00:00:00:00:08'; agentVersion = 'integration-test'
    }
    $agentId = $registration.agentId
    $agentHeaders = @{ Authorization = "Bearer $($registration.credential)" }
    $heartbeat = Invoke-JsonRequest -Method Post -Uri "$ManagementBaseUrl/agent/v1/heartbeat" `
        -Headers $agentHeaders -Body @{
            agentId = $agentId; bootId = [Guid]::NewGuid().ToString(); sequence = 1
            timestamp = [DateTime]::UtcNow.ToString('o'); agentVersion = 'integration-test'
            metrics = @{ cpuPercent = 1; ramTotalBytes = 1024; ramUsedBytes = 256; ramPercent = 25
                systemDiskTotalBytes = 1024; systemDiskUsedBytes = 256; systemDiskPercent = 25
                workspaceDiskTotalBytes = 1024; workspaceDiskUsedBytes = 256; workspaceDiskPercent = 25
                dockerAvailable = $true; dockerVersion = 'fake'; runningEnvironmentCount = 0
                runningContainerCount = 0; collectorErrors = @{} }
        }
    Assert-True $heartbeat.accepted 'Fake Agent heartbeat was rejected'

    $session = Create-TerminalSession $superHeaders $agentId
    $command = Poll-TerminalCommand $agentHeaders
    Start-TerminalCommand $agentHeaders $command
    $agentTicket = Issue-AgentTicket $agentHeaders $command
    $agentUri = "$RelayBaseUrl/terminal/v1/agent/$($session.sessionId)"
    Assert-True ($command.payload.relayUrl -eq $agentUri) 'Command relay URL did not match the isolated WSS relay'
    $agentSocket = New-TerminalWebSocket -Uri $agentUri `
        -Protocols @('xkp-terminal-v1', "xkp-terminal-ticket.$($agentTicket.ticket)") `
        -Headers $agentHeaders
    Assert-ReplayRejected $agentUri $agentTicket.ticket $agentHeaders 'ticket replay rejection'

    $browserTicket = Issue-BrowserTicket $superHeaders $session.sessionId
    $browserUri = "$RelayBaseUrl/terminal/v1/browser/$($session.sessionId)"
    $browserSocket = New-TerminalWebSocket -Uri $browserUri `
        -Protocols @('xkp-terminal-v1', "xkp-terminal-ticket.$($browserTicket.ticket)") `
        -Headers @{ Origin = $BrowserOrigin }
    Assert-ReplayRejected $browserUri $browserTicket.ticket @{ Origin = $BrowserOrigin } `
        'browser ticket replay rejection'
    Wait-TerminalState $superHeaders $session.sessionId @('ACTIVE') | Out-Null

    try {
        Create-TerminalSession $superHeaders $agentId | Out-Null
        throw 'second-session conflict unexpectedly succeeded'
    } catch {
        if ($_.Exception.Message -eq 'second-session conflict unexpectedly succeeded') { throw }
        Assert-True ((Get-HttpStatus $_) -eq 409) 'second-session conflict did not return HTTP 409'
    }

    $sentinel = 'XKP_TERMINAL_SENTINEL_' + [Guid]::NewGuid().ToString('N')
    $sentinelBytes = [Text.Encoding]::UTF8.GetBytes($sentinel)
    Send-WebSocket $browserSocket $sentinelBytes ([System.Net.WebSockets.WebSocketMessageType]::Binary)
    $fakePtyInput = Receive-WebSocket $agentSocket
    Assert-True ($fakePtyInput.Type -eq [System.Net.WebSockets.WebSocketMessageType]::Binary) `
        'Fake PTY did not receive binary input'
    Assert-True ([Convert]::ToBase64String($fakePtyInput.Bytes) -eq [Convert]::ToBase64String($sentinelBytes)) `
        'Fake PTY input changed in transit'
    Send-WebSocket $agentSocket $fakePtyInput.Bytes ([System.Net.WebSockets.WebSocketMessageType]::Binary)
    $binaryEcho = Receive-WebSocket $browserSocket
    Assert-True ([Convert]::ToBase64String($binaryEcho.Bytes) -eq [Convert]::ToBase64String($sentinelBytes)) `
        'binary echo did not round-trip unchanged'

    $resizeJson = '{"type":"resize","columns":120,"rows":36}'
    Send-WebSocket $browserSocket ([Text.Encoding]::UTF8.GetBytes($resizeJson)) `
        ([System.Net.WebSockets.WebSocketMessageType]::Text)
    $resize = Receive-WebSocket $agentSocket
    Assert-True ($resize.Type -eq [System.Net.WebSockets.WebSocketMessageType]::Text -and `
        $resize.Text -eq $resizeJson) 'Resize control did not reach the fake PTY adapter'

    Close-WebSocket $browserSocket
    $browserSocket = $null
    $agentClose = Receive-WebSocket $agentSocket
    Assert-True ($agentClose.Type -eq [System.Net.WebSockets.WebSocketMessageType]::Close) `
        'disconnect cleanup did not close the Agent peer'
    Close-WebSocket $agentSocket
    $agentSocket = $null
    Complete-TerminalCommand $agentHeaders $command 'TERMINAL_SESSION_CLOSED'
    Wait-TerminalState $superHeaders $session.sessionId @('CLOSED', 'FAILED') | Out-Null

    $idleSession = Create-TerminalSession $superHeaders $agentId
    $idleCommand = Poll-TerminalCommand $agentHeaders
    Start-TerminalCommand $agentHeaders $idleCommand
    $idleAgentTicket = Issue-AgentTicket $agentHeaders $idleCommand
    $idleAgentUri = "$RelayBaseUrl/terminal/v1/agent/$($idleSession.sessionId)"
    $agentSocket = New-TerminalWebSocket -Uri $idleAgentUri `
        -Protocols @('xkp-terminal-v1', "xkp-terminal-ticket.$($idleAgentTicket.ticket)") `
        -Headers $agentHeaders
    $idleBrowserTicket = Issue-BrowserTicket $superHeaders $idleSession.sessionId
    $idleBrowserUri = "$RelayBaseUrl/terminal/v1/browser/$($idleSession.sessionId)"
    $browserSocket = New-TerminalWebSocket -Uri $idleBrowserUri `
        -Protocols @('xkp-terminal-v1', "xkp-terminal-ticket.$($idleBrowserTicket.ticket)") `
        -Headers @{ Origin = $BrowserOrigin }
    Wait-TerminalState $superHeaders $idleSession.sessionId @('ACTIVE') | Out-Null
    Assert-ShortIdleClockConfigured
    Wait-TerminalState $superHeaders $idleSession.sessionId @('CLOSED', 'FAILED') | Out-Null
    Complete-TerminalCommand $agentHeaders $idleCommand 'TERMINAL_IDLE_TIMEOUT'
    Close-WebSocket $browserSocket
    Close-WebSocket $agentSocket
    $browserSocket = $null
    $agentSocket = $null

    Assert-SentinelAbsent $sentinel
    Write-Host 'Terminal relay flow passed: binary echo, resize, ticket replay rejection, second-session conflict, disconnect cleanup, short idle clock, database sentinel absence, log sentinel absence.'
} finally {
    Close-WebSocket $browserSocket
    Close-WebSocket $agentSocket
    if ($null -ne $agentId) {
        try {
            Invoke-JsonRequest -Method Delete `
                -Uri "$ManagementBaseUrl/super-admin/processing-agents/$agentId" `
                -Headers $superHeaders | Out-Null
        } catch { Write-Warning 'Could not remove fake Agent from the disposable database' }
    }
}
