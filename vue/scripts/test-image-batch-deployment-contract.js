const fs = require('fs')
const source = fs.readFileSync('src/components/operations/ImageDeploymentDialog.vue', 'utf8')

for (const token of ['选择全部在线服务器', 'multiple filterable', 'form.agentIds', 'onlineAgents', 'ui-batch-', 'Promise.all', '批量推送已提交']) {
  if (!source.includes(token)) throw new Error(`batch deployment contract missing: ${token}`)
}

if (source.includes('v-model="form.agentId"')) throw new Error('single-agent selector must not remain')
console.log('image batch deployment contract passed')
