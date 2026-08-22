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
assert.match(shell, /<slot name="status"/)
assert.match(shell, /<slot name="account-actions"/)
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
assert.doesNotMatch(shellCss, /linear-gradient|radial-gradient|\.orb|bokeh/i)

console.log('platform UI contract passed')
