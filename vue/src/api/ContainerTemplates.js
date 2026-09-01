import request from '@/utils/request.js'

const BASE = 'super-admin/container-templates'

export const listContainerTemplates = () => request.get(BASE)
export const publishContainerTemplate = data => request.post(BASE, data, { headers: { 'Content-Type': 'application/json' } })
export const disableContainerTemplate = (id, version) => request.post(`${BASE}/${id}/versions/${version}/disable`)
export const deleteContainerTemplate = (id, version) => request.delete(`${BASE}/${id}/versions/${version}`)
export const listContainerTemplateVersions = id => request.get(`${BASE}/${id}/versions`)
