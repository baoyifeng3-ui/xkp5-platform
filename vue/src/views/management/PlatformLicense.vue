<template>
  <section class="module-page license-page">
    <header class="module-heading">
      <div><h1>平台授权</h1><p>XKP5.0 管理服务器离线授权状态</p></div>
      <el-button icon="el-icon-refresh" :loading="loading" @click="loadStatus">刷新</el-button>
    </header>

    <section class="status-band" :class="statusTone">
      <div class="status-main"><span class="status-dot" /><div><small>当前状态</small><strong>{{ stateLabel }}</strong></div></div>
      <dl>
        <div><dt>授权单位</dt><dd>{{ status.organization || '--' }}</dd></div>
        <div><dt>授权编号</dt><dd>{{ status.licenseId || '--' }}</dd></div>
        <div><dt>到期时间</dt><dd>{{ formatTime(status.expiresAt) }}</dd></div>
        <div><dt>剩余时间</dt><dd>{{ remainingDays }}</dd></div>
        <div><dt>处理服务器上限</dt><dd>{{ serverLimit }}</dd></div>
      </dl>
    </section>

    <div class="license-actions">
      <section class="action-pane">
        <header><i class="el-icon-download" /><div><strong>导出平台信息</strong><span>生成离线授权请求文件</span></div></header>
        <el-form label-position="top">
          <el-form-item label="授权单位（选填）">
            <el-input v-model.trim="organization" maxlength="200" show-word-limit placeholder="例如：实验中心" />
          </el-form-item>
        </el-form>
        <el-button type="primary" icon="el-icon-download" :loading="exporting" @click="exportRequest">导出 .xkpreq</el-button>
      </section>

      <section class="action-pane">
        <header><i class="el-icon-upload2" /><div><strong>导入授权文件</strong><span>应用签发后的平台授权</span></div></header>
        <div class="file-picker" :class="{ selected: selectedFile }" @click="$refs.file.click()">
          <i :class="selectedFile ? 'el-icon-document-checked' : 'el-icon-folder-opened'" />
          <span>{{ selectedFile ? selectedFile.name : '选择 .xkplic 文件' }}</span>
          <small>{{ selectedFile ? formatSize(selectedFile.size) : '最大 256 KiB' }}</small>
        </div>
        <input ref="file" class="native-file" type="file" accept=".xkplic,application/json" @change="selectFile">
        <el-button type="primary" icon="el-icon-upload2" :disabled="!selectedFile" :loading="importing" @click="confirmImport">导入授权</el-button>
      </section>
    </div>
  </section>
</template>

<script>
import { saveAs } from 'file-saver'
import { createLicenseRequest, downloadLicenseRequest, getLicenseStatus, importLicense } from '@/api/License'

const STATE_LABELS = {
  NOT_ACTIVATED: '未激活', ACTIVE: '授权有效', EXPIRING: '即将到期', EXPIRED: '授权已到期',
  INVALID: '授权无效', CLOCK_ROLLBACK: '系统时间异常'
}

export default {
  data: () => ({ status: {}, loading: false, exporting: false, importing: false, organization: '', selectedFile: null }),
  computed: {
    stateLabel () { return STATE_LABELS[this.status.state] || '状态未知' },
    statusTone () {
      if (this.status.state === 'ACTIVE') return 'is-active'
      if (this.status.state === 'EXPIRING') return 'is-warning'
      return 'is-danger'
    },
    remainingDays () {
      if (!this.status.expiresAt) return '--'
      const days = Math.ceil((new Date(this.status.expiresAt).getTime() - Date.now()) / 86400000)
      return days > 0 ? `${days} 天` : '0 天'
    },
    serverLimit () { return this.status.maxProcessingServers == null ? '不限' : `${this.status.maxProcessingServers} 台` }
  },
  created () { this.loadStatus() },
  methods: {
    async loadStatus () {
      this.loading = true
      try { const result = await getLicenseStatus(); this.status = result.data || {} } finally { this.loading = false }
    },
    async exportRequest () {
      this.exporting = true
      try {
        const created = await createLicenseRequest({ organization: this.organization || null })
        const response = await downloadLicenseRequest(created.data.requestId)
        saveAs(response.data, created.data.filename)
        this.$message.success('平台信息已导出')
      } finally { this.exporting = false }
    },
    selectFile (event) {
      const file = event.target.files && event.target.files[0]
      event.target.value = ''
      if (!file) return
      if (!file.name.toLowerCase().endsWith('.xkplic')) return this.$message.error('请选择 .xkplic 授权文件')
      if (file.size > 256 * 1024) return this.$message.error('授权文件不能超过 256 KiB')
      this.selectedFile = file
    },
    async confirmImport () {
      await this.$confirm(`确认导入 ${this.selectedFile.name}？`, '导入授权', { type: 'warning', confirmButtonText: '确认导入' })
      this.importing = true
      try {
        await importLicense(this.selectedFile)
        this.selectedFile = null
        this.$message.success('授权已生效')
        await this.loadStatus()
      } finally { this.importing = false }
    },
    formatTime (value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--' },
    formatSize (size) { return `${Math.max(1, Math.ceil(size / 1024))} KiB` }
  }
}
</script>

<style scoped>
.license-page{max-width:1120px}.module-heading{display:flex;align-items:flex-start;justify-content:space-between}.status-band{margin-bottom:18px;border:1px solid #dce5eb;border-left:4px solid #8a98a3;background:#fff}.status-band.is-active{border-left-color:#2f8a67}.status-band.is-warning{border-left-color:#c58a22}.status-band.is-danger{border-left-color:#c75454}.status-main{display:flex;align-items:center;gap:12px;padding:20px 22px;border-bottom:1px solid #e7edf1}.status-main small,.status-main strong{display:block}.status-main small{margin-bottom:4px;color:#7a8994}.status-main strong{font-size:22px}.status-dot{width:12px;height:12px;border-radius:50%;background:#8a98a3}.is-active .status-dot{background:#2f8a67}.is-warning .status-dot{background:#c58a22}.is-danger .status-dot{background:#c75454}.status-band dl{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));margin:0}.status-band dl>div{min-width:0;padding:16px 20px;border-right:1px solid #edf1f4}.status-band dl>div:last-child{border:0}.status-band dt{margin-bottom:7px;color:#7a8994;font-size:12px}.status-band dd{margin:0;overflow:hidden;color:#263d4d;text-overflow:ellipsis;white-space:nowrap}.license-actions{display:grid;grid-template-columns:1fr 1fr;gap:18px}.action-pane{padding:22px;border:1px solid #dce5eb;background:#fff}.action-pane header{display:flex;align-items:center;gap:12px;margin-bottom:22px}.action-pane header>i{color:#318063;font-size:28px}.action-pane header strong,.action-pane header span{display:block}.action-pane header span{margin-top:4px;color:#7a8994;font-size:13px}.file-picker{display:grid;grid-template-columns:24px 1fr auto;align-items:center;gap:10px;height:62px;margin-bottom:22px;padding:0 16px;border:1px dashed #b9c7d0;cursor:pointer}.file-picker:hover,.file-picker.selected{border-color:#318063;background:#f5faf8}.file-picker>i{font-size:20px}.file-picker small{color:#7a8994}.native-file{display:none}@media(max-width:900px){.status-band dl{grid-template-columns:repeat(2,1fr)}.license-actions{grid-template-columns:1fr}}@media(max-width:520px){.status-band dl{grid-template-columns:1fr}.file-picker{grid-template-columns:24px 1fr}.file-picker small{grid-column:2}}
</style>
