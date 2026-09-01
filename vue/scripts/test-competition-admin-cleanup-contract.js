const assert = require('assert')
const fs = require('fs')
const read = file => fs.readFileSync(file, 'utf8')
const layout = read('src/views/Layout.vue')
const admin = read('src/views/Admin.vue')
const grading = read('src/components/AdminGrading.vue')
const router = read('src/router/index.js')

assert.doesNotMatch(layout, /participantItems\.push\([^\n]*比赛实操/)
assert.match(router, /path:\s*["']\/competition-practical["']/)
assert.doesNotMatch(admin, /<header class="admin-utility">/)
assert.doesNotMatch(admin, /<h1>比赛控制<\/h1>|<h1>赛规赛程<\/h1>|<h1>试卷题目<\/h1>/)
;['loadPublicState', 'saveCompetitionContent', 'clearSubjectAnswers', 'clearAllSubjects', 'openCompetitionHelp', 'openSubjectCreateDialog']
  .forEach(name => assert.match(admin, new RegExp(`@click="${name}`)))
assert.doesNotMatch(grading, /<h1>试卷判分<\/h1>/)
assert.match(grading, /@click="createExport"/)
assert.match(grading, /@click="openExportHistory"/)

console.log('competition admin cleanup contract passed')
