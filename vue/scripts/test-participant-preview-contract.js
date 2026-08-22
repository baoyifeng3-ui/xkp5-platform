const assert = require('assert')
const fs = require('fs')
const preview = require('../src/services/participantPreview')
const navigation = require('../src/navigation/roleNavigation')

assert.strictEqual(preview.previewHome('TRAINING'), '/course-platform')
assert.strictEqual(preview.previewHome('COMPETITION'), '/Publicity')
assert.strictEqual(preview.previewHome('UNKNOWN'), '/course-platform')
assert.strictEqual(preview.isPreviewRoute({ path: '/Question', query: { preview: '1' } }, 'ADMIN'), true)
assert.strictEqual(preview.isPreviewRoute({ path: '/Question', query: { preview: '1' } }, 'USER'), false)
assert.strictEqual(preview.isPreviewRoute({ path: '/management', query: { preview: '1' } }, 'ADMIN'), false)
assert.strictEqual(preview.isPreviewRoute({ path: '/Question', query: { preview: 1 } }, 'ADMIN'), false)
assert.strictEqual(preview.isPreviewRoute({ path: '/Question', query: {} }, 'ADMIN'), false)
assert.strictEqual(preview.isPreviewRoute(null, 'ADMIN'), false)

assert.deepStrictEqual(navigation.previewDestinations, [
  '/course-platform', '/resource-center', '/training-environment',
  '/Publicity', '/Home', '/Question', '/Detect', '/competition-practical'
])
assert.strictEqual(navigation.previewDestinations, preview.previewDestinations)

const page = fs.readFileSync('src/views/management/CompetitionPreview.vue', 'utf8')
assert.doesNotMatch(page, /el-radio-group|el-radio-button|preview-toolbar|刷新预览/)
assert.match(page, /getPlatformMode/)
assert.match(page, /previewHome/)
assert.match(page, /preview=1/)
assert.match(page, /v-loading="loading"/)
assert.match(page, /loadError/)

const router = fs.readFileSync('src/router/index.js', 'utf8')
assert.match(router, /getRole\(\)\s*===\s*['"]ADMIN['"]/)
assert.match(router, /isPreviewRoute\(to, getRole\(\)\)/)
assert.match(router, /roleNavigation\.previewDestinations\.includes\(to\.path\)/)
assert.match(router, /if\s*\(!adminPreview\s*&&\s*requiredRoles/)
assert.match(router, /if\s*\(!adminPreview\s*&&\s*getRole\(\)\s*===\s*['"]USER['"]\s*&&\s*requiredModes/)

const noticePath = 'src/components/ParticipantPreviewNotice.vue'
assert.ok(fs.existsSync(noticePath), `${noticePath} must exist`)
const notice = fs.readFileSync(noticePath, 'utf8')
assert.match(notice, /当前为只读预览，操作不会提交。/)

const mutationViews = [
  'src/views/user/TrainingEnvironment.vue',
  'src/views/competition/CompetitionPractical.vue',
  'src/views/Detect.vue',
  'src/components/Question.vue'
]
mutationViews.forEach(file => {
  const text = fs.readFileSync(file, 'utf8')
  assert.match(text, /isPreviewRoute/, `${file} must detect preview mode`)
  assert.match(text, /getRole/, `${file} must authorize preview by role`)
  assert.match(text, /isPreviewRoute\(this\.\$route, getRole\(\)\)/, `${file} must require ADMIN preview`)
  assert.match(text, /previewOnly/, `${file} must expose preview-only UI state`)
})

const training = fs.readFileSync('src/views/user/TrainingEnvironment.vue', 'utf8')
assert.match(training, /async start \(row\)\s*{\s*if \(this\.previewOnly\) return/)
assert.match(training, /:disabled="previewOnly \|\| isPending\(scope\.row\)"/)

const practical = fs.readFileSync('src/views/competition/CompetitionPractical.vue', 'utf8')
assert.match(practical, /open \(value\)\s*{\s*if \(this\.previewOnly\) return/)
assert.match(practical, /:disabled="previewOnly \|\| !safeUrl/)
assert.match(practical, /<small v-if="!previewOnly">槽位/)
assert.match(practical, /adminParticipantPreviewCompetitionEnvironmentApi/)
assert.doesNotMatch(practical, /\{\{ environment\.readinessCode/)
assert.match(practical, /this\.previewOnly\s*\?\s*\{ readiness: 'DEGRADED' }/)
assert.match(practical, /v-else-if="previewOnly" class="practical-state"/)
assert.match(practical, /预览模式不连接实际比赛环境/)

const question = fs.readFileSync('src/components/Question.vue', 'utf8')
assert.match(question, /async saveAnswer \(item\)\s*{\s*if \(this\.previewOnly\) return/)
assert.match(question, /async submitPaper \(\)\s*{\s*if \(this\.previewOnly\) return/)
assert.match(question, /onAnswerEnvironment \(item\)\s*{\s*if \(this\.previewOnly\) return/)
assert.doesNotMatch(question, /isAdminPreview/)
assert.match(question, /v-if="!previewOnly && Number\(item\.subject\.score/)
assert.match(question, /const answerText = !this\.previewOnly/)
assert.match(question, /:id="previewOnly \? undefined : `subject-/)
assert.match(question, /adminParticipantPreviewSubjectsApi/)
assert.match(question, /sanitizePreviewRecord/)
assert.match(question, /answerSheet: null/)
assert.match(question, /const subject = \{\s*modular:/)
assert.match(question, /records\.map\(this\.sanitizePreviewRecord\)/)

const practicalAnswer = fs.readFileSync('src/components/dthj.vue', 'utf8')
assert.match(practicalAnswer, /async upfile\(\)\s*{\s*if \(this\.previewMode/)
assert.match(practicalAnswer, /mounted\(\)\s*{\s*if \(this\.previewMode\) return/)

;['src/views/Home.vue', 'src/views/Publicity.vue'].forEach(file => {
  const text = fs.readFileSync(file, 'utf8')
  assert.match(text, /isPreviewRoute/, `${file} must share preview route detection`)
  assert.match(text, /previewOnly/, `${file} must expose preview state for the participant shell`)
})

;['src/views/DetectA.vue', 'src/views/DetectB.vue'].forEach(file => {
  const text = fs.readFileSync(file, 'utf8')
  assert.match(text, /previewOnly/, `${file} must receive preview state`)
  assert.match(text, /async doTesting \(\)\s*{\s*if \(this\.previewOnly\) return/)
  assert.match(text, /async doCheckUtils \(\)\s*{\s*if \(this\.previewOnly\) return/)
  assert.match(text, /mounted \(\)\s*{\s*if \(!this\.previewOnly\) this\.tryUrl\(\)/)
})

const layout = fs.readFileSync('src/views/Layout.vue', 'utf8')
assert.match(layout, /isPreviewRoute/)
assert.match(layout, /previewOnly/)
assert.match(layout, /participantLocation \(path\)/)
assert.match(layout, /query:\s*this\.previewOnly\s*\?\s*{ preview: '1' }\s*:\s*undefined/)
assert.match(layout, /if \(!this\.previewOnly\) startUserActivity\(\)/)
assert.match(layout, /if \(!this\.isAdmin \|\| this\.previewOnly\)/)
assert.match(layout, /this\.participantLocation\('\/Question'\)/)
;['/Publicity', '/Home', '/Detect', '/competition-practical'].forEach(path => {
  assert.ok(layout.includes(`this.participantLocation('${path}')`), `Layout must preserve preview state for ${path}`)
})

const trainingShell = fs.readFileSync('src/layouts/NormalUserShell.vue', 'utf8')
assert.match(trainingShell, /isPreviewRoute/)
assert.match(trainingShell, /previewOnly/)
assert.match(trainingShell, /<PlatformShell/)
assert.match(trainingShell, /@navigate="navigate"/)
assert.match(trainingShell, /participantLocation \(path\)/)
assert.match(trainingShell, /if \(!this\.previewOnly\) startUserActivity\(\)/)
assert.match(trainingShell, /v-if="previewOnly" #account-actions/)
assert.match(trainingShell, /@logout="logout"/)
assert.match(trainingShell, /this\.previewOnly \? '参赛端预览'/)

const trainingApiSource = fs.readFileSync('src/views/user/TrainingEnvironment.vue', 'utf8')
assert.match(trainingApiSource, /adminParticipantPreviewTrainingEnvironmentsApi/)
assert.match(trainingApiSource, /scope\.row\.courseLabel \|\| scope\.row\.courseId/)

console.log('participant preview contract passed')
