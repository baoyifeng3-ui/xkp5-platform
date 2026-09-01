const fs = require('fs')
const frontend = fs.readFileSync('src/components/operations/ImageUploadDialog.vue', 'utf8')
const backend = fs.readFileSync('../java/match-mgr/src/main/java/com/match/registry/service/ImageUploadService.java', 'utf8')

if (!frontend.includes('const CHUNK_SIZE = 64 * 1024 * 1024')) throw new Error('image upload chunks must be 64 MiB')
if (backend.includes('stagedBytes() - prior + bytes')) throw new Error('quota reservation must not scan the full staging tree for every chunk')
if (!backend.includes('cachedStagedBytes')) throw new Error('staging quota must use the cached byte counter')
console.log('image upload performance contract passed')
