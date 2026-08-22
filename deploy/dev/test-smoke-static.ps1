$ErrorActionPreference = 'Stop'
$script = Get-Content -Raw (Join-Path $PSScriptRoot 'smoke-test.ps1')
if ($script -notmatch 'AdminUser and AdminPassword are required') { throw 'credential guard missing' }
if ($script -notmatch '/admin/platform-mode') { throw 'mode endpoint check missing' }
if ($script -notmatch 'Rebuild the current Java image') { throw 'stale-image guidance missing' }
if ($script -notmatch 'role -notin') { throw 'admin role check missing' }
if ($script -notmatch "role -ne 'USER'") { throw 'participant role check missing' }
Write-Output 'Smoke test static contract passed.'
