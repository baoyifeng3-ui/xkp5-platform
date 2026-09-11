const fs = require('fs')
const source = fs.readFileSync(require('path').join(__dirname, '../src/views/management/ManagementHome.vue'), 'utf8')
for (const required of ['classInSession || platformMode !== \'TRAINING\' || transitionBusy', ':disabled="!classInSession"', 'classInSession()', '上课中', '环境启动中', '上课失败']) {
  if (!source.includes(required)) throw new Error(`missing class state control: ${required}`)
}
console.log('class button state contract passed')
