const fs = require('fs')
const assert = require('assert')

for (const file of [
  '../java/match-mgr/src/main/java/com/match/course/service/CourseDeliveryService.java',
  '../java/match-mgr/src/main/java/com/match/transfer/TemporaryTransferService.java'
]) {
  const source = fs.readFileSync(file, 'utf8')
  assert.match(source, /"\/agent\/v1\/files\/"\s*\+/)
  assert.doesNotMatch(source, /"\/files\/"\s*\+/)
}

console.log('agent file transfer contract: ok')
