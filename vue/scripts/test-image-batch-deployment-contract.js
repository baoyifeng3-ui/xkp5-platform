const fs = require('fs')
const source = fs.readFileSync('src/components/operations/ImageDeploymentDialog.vue', 'utf8')

for (const token of ['选择全部在线服务器', 'multiple filterable', 'v-model="agentIds"', 'onlineAgents', 'Date.now().toString(36)', 'for (let i = 0; i < this.agentIds.length; i++)', '已提交 ']) {
  if (!source.includes(token)) throw new Error(`batch deployment contract missing: ${token}`)
}

if (source.includes('v-model="form.agentId"')) throw new Error('single-agent selector must not remain')
console.log('image batch deployment contract passed')
