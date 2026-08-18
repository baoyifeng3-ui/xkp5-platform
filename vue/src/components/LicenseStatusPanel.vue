<template><section class="license-strip" :class="tone"><div><small>平台授权</small><strong>{{ label }}</strong><span>{{ summary }}</span></div><el-button size="small" icon="el-icon-key" @click="$router.push('/management/license')">授权管理</el-button></section></template>
<script>
import { getLicenseStatus } from '@/api/License'
const labels = { NOT_ACTIVATED: '未激活', ACTIVE: '授权有效', EXPIRING: '即将到期', EXPIRED: '授权已到期', INVALID: '授权无效', CLOCK_ROLLBACK: '系统时间异常' }
export default {
  data: () => ({ status: {} }),
  computed: {
    label () { return labels[this.status.state] || '正在读取' },
    summary () { return this.status.expiresAt ? `有效期至 ${new Date(this.status.expiresAt).toLocaleDateString('zh-CN')}` : '导入授权后可开启实训与比赛环境' },
    tone () { return ['ACTIVE', 'EXPIRING'].includes(this.status.state) ? 'is-usable' : 'is-locked' }
  },
  async created () { const result = await getLicenseStatus(); this.status = result.data || {} }
}
</script>
<style scoped>.license-strip{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;padding:15px 18px;border-left:4px solid #c75454;background:#fff}.license-strip.is-usable{border-left-color:#318063}.license-strip small,.license-strip strong,.license-strip span{display:block}.license-strip small{color:#7a8994}.license-strip strong{margin:3px 0;color:#263d4d}.license-strip span{color:#667984;font-size:13px}@media(max-width:520px){.license-strip{align-items:flex-start;gap:12px}}</style>
