const assert = require('assert')
const fs = require('fs')
const vm = require('vm')
const source = fs.readFileSync('src/views/operations/ImageRegistryView.vue', 'utf8').split('<script>')[1].split('</script>')[0].replace(/^import .*$/gm, '').replace('export default', 'module.exports =')
let states = []; const messages = []; let refreshes = 0; let requests = 0
const sandbox = { module: {}, ImageUploadDialog: {}, ImageDeploymentDialog: {}, setTimeout: resolve => resolve(), getDockerCommand: async () => ({ data: states.shift() || { state: 'RUNNING' } }), deleteDockerImage: async () => { requests++; return { data: { commandId: 'delete' } } }, deleteDockerContainer: async () => { requests++; return { data: { commandId: 'delete' } } } }
vm.runInNewContext(source, sandbox)
const component = sandbox.module.exports
const page = { ...component.data(), $confirm: async () => {}, $message: { info () {}, success: text => messages.push(['success', text]), error: text => messages.push(['error', text]) } }
for (const [key, method] of Object.entries(component.methods)) page[key] = method.bind(page)
page.loadAll = async () => { refreshes++ }
;(async () => {
  page.files = [{ fileId: 'f', originalFilename: '镜像.tar', imageRepository: 'xkp/image', imageTag: 'latest' }]
  assert.strictEqual(page.deploymentLabel({ fileId: 'f' }), '镜像.tar')
  assert.strictEqual(page.imageLabel({ repository: 'xkp/image', tag: 'latest' }), '镜像.tar')
  states = [{ state: 'FAILED', resultMessage: 'container still exists' }]
  await page.removeDockerImage({ agentId: 'server' }, { repository: 'xkp/image', tag: 'latest', id: 'sha256:abc' })
  assert.strictEqual(messages[0][0], 'error'); assert.strictEqual(refreshes, 0)
  states = [{ state: 'RUNNING' }, { state: 'SUCCEEDED' }]
  await page.removeDockerContainer({ agentId: 'server' }, { name: 'container', id: 'abc' })
  assert.strictEqual(messages[1][0], 'success'); assert.strictEqual(refreshes, 1)
  page.$confirm = async () => { throw 'cancel' }
  await page.removeDockerContainer({ agentId: 'server' }, { name: 'container', id: 'abc' })
  assert.strictEqual(requests, 2); assert.strictEqual(messages.length, 2)
  await assert.rejects(page.waitDockerDelete('server', { data: { commandId: 'timeout' } }), /超时/)
  assert.strictEqual(messages.length, 2)
  console.log('docker delete behavior passed')
})().catch(error => { console.error(error); process.exit(1) })
