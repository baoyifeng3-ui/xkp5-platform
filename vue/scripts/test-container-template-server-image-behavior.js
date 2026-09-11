const assert = require('assert')
const fs = require('fs')
const vm = require('vm')
const source = fs.readFileSync('src/views/operations/ContainerTemplates.vue', 'utf8').split('<script>')[1].split('</script>')[0].replace(/^import .*$/gm, '').replace('export default', 'module.exports =')
let saved = []
const sandbox = { module: {}, listImageFiles: async () => ({ data: [{ fileId: 'file-1', imageRepository: 'xkp/anno', imageTag: 'latest' }] }), listImageDeployments: async () => ({ data: [{ fileId: 'file-1', state: 'SUCCEEDED', targetDigest: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }] }), listContainerTemplates: async () => ({ data: [] }), publishContainerTemplate: async payload => saved.push(payload) }
vm.runInNewContext(source, sandbox)
const component = sandbox.module.exports
const page = { ...component.data(), $message: { warning () {}, success () {}, error () {} } }
page.imageFiles = [{ fileId: 'file-1', imageRepository: 'xkp/anno', imageTag: 'latest', originalFilename: 'zy-anno.tar' }]
page.deployments = [{ fileId: 'file-1', state: 'SUCCEEDED', targetDigest: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }]
Object.defineProperty(page, 'deployedImages', { get: component.computed.deployedImages.bind(page) })
for (const [key, method] of Object.entries(component.methods)) page[key] = method.bind(page)
;(async () => {
  page.openCreate(); page.form.templateName = 'test'; page.form.imageReference = 'xkp/anno:latest'
  await page.publish()
  assert.strictEqual(saved.length, 1); assert.strictEqual(saved[0].imageReference, 'xkp/anno:latest')
  assert.strictEqual(page.deployedImages[0].imageReference, 'xkp/anno:latest')
  console.log('container template pushed image behavior passed')
})().catch(error => { console.error(error); process.exit(1) })
