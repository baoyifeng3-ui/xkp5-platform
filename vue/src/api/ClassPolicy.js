import request from '@/utils/request'
export const getUserClassPolicy = () => request.get('/user/class-policy', { silentError: true })
