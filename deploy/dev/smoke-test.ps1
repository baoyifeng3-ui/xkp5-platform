[CmdletBinding()]
param(
    [string]$BackendUrl = 'http://127.0.0.1:19141',
    [string]$FrontendUrl = 'http://127.0.0.1:19140',
    [string]$AdminUser,
    [string]$AdminPassword,
    [string]$UserName,
    [string]$UserPassword
)

$ErrorActionPreference = 'Stop'

function Invoke-JsonRequest {
    param([string]$Uri, [hashtable]$Body, [Microsoft.PowerShell.Commands.WebRequestSession]$Session)
    $params = @{ Uri = $Uri; Method = 'Post'; Body = $Body; ContentType = 'application/x-www-form-urlencoded'; UseBasicParsing = $true }
    if ($Session) { $params.WebSession = $Session }
    Invoke-WebRequest @params
}

if ([string]::IsNullOrWhiteSpace($AdminUser) -or [string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw 'AdminUser and AdminPassword are required.'
}

$health = Invoke-WebRequest -Uri "$BackendUrl/health" -UseBasicParsing
if ($health.StatusCode -lt 200 -or $health.StatusCode -ge 500) { throw "Backend health failed: $($health.StatusCode)" }
$frontend = Invoke-WebRequest -Uri $FrontendUrl -UseBasicParsing
if ($frontend.StatusCode -ne 200) { throw "Frontend failed: $($frontend.StatusCode)" }

$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$adminLogin = Invoke-JsonRequest "$BackendUrl/user/login" @{ UserName = $AdminUser; Password = $AdminPassword } $adminSession
$adminData = ($adminLogin.Content | ConvertFrom-Json).data
if ($adminData.role -notin @('ADMIN', 'SUPER_ADMIN')) { throw "Unexpected admin role: $($adminData.role)" }
try {
    $mode = Invoke-WebRequest -Uri "$BackendUrl/admin/platform-mode" -WebSession $adminSession -UseBasicParsing
} catch {
    $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'network' }
    throw "Platform mode endpoint unavailable ($status). Rebuild the current Java image before testing."
}
$modeData = ($mode.Content | ConvertFrom-Json).data
if ($modeData.mode -notin @('TRAINING', 'COMPETITION')) { throw "Unexpected platform mode: $($modeData.mode)" }

if ($UserName -and $UserPassword) {
    $userSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $userLogin = Invoke-JsonRequest "$BackendUrl/user/login" @{ UserName = $UserName; Password = $UserPassword } $userSession
    $userData = ($userLogin.Content | ConvertFrom-Json).data
    if ($userData.role -ne 'USER') { throw "Unexpected participant role: $($userData.role)" }
    Write-Output "Participant login passed: $($userData.userName) mode=$($userData.platformMode)"
}

Write-Output "Smoke test passed: backend=$BackendUrl frontend=$FrontendUrl mode=$($modeData.mode) adminRole=$($adminData.role)"
