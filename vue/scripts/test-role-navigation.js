const assert = require('assert')
const fs = require('fs')
const path = require('path')
const navigation = require('../src/navigation/roleNavigation')

assert.deepStrictEqual(navigation.landingRoute('SUPER_ADMIN'), { path: '/operations' })
assert.deepStrictEqual(navigation.landingRoute('ADMIN'), { path: '/management' })
assert.deepStrictEqual(navigation.landingRoute('USER'), { path: '/course-platform' })
assert.deepStrictEqual(navigation.userItems.map(item => item.label), [
  '课程平台', '资源中心', '实训环境', '模型验证'
])
assert.deepStrictEqual(navigation.managementItems.map(item => item.label), [
  '主页', '竞赛管理', '课程与资源', '实训管理', '平台管理'
])
assert.deepStrictEqual(navigation.competitionItems.map(item => item.label), [
  '模式与环境', '参赛端预览', '比赛控制', '赛程赛规', '试卷题目', '试卷评分', '比赛账号与容器绑定'
])
assert.deepStrictEqual(navigation.competitionItems.map(item => item.route), [
  '/management/competition-mode',
  '/competition-preview',
  '/Admin?tab=timer',
  '/Admin?tab=rules',
  '/Admin?tab=subjects',
  '/Admin?tab=grading',
  '/management/competition-accounts'
])
const competitionManagement = navigation.managementItems.find(item => item.label === '竞赛管理')
assert.strictEqual(competitionManagement.children, navigation.competitionItems)
const platformManagement = navigation.managementItems.find(item => item.label === '平台管理')
assert.ok(platformManagement.children.some(item => item.route === '/management/platform-settings'))
assert.ok(!navigation.userItems.some(item => item.label === '镜像仓库'))
assert.strictEqual(typeof navigation.legacyAdminRoute, 'function')
assert.deepStrictEqual(navigation.legacyAdminRoute('training'), { path: '/management/devices', replace: true })
assert.deepStrictEqual(navigation.legacyAdminRoute('settings'), { path: '/management/platform-settings', replace: true })
assert.strictEqual(navigation.legacyAdminRoute('timer'), null)
assert.strictEqual(navigation.routeRoles['/operations'], 'SUPER_ADMIN')
assert.strictEqual(navigation.routeRoles['/management'], 'ADMIN')
assert.strictEqual(navigation.routeRoles['/course-platform'], 'USER')
assert.deepStrictEqual(navigation.previewDestinations, [
  '/course-platform', '/resource-center', '/training-environment',
  '/Publicity', '/Home', '/Question', '/Detect', '/competition-practical'
])
assert.deepStrictEqual(navigation.operationsItems.map(item => item.label), ['运维主页', '处理服务器', '容器模板', '镜像仓库', '比赛容器', '管理员账号', '授权诊断'])
navigation.competitionItems.forEach(item => assert.ok(item.route, `${item.label} must define a route`))

const routerSource = fs.readFileSync(path.join(__dirname, '../src/router/index.js'), 'utf8')
assert.match(routerSource, new RegExp("path:\\s*['\"]competition['\"],\\s*name:\\s*['\"]CompetitionManagement['\"]"))
assert.match(routerSource, new RegExp("path:\\s*['\"]platform-settings['\"],\\s*name:\\s*['\"]PlatformSettings['\"]"))

const adminSource = fs.readFileSync(path.join(__dirname, '../src/views/Admin.vue'), 'utf8')
assert.match(adminSource, /<el-tab-pane\s+v-if="false"\s+name=["']training["']/)
assert.match(adminSource, /<el-tab-pane\s+v-if="false"\s+name=["']settings["']/)
assert.match(adminSource, /\['timer',\s*'rules',\s*'subjects',\s*'grading',\s*'users'\]/)

const settingsSource = fs.readFileSync(path.join(__dirname, '../src/views/management/PlatformSettings.vue'), 'utf8')
assert.match(settingsSource, /import\s+\{\s*competitionApi,\s*updatePlatformSettingsApi,\s*uploadLoginBackgroundApi\s*\}\s+from\s+'@\/api\/Match'/)
assert.match(settingsSource, /created\s*\(\)\s*\{\s*this\.loadPlatformSettings\(\)\s*\}/)
assert.match(settingsSource, /async\s+loadPlatformSettings\s*\(\)[\s\S]*?await\s+competitionApi\(\)[\s\S]*?SET_PLATFORM_SETTINGS/)
assert.match(settingsSource, /if\s*\(!this\.initialized\)\s*return[\s\S]*?updatePlatformSettingsApi/)
assert.match(settingsSource, /:disabled="platformSaving \|\| !initialized \|\| loading"/)

assert.match(routerSource, /to\.path\s*===\s*['"]\/Admin['"][\s\S]*?roleNavigation\.legacyAdminRoute\(to\.query\.tab\)/)

console.log('role navigation tests passed')
