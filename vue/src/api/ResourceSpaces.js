import request from '@/utils/request.js'

const adminBase = 'admin/resource-spaces'
const userBase = 'user/resource-spaces'
const base = admin => admin ? adminBase : userBase
const json = { headers: { 'Content-Type': 'application/json' } }

export const listResourceEntries = (admin, space, parentId) => request.get(`${base(admin)}/${space}/entries`, { params: { parentId } })
export const createResourceDirectory = (admin, space, data) => request.post(`${base(admin)}/${space}/directories`, data, json)
export const uploadResourceFile = (admin, space, data, onUploadProgress) => request.post(`${base(admin)}/${space}/files`, data, { timeout: 0, onUploadProgress })
export const getResourceDownload = (admin, space, fileId) => request.get(`${base(admin)}/${space}/files/${fileId}/download`)
export const deleteResourceFile = (admin, space, fileId) => request.delete(`${base(admin)}/${space}/files/${fileId}`)
export const deleteResourceDirectory = (admin, space, directoryId) => request.delete(`${base(admin)}/${space}/directories/${directoryId}`)
export const getHomeworkSummary = () => request.get(`${adminBase}/homework/summary`)
export const clearResourceSpace = space => request.post(`${adminBase}/${space}/clear`, null, { params: { confirm: true } })
export const listCourseLibraryFiles = () => request.get(`${adminBase}/course/files`)
export const linkCourseResource = (fileId, courseId, resourceType) => request.post(`${adminBase}/course/files/${fileId}/courses/${courseId}`, { resourceType }, json)
export const unlinkCourseResource = (fileId, courseId) => request.delete(`${adminBase}/course/files/${fileId}/courses/${courseId}`)
export const deliverPublicResource = (fileId, environmentId) => request.post(`${userBase}/public/files/${fileId}/deliver`, null, { params: { environmentId } })
