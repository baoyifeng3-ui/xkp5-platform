const assert = require('assert')
const navigation = require('../src/navigation/roleNavigation')

assert.deepStrictEqual(navigation.landingRoute('SUPER_ADMIN'), { path: '/operations' })
assert.deepStrictEqual(navigation.landingRoute('ADMIN'), { path: '/management' })
assert.deepStrictEqual(navigation.landingRoute('USER'), { path: '/course-platform' })
assert.deepStrictEqual(navigation.userItems.map(item => item.label), [
  '课程平台', '资源中心', '实训环境'
])
assert.deepStrictEqual(navigation.managementItems.map(item => item.label), [
  '主页', '课程管理', '资源管理', '实训管理', '竞赛管理', '用户管理', '设备管理'
])
assert.deepStrictEqual(navigation.competitionItems.map(item => item.label), [
  '比赛预览', '比赛控制', '赛程赛规编辑', '试卷题目', '试卷评分', '比赛账号', '比赛设备', '平台设置'
])
assert.strictEqual(navigation.routeRoles['/operations'], 'SUPER_ADMIN')
assert.strictEqual(navigation.routeRoles['/management'], 'ADMIN')
assert.strictEqual(navigation.routeRoles['/course-platform'], 'USER')
assert.deepStrictEqual(navigation.previewDestinations, ['/Publicity', '/Home', '/Question', '/Detect'])
assert.deepStrictEqual(navigation.operationsItems.map(item => item.label), ['运维主页', '处理服务器', '管理员账号', '授权诊断'])
navigation.competitionItems.forEach(item => assert.ok(item.route, `${item.label} must define a route`))

console.log('role navigation tests passed')
