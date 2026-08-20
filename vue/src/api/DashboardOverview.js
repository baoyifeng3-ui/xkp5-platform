import request from '@/utils/request.js'

export function getDashboardOverview () {
  return request({ url: 'admin/dashboard/overview', method: 'get' })
}
