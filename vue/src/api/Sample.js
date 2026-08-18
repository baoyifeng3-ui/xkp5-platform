import request from '@/utils/request.js'
import Qs from 'qs'

export function getListbyTypeApi(data) {
    return request({
        url: "sample/getListByType",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function getListByMutiTypeApi(data) {
    return request({
        url: "sample/getListByMutiType",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function insertDiggWellApi(data) {
    return request({
        url: "diggings-well/insertDiggWell",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function getWellListApi(data) {
    return request({
        url: "diggings-well/list",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function addSampleApi(data) {
    return request({
        url: "sample/add",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function updateSampleApi(data) {
    return request({
        url: "sample/update",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function getAllDetectListBySampleNoApi(data) {
    return request({
        url: "sample/getAllDetectListBySampleNo",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function getSimilarSampleBySampleNoApi(data) {
    return request({
        url: "sample/getSimilarSampleBySampleNo",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function deleteSampleApi(data) {
    return request({
        url: "sample/delete",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function analysisImageApi(data) {
    return request({
        url: "sample/redetect",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function exportExcelApi(data) {
    return request({
        url: "sample/getListByMutiType/export",
        method: "post",
        data: Qs.stringify(data),
        responseType: 'blob'
    })
}

export function handleBase64Api(data) {
    return request({
        url: "sample/httpToBase64",
        method: "post",
        data: Qs.stringify(data)
    })
}
