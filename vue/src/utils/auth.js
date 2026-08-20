const TokenKey = 'admin-token'
const PlanKey = 'Plan'
const UserNameKey = 'UserName'
const UserInfoKey = 'UserInfo'
const CompetitionAccessPhaseKey = 'CompetitionAccessPhase'
const roleNavigation = require('@/navigation/roleNavigation')

export function getToken () {
    return sessionStorage.getItem(TokenKey)
}

export function setToken (token) {
    return sessionStorage.setItem(TokenKey, token)
}

export function removeToken () {
    sessionStorage.removeItem(TokenKey)
}

export function setPlan (plan) {
    if (plan) sessionStorage.setItem(PlanKey, plan)
    else sessionStorage.removeItem(PlanKey)
}

export function getPlan () {
    return sessionStorage.getItem(PlanKey)
}

export function removePlan () {
    sessionStorage.removeItem(PlanKey)
}

export function setUserName (userName) {
    sessionStorage.setItem(UserNameKey, userName || '')
}

export function getUserName () {
    return sessionStorage.getItem(UserNameKey)
}

export function setUserInfo (userInfo) {
    sessionStorage.setItem(UserInfoKey, JSON.stringify(userInfo || {}))
}

export function getUserInfo () {
    try {
        return JSON.parse(sessionStorage.getItem(UserInfoKey) || '{}')
    } catch (error) {
        return {}
    }
}

export function isAdmin () {
    return [roleNavigation.ADMIN, roleNavigation.SUPER_ADMIN].includes(getRole())
}

export function getRole () {
    const info = getUserInfo()
    if (info.role) return String(info.role).toUpperCase()
    return info.admin === true ? roleNavigation.ADMIN : roleNavigation.USER
}

export function hasRole (role) {
    return getRole() === role
}

export function landingRoute () {
    return roleNavigation.landingRoute(getRole())
}

export function mustChangePassword () {
    return getUserInfo().mustChangePassword === true
}

export function setCompetitionAccessPhase (phase) {
    sessionStorage.setItem(CompetitionAccessPhaseKey, phase || '')
}

export function getCompetitionAccessPhase () {
    return sessionStorage.getItem(CompetitionAccessPhaseKey) || ''
}

export function clearSession () {
    removeToken()
    removePlan()
    sessionStorage.removeItem(UserNameKey)
    sessionStorage.removeItem(UserInfoKey)
    sessionStorage.removeItem('userId')
    sessionStorage.removeItem(CompetitionAccessPhaseKey)
}
