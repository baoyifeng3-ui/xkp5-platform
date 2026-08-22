import request from '@/utils/request.js'
export const listAdminCourses = () => request.get('/admin/courses')
export const createCourse = payload => request.post('/admin/courses', payload)
export const updateCourse = (id, payload) => request.put(`/admin/courses/${id}`, payload)
export const setCourseEnabled = (id, enabled) => request.post(`/admin/courses/${id}/enabled`, null, { params: { enabled } })
export const addCourseResource = (id, payload) => request.post(`/admin/courses/${id}/resources`, payload)
export const listUserCourses = () => request.get('/user/courses')
export const recordCourseProgress = payload => request.post('/user/courses/progress', payload)
