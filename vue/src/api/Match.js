import request from '@/utils/request.js'
import Qs from 'qs'

export function userLoginApi (data) {
    return request({
        url: "user/login",
        method: "post",
        data: Qs.stringify(data)
    })
}

export function currentUserApi () {
    return request({ url: 'user/me', method: 'get' })
}

export function changePasswordApi (data) {
    return request({
        url: 'user/change-password',
        method: 'post',
        data,
        headers: { 'Content-Type': 'application/json' }
    })
}

export function competitionApi () {
    return request({ url: 'competition', method: 'get' })
}

export function scoreApi (data) {
    return request({
        url: "score",
        method: "put",
        data: Qs.stringify({ anotation: JSON.stringify(data.anotation), paperType: data.paperType })
    })
}

export function scoreTopApi (params) {
    return request({
        url: "score/top",
        method: "get",
        params
    })
}

export function trainUrlApi (params) {
    return request({
        url: "train-url",
        method: "get",
        params
    })
}

export function getClearTime (params) {
    return request({
        url: "countdown",
        method: "get",
        params
    })
}

export function adminCountdownApi (data) {
    return request({
        url: 'admin/countdown',
        method: 'post',
        data,
        headers: { 'Content-Type': 'application/json' }
    })
}

export function adminUsersApi () {
    return request({ url: 'admin/users', method: 'get' })
}

export function createAdminUserApi (data) {
    return request({ url: 'admin/users', method: 'post', data, headers: { 'Content-Type': 'application/json' } })
}

export function createAdminUsersBatchApi (data) {
    return request({ url: 'admin/users/batch', method: 'post', data, headers: { 'Content-Type': 'application/json' } })
}

export function clearAdminUsersApi () {
    return request({ url: 'admin/users/participants', method: 'delete' })
}

export function updateAdminUserApi (userId, data) {
    return request({ url: `admin/users/${userId}`, method: 'put', data, headers: { 'Content-Type': 'application/json' } })
}

export function announcementFieldsApi () {
    return request({ url: 'admin/announcement-fields', method: 'get' })
}

export function createAnnouncementFieldApi (data) {
    return request({ url: 'admin/announcement-fields', method: 'post', data, headers: { 'Content-Type': 'application/json' } })
}

export function updateAnnouncementFieldApi (fieldId, data) {
    return request({ url: `admin/announcement-fields/${fieldId}`, method: 'put', data, headers: { 'Content-Type': 'application/json' } })
}

export function adminNoticeApi () {
    return request({ url: 'admin/notice', method: 'get' })
}

export function updateAdminNoticeApi (data) {
    return request({ url: 'admin/notice', method: 'put', data, headers: { 'Content-Type': 'application/json' } })
}

export function trainingServersApi () {
    return request({ url: 'admin/training/servers', method: 'get' })
}

export function trainingServerHeartbeatApi () {
    return request({ url: 'admin/training/servers/heartbeat', method: 'get', timeout: 10000 })
}

export function createTrainingServerApi (data) {
    return request({ url: 'admin/training/servers', method: 'post', data, headers: { 'Content-Type': 'application/json' } })
}

export function updateTrainingServerApi (data) {
    return request({ url: 'admin/training/servers', method: 'put', data, headers: { 'Content-Type': 'application/json' } })
}

export function deleteTrainingServerApi (trainingServerId) {
    return request({ url: `admin/training/servers/${trainingServerId}`, method: 'delete' })
}

export function assignTrainingApi (data) {
    return request({ url: 'admin/training/assignments', method: 'post', data, headers: { 'Content-Type': 'application/json' } })
}

export function unassignTrainingApi (userId) {
    return request({ url: 'admin/training/assignments', method: 'delete', params: { userId } })
}

export function adminSubjectsApi (params) {
    return request({ url: 'admin/subjects', method: 'get', params })
}

export function createAdminSubjectApi (data) {
    return request({ url: 'admin/subjects', method: 'post', data, headers: { 'Content-Type': 'application/json' } })
}

export function updateAdminSubjectApi (subjectId, data) {
    return request({ url: `admin/subjects/${subjectId}`, method: 'put', data, headers: { 'Content-Type': 'application/json' } })
}

export function deleteAdminSubjectApi (subjectId) {
    return request({ url: `admin/subjects/${subjectId}`, method: 'delete' })
}

export function clearAdminSubjectAnswersApi () {
    return request({ url: 'admin/subjects/answers', method: 'delete' })
}

export function clearAdminSubjectsApi () {
    return request({ url: 'admin/subjects', method: 'delete' })
}

export function selectCompetitionApi (paperType) {
    return request({
        url: 'competition',
        method: 'post',
        data: { paperType },
        headers: { 'Content-Type': 'application/json' }
    })
}

export function createPaperApi (paperName) {
    return request({
        url: 'competition/papers',
        method: 'post',
        data: { paperName },
        headers: { 'Content-Type': 'application/json' }
    })
}

export function updatePlatformNameApi (data) {
    return request({
        url: 'competition/platform-name',
        method: 'put',
        data,
        headers: { 'Content-Type': 'application/json' }
    })
}

export function updatePlatformSettingsApi (data) {
    return request({
        url: 'competition/settings',
        method: 'put',
        data,
        headers: { 'Content-Type': 'application/json' }
    })
}

export function uploadLoginBackgroundApi (file) {
    const data = new FormData()
    data.append('file', file)
    return request({
        url: 'competition/settings/login-background',
        method: 'post',
        data
    })
}

export function updateCompetitionContentApi (data) {
    return request({
        url: 'competition/content',
        method: 'put',
        data,
        headers: { 'Content-Type': 'application/json' }
    })
}

export function updateCompetitionHelpApi (data) {
    return request({
        url: 'competition/help',
        method: 'put',
        data,
        headers: { 'Content-Type': 'application/json' }
    })
}

export function getSubject (params) {
    return request({
        url: "testPaper/getAnswer",
        method: "get",
        params
    })
}

export function getTeamInformation (params) {
    return request({
        url: "teams",
        method: "get",
        params
    })
}
export function getNoticelist (params) {
    return request({
        url: "notice/getNotice",
        method: "get",
        params
    })
}
export function preservationAnswer (data) {
    return request({
        url: "testPaper/addAnswer",
        method: "post",
        data: data
    })
}

export function saveSubjectAnswerApi (data) {
    return request({
        url: 'testPaper/addAnswer',
        method: 'post',
        data
    })
}

export function paperSubmissionStatusApi (testPaperType) {
    return request({ url: 'testPaper/submission', method: 'get', params: { testPaperType } })
}

export function submitPaperApi (testPaperType) {
    return request({
        url: 'testPaper/submit',
        method: 'post',
        data: Qs.stringify({ testPaperType })
    })
}

export function gradingSubmissionsApi (params) {
    return request({ url: 'admin/grading/submissions', method: 'get', params })
}

export function gradingSubmissionApi (submissionId) {
    return request({ url: `admin/grading/submissions/${submissionId}`, method: 'get' })
}

export function saveGradingScoresApi (submissionId, data) {
    return request({
        url: `admin/grading/submissions/${submissionId}/scores`,
        method: 'put',
        data,
        headers: { 'Content-Type': 'application/json' }
    })
}

export function completeGradingApi (submissionId) {
    return request({ url: `admin/grading/submissions/${submissionId}/complete`, method: 'post' })
}

export function returnSubmissionApi (submissionId, reason) {
    return request({
        url: `admin/grading/submissions/${submissionId}/return`,
        method: 'post',
        data: { reason },
        headers: { 'Content-Type': 'application/json' }
    })
}

export function gradingExportsApi (paperType) {
    return request({ url: 'admin/grading/exports', method: 'get', params: { paperType } })
}

export function gradingExportStatusApi (paperType) {
    return request({ url: 'admin/grading/exports/status', method: 'get', params: { paperType } })
}

export function createGradingExportApi (paperType) {
    return request({
        url: 'admin/grading/exports',
        method: 'post',
        params: { paperType },
        responseType: 'blob',
        timeout: 120000
    })
}

export function downloadGradingExportApi (batchId) {
    return request({
        url: `admin/grading/exports/${batchId}/download`,
        method: 'get',
        responseType: 'blob',
        timeout: 120000
    })
}

export function getTeamUserApi (params) {
    return request({
        url: "teams/teamsUser",
        method: "get",
        params
    })
}

export function getUserApi (params) {
    return request({
        url: "teams/user",
        method: "get",
        params
    })
}
