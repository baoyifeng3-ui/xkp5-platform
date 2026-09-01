const assert = require('assert')
const fs = require('fs')

const admin = fs.readFileSync('src/views/Admin.vue', 'utf8')

assert.ok(admin.includes(':disabled="!paperSelectable(paper)"'))
assert.ok(admin.includes(':disabled="!paperDraft"'))

console.log('paper selection contract tests passed')
