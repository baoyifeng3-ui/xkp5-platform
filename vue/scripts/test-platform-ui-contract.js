const assert = require('assert')
const fs = require('fs')

function read (file) {
  return fs.readFileSync(file, 'utf8')
}

const shell = read('src/components/PlatformShell.vue')
const theme = read('src/assets/style/platform-theme.css')

assert.match(shell, /shell-drawer/)
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
assert.match(theme, /--ui-primary:\s*#6f7ff7/)
assert.match(theme, /--ui-workspace:\s*#f3f5f9/)
assert.match(theme, /--ui-radius:\s*8px/)
assert.match(theme, /--ui-sidebar-width:\s*220px/)
assert.match(theme, /--ui-sidebar-compact-width:\s*76px/)

;['ManagementShell', 'OperationsShell', 'NormalUserShell'].forEach(name => {
  assert.match(read(`src/layouts/${name}.vue`), /PlatformShell/)
})

const competitionLayout = read('src/views/Layout.vue')
assert.match(competitionLayout, /PlatformShell/)
assert.match(competitionLayout, /participantLocation/)
assert.match(competitionLayout, /clearTimeText/)
assert.match(competitionLayout, /preStartLocked/)
assert.match(competitionLayout, /showAdminNavigation/)

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
assert.match(login, /autocomplete="username"/)
assert.match(login, /autocomplete="current-password"/)
assert.match(login, /:style="loginStyle"/)
assert.match(loginCss, /--ui-primary/)
assert.match(loginCss, /border-radius:\s*var\(--ui-radius\)/)
assert.doesNotMatch(login + loginCss, /linear-gradient|radial-gradient|\.orb|bokeh/i)

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

console.log('platform UI contract passed')
