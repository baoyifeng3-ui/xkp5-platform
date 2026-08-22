<template>
  <section class="module-page module-composed-page">
    <header class="module-heading"><h1>设备管理</h1><p>查看处理服务器在线状态与资源占用。</p></header>
    <AgentStatusTable :agents="agents" :loading="loading" @select="selectAgent" />
    <section v-if="selected" class="agent-detail">
      <header>
        <div><h2>{{ selected.displayName || selected.hostname }}</h2><span>{{ selected.primaryIp }} · {{ selected.agentVersion }}</span></div>
        <div class="power-actions module-toolbar">
          <el-tooltip content="发送局域网 Wake-on-LAN 唤醒数据包" placement="top"><el-button icon="el-icon-sunny" :loading="busyAction === 'wake'" :disabled="actionBusy" @click="wakeAgent">开机</el-button></el-tooltip>
          <el-tooltip content="通知在线 Agent 安全关闭处理服务器" placement="top"><el-button type="danger" plain icon="el-icon-switch-button" :loading="busyAction === 'shutdown'" :disabled="actionBusy || !selected.online" @click="shutdownAgent">关机</el-button></el-tooltip>
          <el-button icon="el-icon-refresh" :disabled="actionBusy" @click="loadSelected">刷新</el-button>
        </div>
      </header>
      <AgentMetricSummary :metrics="selected.latestMetrics || {}" />
      <AgentHistoryChart :points="history" />
      <section class="command-history">
        <h3>电源操作记录</h3>
        <div class="module-table-wrap"><el-table :data="commands" empty-text="暂无操作记录" size="small">
          <el-table-column prop="requestedAt" label="发起时间" min-width="170" />
          <el-table-column label="操作" width="100"><template>关机</template></el-table-column>
          <el-table-column label="状态" width="150"><template slot-scope="scope"><el-tag size="small" :type="commandTag(scope.row.state)">{{ commandState(scope.row) }}</el-tag></template></el-table-column>
          <el-table-column prop="resultMessage" label="结果" min-width="240" show-overflow-tooltip />
        </el-table></div>
      </section>
    </section>
  </section>
</template>
<script>
import AgentStatusTable from '@/components/agents/AgentStatusTable.vue'
import AgentMetricSummary from '@/components/agents/AgentMetricSummary.vue'
import AgentHistoryChart from '@/components/agents/AgentHistoryChart.vue'
import { listProcessingAgents, getProcessingAgentHistory, listProcessingAgentCommands, wakeProcessingAgent, shutdownProcessingAgent } from '@/api/ProcessingAgents'
export default {
  name: 'DeviceManagement', components: { AgentStatusTable, AgentMetricSummary, AgentHistoryChart },
  data: () => ({ agents: [], selected: null, history: [], commands: [], loading: false, busyAction: '', timer: null }),
  computed: { actionBusy () { return Boolean(this.busyAction) } },
  mounted () { this.load(); this.timer = setInterval(this.load, 10000) },
  beforeDestroy () { clearInterval(this.timer) },
  methods: {
    async load () { this.loading = true; try { const result = await listProcessingAgents(); this.agents = result.data || []; if (this.selected) this.selected = this.agents.find(a => a.agentId === this.selected.agentId) || null } finally { this.loading = false } },
    async selectAgent (agent) { this.selected = agent; await this.loadSelected() },
    async loadSelected () {
      if (!this.selected) return
      const agentId = this.selected.agentId
      const to = new Date()
      const from = new Date(to.getTime() - 24 * 60 * 60 * 1000)
      const [metrics, commands] = await Promise.all([
        getProcessingAgentHistory(agentId, { from: from.toISOString(), to: to.toISOString() }),
        listProcessingAgentCommands(agentId)
      ])
      if (this.selected && this.selected.agentId === agentId) {
        this.history = metrics.data || []
        this.commands = commands.data || []
      }
    },
    async wakeAgent () {
      this.busyAction = 'wake'
      try {
        await wakeProcessingAgent(this.selected.agentId)
        this.$message.success('唤醒数据包已发送，请等待服务器上线')
      } finally { this.busyAction = '' }
    },
    async shutdownAgent () {
      await this.$confirm('服务器将安全关机，正在运行的环境会停止。是否继续？', '确认关机', { type: 'warning' })
      this.busyAction = 'shutdown'
      try {
        await shutdownProcessingAgent(this.selected.agentId)
        this.$message.success('关机命令已提交，等待服务器离线确认')
        await this.loadSelected()
      } finally { this.busyAction = '' }
    },
    commandState (command) { return ({ PENDING: '等待 Agent 接收', LEASED: '已送达', RUNNING: '等待离线确认', SUCCEEDED: '已完成', FAILED: command.resultMessage || '执行失败' })[command.state] || command.state },
    commandTag (state) { return ({ PENDING: 'info', LEASED: '', RUNNING: 'warning', SUCCEEDED: 'success', FAILED: 'danger' })[state] || 'info' }
  }
}
</script>
<style scoped>
.agent-detail { margin-top: 24px; }
.agent-detail > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.agent-detail h2 { margin: 0 0 4px; color: var(--ui-text); font-size: 18px; }
.agent-detail header span { color: #718096; font-size: 13px; }
.power-actions { display: flex; gap: 8px; }
.command-history { margin-top: 24px; }
.command-history h3 { margin: 0 0 12px; color: var(--ui-text); font-size: 16px; }
</style>
