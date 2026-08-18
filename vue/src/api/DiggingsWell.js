import request from '@/utils/request.js'
import Qs from 'qs'

export function getDiggingsTableApi(data) {
    return request({
        url: "diggings-well/table",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function exportTableApi(data) {
    return request({
        url: "diggings-well/table/export",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function getMapApi(data) {
    return request({
        url: "diggings-well/map",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function getLineOrBodyApi(data) {
    return request({
        url: "diggings-well/lineOrBody",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function getSurfaceApi(data) {
    return request({
        url: "diggings-well/surface",
        method: "post",
        data: Qs.stringify(data)
    })
}
