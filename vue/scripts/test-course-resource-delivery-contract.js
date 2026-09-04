const fs = require('fs')
const assert = require('assert')

const controller = fs.readFileSync('../java/match-mgr/src/main/java/com/match/course/web/CourseDeliveryController.java', 'utf8')
const management = fs.readFileSync('src/views/management/ResourceManagement.vue', 'utf8')

assert.match(controller, /@PostMapping\("\/\{resourceId\}\/deliver-to-all"\)/)
assert.match(controller, /deliverToAllUsers\(resourceId/)
assert.match(management, /:allow-deliver="space === 'course'"/)
assert.match(management, /deliver-label="发送到所有已分配课程环境"/)
assert.match(management, /@deliver="deliver"/)
assert.match(management, /deliverCourseResourceToAll/)

console.log('course resource delivery contract: ok')
