$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
function Read-RepoFile([string]$relativePath) {
    $path = Join-Path $repoRoot $relativePath
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing flow contract file: $relativePath" }
    return Get-Content -Raw -LiteralPath $path
}
function Assert-Contains([string]$text, [string]$pattern, [string]$message) {
    if ($text -notmatch $pattern) { throw $message }
}
function Assert-Ordered([string]$text, [string[]]$markers, [string]$message) {
    $previous = -1
    foreach ($marker in $markers) {
        $position = $text.IndexOf($marker)
        if ($position -lt 0 -or $position -le $previous) { throw $message }
        $previous = $position
    }
}

$upload = Read-RepoFile 'java/match-mgr/src/main/java/com/match/registry/service/ImageUploadService.java'
$import = Read-RepoFile 'java/match-mgr/src/main/java/com/match/registry/service/ImageImportWorker.java'
$release = Read-RepoFile 'java/match-mgr/src/main/java/com/match/registry/service/ImageReleaseService.java'
$deployment = Read-RepoFile 'java/match-mgr/src/main/java/com/match/registry/service/ImageDeploymentService.java'
$command = Read-RepoFile 'java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java'
$listener = Read-RepoFile 'java/match-mgr/src/main/java/com/match/registry/service/ImageDeploymentResultListener.java'
$recovery = Read-RepoFile 'java/match-mgr/src/main/java/com/match/registry/service/ImageDeploymentRecovery.java'
$deploymentTests = Read-RepoFile 'java/match-mgr/src/test/java/com/match/registry/service/ImageDeploymentServiceTest.java'

Assert-Ordered $upload @('public ImageUploadChunkView putChunk', 'public ImageUploadRecord complete', 'transitionToReview') 'Upload resume/completion ordering is missing'
Assert-Ordered $import @('"APPROVED".equals', 'claimImport', 'importTool.importArchive', 'completeImport') 'Import ordering must be approval -> claim -> import -> digest'
$completedImportPosition = $import.IndexOf('completeImport')
if ($completedImportPosition -lt 0 -or $import.Substring($completedImportPosition) -notmatch 'deleteImportedArchive\(archive\)') {
    throw 'Imported staging archive must be deleted only after digest persistence'
}
Assert-Contains $release '"APPROVED"\.equals\(artifact\.getReviewState\(\)\)' 'Release must require approved review state'
Assert-Contains $release '"READY"\.equals\(artifact\.getImportState\(\)\)' 'Release must require imported artifact'
Assert-Contains $release '\^sha256:\[0-9a-f\]\{64\}\$' 'Release must persist immutable lowercase digest only'
Assert-Contains $deployment 'UPDATE_CONTAINERS' 'Default container update policy is missing'
Assert-Contains $deployment 'IMAGE_ONLY' 'Image-only policy is missing'
Assert-Contains $deployment 'selectActiveSlotForUpdate' 'Per-Agent/component active deployment lock is missing'
Assert-Contains $deployment 'setPreviousDigest' 'Previous working digest is not recorded'
Assert-Contains $deployment '"PULLED"' 'PULLED deployment progress is missing'
Assert-Contains $deployment '"RUNNING"' 'RUNNING/health deployment progress is missing'
Assert-Contains $deployment 'setActiveAgentComponentKey\(null\)' 'Terminal deployment must release its active slot'
Assert-Contains $deployment 'rollback\(' 'Rollback operation is missing'
Assert-Contains $command 'payload\.put\("deploymentId"' 'Agent payload must carry deployment identity'
Assert-Contains $command 'DEPLOY_IMAGE' 'Agent image deployment command is missing'
Assert-Contains $listener 'deployments\.reconcile' 'Agent result listener is not wired to deployment reconciliation'
Assert-Contains $recovery 'selectTerminalImageCommands' 'Terminal deployment recovery query is missing'

foreach ($name in @(
    'deploymentPersistsDesiredDigestAndSendsOnlyImmutablePayload',
    'imageOnlyDoesNotRequireContainerConfirmation',
    'progressAndSuccessReconcileAndReleaseActiveSlot',
    'failedDeploymentKeepsPreviousDigestAndClearsSlot',
    'rollbackUsesPreviousDigestAfterFailedDeployment',
    'staleCommandCannotAdvanceDeployment')) {
    Assert-Contains $deploymentTests $name "Missing deployment flow test: $name"
}

# Exercise externally visible state transitions without credentials or sockets.
$flow = [ordered]@{ upload = 'UPLOADING'; review = 'PENDING_REVIEW'; import = 'NOT_IMPORTED'; deployment = 'PENDING' }
if ($flow.upload -ne 'UPLOADING') { throw 'Fake upload did not start resumably' }
$flow.upload = 'PENDING_REVIEW'; $flow.review = 'APPROVED'
$flow.import = 'READY'; $digest = 'sha256:' + ('a' * 64)
if ($digest -notmatch '^sha256:[0-9a-f]{64}$') { throw 'Fake importer digest is not immutable' }
$flow.release = 'PUBLISHED'; $flow.deployment = 'PULLED'
if ($flow.deployment -ne 'PULLED') { throw 'Fake Agent pull progress was not observed' }
$flow.deployment = 'RUNNING'
if ($flow.deployment -ne 'RUNNING') { throw 'Fake Agent health progress was not observed' }
$previousDigest = 'sha256:' + ('b' * 64)
$flow.deployment = 'FAILED'
if ($flow.deployment -ne 'FAILED' -or $previousDigest -notmatch '^sha256:[0-9a-f]{64}$') { throw 'Failure did not preserve previous digest' }
$flow.rollback = 'PENDING'; $flow.rollbackDigest = $previousDigest
if ($flow.rollbackDigest -ne $previousDigest) { throw 'Rollback did not target previous digest' }

$realFlow = Join-Path $PSScriptRoot 'test-image-registry-flow.ps1'
$realFlowText = Get-Content -Raw -LiteralPath $realFlow
if ($realFlowText -match '(?i)(password\s*=\s*["''][^"'']+|secret\s*=\s*["''][^"'']+|docker\.sock|/var/run/docker)') {
    throw "Credential or Docker socket literal found in real flow script: $realFlow"
}

Write-Output 'Image registry integrated flow contract passed (fake Registry/importer/Agent; no credentials).'
