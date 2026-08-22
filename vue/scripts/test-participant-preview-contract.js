const assert = require('assert')
const fs = require('fs')
const preview = require('../src/services/participantPreview')
const navigation = require('../src/navigation/roleNavigation')

assert.strictEqual(preview.previewHome('TRAINING'), '/course-platform')
assert.strictEqual(preview.previewHome('COMPETITION'), '/Publicity')
assert.strictEqual(preview.previewHome('UNKNOWN'), '/course-platform')
assert.strictEqual(preview.isPreviewRoute({ query: { preview: '1' } }), true)
assert.strictEqual(preview.isPreviewRoute({ query: { preview: 1 } }), false)
assert.strictEqual(preview.isPreviewRoute({ query: {} }), false)
assert.strictEqual(preview.isPreviewRoute(null), false)

assert.deepStrictEqual(navigation.previewDestinations, [
  '/course-platform', '/resource-center', '/training-environment',
  '/Publicity', '/Home', '/Question', '/Detect', '/competition-practical'
])

const page = fs.readFileSync('src/views/management/CompetitionPreview.vue', 'utf8')
assert.doesNotMatch(page, /el-radio-group|el-radio-button|preview-toolbar|刷新预览/)
assert.match(page, /getPlatformMode/)
assert.match(page, /previewHome/)
assert.match(page, /preview=1/)
assert.match(page, /v-loading="loading"/)
assert.match(page, /loadError/)

const router = fs.readFileSync('src/router/index.js', 'utf8')
assert.match(router, /getRole\(\)\s*===\s*['"]ADMIN['"]/)
assert.match(router, /isPreviewRoute\(to\)/)
assert.match(router, /roleNavigation\.previewDestinations\.includes\(to\.path\)/)
assert.match(router, /if\s*\(!adminPreview\s*&&\s*requiredRoles/)
assert.match(router, /if\s*\(!adminPreview\s*&&\s*getRole\(\)\s*===\s*['"]USER['"]\s*&&\s*requiredModes/)

console.log('participant preview contract passed')
