import request from '@/utils/request'

export const listTrainingModelFiles = environmentId =>
    request.post(`/training-models/environments/${environmentId}/files`)
export const deployTrainingModel = (environmentId, data) =>
    request.post(`/training-models/environments/${environmentId}/deploy`, data, {
        headers: { 'Content-Type': 'application/json' }
    })
export const trainingModelCommand = commandId =>
    request.get(`/training-models/commands/${commandId}`)
