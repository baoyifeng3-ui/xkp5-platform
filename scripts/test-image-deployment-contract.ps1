$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$service = Join-Path $root 'java/match-mgr/src/main/java/com/match/registry/service/ImageDeploymentService.java'
$command = Join-Path $root 'java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java'
$sql = Join-Path $root 'java/match-mgr/src/main/resources/db/migration/V26__image_registry_catalog.sql'
$v27 = Join-Path $root 'java/match-mgr/src/main/resources/db/migration/V27__image_deployment_active_slots.sql'

foreach ($file in @($service, $command, $sql, $v27)) { if (-not (Test-Path $file)) { throw "Missing contract file: $file" } }
$deployment = Get-Content -Raw $service
$agentCommand = Get-Content -Raw $command
$schema = Get-Content -Raw $sql
$migration = Get-Content -Raw $v27
if ($deployment -notmatch 'UPDATE_CONTAINERS') { throw 'Default container update policy is missing' }
if ($deployment -notmatch 'IMAGE_ONLY') { throw 'Image-only policy is missing' }
if ($deployment -notmatch 'selectActiveSlotForUpdate') { throw 'Per-agent/component active-slot lock is missing' }
if ($deployment -notmatch 'setTargetDigest') { throw 'Desired digest is not persisted' }
if ($agentCommand -notmatch 'DEPLOY_IMAGE') { throw 'Image command type is missing' }
if ($agentCommand -notmatch '\^sha256:\[0-9a-f\]\{64\}') { throw 'Command digest validation is missing' }
if ($agentCommand -match 'password|credential|rawImage|imageContents') { throw 'Command contract contains sensitive payload fields' }
if ($schema -notmatch 'uk_image_deployment_active') { throw 'Active deployment uniqueness contract is missing' }
if ($migration -notmatch 'uk_image_deployment_agent_component_active') { throw 'Agent/component uniqueness migration is missing' }
if ($migration -notmatch 'active_agent_component_key VARCHAR') { throw 'Active slot migration is missing' }
if ($migration -notmatch 'command_id VARCHAR') { throw 'Deployment command identity migration is missing' }
if ($migration -notmatch 'idempotency_key VARCHAR') { throw 'Immutable idempotency key migration is missing' }
if ($migration -notmatch 'uk_image_deployment_agent_component_idempotency') { throw 'Idempotency uniqueness migration is missing' }
if ($migration -notmatch 'SUBSTRING_INDEX\(active_deployment_key') { throw 'Historical active idempotency backfill is missing' }
if ($deployment -notmatch 'indexOf') { throw 'Composite idempotency key delimiter must be rejected' }
if ($agentCommand -notmatch 'deploymentId') { throw 'Deployment identity is missing from command payload' }
Write-Output 'Image deployment contract checks passed.'
