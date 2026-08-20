<template>
  <section class="module-page">
    <header class="module-heading action-heading"><div><h1>处理服务器</h1><p>维护 Agent 注册、启停和移除。</p></div><el-button type="primary" icon="el-icon-plus" @click="tokenDialog = true">生成注册码</el-button></header>
    <AgentStatusTable :agents="agents" :loading="loading" @select="selectAgent" />
    <div v-if="selected" class="operations-bar">
      <strong>{{ selected.displayName || selected.hostname }}</strong><span class="grow" />
      <el-tooltip content="发送局域网 Wake-on-LAN 唤醒数据包" placement="top"><el-button icon="el-icon-sunny" :loading="busyAction === 'wake'" :disabled="actionBusy" @click="wakeAgent">开机</el-button></el-tooltip>
      <el-tooltip content="通知在线 Agent 安全关闭处理服务器" placement="top"><el-button icon="el-icon-switch-button" :loading="busyAction === 'shutdown'" :disabled="actionBusy || !selected.online" @click="shutdownAgent">关机</el-button></el-tooltip>
      <el-button v-if="selected.enabled" icon="el-icon-video-pause" @click="disableProcessingAgentAction">停用</el-button>
      <el-button v-else type="success" icon="el-icon-video-play" @click="enableAgent">启用</el-button>
      <el-tooltip v-if="canOpenRootTerminal" content="打开受控 root 终端" placement="top"><el-button type="warning" icon="el-icon-monitor" :loading="terminalOpening" :disabled="actionBusy || terminalOpening" @click="openRootTerminal">终端</el-button></el-tooltip>
      <el-button type="danger" plain icon="el-icon-delete" @click="removeAgent">移除</el-button>
    </div>
    <section v-if="selected" class="command-history">
      <h2>电源操作记录</h2>
      <el-table :data="commands" empty-text="暂无操作记录" size="small">
        <el-table-column prop="requestedAt" label="发起时间" min-width="170" />
        <el-table-column label="操作" width="100"><template>关机</template></el-table-column>
        <el-table-column label="状态" width="150"><template slot-scope="scope"><el-tag size="small" :type="commandTag(scope.row.state)">{{ commandState(scope.row) }}</el-tag></template></el-table-column>
        <el-table-column prop="resultMessage" label="结果" min-width="240" show-overflow-tooltip />
      </el-table>
    </section>
    <el-dialog title="生成一次性注册码" :visible.sync="tokenDialog" width="460px" @closed="clearToken">
      <el-input v-model="label" placeholder="用途，例如：A 机房" maxlength="80" />
      <div v-if="issuedToken" class="issued-token"><code>{{ issuedToken }}</code><el-button icon="el-icon-document-copy" @click="copyToken">复制</el-button></div>
      <span slot="footer"><el-button @click="tokenDialog = false">关闭</el-button><el-button type="primary" :loading="issuing" @click="issueToken">生成</el-button></span>
    </el-dialog>
    <RootTerminalDialog v-if="terminalVisible" :visible.sync="terminalVisible" :session="terminalSession" :agent-name="selected && (selected.displayName || selected.hostname)" @closed="terminalSession = null" />
  </section>
</template>
<script>
import AgentStatusTable from '@/components/agents/AgentStatusTable.vue'
import RootTerminalDialog from '@/components/agents/RootTerminalDialog.vue'
import { listProcessingAgents, createRegistrationToken, enableProcessingAgent, disableProcessingAgent, removeProcessingAgent, listProcessingAgentCommands, wakeProcessingAgent, shutdownProcessingAgent } from '@/api/ProcessingAgents'
import { createTerminalSession } from '@/api/TerminalSessions'
import { getRole } from '@/utils/auth'
import { SUPER_ADMIN } from '@/navigation/roleNavigation'
export default {
  name: 'ProcessingAgents', components: { AgentStatusTable, RootTerminalDialog },
  data: () => ({ agents: [], selected: null, commands: [], loading: false, busyAction: '', tokenDialog: false, label: '', issuedToken: '', issuing: false, terminalVisible: false, terminalSession: null, terminalOpening: false }),
  computed: { actionBusy () { return Boolean(this.busyAction) }, canOpenRootTerminal () { return getRole() === SUPER_ADMIN && Boolean(this.selected && this.selected.online && this.selected.enabled) } },
  mounted () { this.load() },
  methods: {
    async load () { this.loading = true; try { const result = await listProcessingAgents(); this.agents = result.data || [] } finally { this.loading = false } },
    async selectAgent (agent) { this.selected = agent; await this.loadCommands() },
    async loadCommands () { if (!this.selected) return; const result = await listProcessingAgentCommands(this.selected.agentId); this.commands = result.data || [] },
    async wakeAgent () { this.busyAction = 'wake'; try { await wakeProcessingAgent(this.selected.agentId); this.$message.success('唤醒数据包已发送，请等待服务器上线') } finally { this.busyAction = '' } },
    async shutdownAgent () { await this.$confirm('服务器将安全关机，正在运行的环境会停止。是否继续？', '确认关机', { type: 'warning' }); this.busyAction = 'shutdown'; try { await shutdownProcessingAgent(this.selected.agentId); this.$message.success('关机命令已提交，等待服务器离线确认'); await this.loadCommands() } finally { this.busyAction = '' } },
    async openRootTerminal () {
      if (!this.canOpenRootTerminal || this.terminalOpening) return
      this.terminalOpening = true
      try {
        await this.$confirm(`将以 root 权限连接“${this.selected.displayName || this.selected.hostname}”，可执行主机级维护命令。确认继续？`, '打开 root 终端', { type: 'warning', confirmButtonText: '确认连接' })
        const result = await createTerminalSession(this.selected.agentId); this.terminalSession = result.data || result; this.terminalVisible = true
      } finally { this.terminalOpening = false }
    },
    commandState (command) { return ({ PENDING: '等待 Agent 接收', LEASED: '已送达', RUNNING: '等待离线确认', SUCCEEDED: '已完成', FAILED: command.resultMessage || '执行失败' })[command.state] || command.state },
    commandTag (state) { return ({ PENDING: 'info', LEASED: '', RUNNING: 'warning', SUCCEEDED: 'success', FAILED: 'danger' })[state] || 'info' },
    async issueToken () { this.issuing = true; try { const result = await createRegistrationToken({ label: this.label }); this.issuedToken = (result.data || {}).token || '' } finally { this.issuing = false } },
    async copyToken () { await navigator.clipboard.writeText(this.issuedToken); this.$message.success('注册码已复制，请妥善保存') },
    clearToken () { this.issuedToken = ''; this.label = '' },
    async disableProcessingAgentAction () { await this.confirmAction('停用后该服务器将不能继续上报，是否继续？', () => disableProcessingAgent(this.selected.agentId)) },
    async enableAgent () { await enableProcessingAgent(this.selected.agentId); await this.load() },
    async removeAgent () { await this.confirmAction('移除后需重新注册才能接入，历史监控数据会保留。', () => removeProcessingAgent(this.selected.agentId)) },
    async confirmAction (message, action) { await this.$confirm(message, '确认操作', { type: 'warning' }); await action(); this.selected = null; await this.load() }
  }
}
</script>
<style scoped>
.action-heading, .operations-bar { display: flex; align-items: center; justify-content: space-between; }
.operations-bar { min-height: 58px; padding: 0 16px; border: 1px solid #dfe5ec; border-top: 0; }
.grow { flex: 1; }
.command-history { margin-top: 24px; }
.command-history h2 { margin: 0 0 12px; color: #17324d; font-size: 16px; }
.issued-token { display: flex; align-items: center; gap: 10px; margin-top: 16px; }
.issued-token code { flex: 1; overflow-wrap: anywhere; padding: 12px; background: #f3f6f9; color: #17324d; }
</style>
