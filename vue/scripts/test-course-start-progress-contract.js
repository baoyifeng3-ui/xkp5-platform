const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/components/course/CourseLearningWorkspace.vue'), 'utf8')
for (const token of ['workspace-progress-panel', ':stroke-width="10"', '启动进度 {{ startProgress }}%', 'startStage']) {
  if (!source.includes(token)) throw new Error(`course start progress missing ${token}`)
}
const standalone = fs.readFileSync(path.resolve(__dirname, '../src/views/user/TrainingEnvironment.vue'), 'utf8')
for (const token of ['standalone-start-progress', 'environmentStarting', 'startProgress', '启动进度']) {
  if (!standalone.includes(token)) throw new Error(`standalone training start progress missing ${token}`)
}
console.log('course start progress contract passed')
