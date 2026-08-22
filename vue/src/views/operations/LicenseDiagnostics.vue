<template>
  <section class="module-page module-composed-page diagnostics-page">
    <header class="module-heading module-toolbar">
      <div><h1>授权诊断</h1><p>XKP5.0 平台授权与可信时钟状态</p></div>
      <el-button type="primary" icon="el-icon-refresh" :loading="revalidating" @click="revalidate">重新校验</el-button>
    </header>
    <section class="diagnostic-band">
      <div class="state-cell" :class="tone"><small>授权状态</small><strong>{{ stateLabel }}</strong></div>
      <dl>
        <div><dt>安装实例</dt><dd>{{ diagnostics.installationId || '--' }}</dd></div>
        <div><dt>授权编号</dt><dd>{{ diagnostics.licenseId || '--' }}</dd></div>
        <div><dt>运行环境</dt><dd>{{ diagnostics.environment || '--' }}</dd></div>
        <div><dt>可信时间</dt><dd>{{ formatTime(diagnostics.maxTrustedTime) }}</dd></div>
        <div><dt>主机指纹</dt><dd>{{ diagnostics.fingerprint || '--' }}</dd></div>
        <div><dt>公钥标识</dt><dd>{{ keyIds }}</dd></div>
      </dl>
    </section>
    <section class="audit-section">
      <header class="module-toolbar"><div><strong>授权审计</strong><span>请求、导入和状态变化记录</span></div><el-button icon="el-icon-refresh" circle title="刷新审计" @click="loadAudits" /></header>
      <div class="module-table-wrap"><el-table :data="audits" v-loading="loading" empty-text="暂无审计记录">
        <el-table-column prop="createdAt" label="时间" min-width="170"><template slot-scope="scope">{{ formatTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column prop="action" label="操作" min-width="170" />
        <el-table-column prop="result" label="结果" width="110"><template slot-scope="scope"><el-tag size="small" :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.result }}</el-tag></template></el-table-column>
        <el-table-column prop="reasonCode" label="原因代码" min-width="170" />
        <el-table-column prop="actorUserId" label="操作者" width="100" />
        <el-table-column prop="correlationId" label="关联编号" min-width="240" show-overflow-tooltip />
      </el-table></div>
      <el-pagination background layout="total, prev, pager, next" :current-page="page" :page-size="size" :total="total" @current-change="changePage" />
    </section>
  </section>
</template>

<script>
import { getLicenseDiagnostics, listLicenseAudits, revalidateLicense } from '@/api/SuperAdmin'
const labels = { NOT_ACTIVATED: '未激活', ACTIVE: '授权有效', EXPIRING: '即将到期', EXPIRED: '授权已到期', INVALID: '授权无效', CLOCK_ROLLBACK: '系统时间异常' }
export default {
  data: () => ({ diagnostics: {}, audits: [], page: 1, size: 20, total: 0, loading: false, revalidating: false }),
  computed: {
    stateLabel () { return labels[this.diagnostics.state] || '状态未知' },
    tone () { return ['ACTIVE', 'EXPIRING'].includes(this.diagnostics.state) ? 'is-usable' : 'is-locked' },
    keyIds () { return Array.isArray(this.diagnostics.keyIds) && this.diagnostics.keyIds.length ? this.diagnostics.keyIds.join('、') : '--' }
  },
  created () { this.loadAll() },
  methods: {
    async loadAll () { await Promise.all([this.loadDiagnostics(), this.loadAudits()]) },
    async loadDiagnostics () { const result = await getLicenseDiagnostics(); this.diagnostics = result.data || {} },
    async loadAudits () {
      this.loading = true
      try { const result = await listLicenseAudits({ page: this.page, size: this.size }); const data = result.data || {}; this.audits = data.records || []; this.total = Number(data.total || 0) } finally { this.loading = false }
    },
    async revalidate () { this.revalidating = true; try { await revalidateLicense(); await this.loadAll(); this.$message.success('授权状态已重新校验') } finally { this.revalidating = false } },
    changePage (page) { this.page = page; this.loadAudits() },
    formatTime (value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--' }
  }
}
</script>

<style scoped>
.diagnostics-page{max-width:1180px}.module-heading{display:flex;align-items:flex-start;justify-content:space-between}.diagnostic-band{display:grid;grid-template-columns:190px 1fr;margin-bottom:20px;border:1px solid var(--ui-border);background:var(--ui-surface)}.state-cell{display:flex;flex-direction:column;justify-content:center;padding:24px;border-left:4px solid var(--ui-danger);border-right:1px solid var(--ui-border)}.state-cell.is-usable{border-left-color:var(--ui-success)}.state-cell small{margin-bottom:7px;color:var(--ui-muted)}.state-cell strong{font-size:22px}.diagnostic-band dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));margin:0}.diagnostic-band dl>div{min-width:0;padding:16px 18px;border-right:1px solid var(--ui-border);border-bottom:1px solid var(--ui-border)}.diagnostic-band dt{margin-bottom:6px;color:var(--ui-muted);font-size:12px}.diagnostic-band dd{margin:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.audit-section{background:var(--ui-surface);border:1px solid var(--ui-border)}.audit-section>header{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-bottom:1px solid var(--ui-border)}.audit-section>header strong,.audit-section>header span{display:block}.audit-section>header span{margin-top:3px;color:var(--ui-muted);font-size:12px}.el-pagination{padding:16px 18px;text-align:right}@media(max-width:850px){.diagnostic-band{grid-template-columns:1fr}.state-cell{border-right:0;border-bottom:1px solid var(--ui-border)}.diagnostic-band dl{grid-template-columns:repeat(2,1fr)}}@media(max-width:520px){.diagnostic-band dl{grid-template-columns:1fr}}
</style>
