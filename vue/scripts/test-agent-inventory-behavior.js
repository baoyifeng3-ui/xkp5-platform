const assert = require('assert')
const fs = require('fs')
const vm = require('vm')
function component(path, globals) {
  const source = require('vue-template-compiler').parseComponent(fs.readFileSync(path, 'utf8')).script.content
    .replace(/import[\s\S]*?from\s*["'][^"']+["'];?/g, '').replace('export default', 'module.exports =')
  const context = { module: { exports: {} }, ...globals }
  vm.runInNewContext(source, context)
  return context.module.exports
}
const inventory = component('src/views/operations/ImageRegistryView.vue', { ImageUploadDialog: {}, ImageDeploymentDialog: {} })
const rows = [{ id: 'sha256:a', repository: 'original', tag: 'latest', sizeBytes: 100 }, { id: 'sha256:a', repository: 'xkp/code', tag: 'v1', sizeBytes: 100 }]
const ctx = { ...inventory.methods, files: [{ originalFilename: 'code.tar', imageRepository: 'xkp/code', imageTag: 'v1' }] }
const images = ctx.images({ latestMetrics: { images: rows } })
assert.strictEqual(images.length, 1, 'two tags must count as one image')
assert.strictEqual(ctx.imageLabel(images[0]), 'code.tar')
assert.strictEqual(images[0].repoTags.length, 2)
assert.strictEqual(rows.length, 2, 'do not mutate heartbeat inventory')
const agent = component('src/views/operations/ProcessingAgents.vue', { AgentStatusTable: {}, RootTerminalDialog: {}, PortPoolDialog: {} })
assert.strictEqual(agent.computed.upgradeSupported.call({ selected: { upgradeAvailable: false, agentVersion: '0.2.31' } }), false)
assert.strictEqual(agent.computed.upgradeSupported.call({ selected: { upgradeAvailable: true, agentVersion: '0.2.9' } }), true)
console.log('Agent upgrade and unique image behavior passed')
const training = component('src/views/management/TrainingManagement.vue', {})
const creation = { ...training.methods, results: [{ userId: 1, success: true, operation: { environmentId: 'env', operationId: 'op' } }], environments: [{ environmentId: 'env', actualState: 'ERROR', operationId: 'op', operationState: 'FAILED', resultMessage: 'certificate missing' }] }
creation.syncCreationResults()
assert.strictEqual(creation.results[0].state, 'FAILED')
assert.strictEqual(creation.results[0].message, 'certificate missing')
console.log('Creation result follows actual Agent failure')
