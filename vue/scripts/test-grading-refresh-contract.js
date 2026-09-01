const assert = require('assert')
const fs = require('fs')

const admin = fs.readFileSync('src/views/Admin.vue', 'utf8')
assert.ok(admin.includes('ref="adminGrading"'))
assert.ok(admin.includes("if (value === 'grading' && this.$refs.adminGrading) this.$refs.adminGrading.reload()"))

console.log('grading refresh contract tests passed')
