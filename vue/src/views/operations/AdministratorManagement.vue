<template>
  <section class="module-page">
    <header class="module-heading account-heading"><div><h1>管理员账号</h1><p>创建和维护日常使用的普通管理员账号。</p></div><el-button type="primary" icon="el-icon-plus" @click="openCreate">新建管理员</el-button></header>
    <div class="account-summary"><span><small>普通管理员</small><strong>{{ administrators.length }}</strong></span><span><small>已启用</small><strong>{{ enabledCount }}</strong></span><span><small>需修改初始密码</small><strong>{{ passwordChangeCount }}</strong></span></div>
    <el-table v-loading="loading" :data="administrators" empty-text="暂无普通管理员账号" class="account-table">
      <el-table-column prop="userName" label="账号" min-width="220"><template slot-scope="scope"><div class="account-name"><span>{{ initial(scope.row.userName) }}</span><div><strong>{{ scope.row.userName }}</strong><small>普通管理员</small></div></div></template></el-table-column>
      <el-table-column label="首次改密" width="150"><template slot-scope="scope"><el-tag :type="scope.row.mustChangePassword ? 'warning' : 'success'" size="small">{{ scope.row.mustChangePassword ? '待修改' : '已完成' }}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="180"><template slot-scope="scope"><div class="status-control"><el-switch :value="scope.row.enabled" :disabled="updatingId !== null" @change="toggle(scope.row, $event)" /><span>{{ scope.row.enabled ? '已启用' : '已停用' }}</span></div></template></el-table-column>
      <el-table-column label="操作" width="160" align="right"><template slot-scope="scope"><el-button type="text" icon="el-icon-key" @click="openReset(scope.row)">重置密码</el-button></template></el-table-column>
    </el-table>

    <el-dialog title="新建普通管理员" :visible.sync="createVisible" width="460px" :close-on-click-modal="false" @closed="clearCreate"><el-form ref="createForm" :model="createForm" :rules="rules" label-position="top"><el-form-item label="账号" prop="userName"><el-input v-model="createForm.userName" maxlength="50" autocomplete="off" /></el-form-item><el-form-item label="初始密码" prop="password"><el-input v-model="createForm.password" type="password" show-password autocomplete="new-password" /></el-form-item></el-form><span slot="footer"><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="createAccount">创建</el-button></span></el-dialog>

    <el-dialog title="重置管理员密码" :visible.sync="resetVisible" width="460px" :close-on-click-modal="false" @closed="clearReset"><p class="dialog-note">账号：<strong>{{ selected && selected.userName }}</strong></p><el-form ref="resetForm" :model="resetForm" :rules="resetRules" label-position="top"><el-form-item label="临时密码" prop="password"><el-input v-model="resetForm.password" type="password" show-password autocomplete="new-password" /></el-form-item></el-form><span slot="footer"><el-button @click="resetVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="resetPassword">确认重置</el-button></span></el-dialog>
  </section>
</template>

<script>
import { listAdministrators, createAdministrator, updateAdministrator, resetAdministratorPassword } from '@/api/SuperAdmin'

export default {
  data () {
    return {
      administrators: [], loading: false, saving: false, updatingId: null,
      createVisible: false, resetVisible: false, selected: null,
      createForm: { userName: '', password: '' }, resetForm: { password: '' },
      rules: { userName: [{ required: true, message: '请输入账号', trigger: 'blur' }], password: [{ required: true, min: 6, message: '初始密码至少需要 6 个字符', trigger: 'blur' }] },
      resetRules: { password: [{ required: true, min: 6, message: '临时密码至少需要 6 个字符', trigger: 'blur' }] }
    }
  },
  computed: {
    enabledCount () { return this.administrators.filter(item => item.enabled).length },
    passwordChangeCount () { return this.administrators.filter(item => item.mustChangePassword).length }
  },
  mounted () { this.load() },
  methods: {
    initial (name) { return String(name || 'A').slice(0, 1).toUpperCase() },
    async load () { this.loading = true; try { const result = await listAdministrators(); if (result.code === 200) this.administrators = result.data || []; else this.$message.error(result.msg || '管理员列表加载失败') } finally { this.loading = false } },
    openCreate () { this.createVisible = true },
    clearCreate () { this.createForm = { userName: '', password: '' }; this.$refs.createForm && this.$refs.createForm.clearValidate() },
    createAccount () { this.$refs.createForm.validate(async valid => { if (!valid) return; this.saving = true; try { const result = await createAdministrator(this.createForm); if (result.code !== 200) return this.$message.error(result.msg || '创建失败'); this.$message.success('普通管理员已创建'); this.createVisible = false; await this.load() } finally { this.saving = false } }) },
    async toggle (administrator, enabled) { this.updatingId = administrator.userId; try { const result = await updateAdministrator(administrator.userId, { enabled }); if (result.code !== 200) return this.$message.error(result.msg || '状态更新失败'); administrator.enabled = enabled; this.$message.success(enabled ? '管理员已启用' : '管理员已停用') } finally { this.updatingId = null } },
    openReset (administrator) { this.selected = administrator; this.resetVisible = true },
    clearReset () { this.selected = null; this.resetForm = { password: '' }; this.$refs.resetForm && this.$refs.resetForm.clearValidate() },
    resetPassword () { this.$refs.resetForm.validate(async valid => { if (!valid || !this.selected) return; this.saving = true; try { const result = await resetAdministratorPassword(this.selected.userId, { password: this.resetForm.password }); if (result.code !== 200) return this.$message.error(result.msg || '密码重置失败'); this.$message.success('密码已重置，该管理员下次登录必须修改密码'); this.resetVisible = false; await this.load() } finally { this.saving = false } }) }
  }
}
</script>

<style scoped>.account-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.account-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;margin-bottom:18px;border:1px solid #dde5eb;background:#dde5eb}.account-summary span{padding:18px;background:#fff}.account-summary small,.account-summary strong{display:block}.account-summary small{color:#74838e}.account-summary strong{margin-top:8px;font-size:26px}.account-table{border:1px solid #dde5eb}.account-name{display:flex;align-items:center;gap:12px}.account-name>span{width:36px;height:36px;display:grid;place-items:center;border-radius:50%;background:#e2f1eb;color:#267357;font-weight:700}.account-name strong,.account-name small{display:block}.account-name small{margin-top:3px;color:#81909b}.status-control{display:flex;align-items:center;gap:10px}.dialog-note{margin:-4px 0 18px;color:#667681}@media(max-width:640px){.account-heading{align-items:stretch;flex-direction:column}.account-summary{grid-template-columns:1fr}}</style>
