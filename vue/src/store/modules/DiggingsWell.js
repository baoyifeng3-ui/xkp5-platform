import { getDiggingsTableApi, exportTableApi } from '@/api/DiggingsWell.js'

const state = {
    tableData: [],
    total: 0,
    current: 0,
    pages: 0,
    size: 0,
}

const mutations = {
    SET_TABLEDATA(state, tableData) {
        state.tableData = tableData
    },
    SET_TOTAL(state, total) {
        state.total = total
    },
    SET_PAGE(state, data) {
        state.current = data.current;
        state.pages = data.pages;
        state.size = data.size
    }
}

const actions = {
    async getdiggingsTable({ commit }, data) {
        const r = await getDiggingsTableApi(data)
        commit('SET_TABLEDATA', r.data.records)
        commit('SET_TOTAL', r.data.total)
        commit('SET_PAGE', r.data)
    },
    async exportTable({ commit }, data) {
        const r = await exportTableApi(data)
        console.log(r);
    }
}

export default {
    namespaced: true,
    state,
    mutations,
    actions
}