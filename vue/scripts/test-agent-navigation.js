const assert = require('assert')
const navigation = require('../src/navigation/roleNavigation')

assert.ok(navigation.operationsItems.some(item => item.route === '/operations/processing-agents'))
assert.ok(navigation.managementItems.some(item => Array.isArray(item.children) && item.children.some(child => child.route === '/management/devices')))
console.log('agent navigation tests passed')
