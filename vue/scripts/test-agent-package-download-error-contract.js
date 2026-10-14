const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/operations/ProcessingAgents.vue'), 'utf8')

if (!source.includes('await this.responseErrorMessage(error')) {
  throw new Error('Agent package download must decode and display the backend error response')
}
if (!source.includes('data instanceof Blob') || !source.includes('await data.text()')) {
  throw new Error('Agent package download must decode Blob error bodies')
}

console.log('Agent package download error contract passed.')
