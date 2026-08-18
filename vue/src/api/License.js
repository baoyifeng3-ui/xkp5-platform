import request from '@/utils/request.js'

export function getLicenseStatus () {
  return request({ url: 'admin/license/status', method: 'get' })
}

export function createLicenseRequest (data) {
  return request({
    url: 'admin/license/requests',
    method: 'post',
    data,
    headers: { 'Content-Type': 'application/json' }
  })
}

export function downloadLicenseRequest (id) {
  return request({
    url: `admin/license/requests/${id}/download`,
    method: 'get',
    responseType: 'blob'
  })
}

export function importLicense (file) {
  const data = new FormData()
  data.append('file', file)
  return request({ url: 'admin/license/import', method: 'post', data })
}
