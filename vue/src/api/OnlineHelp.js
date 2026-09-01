import request from '@/utils/request'
const json={headers:{'Content-Type':'application/json'}}
export const listHelpDocuments=()=>request.get('/help-documents')
export const listAllHelpDocuments=()=>request.get('/super-admin/help-documents')
export const saveHelpDocument=data=>request.post('/super-admin/help-documents',data,json)
export const uploadHelpDocument=(data,params)=>request.post('/super-admin/help-documents/upload',data,{params,timeout:0})
export const uploadHelpImage=data=>request.post('/super-admin/help-images',data,{timeout:0})
export const setHelpPublished=(id,value)=>request.post(`/super-admin/help-documents/${id}/published`,null,{params:{value}})
