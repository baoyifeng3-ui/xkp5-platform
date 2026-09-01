const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const view = fs.readFileSync(path.join(root, 'src/views/management/TrainingManagement.vue'), 'utf8')
const api = fs.readFileSync(path.join(root, 'src/api/TrainingEnvironments.js'), 'utf8')

const requiredViewText = [
  '环境类型',
  '课程环境',
  '比赛环境',
  '图像标注端口',
  'VS Code 端口',
  'Jupyter 端口',
  'T100 端口',
  'annotationHostPort',
  'editorVscodeHostPort',
  'editorJupyterHostPort',
  'editorT100HostPort'
]

for (const token of requiredViewText) {
  if (!view.includes(token)) throw new Error(`TrainingManagement.vue missing ${token}`)
}

for (const token of ['listAdminEnvironmentTemplates', 'listAvailableEnvironmentPorts']) {
  if (!api.includes(token)) throw new Error(`TrainingEnvironments.js missing ${token}`)
}

const openCreate = view.match(/async openCreate\(\)\s*\{([\s\S]*?)\n\s*\},\n\s*(?:async\s+)?toggleAll/)
if (!openCreate) throw new Error('openCreate must refresh create options asynchronously')
for (const token of ['listEligibleEnvironmentAccounts()', 'listAdminCourses()', 'listAdminEnvironmentTemplates()']) {
  if (!openCreate[1].includes(token)) throw new Error(`openCreate must refresh ${token}`)
}

if (/label="容器端口"/.test(fs.readFileSync(path.join(root, 'src/views/operations/ContainerTemplates.vue'), 'utf8'))) {
  throw new Error('Container template form must not request host ports')
}
if (/:disabled="s\.row\.items\.some\([\s\S]{0,120}全部删除/.test(view)) throw new Error('group delete must remain available for failed environments')

console.log('training environment creation contract passed')
