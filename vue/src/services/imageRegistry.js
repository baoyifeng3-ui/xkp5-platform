import request from '@/utils/request.js'

const GROUPS = 'admin/image-groups'
const ARTIFACTS = 'admin/image-artifacts'
const UPLOADS = 'admin/image-uploads'
const RELEASES = 'admin/image-releases'

export const listImageGroups = params => request.get(GROUPS, { params })
export const listImageArtifacts = params => request.get(ARTIFACTS, { params })
export const listImageReleases = params => request.get(RELEASES, { params })
export const listImageDeployments = params => request.get(`${RELEASES}/deployments`, { params })

export const createImageUpload = data => request.post(UPLOADS, data, { headers: { 'Content-Type': 'application/json' } })
export const getImageUploadStatus = uploadId => request.get(`${UPLOADS}/${uploadId}`)
export const uploadImageChunk = (uploadId, chunkIndex, chunk, checksum, onUploadProgress) => {
  const data = new FormData()
  data.append('file', chunk)
  return request.put(`${UPLOADS}/${uploadId}/chunks/${chunkIndex}`, data, {
    headers: { 'X-Chunk-SHA256': checksum },
    timeout: 0,
    onUploadProgress
  })
}
export const completeImageUpload = uploadId => request.post(`${UPLOADS}/${uploadId}/complete`)
export const cancelImageUpload = uploadId => request.post(`${UPLOADS}/${uploadId}/cancel`)
export const reviewImageArtifact = (artifactId, decision, reason = '') => request.post(`${ARTIFACTS}/${artifactId}/review`, { decision, reason })

export const publishImageRelease = artifactId => request.post(`${RELEASES}/publish/${artifactId}`)
export const deployImageRelease = (releaseId, data) => request.post(`${RELEASES}/${releaseId}/deploy`, data)
export const getImageDeployment = deploymentId => request.get(`${RELEASES}/deployments/${deploymentId}`)
export const rollbackImageDeployment = deploymentId => request.post(`${RELEASES}/deployments/${deploymentId}/rollback`, null, { params: { confirm: true } })
