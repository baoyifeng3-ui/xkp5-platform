const fs = require('fs')
const source = fs.readFileSync(require('path').join(__dirname, '../src/views/management/ManagementHome.vue'), 'utf8')
if (!source.includes('@click.native="startSelectedClass"')) throw new Error('class start button does not use a native click handler')
console.log('class start button contract passed')
