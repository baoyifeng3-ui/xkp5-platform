const fs = require('fs')
const source = fs.readFileSync(require('path').join(__dirname, '../src/views/user/CoursePlatform.vue'), 'utf8')
if (!source.includes('tool === "VSCODE" && url.startsWith("http://")')) throw new Error('course embedded editor URL is not normalized')
console.log('course embedded editor HTTPS contract passed')
