const assert = require('assert')
const fs = require('fs')

function read (file) {
  return fs.readFileSync(file, 'utf8')
}

const shell = read('src/components/PlatformShell.vue')
const theme = read('src/assets/style/platform-theme.css')
assert.ok(fs.existsSync('src/services/platformTheme.js'), 'platform theme service must exist')
const themeService = require('../src/services/platformTheme')

assert.match(shell, /shell-drawer/)
assert.match(shell, /platformName/)
assert.match(shell, /\{\{ platformName \}\}/)
assert.match(shell, /platformName:\s*\{\s*type:\s*String,\s*default:\s*'XKP5\.0平台'/)
assert.doesNotMatch(shell, /<strong>XKP5\.0平台<\/strong>/)
assert.match(shell, /el-icon-search/)
assert.match(shell, /aria-label="打开导航"/)
assert.match(shell, /aria-label="关闭导航"/)
assert.match(shell, /ref="drawerTrigger"/)
assert.match(shell, /:aria-hidden="drawerHidden \? 'true' : 'false'"/)
assert.match(shell, /:inert="drawerHidden \? '' : null"/)
assert.match(shell, /window\.addEventListener\('keydown', this\.handleEscape\)/)
assert.match(shell, /event\.key === 'Escape'/)
assert.match(shell, /this\.\$refs\.drawerTrigger\.focus\(\)/)
assert.match(shell, /drawerHidden \(\)\s*{\s*return this\.mobileViewport && !this\.drawerOpen/)
assert.match(shell, /updateMobileViewport \(event\)[\s\S]*if \(!this\.mobileViewport\) this\.drawerOpen = false/)
assert.match(shell, /addEventListener\('change', this\.updateMobileViewport\)/)
assert.match(shell, /<slot name="status"/)
assert.match(shell, /<slot name="account-actions"/)
assert.match(shell, /aria-label="导航搜索建议"/)
assert.doesNotMatch(shell, /role="listbox"|role="option"/)
assert.match(theme, /--ui-primary:\s*var\(--platform-theme-color,\s*#6f7ff7\)/)
assert.strictEqual(themeService.DEFAULT_THEME_COLOR, '#6f7ff7')
assert.deepStrictEqual(themeService.themeVariables('#123456'), {
  '--platform-theme-color': '#123456',
  '--ui-primary-strong': '#0f2b47',
  '--ui-primary-soft': '#e3e7eb'
})
;['src/store/modules/Match.js', 'src/views/Admin.vue', 'src/views/management/PlatformSettings.vue']
  .forEach(file => assert.doesNotMatch(read(file), /DEFAULT_THEME_COLOR\s*=\s*['"]#162d45['"]/i))
assert.match(theme, /--ui-workspace:\s*#f3f5f9/)
assert.match(theme, /--ui-radius:\s*8px/)
assert.match(theme, /--ui-sidebar-width:\s*220px/)
assert.match(theme, /--ui-sidebar-compact-width:\s*76px/)

;['ManagementShell', 'OperationsShell', 'NormalUserShell'].forEach(name => {
  const adapter = read(`src/layouts/${name}.vue`)
  assert.match(adapter, /PlatformShell/)
  assert.match(adapter, /:platform-name="platformName"/)
  assert.match(adapter, /platformName\s*[:(]/)
})

const competitionLayout = read('src/views/Layout.vue')
assert.match(competitionLayout, /PlatformShell/)
assert.match(competitionLayout, /participantLocation/)
assert.match(competitionLayout, /clearTimeText/)
assert.match(competitionLayout, /preStartLocked/)
assert.match(competitionLayout, /showAdminNavigation/)
assert.match(competitionLayout, /:platform-name="platformName"/)

const shellCss = read('src/assets/style/platform-shell.css')
assert.match(shellCss, /@media \(max-width:\s*720px\)/)
assert.match(shellCss, /grid-template-columns:\s*minmax\(0,\s*1fr\)/)
assert.match(shellCss, /\.shell-sidebar\s*{[^}]*visibility:\s*hidden;[^}]*pointer-events:\s*none;/s)
assert.match(shellCss, /\.shell-sidebar\.is-open\s*{[^}]*visibility:\s*visible;[^}]*pointer-events:\s*auto;/s)
assert.doesNotMatch(shellCss, /linear-gradient|radial-gradient|\.orb|bokeh/i)

const login = read('src/views/Login.vue')
const loginCss = read('src/assets/style/login.css')
const app = read('src/App.vue')

assert.match(app, /class="platform-app"/)
assert.match(app, /platform-theme\.css/)
assert.match(login, /login-status/)
assert.match(login, /serviceStatus:\s*'checking'/)
assert.match(login, /正在检查平台服务/)
assert.match(login, /平台服务就绪/)
assert.match(login, /平台服务暂不可用，可稍后重试/)
assert.match(login, /this\.serviceStatus\s*=\s*'ready'/)
assert.match(login, /catch \(error\)[\s\S]*?this\.serviceStatus\s*=\s*'unavailable'/)
assert.match(login, /autocomplete="username"/)
assert.match(login, /autocomplete="current-password"/)
assert.match(login, /:style="loginStyle"/)
assert.match(loginCss, /--ui-primary/)
assert.match(loginCss, /border-radius:\s*var\(--ui-radius\)/)
assert.doesNotMatch(login + loginCss, /linear-gradient|radial-gradient|\.orb|bokeh/i)
assert.match(login, /login-context/)
assert.doesNotMatch(login, /login-intro/)
assert.doesNotMatch(loginCss, /\.login-shell\s*\{[^}]*grid-template-columns/s)
assert.match(loginCss, /@media \(max-width:\s*760px\) and \(max-height:\s*650px\)/)
assert.match(loginCss, /@media \(max-width:\s*760px\) and \(max-height:\s*650px\)[\s\S]*?login-context p,[\s\S]*?login-status\s*\{\s*display:\s*none;/)
assert.doesNotMatch(loginCss, /\.(?:login-form|button-list)\s*\{[^}]*display:\s*none;/s)
assert.strictEqual((login.match(/<el-button/g) || []).length, 2)

;[
  '\\.platform-app \\.el-button',
  '\\.platform-app \\.el-input__inner',
  '\\.platform-app \\.el-table',
  '\\.platform-app \\.el-tag',
  '\\.platform-app \\.el-dialog',
  '\\.platform-app \\.el-tabs',
  '\\.platform-app \\.el-pagination',
  '\\.platform-app \\.el-empty',
  '\\.platform-app \\.el-loading-mask'
].forEach(selector => assert.match(theme, new RegExp(selector)))
assert.doesNotMatch(theme, /\.xterm(?:\s|,|\{|\.)/)

const composedPages = [
  'src/views/management/ManagementHome.vue',
  'src/views/management/CompetitionManagement.vue',
  'src/views/management/CompetitionMode.vue',
  'src/views/management/CourseManagement.vue',
  'src/views/management/ResourceManagement.vue',
  'src/views/management/TrainingManagement.vue',
  'src/views/management/DeviceManagement.vue',
  'src/views/operations/AdministratorManagement.vue',
  'src/views/operations/CompetitionEnvironments.vue',
  'src/views/operations/ContainerTemplates.vue',
  'src/views/operations/ImageRegistryView.vue',
  'src/views/operations/LicenseDiagnostics.vue',
  'src/views/operations/OperationsHome.vue',
  'src/views/operations/ProcessingAgents.vue',
  'src/views/user/CoursePlatform.vue',
  'src/views/user/ResourceCenter.vue',
  'src/views/user/TrainingEnvironment.vue',
  'src/views/Home.vue',
  'src/views/Publicity.vue',
  'src/components/Question.vue',
  'src/views/Detect.vue',
  'src/views/competition/CompetitionPractical.vue'
]

composedPages.forEach(file => {
  const source = read(file)
  assert.match(source, /module-page/, `${file} must use the shared page composition`)
  assert.match(source, /module-heading/, `${file} must use the shared page heading`)
  assert.doesNotMatch(source, /h[1-6][^{]*\{[^{}]*font-size\s*:\s*(?:3[3-9]|[4-9]\d|\d{3,})px/, `${file} must keep panel headings compact`)
})

const managementPages = composedPages.filter(file => file.includes('/management/'))
managementPages.forEach(file => {
  const source = read(file)
  assert.doesNotMatch(source, /<el-card[\s\S]*<el-card/, `${file} must not nest cards`)
})

const workspacePages = composedPages.filter(file => /\/(management|operations|user)\//.test(file))
workspacePages.forEach(file => {
  const source = read(file)
  assert.match(source, /module-composed-page/, `${file} must opt into the refreshed workspace composition`)
  assert.doesNotMatch(source, /#(?:17324d|294152|318063|216c54|9a7517|1e587e|25745b|356d95)\b/i, `${file} must use shared theme tokens instead of the legacy page palette`)
})

const tablePages = [
  'src/views/management/CompetitionMode.vue',
  'src/views/management/TrainingManagement.vue',
  'src/views/management/DeviceManagement.vue',
  'src/views/operations/AdministratorManagement.vue',
  'src/views/operations/CompetitionEnvironments.vue',
  'src/views/operations/ContainerTemplates.vue',
  'src/views/operations/ImageRegistryView.vue',
  'src/views/operations/LicenseDiagnostics.vue',
  'src/views/operations/ProcessingAgents.vue',
  'src/views/user/TrainingEnvironment.vue'
]
tablePages.forEach(file => {
  const source = read(file)
  const tableCount = (source.match(/<el-table(?:\s|>)/g) || []).length
  const wrapperCount = (source.match(/module-table-wrap/g) || []).length
  assert.strictEqual(wrapperCount, tableCount, `${file} must wrap every table for stable horizontal scrolling`)
})

const toolbarPages = [
  'src/views/management/CompetitionMode.vue',
  'src/views/management/DeviceManagement.vue',
  'src/views/operations/AdministratorManagement.vue',
  'src/views/operations/CompetitionEnvironments.vue',
  'src/views/operations/ContainerTemplates.vue',
  'src/views/operations/ImageRegistryView.vue',
  'src/views/operations/LicenseDiagnostics.vue',
  'src/views/operations/ProcessingAgents.vue'
]
toolbarPages.forEach(file => assert.match(read(file), /module-toolbar/, `${file} must use a compact action toolbar`))

const competitionHomeCss = read('src/assets/style/home.css')
assert.doesNotMatch(competitionHomeCss, /\.h-box\s*\{[^}]*background:\s*#fff[^}]*border:[^}]*box-shadow:/s)

const contextPages = [
  'src/views/management/CourseManagement.vue',
  'src/views/management/ResourceManagement.vue',
  'src/views/operations/OperationsHome.vue',
  'src/views/user/CoursePlatform.vue',
  'src/views/user/ResourceCenter.vue'
]
contextPages.forEach(file => {
  const source = read(file)
  assert.match(source, /module-page/, `${file} must render a module page`)
  assert.doesNotMatch(source, /待接入|暂无课程数据/, `${file} must not remain a placeholder`)
})
assert.match(read('src/views/operations/OperationsHome.vue'), /module-toolbar/)
assert.match(read('src/views/operations/OperationsHome.vue'), /to="\/operations\/administrators"/)
assert.match(read('src/views/operations/OperationsHome.vue'), /to="\/operations\/processing-agents"/)

const agentTable = read('src/components/agents/AgentStatusTable.vue')
assert.match(agentTable, /class="agent-table module-table-wrap"/)

const participantCss = competitionHomeCss + read('src/assets/style/publicity.css')
assert.doesNotMatch(participantCss, /#(?:010d3b|012292|091722|1e65b9|083e81|173d5b|102b47|9a7517|d5a92f|162d45)\b/i)

const competitionHub = read('src/views/management/CompetitionManagement.vue')
const { competitionItems } = require('../src/navigation/roleNavigation')
assert.deepStrictEqual(competitionItems.map(item => item.label), [
  '模式与环境', '参赛端预览', '比赛控制', '赛程赛规', '试卷题目', '试卷评分', '比赛账号'
])
assert.strictEqual(competitionItems.length, 7)
assert.ok(!competitionHub.includes('比赛设备'))
assert.ok(!competitionHub.includes('平台设置'))

assert.match(theme, /\.module-toolbar/)
assert.match(theme, /\.module-table-wrap/)
assert.match(theme, /overflow-x:\s*auto/)

console.log('platform UI contract passed')
