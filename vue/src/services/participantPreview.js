const previewDestinations = [
  '/course-platform', '/resource-center', '/training-environment',
  '/Publicity', '/Home', '/Question', '/Detect', '/competition-practical'
]
const previewDestinationSet = new Set(previewDestinations)

function isPreviewRoute (route, role) {
  return Boolean(role === 'ADMIN' && route && previewDestinationSet.has(route.path) &&
    route.query && route.query.preview === '1')
}

function previewHome (mode) {
  return mode === 'COMPETITION' ? '/Publicity' : '/course-platform'
}

module.exports = { isPreviewRoute, previewHome, previewDestinations }
