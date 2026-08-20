import { userLoginApi, scoreTopApi, scoreApi, trainUrlApi, getUserApi, getTeamUserApi, competitionApi } from "@/api/Match";
import { setToken, getToken, setPlan, getPlan, setUserName, getUserName, setUserInfo, getUserInfo, clearSession } from "@/utils/auth"
import { Message } from 'element-ui'

const DEFAULT_PLATFORM_NAME = 'XKP5.0平台'
const DEFAULT_THEME_COLOR = '#162d45'

const state = {
    token: getToken("satoken"),
    userName: getUserName("userName"),
    userInfo: getUserInfo(),
    role: getUserInfo().role || 'USER',
    isAdmin: getUserInfo().admin === true,
    mustChangePassword: getUserInfo().mustChangePassword === true,
    platformName: DEFAULT_PLATFORM_NAME,
    themeColor: DEFAULT_THEME_COLOR,
    loginBackgroundUrl: '',
    loginBrandName: 'XKP5.0平台',
    loginTitle: '进入平台工作台',
    loginDescription: '请使用已分配的平台账号登录。',
    loginCopyright: '2026 XKP5.0平台',
    tableData: [],
    plan: getPlan("Plan"),
    activePaper: getPlan() || '',
    url: {},
    isLight: true,
    layout_match: ''
}

const mutations = {
    SET_USER (state, userName) {
        setUserName(userName)
        state.userName = userName
    },
    SET_TABLEDATA (state, tableData) {
        state.tableData = tableData
    },
    SET_PLAN (state, plan) {
        setPlan(plan)
        state.plan = plan
        state.layout_match = plan
    },
    SET_TOKEN (state, token) {
        setToken(token)
        state.token = token
    },
    SET_USER_INFO (state, userInfo) {
        setUserInfo(userInfo)
        state.userInfo = userInfo
        state.role = userInfo.role || (userInfo.admin === true ? 'ADMIN' : 'USER')
        state.isAdmin = userInfo.admin === true
        state.mustChangePassword = userInfo.mustChangePassword === true
    },
    SET_ACTIVE_PAPER (state, paper) {
        state.activePaper = paper || ''
        setPlan(state.activePaper)
        state.plan = state.activePaper
    },
    SET_PLATFORM_NAME (state, platformName) {
        state.platformName = String(platformName || '').trim() || DEFAULT_PLATFORM_NAME
    },
    SET_PLATFORM_SETTINGS (state, settings) {
        const value = settings || {}
        state.platformName = String(value.platformName || '').trim() || DEFAULT_PLATFORM_NAME
        state.themeColor = /^#[0-9a-f]{6}$/i.test(String(value.themeColor || ''))
            ? String(value.themeColor).toLowerCase()
            : DEFAULT_THEME_COLOR
        state.loginBackgroundUrl = String(value.loginBackgroundUrl || '').trim()
        state.loginBrandName = String(value.loginBrandName || '').trim() || 'XKP5.0平台'
        state.loginTitle = String(value.loginTitle || '').trim() || '进入平台工作台'
        state.loginDescription = String(value.loginDescription || '').trim() || '请使用已分配的平台账号登录。'
        state.loginCopyright = String(value.loginCopyright || '').trim() || '2026 XKP5.0平台'
        document.documentElement.style.setProperty('--platform-theme-color', state.themeColor)
    },
    SET_URL (state, url) {
        state.url = url
    },
    SET_ISLIGHT (state) {
        state.isLight = !state.isLight
    },
    RESET_SESSION (state) {
        clearSession()
        state.token = ''
        state.userName = ''
        state.userInfo = {}
        state.role = 'USER'
        state.isAdmin = false
        state.mustChangePassword = false
        state.platformName = DEFAULT_PLATFORM_NAME
        state.themeColor = DEFAULT_THEME_COLOR
        state.loginBackgroundUrl = ''
        state.loginBrandName = 'XKP5.0平台'
        state.loginTitle = '进入平台工作台'
        state.loginDescription = '请使用已分配的平台账号登录。'
        state.loginCopyright = '2026 XKP5.0平台'
        state.plan = ''
        state.activePaper = ''
    }
}

const actions = {
    async userLogin ({ commit, dispatch }, data) {
        const r = await userLoginApi(data)
        if (r.code == 200) {
            commit("SET_USER", r.data.userName)
            commit("SET_TOKEN", r.data.tokenValue)
            sessionStorage.setItem('userId', r.data.loginId)
            commit("SET_USER_INFO", r.data)
            try {
                await dispatch("syncActivePaper")
            } catch (error) {
                // The request interceptor reports the sync failure; login remains valid.
            }
            Message({
                message: '登录成功',
                type: 'success',
            })
            return r.data
        } else {
            Message({
                message: r.msg || '登录失败',
                type: 'error',
            })
        }
    },
    async syncActivePaper ({ commit }) {
        const competition = await competitionApi()
        if (competition.code !== 200) return { synced: false, activePaper: '' }
        const paper = String((competition.data && competition.data.activePaper) || '').trim().toUpperCase()
        const activePaper = /^[A-Z]$/.test(paper) ? paper : ''
        commit("SET_ACTIVE_PAPER", activePaper)
        commit("SET_PLATFORM_SETTINGS", competition.data)
        return { synced: true, activePaper: activePaper || '' }
    },
    async scoreTop ({ commit }) {
        const r = await scoreTopApi()
        console.log(r)
        commit("SET_TABLEDATA", r.data)
    },
    async getScore ({ commit }, data) {
        const r = await scoreApi(data)
        return r
    },
    async trainUrl ({ commit }) {
        const r = await trainUrlApi()
        if (r.code == 200) {
            commit("SET_URL", r.data)
            return r.data
        }
    },
    async getTeamUser ({ commit }) {
        const r = await getTeamUserApi()
        if (r.code == 200) {
            return r.data
        }
    },
    async getUser ({ commit }) {
        const r = await getUserApi()
        if (r.code == 200) {
            return r.data
        }
    },
}

export default {
    namespaced: true,
    state,
    mutations,
    actions
}
