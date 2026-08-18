import axios from 'axios'
import { Message } from 'element-ui'
import router from '@/router'
import { clearSession, getToken } from '@/utils/auth'

function handleUnauthorized () {
    const hadToken = Boolean(getToken())
    clearSession()
    if (router.currentRoute.path !== '/login') {
        router.replace({ path: '/login' }).catch(() => {})
    }
    if (hadToken) {
        Message({ message: '登录已失效，请重新登录', type: 'error', duration: 5000 })
    }
}

const service = axios.create({
    baseURL: '/api/',
    timeout: 15000,
    headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        satoken: getToken()
    }
})

service.interceptors.request.use(
    config => {
        config.headers.satoken = getToken()
        // Let the browser add the multipart boundary for FormData requests.
        if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
            delete config.headers['Content-Type']
            delete config.headers['content-type']
        }
        return config
    },
    error => Promise.reject(error)
)

service.interceptors.response.use(
    response => {
        if (response.config && response.config.responseType === 'blob') {
            return response
        }
        const result = response.data
        if (result.code === 401) {
            handleUnauthorized()
        }
        return result
    },
    error => {
        let message = '请求失败，请稍后重试'
        if (error.code === 'ECONNABORTED') {
            message = '请求超时，请检查后端服务'
        } else if (!error.response) {
            message = '无法连接后端服务，请确认 localhost:19141 已启动'
        } else if (error.response.status === 401) {
            handleUnauthorized()
            return Promise.reject(error)
        } else if (error.response.data) {
            const responseData = error.response.data
            if (typeof responseData === 'string') {
                message = responseData
            } else if (responseData.msg) {
                message = responseData.msg
            } else if (responseData.data && responseData.data.msg) {
                message = responseData.data.msg
            } else if (error.response.status === 403) {
                message = '没有权限执行此操作'
            }
        } else if (error.response.status === 403) {
            message = '没有权限执行此操作'
        }
        Message({ message, type: 'error', duration: 5000 })
        return Promise.reject(error)
    }
)

export default service
