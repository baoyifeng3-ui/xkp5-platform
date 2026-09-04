const fs = require('fs')
const path = require('path')

const nginx = fs.readFileSync(path.resolve(__dirname, '../../docker/vue/nginx.conf'), 'utf8')
if (!/location \/api\/ \{[\s\S]*?client_max_body_size 50g;/.test(nginx)) {
  throw new Error('API upload limit must match Spring multipart limit of 50GB')
}
if (!/location \/agent\/v1\/ \{[\s\S]*?client_max_body_size 1m;/.test(nginx)) {
  throw new Error('Agent API limit must remain 1MB')
}
console.log('upload size contract passed')
