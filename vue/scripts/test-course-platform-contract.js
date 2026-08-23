const assert = require('assert')
const fs = require('fs')

const api = fs.readFileSync('src/api/Courses.js', 'utf8')
const coursePage = fs.readFileSync('src/views/user/CoursePlatform.vue', 'utf8')
const trainingPage = fs.readFileSync('src/views/user/TrainingEnvironment.vue', 'utf8')
const validationPage = fs.readFileSync('src/views/user/TrainingValidation.vue', 'utf8')
const adminPage = fs.readFileSync('src/views/management/CourseManagement.vue', 'utf8')

for (const endpoint of ['/admin/courses', '/user/courses', '/user/courses/progress', '/user/courses/resources/', '/admin/course-resources/upload', '/course-resources/', '/admin/courses/resources/']) {
  assert.ok(api.includes(endpoint), `Courses API must include ${endpoint}`)
}
assert.match(coursePage, /listUserCourses/)
assert.match(coursePage, /recordCourseProgress/)
assert.match(coursePage, /进入本课程实训/)
assert.match(trainingPage, /deliverCourseResource/)
assert.match(validationPage, /api\/v2\/t100/)
assert.match(validationPage, /httpToBase64/)
assert.match(validationPage, /request\.post\('\/sample\/httpToBase64'/)
assert.doesNotMatch(validationPage, /paperType\s*:\s*['"]A['"]|data_set_A/, 'training validation must not use beginner A paper resources')
assert.match(adminPage, /uploadCourseResource/)
assert.match(adminPage, /选择文件并上传/)
console.log('course platform contract passed')
