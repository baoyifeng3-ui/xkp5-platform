function isPreviewRoute (route) {
  return Boolean(route && route.query && route.query.preview === '1')
}

function previewHome (mode) {
  return mode === 'COMPETITION' ? '/Publicity' : '/course-platform'
}

module.exports = { isPreviewRoute, previewHome }
