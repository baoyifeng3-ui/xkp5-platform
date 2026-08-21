$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$workerPath = Join-Path $repoRoot 'java/match-mgr/src/main/java/com/match/registry/service/ImageImportWorker.java'
$toolPath = Join-Path $repoRoot 'java/match-mgr/src/main/java/com/match/registry/service/RegistryImportTool.java'
$artifactMapperPath = Join-Path $repoRoot 'java/match-mgr/src/main/java/com/match/registry/persistence/ImageArtifactMapper.java'
$dockerfilePath = Join-Path $repoRoot 'deploy/registry/import-worker.Dockerfile'

@($workerPath, $toolPath, $artifactMapperPath, $dockerfilePath) | ForEach-Object {
    if (-not (Test-Path -LiteralPath $_)) {
        throw "Missing image import contract file: $_"
    }
}

$worker = Get-Content -Raw -LiteralPath $workerPath
$tool = Get-Content -Raw -LiteralPath $toolPath
$mapper = Get-Content -Raw -LiteralPath $artifactMapperPath
$dockerfile = Get-Content -Raw -LiteralPath $dockerfilePath

$approval = $worker.IndexOf('"APPROVED".equals')
$claim = $worker.IndexOf('artifactMapper.claimImport')
$toolCall = $worker.IndexOf('importTool.importArchive')
$persist = $worker.IndexOf('artifactMapper.completeImport')
$delete = $worker.IndexOf('deleteImportedArchive(archive)')
if ($approval -lt 0 -or $claim -le $approval -or $toolCall -le $claim -or
    $persist -le $toolCall -or $delete -le $persist) {
    throw 'Import state ordering must be APPROVED -> claim -> tool -> digest persistence -> staging deletion'
}

if ($mapper -notmatch "import_state IN \('NOT_IMPORTED', 'FAILED'\)" -or
    $mapper -notmatch "import_state = 'IMPORTING'" -or
    $mapper -notmatch "import_state = 'READY'") {
    throw 'Atomic claim, retry, and completion states are missing'
}
if ($worker -notmatch 'IMMUTABLE_DIGEST' -or
    $worker -notmatch '\^sha256:\[0-9a-f\]\{64\}\$') {
    throw 'Immutable lowercase Registry digest validation is missing'
}
if ($tool -notmatch 'MAX_CAPTURE_BYTES\s*=\s*64\s*\*\s*1024' -or
    $tool -notmatch 'BoundedStreamCollector') {
    throw 'Importer stdout/stderr must be drained with a bounded capture'
}
if ($tool -notmatch 'skopeo' -or $dockerfile -notmatch '\bskopeo\b') {
    throw 'Dedicated importer must use standard Docker archive-to-Registry tooling'
}
if ($tool -match '--dest-creds' -or $tool -notmatch '--dest-authfile') {
    throw 'Registry credentials must not be exposed in importer process arguments'
}
if ($worker -notmatch 'catch \(RegistryImportTool\.ImportException' -or
    $worker -notmatch 'artifactMapper\.failImport') {
    throw 'Tool failures must return the artifact to a retryable FAILED state'
}

$javaAndDockerfile = $worker + "`n" + $tool + "`n" + $dockerfile
if ($javaAndDockerfile -match 'docker\.sock|/var/run/docker') {
    throw 'Java importer must not use or mount a Docker socket'
}
if ($worker.Substring(0, $persist).Contains('Files.delete') -or
    $tool -match 'deleteIfExists\(request\.getArchive') {
    throw 'Staging archive deletion is forbidden before digest persistence'
}

Write-Host 'image import contract passed'
