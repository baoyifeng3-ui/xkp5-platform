$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$service = Join-Path $root 'java/match-mgr/src/main/java/com/match/registry/service/ImageDeploymentService.java'
$command = Join-Path $root 'java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java'
$sql = Join-Path $root 'java/match-mgr/src/main/resources/db/migration/V26__image_registry_catalog.sql'

foreach ($file in @($service, $command, $sql)) { if (-not (Test-Path $file)) { throw "Missing contract file: $file" } }
$deployment = Get-Content -Raw $service
$agentCommand = Get-Content -Raw $command
$schema = Get-Content -Raw $sql
if ($deployment -notmatch 'UPDATE_CONTAINERS') { throw 'Default container update policy is missing' }
if ($deployment -notmatch 'IMAGE_ONLY') { throw 'Image-only policy is missing' }
if ($deployment -notmatch 'selectActiveForUpdate') { throw 'Per-agent/component lock is missing' }
if ($deployment -notmatch 'setTargetDigest') { throw 'Desired digest is not persisted' }
if ($agentCommand -notmatch 'DEPLOY_IMAGE') { throw 'Image command type is missing' }
if ($agentCommand -notmatch '\^sha256:\[0-9a-f\]\{64\}') { throw 'Command digest validation is missing' }
if ($agentCommand -match 'password|credential|rawImage|imageContents') { throw 'Command contract contains sensitive payload fields' }
if ($schema -notmatch 'uk_image_deployment_active') { throw 'Active deployment uniqueness contract is missing' }
Write-Output 'Image deployment contract checks passed.'
