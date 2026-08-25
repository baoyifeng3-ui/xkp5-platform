import request from '@/utils/request.js'

const ADMIN_BASE = '/admin/training-environments'
const USER_BASE = '/user/training-environments'

export const listAdminTrainingEnvironments = () => request.get(ADMIN_BASE)
export const createAdminTrainingEnvironment = payload => request.post(ADMIN_BASE, payload, { headers: { 'Content-Type': 'application/json' } })
export const listAdminTrainingSlots = () => request.get(`${ADMIN_BASE}/slots`)
export const startAdminTrainingEnvironment = id => request.post(`${ADMIN_BASE}/${id}/start`)
export const stopAdminTrainingEnvironment = id => request.post(`${ADMIN_BASE}/${id}/stop`)
export const restoreAdminTrainingEnvironment = id => request.post(`${ADMIN_BASE}/${id}/restore`)
export const deleteAdminTrainingEnvironment = id => request.delete(`${ADMIN_BASE}/${id}`)
export const listUserTrainingEnvironments = () => request.get(USER_BASE)
export const startUserTrainingEnvironment = id => request.post(`${USER_BASE}/${id}/start`)
export const adminParticipantPreviewTrainingEnvironmentsApi = () => request.get('/admin/participant-preview/training-environments')
