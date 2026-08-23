import request from '@/utils/request.js'
export const listAdminCourses = () => request.get('/admin/courses')
export const createCourse = payload => request.post('/admin/courses', payload)
export const updateCourse = (id, payload) => request.put(`/admin/courses/${id}`, payload)
export const setCourseEnabled = (id, enabled) => request.post(`/admin/courses/${id}/enabled`, null, { params: { enabled } })
export const addCourseResource = (id, payload) => request.post(`/admin/courses/${id}/resources`, payload)
export const uploadCourseResource = file => { const data = new FormData(); data.append('file', file); return request.post('/admin/course-resources/upload', data) }
export const listUserCourses = () => request.get('/user/courses')
export const recordCourseProgress = payload => request.post('/user/courses/progress', payload)
export const listCourseProgress = () => request.get('/user/courses/progress')
export const deliverCourseResource = (resourceId, environmentId) => request.post(`/course-resources/${resourceId}/deliver`, null, { params: { environmentId } })
export const deliverCourseResourceForUser = (resourceId, environmentId, userId) => request.post(`/course-resources/${resourceId}/deliver-for-user`, null, { params: { environmentId, userId } })
export const deliverCourseResourceToAll = resourceId => request.post(`/course-resources/${resourceId}/deliver-to-all`)
