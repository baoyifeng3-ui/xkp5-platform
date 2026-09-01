import request from '@/utils/request.js'

const ADMIN_BASE = 'admin/processing-agents'
const SUPER_BASE = 'super-admin/processing-agents'

export const listProcessingAgents = () => request.get(ADMIN_BASE)
export const getProcessingAgent = id => request.get(`${ADMIN_BASE}/${id}`)
export const getProcessingAgentHistory = (id, params) => request.get(`${ADMIN_BASE}/${id}/metrics`, { params })
export const listProcessingAgentCommands = id => request.get(`${ADMIN_BASE}/${id}/commands`)
export const wakeProcessingAgent = id => request.post(`${ADMIN_BASE}/${id}/wake`)
export const shutdownProcessingAgent = id => request.post(`${ADMIN_BASE}/${id}/shutdown`)
export const upgradeProcessingAgent = id => request.post(`${SUPER_BASE}/${id}/upgrade`)
export const listRegistrationTokens = () => request.get(`${SUPER_BASE}/registration-tokens`)
export const createRegistrationToken = data => request.post(`${SUPER_BASE}/registration-tokens`, data, { headers: { 'Content-Type': 'application/json' } })
export const enableProcessingAgent = id => request.post(`${SUPER_BASE}/${id}/enable`)
export const disableProcessingAgent = id => request.post(`${SUPER_BASE}/${id}/disable`)
export const removeProcessingAgent = id => request.delete(`${SUPER_BASE}/${id}`)
export const downloadAgentPackage = payload => request.post(`${SUPER_BASE}/package`, payload, { responseType: 'blob', headers: { 'Content-Type': 'application/json' } })
export const checkAgentConnectivity = serverIp => request.get(`${SUPER_BASE}/connectivity`, { params: { serverIp } })
export const remoteDeployAgent = payload => request.post(`${SUPER_BASE}/remote-deploy`, payload, { headers: { 'Content-Type': 'application/json' }, timeout: 0 })
export const listAgentPortPools = id => request.get(`${SUPER_BASE}/${id}/port-pools`)
export const updateAgentPortPool = (agentId, poolId, payload) => request.put(`${SUPER_BASE}/${agentId}/port-pools/${poolId}`, payload, { headers: { 'Content-Type': 'application/json' } })
