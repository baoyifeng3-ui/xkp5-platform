const assert = require('assert')
const fs = require('fs')

const api = fs.readFileSync('src/api/Match.js', 'utf8')
const store = fs.readFileSync('src/store/modules/Match.js', 'utf8')
const question = fs.readFileSync('src/components/Question.vue', 'utf8')
const detectA = fs.readFileSync('src/views/DetectA.vue', 'utf8')

assert.ok(api.includes("url: 'user/competition-environment'"))
assert.ok(api.includes("url: 'user/competition-environment/start'"))
assert.ok(store.includes('startCompetitionEnvironmentApi'))
assert.ok(store.includes("readiness === 'RUNNING'"))
assert.ok(question.includes('async onAnswerEnvironment'))
assert.ok(question.includes("this.$store.dispatch('Match/trainUrl')"))
assert.ok(question.includes("window.open('', '_blank')"))
assert.ok(!detectA.includes('previewOnly || !t100Url'))

console.log('competition environment access contract tests passed')
