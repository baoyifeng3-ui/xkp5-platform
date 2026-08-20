import {
    getListbyTypeApi,
    getListByMutiTypeApi,
    insertDiggWellApi,
    getWellListApi,
    addSampleApi,
    updateSampleApi,
    getAllDetectListBySampleNoApi,
    getSimilarSampleBySampleNoApi,
    deleteSampleApi,
    analysisImageApi,
    exportExcelApi,
    handleBase64Api
} from '@/api/Sample.js'

const state = {
    tableData: [],
    total: 0,
    current: 0,
    pages: 0,
    size: 0,
    wellList: [],
    detectList: {
        diggingsWell: {},
        user: {},
        sampleInspectionRecordAll: []
    },
    similar: [],
    detailList: {
        diggingsWell: {},
        user: {},
        sampleInspectionRecordAll: []
    },
    detail: {}
}

const mutations = {
    SET_TABLEDATA(state, tableData) {
        state.tableData = tableData
    },
    SET_TOTAL(state, total) {
        state.total = total
    },
    SET_WELLLIST(state, wellList) {
        state.wellList = wellList
    },
    SET_DETECTLIST(state, detectList) {
        state.detectList = detectList
    },
    SET_SIMILAR(state, similar) {
        state.similar = similar
    },
    SET_PAGE(state, data) {
        state.current = data.current;
        state.pages = data.pages;
        state.size = data.size
    },
    SET_DETAIL(state, detail) {
        state.detail = detail
    }
}

const actions = {
    async getListbyType({ commit }, data) {
        const r = await getListbyTypeApi(data)
        commit('SET_TABLEDATA', r.data.records)
        commit('SET_TOTAL', r.data.total)
    },
    async getListByMutiType({ commit }, data) {
        const r = await getListByMutiTypeApi(data)
        commit('SET_TABLEDATA', r.data.records)
        commit('SET_TOTAL', r.data.total)
        commit('SET_PAGE', r.data)
    },
    async insertDiggWell({ commit }, data) {
        await insertDiggWellApi(data)
    },
    async getWellList({ commit }, data) {
        const r = await getWellListApi(data)
        commit('SET_WELLLIST', r.data)
    },
    async addSample({ commit }, data) {
        const r = await addSampleApi(data)
        return r.code
    },
    async updateSample({ commit }, data) {
        const r = await updateSampleApi(data)
        return r.code
    },
    async getAllDetectListBySampleNo({ commit }, data) {
        const r = await getAllDetectListBySampleNoApi(data)
        commit('SET_DETECTLIST', r.data)
    },
    async getSimilarSampleBySampleNo({ commit }, data) {
        const r = await getSimilarSampleBySampleNoApi(data)
        commit('SET_SIMILAR', r.data)
    },
    async deleteSample({ commit }, data) {
        await deleteSampleApi(data)
    },
    async browserDetail({ commit }, data) {
        const r = await getAllDetectListBySampleNoApi(data)
        commit('SET_DETAIL', r.data)
    },
    async analysisImage({ commit }, data) {
        const r = await analysisImageApi(data)
        console.log(r);
    },
    async exportExcel({ commit }, data) {
        const r = await exportExcelApi(data)
        return r
    },
    async handleBase64({ commit }, data) {
        const r = await handleBase64Api(data)
        console.log(r);
    }
}

export default {
    namespaced: true,
    state,
    mutations,
    actions
}