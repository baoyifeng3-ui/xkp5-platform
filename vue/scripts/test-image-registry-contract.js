const assert = require('assert')
const fs = require('fs')

function source (path) {
  assert.ok(fs.existsSync(path), `missing ${path}`)
  return fs.readFileSync(path, 'utf8')
}

const router = source('src/router/index.js')
const navigation = source('src/navigation/roleNavigation.js')
const service = source('src/services/imageRegistry.js')
const view = source('src/views/operations/ImageRegistryView.vue')
const upload = source('src/components/operations/ImageUploadDialog.vue')
const deployment = source('src/components/operations/ImageDeploymentDialog.vue')

assert.ok(router.includes("path: 'image-registry'"), 'registry route must be registered')
assert.ok(router.includes("roles: ['ADMIN', 'SUPER_ADMIN']"), 'route must be admin-only')
assert.ok(router.includes("path: '/operations', component: OperationsShell, meta: { roles: ['ADMIN', 'SUPER_ADMIN'] }"), 'operations shell must be reachable for registry readers')
assert.ok(router.includes("path: '', name: 'OperationsHome', component: OperationsHome, meta: { roles: ['SUPER_ADMIN'] }"), 'operations home must remain super-admin only')
assert.ok(router.includes("path: 'processing-agents', name: 'ProcessingAgents', component: ProcessingAgents, meta: { roles: ['SUPER_ADMIN'] }"), 'processing agents must remain super-admin only')
assert.ok(navigation.includes('/operations/image-registry'), 'operations navigation must expose registry')
assert.ok(!navigation.includes("route: '/course-platform/image-registry'"), 'participant navigation must not expose registry')

assert.ok(service.includes('createImageUpload'), 'service must create resumable uploads')
assert.ok(service.includes('getImageUploadStatus'), 'service must retrieve resumable upload status')
assert.ok(service.includes('uploadImageChunk'), 'service must upload individual chunks')
assert.ok(service.includes('X-Chunk-SHA256'), 'chunk checksums must be sent')
assert.ok(service.includes('reviewImageArtifact'), 'service must support review')
assert.ok(service.includes('publishImageRelease'), 'service must publish immutable releases')
assert.ok(service.includes('deployImageRelease'), 'service must deploy releases')
assert.ok(service.includes('rollbackImageDeployment'), 'service must support rollback')

assert.ok(view.includes('getRole() === SUPER_ADMIN'), 'write actions must be SUPER_ADMIN gated')
assert.ok(view.includes('搜索镜像组、版本或摘要'), 'catalog must be searchable')
assert.ok(view.includes('registryDigest'), 'immutable digest must be visible')
assert.ok(view.includes("componentType === 'ANNOTATION'"), 'annotation releases must be independent')
assert.ok(view.includes("componentType === 'EDITOR'"), 'editor releases must be independent')
assert.ok(view.includes('PENDING_REVIEW'), 'pending review state must be visible')
assert.ok(view.includes('failureMessage'), 'deployment failures must be visible')
assert.ok(view.includes('rollbackImageDeployment'), 'rollback must be available')
assert.ok(view.includes('() => this.refreshDeployments()'), 'deployment polling must retain component context')
assert.ok(view.includes('.catch(() => {})'), 'deployment polling errors must not create unhandled rejections')

assert.ok(upload.includes('getImageUploadStatus'), 'upload dialog must resume from server status')
assert.ok(upload.includes('localStorage'), 'unfinished upload identity must survive a reload')
assert.ok(upload.includes('receivedBytes'), 'resume progress must use server received bytes')
assert.ok(upload.includes('crypto.subtle.digest'), 'each chunk must have a SHA-256 checksum')

assert.ok(deployment.includes('listProcessingAgents'), 'deployment must select target Agents')
assert.ok(deployment.includes("updatePolicy: 'UPDATE_CONTAINERS'"), 'container update must be the default')
assert.ok(deployment.includes('IMAGE_ONLY'), 'image-only deployment must be selectable')
assert.ok(deployment.includes('更新运行中容器'), 'default policy must show an explicit warning')
assert.ok(deployment.includes('confirm: true'), 'container updates must be explicitly confirmed')

for (const rendered of [view, upload, deployment]) {
  assert.ok(!/password|credential|authorization|rawPayload/i.test(rendered), 'credentials and raw command payloads must not be rendered')
}

console.log('image registry UI contract tests passed')
