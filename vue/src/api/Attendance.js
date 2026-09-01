import request from '@/utils/request'

export const getAttendanceStatus = () => request.get('/attendance/status', { silentError: true })
export const startAttendance = () => request.post('/attendance/start')
export const endAttendance = () => request.post('/attendance/end')
export const checkInAttendance = () => request.post('/attendance/check-in')
