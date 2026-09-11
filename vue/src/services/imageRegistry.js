import request from '@/utils/request.js'

const GROUPS = 'admin/image-groups'
const ARTIFACTS = 'admin/image-artifacts'
const UPLOADS = 'admin/image-uploads'
const RELEASES = 'admin/image-releases'
const FILES = 'admin/image-files'
const inventory = agentId => `admin/docker-inventory/${encodeURIComponent(agentId)}`

export const listImageGroups = params => request.get(GROUPS, { params })
export const listImageArtifacts = params => request.get(ARTIFACTS, { params })
export const listImageReleases = params => request.get(RELEASES, { params })
export const listImageDeployments = params => request.get(`${RELEASES}/deployments`, { params })

export const createImageUpload = data => request.post(UPLOADS, data, { silentError: true, headers: { 'Content-Type': 'application/json' } })
export const getImageUploadStatus = uploadId => request.get(`${UPLOADS}/${uploadId}`, { silentError: true })
export const getImageUploadByArtifact = artifactId => request.get(`${UPLOADS}/artifact/${artifactId}`)
export const uploadImageChunk = (uploadId, chunkIndex, chunk, onUploadProgress) => {
  const data = new FormData()
  data.append('file', chunk)
  return request.put(`${UPLOADS}/${uploadId}/chunks/${chunkIndex}`, data, {
    timeout: 0,
    onUploadProgress
  })
}
export const listImageFiles = params => request.get(FILES, { params })
export const deleteImageFile = fileId => request.delete(`${FILES}/${fileId}`)
export const uploadImageFile = (data, onUploadProgress) => request.post(`${FILES}/upload`, data, { timeout: 0, onUploadProgress, headers: { 'Content-Type': 'multipart/form-data' } })
export const deployImageFile = (fileId, data) => request.post(`${FILES}/${fileId}/deploy`, data, { headers: { 'Content-Type': 'application/json' } })
export const completeImageUpload = uploadId => request.post(`${UPLOADS}/${uploadId}/complete`, null, { timeout: 0 })
export const cancelImageUpload = uploadId => request.post(`${UPLOADS}/${uploadId}/cancel`, null, { silentError: true })
export const deleteFailedImageUpload = uploadId => request.delete(`${UPLOADS}/${uploadId}`)
export const deleteImageArtifact = artifactId => request.delete(`${ARTIFACTS}/${artifactId}`)
export const reviewImageArtifact = (artifactId, decision, reason = '') => request.post(`${ARTIFACTS}/${artifactId}/review`, { decision, reason }, { headers: { 'Content-Type': 'application/json' } })

export const publishImageRelease = artifactId => request.post(`${RELEASES}/publish/${artifactId}`)
export const deployImageRelease = (releaseId, data) => request.post(`${RELEASES}/${releaseId}/deploy`, data, { headers: { 'Content-Type': 'application/json' } })
export const getImageDeployment = deploymentId => request.get(`${RELEASES}/deployments/${deploymentId}`)
export const deleteFailedImageDeployment = deploymentId => request.delete(`${RELEASES}/deployments/${deploymentId}`)
export const rollbackImageDeployment = deploymentId => request.post(`${RELEASES}/deployments/${deploymentId}/rollback`, null, { params: { confirm: true } })
export const listDockerImages = agentId => request.get(`${inventory(agentId)}/images`)
export const inspectDockerImage = (agentId, target) => request.post(`${inventory(agentId)}/inspect`, { target })
export const getDockerCommand = (agentId, commandId) => request.get(`${inventory(agentId)}/commands/${commandId}`)
export const deleteDockerImage = (agentId, target, id) => request.delete(`${inventory(agentId)}/images`, { data: { target, id } })
export const deleteDockerContainer = (agentId, target, id) => request.delete(`${inventory(agentId)}/containers`, { data: { target, id } })
