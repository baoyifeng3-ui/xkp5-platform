const fs = require('fs')
const source = fs.readFileSync(require('path').join(__dirname, '../src/views/user/TrainingEnvironment.vue'), 'utf8')
if (!source.includes('url.startsWith("http://")')) throw new Error('embedded URL is not normalized to HTTPS')
console.log('embedded editor HTTPS contract passed')
