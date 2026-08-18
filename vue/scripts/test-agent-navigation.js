const assert = require('assert')
const navigation = require('../src/navigation/roleNavigation')

assert.ok(navigation.operationsItems.some(item => item.route === '/operations/processing-agents'))
assert.ok(navigation.managementItems.some(item => item.route === '/management/devices'))
console.log('agent navigation tests passed')
