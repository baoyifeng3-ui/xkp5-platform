import request from '@/utils/request.js'
export const getLearningOverview=()=>request.get('/admin/learning-analysis/overview')
export const listEvaluations=id=>request.get(`/admin/learning-analysis/users/${id}/evaluations`)
export const createEvaluation=(id,data)=>request.post(`/admin/learning-analysis/users/${id}/evaluations`,data,{headers:{'Content-Type':'application/json'}})
export const listMyEvaluations=()=>request.get('/user/learning-evaluations')
