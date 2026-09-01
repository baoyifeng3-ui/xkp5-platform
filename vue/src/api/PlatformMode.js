import request from '@/utils/request.js'

const ADMIN_BASE = 'admin/platform-mode'
const OPERATIONS_BASE = 'operations/mode-transitions'

export const getPlatformMode = () => request.get(ADMIN_BASE)
export const changePlatformMode = targetMode => request.post(ADMIN_BASE, { targetMode }, { headers: { 'Content-Type': 'application/json' } })
export const listModeTransitions = (limit = 100) => request.get(`${ADMIN_BASE}/transitions`, { params: { limit } })
export const getModeTransition = id => request.get(`${ADMIN_BASE}/transitions/${id}`)
export const getOperationsModeTransition = id => request.get(`${OPERATIONS_BASE}/${id}`)
export const retryModeTransition = id => request.post(`${OPERATIONS_BASE}/${id}/retry`)
