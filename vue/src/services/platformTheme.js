const DEFAULT_THEME_COLOR = '#6f7ff7'

function normalizeThemeColor (value) {
  const color = String(value || '').trim().toLowerCase()
  return /^#[0-9a-f]{6}$/.test(color) ? color : DEFAULT_THEME_COLOR
}

function mixHex (source, target, targetWeight) {
  const sourceColor = normalizeThemeColor(source).slice(1)
  const targetColor = target.slice(1)
  const channel = offset => {
    const from = parseInt(sourceColor.slice(offset, offset + 2), 16)
    const to = parseInt(targetColor.slice(offset, offset + 2), 16)
    return Math.round(from + (to - from) * targetWeight).toString(16).padStart(2, '0')
  }
  return `#${channel(0)}${channel(2)}${channel(4)}`
}

function themeVariables (value) {
  const primary = normalizeThemeColor(value)
  return {
    '--platform-theme-color': primary,
    '--ui-primary-strong': mixHex(primary, '#000000', 0.18),
    '--ui-primary-soft': mixHex(primary, '#ffffff', 0.88)
  }
}

function applyPlatformTheme (value, root) {
  const target = root || (typeof document !== 'undefined' ? document.documentElement : null)
  const variables = themeVariables(value)
  if (target && target.style) {
    Object.keys(variables).forEach(name => target.style.setProperty(name, variables[name]))
  }
  return variables['--platform-theme-color']
}

module.exports = { DEFAULT_THEME_COLOR, normalizeThemeColor, themeVariables, applyPlatformTheme }
