import request from '@/utils/request.js'

const ADMIN_BASE = '/admin/training-environments'
const USER_BASE = '/user/training-environments'

const secureEditorUrls = response => {
  const rows = response && response.data
  if (Array.isArray(rows)) rows.forEach(row => {
    if (row.editorUrl && row.editorUrl.startsWith('http://')) row.editorUrl = `https://${row.editorUrl.slice(7)}`
  })
  return response
}
export const listAdminTrainingEnvironments = () => request.get(ADMIN_BASE).then(secureEditorUrls)
export const createAdminTrainingEnvironment = payload => request.post(ADMIN_BASE, payload, { headers: { 'Content-Type': 'application/json' } })
export const listAdminTrainingSlots = () => request.get(`${ADMIN_BASE}/slots`)
export const listAdminEnvironmentTemplates = () => request.get(`${ADMIN_BASE}/templates`)
export const listEligibleEnvironmentAccounts = () => request.get(`${ADMIN_BASE}/eligible-accounts`)
export const listAvailableEnvironmentPorts = (agentId, environmentType, userId) => request.get(`${ADMIN_BASE}/available-ports`, { params: { agentId, environmentType, userId } })
export const startAdminTrainingEnvironment = id => request.post(`${ADMIN_BASE}/${id}/start`)
export const startClassTrainingEnvironment = id => request.post(`${ADMIN_BASE}/${id}/class/start`)
const jsonConfig = { headers: { 'Content-Type': 'application/json' } }
export const startClassTrainingCourse = (courseId, editorTool, ignoreOffline = false) => request.post(`${ADMIN_BASE}/class/start-course`, { courseId, editorTool, ignoreOffline }, { ...jsonConfig, silentError: true })
export const startClassTrainingIndependent = (environmentName, editorTool, ignoreOffline = false) => request.post(`${ADMIN_BASE}/class/start-independent`, { environmentName, editorTool, ignoreOffline }, { ...jsonConfig, silentError: true })
export const getClassTrainingStatus = () => request.get(`${ADMIN_BASE}/class/status`, { silentError: true })
export const stopClassTrainingEnvironment = () => request.post(`${ADMIN_BASE}/class/stop`)
export const stopAdminTrainingEnvironment = id => request.post(`${ADMIN_BASE}/${id}/stop`)
export const restoreAdminTrainingEnvironment = id => request.post(`${ADMIN_BASE}/${id}/restore`)
export const deleteAdminTrainingEnvironment = id => request.delete(`${ADMIN_BASE}/${id}`)
export const listUserTrainingEnvironments = (defaultOnly = false) => request.get(USER_BASE, { params: { defaultOnly } }).then(secureEditorUrls)
export const startUserTrainingEnvironment = id => request.post(`${USER_BASE}/${id}/start`)
export const adminParticipantPreviewTrainingEnvironmentsApi = () => request.get('/admin/participant-preview/training-environments').then(secureEditorUrls)
