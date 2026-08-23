<template>
  <section class="module-page module-composed-page">
    <header class="module-heading action-heading">
      <div><h1>处理服务器</h1><p>下载一键部署包并查看已接入服务器。</p></div>
      <el-button type="primary" icon="el-icon-plus" @click="openTokenDialog">添加服务器</el-button>
    </header>
    <AgentStatusTable :agents="agents" :loading="loading" @select="selectAgent" />
    <div v-if="selected" class="operations-bar">
      <strong>{{ selected.displayName || selected.hostname }}</strong><span class="grow" />
      <el-button icon="el-icon-sunny" :loading="busyAction === 'wake'" @click="wakeAgent">开机</el-button>
      <el-button icon="el-icon-switch-button" :loading="busyAction === 'shutdown'" :disabled="!selected.online" @click="shutdownAgent">关机</el-button>
      <el-button v-if="selected.enabled" icon="el-icon-video-pause" @click="disableAgentAction">停用</el-button>
      <el-button v-else type="success" icon="el-icon-video-play" @click="enableAgent">启用</el-button>
      <el-button type="danger" plain icon="el-icon-delete" @click="removeAgent">移除</el-button>
    </div>
    <section v-if="selected" class="command-history">
      <h2>电源操作记录</h2>
      <div class="module-table-wrap">
        <el-table :data="commands" empty-text="暂无操作记录" size="small">
          <el-table-column prop="requestedAt" label="发起时间" min-width="170" />
          <el-table-column label="状态" width="150"><template slot-scope="s"><el-tag size="small" :type="commandTag(s.row.state)">{{ commandState(s.row) }}</el-tag></template></el-table-column>
          <el-table-column prop="resultMessage" label="结果" min-width="240" />
        </el-table>
      </div>
    </section>
    <el-dialog title="添加服务器" :visible.sync="tokenDialog" width="520px" @closed="clearForm">
      <el-form v-if="!downloading" :model="form" label-width="100px">
        <el-form-item label="服务器 IP">
          <el-input v-model.trim="form.serverIp" placeholder="例如 172.16.33.215" @input="handleServerIpInput" />
          <div class="ip-status">
            <span :class="['status-dot', ipStatusClass]" />
            <span>{{ ipStatusText }}</span>
            <el-button v-if="validServerIp" type="text" icon="el-icon-refresh" :loading="statusChecking" aria-label="重新检测服务器状态" @click="checkServerStatus" />
          </div>
        </el-form-item>
        <el-form-item label="备注名称"><el-input v-model.trim="form.label" placeholder="例如：GPU 训练服务器" maxlength="80" /></el-form-item>
        <el-form-item label="工作目录"><el-input v-model.trim="form.workspace" placeholder="/srv/xkp" /></el-form-item>
      </el-form>
      <div v-else class="adding-state"><i class="el-icon-loading" /><strong>正在生成部署包...</strong><span>{{ form.serverIp }}</span></div>
      <span slot="footer"><el-button :disabled="downloading" @click="tokenDialog = false">关闭</el-button><el-button v-if="!downloading" type="primary" :loading="downloading" :disabled="!formReady" @click="downloadPackage">下载部署包</el-button></span>
    </el-dialog>
    <RootTerminalDialog v-if="terminalVisible" :visible.sync="terminalVisible" :session="terminalSession" :agent-name="selected && (selected.displayName || selected.hostname)" @closed="terminalSession = null" />
  </section>
</template>
<script>
import AgentStatusTable from '@/components/agents/AgentStatusTable.vue'
import RootTerminalDialog from '@/components/agents/RootTerminalDialog.vue'
import { listProcessingAgents, downloadAgentPackage, enableProcessingAgent, disableProcessingAgent, removeProcessingAgent, listProcessingAgentCommands, wakeProcessingAgent, shutdownProcessingAgent } from '@/api/ProcessingAgents'

const SERVER_IP_PATTERN = /^(?:\d{1,3}\.){3}\d{1,3}$/

export default {
  components: { AgentStatusTable, RootTerminalDialog },
  data: () => ({
    agents: [], selected: null, commands: [], loading: false, busyAction: '', tokenDialog: false,
    downloading: false, ipTouched: false, statusChecking: false, refreshTimer: null, ipCheckTimer: null,
    form: { serverIp: '', label: '', workspace: '/srv/xkp' }, terminalVisible: false, terminalSession: null
  }),
  computed: {
    validServerIp () { return SERVER_IP_PATTERN.test(this.form.serverIp) },
    formReady () { return this.validServerIp && !!this.form.label && !!this.form.workspace },
    ipOnline () {
      const current = this.form.serverIp
      return !!current && (this.agents || []).some(agent => agent.primaryIp === current && agent.online)
    },
    ipStatusClass () { if (this.statusChecking) return 'is-checking'; return this.ipOnline ? 'is-online' : 'is-waiting' },
    ipStatusText () {
      if (!this.validServerIp) return '请输入完整 IP 地址'
      if (this.statusChecking) return '正在检测服务器状态'
      return this.ipOnline ? '服务器已接入' : '等待 Agent 注册'
    }
  },
  mounted () {
    this.load()
    this.refreshTimer = window.setInterval(() => { if (!this.loading) this.load() }, 5000)
  },
  beforeDestroy () {
    window.clearInterval(this.refreshTimer)
    window.clearTimeout(this.ipCheckTimer)
  },
  methods: {
    async load () {
      this.loading = true
      try { const result = await listProcessingAgents(); this.agents = result.data || [] } finally { this.loading = false }
    },
    openTokenDialog () { this.tokenDialog = true; this.checkServerStatus() },
    handleServerIpInput () { this.ipTouched = true; this.checkServerStatus() },
    checkServerStatus () {
      window.clearTimeout(this.ipCheckTimer)
      if (!this.validServerIp) { this.statusChecking = false; return }
      this.statusChecking = true
      this.ipCheckTimer = window.setTimeout(async () => { try { await this.load() } finally { this.statusChecking = false } }, 250)
    },
    async selectAgent (agent) { this.selected = agent; const result = await listProcessingAgentCommands(agent.agentId); this.commands = result.data || [] },
    async wakeAgent () { this.busyAction = 'wake'; try { await wakeProcessingAgent(this.selected.agentId); this.$message.success('唤醒请求已提交') } finally { this.busyAction = '' } },
    async shutdownAgent () { await this.$confirm('确认关闭服务器？', '提示', { type: 'warning' }); this.busyAction = 'shutdown'; try { await shutdownProcessingAgent(this.selected.agentId); await this.selectAgent(this.selected) } finally { this.busyAction = '' } },
    async downloadPackage () {
      if (!this.formReady) { this.$message.warning('请完整填写服务器 IP、备注名称和工作目录'); return }
      this.downloading = true
      try {
        const response = await downloadAgentPackage(this.form)
        const payload = response && Object.prototype.hasOwnProperty.call(response, 'data') ? response.data : response
        const blob = payload instanceof Blob ? payload : new Blob([payload], { type: 'application/zip' })
        if (!blob.size) throw new Error('部署包为空')
        const url = window.URL.createObjectURL(blob)
        const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${this.form.label}-xkp-agent.zip`; anchor.style.display = 'none'
        document.body.appendChild(anchor); anchor.click()
        window.setTimeout(() => { if (anchor.parentNode) anchor.parentNode.removeChild(anchor); window.URL.revokeObjectURL(url) }, 2000)
        this.$message.success('Agent 部署包已下载'); this.tokenDialog = false
      } catch (error) { this.$message.error('部署包下载失败，请确认已登录超级管理员账号后重试') } finally { this.downloading = false }
    },
    clearForm () { window.clearTimeout(this.ipCheckTimer); this.form = { serverIp: '', label: '', workspace: '/srv/xkp' }; this.ipTouched = false; this.statusChecking = false },
    async disableAgentAction () { await this.$confirm('停用该处理服务器？', '提示', { type: 'warning' }); await disableProcessingAgent(this.selected.agentId); await this.load() },
    async enableAgent () { await enableProcessingAgent(this.selected.agentId); await this.load() },
    async removeAgent () { await this.$confirm('移除后需重新注册，确认继续？', '提示', { type: 'warning' }); await removeProcessingAgent(this.selected.agentId); this.selected = null; await this.load() },
    commandState (command) { return ({ PENDING: '等待 Agent 接收', LEASED: '已送达', RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: command.resultMessage || '执行失败' })[command.state] || command.state },
    commandTag (state) { return ({ PENDING: 'info', LEASED: '', RUNNING: 'warning', SUCCEEDED: 'success', FAILED: 'danger' })[state] || 'info' }
  }
}
</script>
<style scoped>
.action-heading,.operations-bar{display:flex;align-items:center;justify-content:space-between}.operations-bar{min-height:58px;padding:0 16px;border:1px solid var(--ui-border);border-top:0}.grow{flex:1}.command-history{margin-top:24px}.command-history h2{margin:0 0 12px;font-size:16px}.ip-status{display:flex;align-items:center;gap:6px;margin-top:7px;color:var(--ui-muted);font-size:12px}.ip-status .el-button{margin-left:auto;padding:0}.status-dot{width:7px;height:7px;border-radius:50%;background:#c7cbd5}.status-dot.is-online{background:#35c59c}.status-dot.is-checking{background:#e6a23c}.adding-state{display:flex;flex-direction:column;align-items:center;gap:12px;padding:34px 0;color:var(--ui-muted)}.adding-state i{color:var(--ui-primary);font-size:30px}.adding-state strong{color:var(--ui-text);font-size:17px}
</style>
