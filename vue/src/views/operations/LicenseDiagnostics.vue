<template>
  <section class="module-page module-composed-page diagnostics-page">
    <header class="module-heading module-toolbar">
      <div><h1>授权信息</h1><p>查看当前平台授权状态和有效期</p></div>
      <el-button type="primary" icon="el-icon-refresh" :loading="revalidating" @click="revalidate">重新校验</el-button>
    </header>
    <section class="diagnostic-band">
      <div class="state-cell" :class="tone"><small>授权状态</small><strong>{{ stateLabel }}</strong></div>
      <dl>
        <div><dt>授权单位</dt><dd>{{ diagnostics.organization || '--' }}</dd></div>
        <div><dt>授权期限</dt><dd>{{ expiryLabel }}</dd></div>
        <div><dt>剩余天数</dt><dd>{{ remainingDays }}</dd></div>
        <div><dt>授权导入时间</dt><dd>{{ formatTime(diagnostics.importedAt) }}</dd></div>
      </dl>
    </section>
    <section class="license-actions">
      <div class="action-pane">
        <strong>下载授权请求</strong>
        <el-input v-model.trim="organization" placeholder="授权单位名称" maxlength="200" />
        <el-button type="primary" icon="el-icon-download" :loading="exporting" @click="exportRequest">下载 .xkpreq</el-button>
      </div>
      <div class="action-pane">
        <strong>导入授权文件</strong>
        <input ref="licenseFile" class="native-file" type="file" accept=".xkplic,application/json" @change="selectFile">
        <el-button icon="el-icon-folder-opened" @click="$refs.licenseFile.click()">选择 .xkplic 文件</el-button>
        <span v-if="selectedFile" class="selected-file">{{ selectedFile.name }}</span>
        <el-button type="primary" icon="el-icon-upload2" :disabled="!selectedFile" :loading="importing" @click="confirmImport">导入授权</el-button>
      </div>
    </section>
    <section class="audit-section">
      <header class="module-toolbar"><div><strong>授权审计</strong><span>请求、导入和状态变化记录</span></div><el-button icon="el-icon-refresh" circle title="刷新审计" @click="loadAudits" /></header>
      <div class="module-table-wrap"><el-table :data="audits" v-loading="loading" empty-text="暂无审计记录">
        <el-table-column prop="createdAt" label="时间" min-width="170"><template slot-scope="scope">{{ formatTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column prop="action" label="操作" min-width="170"><template slot-scope="scope">{{ actionLabel(scope.row.action) }}</template></el-table-column>
        <el-table-column prop="result" label="结果" width="110"><template slot-scope="scope"><el-tag size="small" :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.result === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column prop="reasonCode" label="说明" min-width="220"><template slot-scope="scope">{{ reasonLabel(scope.row.reasonCode) }}</template></el-table-column>
      </el-table></div>
      <el-pagination background layout="total, prev, pager, next" :current-page="page" :page-size="size" :total="total" @current-change="changePage" />
    </section>
  </section>
</template>

<script>
import { createLicenseRequest, downloadLicenseRequest, importLicense } from '@/api/License'
import { getLicenseDiagnostics, listLicenseAudits, revalidateLicense } from '@/api/SuperAdmin'
import { saveAs } from 'file-saver'
const labels = { NOT_ACTIVATED: '未激活', ACTIVE: '授权有效', EXPIRING: '即将到期', EXPIRED: '授权已到期', INVALID: '授权无效', CLOCK_ROLLBACK: '系统时间异常' }
export default {
  data: () => ({ diagnostics: {}, audits: [], page: 1, size: 20, total: 0, loading: false, revalidating: false, organization: '', selectedFile: null, exporting: false, importing: false }),
  computed: {
    stateLabel () { return labels[this.diagnostics.state] || '状态未知' },
    tone () { return ['ACTIVE', 'EXPIRING'].includes(this.diagnostics.state) ? 'is-usable' : 'is-locked' },
    expiryLabel () { return this.isPermanent ? '永久授权' : this.formatTime(this.diagnostics.expiresAt) },
    isPermanent () { return this.diagnostics.expiresAt && new Date(this.diagnostics.expiresAt).getUTCFullYear() >= 9999 },
    remainingDays () {
      if (this.isPermanent) return '永久'
      if (!this.diagnostics.expiresAt) return '--'
      return `${Math.max(0, Math.ceil((new Date(this.diagnostics.expiresAt).getTime() - Date.now()) / 86400000))} 天`
    }
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
    async exportRequest () { this.exporting = true; try { const created = await createLicenseRequest({ organization: this.organization || null }); const response = await downloadLicenseRequest(created.data.requestId); saveAs(response.data, created.data.filename); this.$message.success('授权请求已下载') } finally { this.exporting = false } },
    selectFile (event) { const file = event.target.files && event.target.files[0]; event.target.value = ''; if (!file) return; if (!file.name.toLowerCase().endsWith('.xkplic')) return this.$message.error('请选择 .xkplic 授权文件'); if (file.size > 256 * 1024) return this.$message.error('授权文件不能超过 256 KiB'); this.selectedFile = file },
    async confirmImport () { await this.$confirm(`确认导入 ${this.selectedFile.name}？`, '导入授权', { type: 'warning', confirmButtonText: '确认导入' }); this.importing = true; try { await importLicense(this.selectedFile); this.selectedFile = null; await this.loadDiagnostics(); this.$message.success('授权已生效') } finally { this.importing = false } },
    actionLabel (value) { return ({ IMPORT: '导入授权', REQUEST: '生成授权请求', REVALIDATE: '重新校验' })[value] || value || '--' },
    reasonLabel (value) { return ({ SUCCESS: '操作成功', INVALID_SIGNATURE: '授权签名无效', EXPIRED: '授权已过期' })[value] || value || '--' },
    formatTime (value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--' }
  }
}
</script>

<style scoped>
.diagnostics-page{max-width:1180px}.module-heading{display:flex;align-items:flex-start;justify-content:space-between}.diagnostic-band{display:grid;grid-template-columns:190px 1fr;margin-bottom:20px;border:1px solid var(--ui-border);background:var(--ui-surface)}.state-cell{display:flex;flex-direction:column;justify-content:center;padding:24px;border-left:4px solid var(--ui-danger);border-right:1px solid var(--ui-border)}.state-cell.is-usable{border-left-color:var(--ui-success)}.state-cell small{margin-bottom:7px;color:var(--ui-muted)}.state-cell strong{font-size:22px}.diagnostic-band dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));margin:0}.diagnostic-band dl>div{min-width:0;padding:16px 18px;border-right:1px solid var(--ui-border);border-bottom:1px solid var(--ui-border)}.diagnostic-band dt{margin-bottom:6px;color:var(--ui-muted);font-size:12px}.diagnostic-band dd{margin:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.audit-section{background:var(--ui-surface);border:1px solid var(--ui-border)}.audit-section>header{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-bottom:1px solid var(--ui-border)}.audit-section>header strong,.audit-section>header span{display:block}.audit-section>header span{margin-top:3px;color:var(--ui-muted);font-size:12px}.el-pagination{padding:16px 18px;text-align:right}@media(max-width:850px){.diagnostic-band{grid-template-columns:1fr}.state-cell{border-right:0;border-bottom:1px solid var(--ui-border)}.diagnostic-band dl{grid-template-columns:repeat(2,1fr)}}@media(max-width:520px){.diagnostic-band dl{grid-template-columns:1fr}}
.diagnostics-page{max-width:none}
.license-actions{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:20px}.action-pane{display:flex;align-items:center;gap:12px;padding:16px;border:1px solid var(--ui-border);background:var(--ui-surface)}.action-pane strong{margin-right:auto;white-space:nowrap}.action-pane .el-input{max-width:220px}.native-file{display:none}.selected-file{max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--ui-muted)}
</style>
