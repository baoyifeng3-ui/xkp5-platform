import request from '@/utils/request.js'

const ADMIN_BASE = 'admin/competition-slots'
const SUPER_BASE = 'super-admin/competition-environments'

export const listCompetitionSlots = () => request.get(ADMIN_BASE)
export const bindCompetitionSlot = (slotId, userId) => request.post(`${ADMIN_BASE}/${slotId}/binding`, { userId }, { headers: { 'Content-Type': 'application/json' } })
export const unbindCompetitionSlot = (slotId, userId) => request.delete(`${ADMIN_BASE}/${slotId}/binding/${userId}`)
export const listCompetitionEnvironments = () => request.get(SUPER_BASE)
export const getCompetitionEnvironment = id => request.get(`${SUPER_BASE}/${id}`)
export const createCompetitionEnvironment = data => request.post(SUPER_BASE, data)
export const restoreCompetitionEnvironment = id => request.post(`${SUPER_BASE}/${id}/restore`)
