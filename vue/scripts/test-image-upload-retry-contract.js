const fs = require('fs')
const registry = fs.readFileSync('src/views/operations/ImageRegistryView.vue', 'utf8')
const dialog = fs.readFileSync('src/components/operations/ImageUploadDialog.vue', 'utf8')
const api = fs.readFileSync('src/services/imageRegistry.js', 'utf8')

for (const token of ['重试上传', 'retryUpload', 'getImageUploadByArtifact', ':resume-upload="resumeUpload"']) if (!registry.includes(token)) throw new Error(`registry retry contract missing ${token}`)
for (const token of ['resumeUpload', 'prepareOpen', 'originalFilename', '同名同大小文件', 'activeChunkSize', '选择原文件并继续', 'chooseAndContinue', 'this.$nextTick(() => this.start())']) if (!dialog.includes(token)) throw new Error(`upload dialog retry contract missing ${token}`)
if (!api.includes('getImageUploadByArtifact')) throw new Error('artifact upload status API missing')
console.log('image upload retry contract passed')
