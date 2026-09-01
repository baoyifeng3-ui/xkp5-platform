<template>
  <section class="module-page module-composed-page">
    <header class="module-heading action-heading">
      <div><h1>处理服务器</h1><p>检测服务器网络、下载 Agent 部署包并管理已接入服务器。</p></div>
      <el-button v-if="pendingServerIp" plain icon="el-icon-search" @click="searchRegisteredAgent">搜索已注册 Agent</el-button>
      <el-button type="primary" icon="el-icon-plus" @click="openTokenDialog">添加服务器</el-button>
    </header>
    <AgentStatusTable :agents="agents" :loading="loading" @select="selectAgent" />
    <div v-if="selected" class="operations-bar">
      <strong>{{ selected.displayName || selected.hostname }}</strong><span class="grow" />
      <el-button icon="el-icon-sunny" :loading="busyAction === 'wake'" @click="wakeAgent">开机</el-button>
      <el-button icon="el-icon-switch-button" :loading="busyAction === 'shutdown'" :disabled="!selected.online" @click="shutdownAgent">关机</el-button>
      <el-button v-if="selected.enabled" icon="el-icon-video-pause" @click="disableAgentAction">停用</el-button>
      <el-button v-else type="success" icon="el-icon-video-play" @click="enableAgent">启用</el-button>
      <el-button v-if="canOpenTerminal" icon="el-icon-monitor" :loading="terminalOpening" :disabled="!selected.online" @click="openRootTerminal">远程连接</el-button>
      <el-button icon="el-icon-s-grid" @click="portPoolVisible=true">端口池</el-button>
      <el-button type="primary" plain icon="el-icon-upload2" :loading="busyAction === 'upgrade'" :disabled="!selected.online || selected.agentVersion === targetAgentVersion || !upgradeSupported" @click="upgradeAgent">{{ selected.agentVersion === targetAgentVersion ? '已是最新版' : (upgradeSupported ? '升级 Agent' : '请先手动升级') }}</el-button>
      <el-button plain icon="el-icon-connection" @click="openReconnectDialog">重新接入管理服务器</el-button>
      <el-button type="danger" plain icon="el-icon-delete" @click="removeAgent">移除</el-button>
    </div>
    <section v-if="selected" class="remote-info">
      <header><strong>远程连接信息</strong><span>{{ selected.online ? '处理服务器在线' : '处理服务器离线，暂不可连接' }}</span></header>
      <div class="remote-info-grid"><div><small>服务器地址</small><b>{{ selected.primaryIp || '--' }}</b></div><div><small>主机名称</small><b>{{ selected.hostname || '--' }}</b></div><div><small>Agent 版本</small><b>{{ selected.agentVersion || '--' }}</b></div><div><small>最后上报</small><b>{{ selected.lastSeenAt || '--' }}</b></div></div>
      <p>远程连接通过已安装的 Agent 建立临时终端，不保存服务器 SSH 密码。</p>
    </section>
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
            <el-button v-if="validServerIp" type="text" icon="el-icon-refresh" :loading="statusChecking" aria-label="重新检测服务器连通性" @click="checkServerConnectivity" />
          </div>
        </el-form-item>
        <el-form-item label="备注名称"><el-input v-model.trim="form.label" placeholder="例如：GPU 训练服务器" maxlength="80" /></el-form-item>
        <el-form-item label="SSH 用户名"><el-input v-model.trim="form.username" placeholder="例如 root" autocomplete="username" /></el-form-item>
        <el-form-item label="SSH 密码"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="SSH 端口"><el-input-number v-model="form.sshPort" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="工作目录"><el-input v-model.trim="form.workspace" placeholder="/srv/xkp" /></el-form-item>
      </el-form>
      <div v-else class="adding-state"><i class="el-icon-loading" /><strong>正在生成部署包...</strong><span>{{ form.serverIp }}</span></div>
      <span slot="footer"><el-button :disabled="downloading" @click="tokenDialog = false">关闭</el-button><el-button v-if="!downloading" type="primary" plain :loading="remoteDeploying" :disabled="!formReady || !form.username || !form.password" @click="remoteDeploy">远程部署 Agent</el-button><el-button v-if="!downloading" type="primary" :loading="downloading" :disabled="!formReady || serverReachable !== true" @click="downloadPackage">下载部署包</el-button></span>
    </el-dialog>
    <RootTerminalDialog v-if="terminalVisible" :visible.sync="terminalVisible" :session="terminalSession" :agent-name="selected && (selected.displayName || selected.hostname)" @closed="terminalSession = null" />
    <PortPoolDialog v-if="portPoolVisible" :visible.sync="portPoolVisible" :agent-id="selected&&selected.agentId" />
  </section>
</template>
<script>
import AgentStatusTable from '@/components/agents/AgentStatusTable.vue'
import RootTerminalDialog from '@/components/agents/RootTerminalDialog.vue'
import PortPoolDialog from '@/components/agents/PortPoolDialog.vue'
import { listProcessingAgents, checkAgentConnectivity, downloadAgentPackage, remoteDeployAgent, enableProcessingAgent, disableProcessingAgent, removeProcessingAgent, listProcessingAgentCommands, wakeProcessingAgent, shutdownProcessingAgent, upgradeProcessingAgent } from '@/api/ProcessingAgents'
import { createTerminalSession } from '@/api/TerminalSessions'
import { getRole } from '@/utils/auth'

const SERVER_IP_PATTERN = /^(?:\d{1,3}\.){3}\d{1,3}$/

export default {
  components: { AgentStatusTable, RootTerminalDialog, PortPoolDialog },
  data: () => ({
    agents: [], selected: null, commands: [], loading: false, busyAction: '', tokenDialog: false,
    downloading: false, remoteDeploying: false, ipTouched: false, statusChecking: false, serverReachable: null, pendingServerIp: '', refreshTimer: null, ipCheckTimer: null,
    form: { serverIp: '', label: '', workspace: '/srv/xkp', username: '', password: '', sshPort: 22 }, terminalVisible: false, terminalSession: null, terminalOpening: false, portPoolVisible: false, targetAgentVersion: '0.2.28'
  }),
  computed: {
    validServerIp () { return SERVER_IP_PATTERN.test(this.form.serverIp) },
    canOpenTerminal () { return getRole() === 'SUPER_ADMIN' },
    upgradeSupported () { return Boolean(this.selected && this.selected.agentVersion && this.selected.agentVersion !== '0.1.0') },
    formReady () { return this.validServerIp && !!this.form.label && !!this.form.workspace },
    ipStatusClass () { if (this.statusChecking) return 'is-checking'; return this.serverReachable === true ? 'is-online' : this.serverReachable === false ? 'is-offline' : 'is-waiting' },
    ipStatusText () {
      if (!this.validServerIp) return '请输入完整 IP 地址'
      if (this.statusChecking) return '正在检测服务器状态'
      if (this.serverReachable === true) return '服务器网络可达，可以下载部署包'
      if (this.serverReachable === false) return '服务器无法连通，不能下载部署包'
      return '等待检测服务器网络'
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
    openTokenDialog () { this.tokenDialog = true; this.checkServerConnectivity() },
    openReconnectDialog () { if (!this.selected) return; this.form = { serverIp: this.selected.primaryIp || '', label: this.selected.displayName || this.selected.hostname || '', workspace: '/srv/xkp', username: 'zyhit', password: '', sshPort: 22 }; this.tokenDialog = true; this.checkServerConnectivity() },
    handleServerIpInput () { this.ipTouched = true; this.checkServerConnectivity() },
    checkServerStatus () { this.checkServerConnectivity() },
    checkServerConnectivity () {
      window.clearTimeout(this.ipCheckTimer)
      this.serverReachable = null
      if (!this.validServerIp) { this.statusChecking = false; return }
      this.statusChecking = true
      this.ipCheckTimer = window.setTimeout(async () => {
        try {
          const result = await checkAgentConnectivity(this.form.serverIp)
          this.serverReachable = Boolean(result.data && result.data.reachable)
        } catch (error) {
          this.serverReachable = false
        } finally { this.statusChecking = false }
      }, 350)
    },
    async searchRegisteredAgent () {
      await this.load()
      const found = this.agents.find(agent => agent.primaryIp === this.pendingServerIp)
      if (!found) { this.$message.warning(`未找到 ${this.pendingServerIp} 的 Agent，请确认服务器已完成安装并联网`) ; return }
      this.pendingServerIp = ''
      await this.selectAgent(found)
      this.$message.success('已找到并加入平台')
    },
    async selectAgent (agent) { this.selected = agent; const result = await listProcessingAgentCommands(agent.agentId); this.commands = result.data || [] },
    async wakeAgent () { this.busyAction = 'wake'; try { await wakeProcessingAgent(this.selected.agentId); this.$message.success('唤醒请求已提交') } finally { this.busyAction = '' } },
    async shutdownAgent () { await this.$confirm('确认关闭服务器？', '提示', { type: 'warning' }); this.busyAction = 'shutdown'; try { await shutdownProcessingAgent(this.selected.agentId); await this.selectAgent(this.selected) } finally { this.busyAction = '' } },
    async upgradeAgent () { await this.$confirm(`确认将 Agent ${this.selected.agentVersion || '未知版本'} 升级到 ${this.targetAgentVersion}？升级失败会自动回滚。`, '升级 Agent', { type: 'warning' }); this.busyAction = 'upgrade'; try { await upgradeProcessingAgent(this.selected.agentId); this.$message.success('升级任务已下发，Agent 将自动重启'); await this.selectAgent(this.selected) } finally { this.busyAction = '' } },
    async downloadPackage () {
      if (!this.formReady) { this.$message.warning('请完整填写服务器 IP、备注名称和工作目录'); return }
      this.downloading = true
      try {
        const response = await downloadAgentPackage(this.form)
        const payload = response && Object.prototype.hasOwnProperty.call(response, 'data') ? response.data : response
        const blob = payload instanceof Blob ? payload : new Blob([payload], { type: 'application/gzip' })
        if (!blob.size) throw new Error('部署包为空')
        const url = window.URL.createObjectURL(blob)
        const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${this.form.label}-xkp-agent.tar.gz`; anchor.style.display = 'none'
        document.body.appendChild(anchor); anchor.click()
        window.setTimeout(() => { if (anchor.parentNode) anchor.parentNode.removeChild(anchor); window.URL.revokeObjectURL(url) }, 2000)
        this.pendingServerIp = this.form.serverIp
        this.$message.success('部署包已下载，安装完成后点击“搜索已注册 Agent”加入平台'); this.tokenDialog = false
      } catch (error) { this.$message.error('部署包下载失败，请确认已登录超级管理员账号后重试') } finally { this.downloading = false }
    },
    async remoteDeploy () {
      if (!this.formReady || !this.form.username || !this.form.password) return this.$message.warning('请填写 SSH 用户名和密码')
      this.remoteDeploying = true
      try { const result = await remoteDeployAgent(this.form); const data = result.data || result; if (!data.success) throw new Error(data.message || '远程部署失败'); this.$message.success('Agent 已远程部署，等待注册回连'); this.tokenDialog = false; this.pendingServerIp = this.form.serverIp; await this.load() } catch (error) { this.$message.error((error.response && error.response.data && error.response.data.message) || error.message || '远程部署失败') } finally { this.remoteDeploying = false }
    },
    clearForm () { window.clearTimeout(this.ipCheckTimer); this.form = { serverIp: '', label: '', workspace: '/srv/xkp', username: '', password: '', sshPort: 22 }; this.ipTouched = false; this.statusChecking = false; this.serverReachable = null },
    async disableAgentAction () { await this.$confirm('停用该处理服务器？', '提示', { type: 'warning' }); await disableProcessingAgent(this.selected.agentId); await this.load() },
    async enableAgent () { await enableProcessingAgent(this.selected.agentId); await this.load() },
    async removeAgent () { await this.$confirm('移除后需重新注册，确认继续？', '提示', { type: 'warning' }); await removeProcessingAgent(this.selected.agentId); this.selected = null; await this.load() },
    async openRootTerminal () {
      if (!this.selected) return
      if (!this.selected.online) { this.$message.warning('服务器不在线，无法打开终端'); return }
      this.terminalOpening = true
      try {
        const result = await createTerminalSession(this.selected.agentId)
        this.terminalSession = result.data || result
        this.terminalVisible = true
      } catch (error) { this.$message.error('终端会话创建失败，请确认服务器在线且已获得授权') } finally { this.terminalOpening = false }
    },
    commandState (command) { return ({ PENDING: '等待 Agent 接收', LEASED: '已送达', RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: command.resultMessage || '执行失败' })[command.state] || command.state },
    commandTag (state) { return ({ PENDING: 'info', LEASED: '', RUNNING: 'warning', SUCCEEDED: 'success', FAILED: 'danger' })[state] || 'info' }
  }
}
</script>
<style scoped>
.action-heading,.operations-bar{display:flex;align-items:center;justify-content:space-between}.operations-bar{min-height:58px;padding:0 16px;border:1px solid var(--ui-border);border-top:0}.grow{flex:1}.remote-info{margin-top:16px;padding:16px 18px;background:#fff;border:1px solid var(--ui-border);border-radius:8px}.remote-info header{display:flex;justify-content:space-between;gap:12px}.remote-info header span,.remote-info p{color:var(--ui-muted);font-size:12px}.remote-info-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:14px}.remote-info-grid small,.remote-info-grid b{display:block}.remote-info-grid small{color:var(--ui-muted);font-size:11px}.remote-info-grid b{margin-top:5px;font-size:13px}.remote-info p{margin:14px 0 0}.command-history{margin-top:24px}.command-history h2{margin:0 0 12px;font-size:16px}.ip-status{display:flex;align-items:center;gap:6px;margin-top:7px;color:var(--ui-muted);font-size:12px}.ip-status .el-button{margin-left:auto;padding:0}.status-dot{width:7px;height:7px;border-radius:50%;background:#c7cbd5}.status-dot.is-online{background:#35c59c}.status-dot.is-offline{background:#f56c6c}.status-dot.is-checking{background:#e6a23c}.adding-state{display:flex;flex-direction:column;align-items:center;gap:12px;padding:34px 0;color:var(--ui-muted)}.adding-state i{color:var(--ui-primary);font-size:30px}.adding-state strong{color:var(--ui-text);font-size:17px}@media(max-width:760px){.remote-info-grid{grid-template-columns:repeat(2,1fr)}}
</style>
