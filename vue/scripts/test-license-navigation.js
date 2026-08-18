const assert = require('assert')
const fs = require('fs')

const router = fs.readFileSync('src/router/index.js', 'utf8')
const api = fs.readFileSync('src/api/License.js', 'utf8')

assert.ok(router.includes("path: 'license'"), 'missing management license child route')
assert.ok(router.includes("name: 'PlatformLicense'"), 'missing PlatformLicense route name')
assert.ok(api.includes("url: 'admin/license/status'"), 'missing license status API')
assert.ok(api.includes("responseType: 'blob'"), 'request download must use blob response')
console.log('license navigation tests passed')
