<template><main class="password-page"><section class="password-panel"><span class="password-icon"><i class="el-icon-lock" /></span><h1>修改初始密码</h1><p>首次登录需要先设置新密码，完成后进入对应工作台。</p><el-form ref="form" :model="form" :rules="rules" label-position="top"><el-form-item label="当前密码" prop="currentPassword"><el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item><el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item><el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.native.enter="submit" /></el-form-item><el-button type="primary" :loading="saving" class="submit-button" @click="submit">保存并进入平台</el-button></el-form></section></main></template>
<script>
import { changePasswordApi } from '@/api/Match'
import { getRole, getUserInfo, setUserInfo } from '@/utils/auth'
const { landingRoute } = require('@/navigation/roleNavigation')
export default {
  data () {
    const validateNew = (rule, value, callback) => { if (!value || value.length < 6) return callback(new Error('新密码至少需要 6 个字符')); if (value === this.form.currentPassword) return callback(new Error('新密码不能与当前密码相同')); callback() }
    const validateConfirm = (rule, value, callback) => { if (value !== this.form.newPassword) return callback(new Error('两次输入的新密码不一致')); callback() }
    return { saving: false, form: { currentPassword: '', newPassword: '', confirmPassword: '' }, rules: { currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }], newPassword: [{ validator: validateNew, trigger: 'blur' }], confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }] } }
  },
  methods: {
    submit () { this.$refs.form.validate(async valid => { if (!valid) return; this.saving = true; try { const result = await changePasswordApi({ currentPassword: this.form.currentPassword, newPassword: this.form.newPassword }); if (result.code !== 200) { this.$message.error(result.msg || '密码修改失败'); return } const info = { ...getUserInfo(), mustChangePassword: false }; setUserInfo(info); this.$store.commit('Match/SET_USER_INFO', info); this.$message.success('密码修改成功'); this.$router.replace(landingRoute(getRole())) } finally { this.saving = false } }) }
  }
}
</script>
<style scoped>.password-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:#eef3f6}.password-panel{width:min(420px,100%);padding:36px;background:#fff;border:1px solid #dbe4ea;border-radius:6px;box-shadow:0 14px 40px rgba(29,48,63,.1)}.password-icon{width:44px;height:44px;display:grid;place-items:center;border-radius:50%;background:#e4f3ed;color:#257458;font-size:21px}.password-panel h1{margin:18px 0 8px}.password-panel>p{margin:0 0 24px;color:#758591;line-height:1.7}.submit-button{width:100%}</style>
