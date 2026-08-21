const assert = require('assert')
const fs = require('fs')
const path = require('path')
const navigation = require('../src/navigation/roleNavigation')

assert.deepStrictEqual(navigation.landingRoute('SUPER_ADMIN'), { path: '/operations' })
assert.deepStrictEqual(navigation.landingRoute('ADMIN'), { path: '/management' })
assert.deepStrictEqual(navigation.landingRoute('USER'), { path: '/course-platform' })
assert.deepStrictEqual(navigation.userItems.map(item => item.label), [
  '课程平台', '资源中心', '实训环境'
])
assert.deepStrictEqual(navigation.managementItems.map(item => item.label), [
  '主页', '竞赛管理', '课程管理', '资源管理', '实训管理', '用户管理', '设备管理', '平台设置', '镜像仓库'
])
assert.deepStrictEqual(navigation.competitionItems.map(item => item.label), [
  '模式与环境', '参赛端预览', '比赛控制', '赛程赛规', '试卷题目', '试卷评分', '比赛账号'
])
assert.deepStrictEqual(navigation.competitionItems.map(item => item.route), [
  '/management/competition-mode',
  '/competition-preview',
  '/Admin?tab=timer',
  '/Admin?tab=rules',
  '/Admin?tab=subjects',
  '/Admin?tab=grading',
  '/Admin?tab=users'
])
const competitionManagement = navigation.managementItems.find(item => item.label === '竞赛管理')
assert.strictEqual(competitionManagement.route, '/management/competition')
assert.strictEqual(competitionManagement.children, undefined)
const platformSettings = navigation.managementItems.find(item => item.label === '平台设置')
assert.strictEqual(platformSettings.route, '/management/platform-settings')
assert.strictEqual(navigation.routeRoles['/operations'], 'SUPER_ADMIN')
assert.strictEqual(navigation.routeRoles['/management'], 'ADMIN')
assert.strictEqual(navigation.routeRoles['/course-platform'], 'USER')
assert.deepStrictEqual(navigation.previewDestinations, ['/Publicity', '/Home', '/Question', '/Detect'])
assert.deepStrictEqual(navigation.operationsItems.map(item => item.label), ['运维主页', '处理服务器', '容器模板', '镜像仓库', '比赛容器', '管理员账号', '授权诊断'])
navigation.competitionItems.forEach(item => assert.ok(item.route, `${item.label} must define a route`))

const routerSource = fs.readFileSync(path.join(__dirname, '../src/router/index.js'), 'utf8')
assert.match(routerSource, new RegExp("path:\\s*['\"]competition['\"],\\s*name:\\s*['\"]CompetitionManagement['\"]"))
assert.match(routerSource, new RegExp("path:\\s*['\"]platform-settings['\"],\\s*name:\\s*['\"]PlatformSettings['\"]"))

console.log('role navigation tests passed')
