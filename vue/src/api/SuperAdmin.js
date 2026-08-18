import request from '@/utils/request.js'

const json = { headers: { 'Content-Type': 'application/json' } }

export const listAdministrators = () => request.get('super-admin/administrators')
export const createAdministrator = data => request.post('super-admin/administrators', data, json)
export const updateAdministrator = (id, data) => request.put(`super-admin/administrators/${id}`, data, json)
export const resetAdministratorPassword = (id, data) => request.post(`super-admin/administrators/${id}/reset-password`, data, json)
export const getLicenseDiagnostics = () => request.get('super-admin/license/diagnostics')
export const listLicenseAudits = params => request.get('super-admin/license/audits', { params })
export const revalidateLicense = () => request.post('super-admin/license/revalidate')
