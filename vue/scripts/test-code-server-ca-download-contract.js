const fs = require('fs')
const assert = require('assert')

const controllerPath = '../java/match-mgr/src/main/java/com/match/environment/web/CodeServerCertificateController.java'
assert.ok(fs.existsSync(controllerPath), 'root CA controller must exist')
const controller = fs.readFileSync(controllerPath, 'utf8')
const training = fs.readFileSync('src/views/user/TrainingEnvironment.vue', 'utf8')

assert.match(controller, /@GetMapping\("\/root-ca\.pem"\)/)
assert.match(controller, /requireUser\(\)/)
assert.match(controller, /attachment; filename=\\"rootCA\.pem\\"/)
assert.match(training, /downloadCodeServerRootCa/)

console.log('code-server root CA download contract: ok')
