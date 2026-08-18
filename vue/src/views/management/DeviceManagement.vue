<template>
  <section class="module-page">
    <header class="module-heading"><h1>设备管理</h1><p>查看处理服务器在线状态与资源占用。</p></header>
    <AgentStatusTable :agents="agents" :loading="loading" @select="selectAgent" />
    <section v-if="selected" class="agent-detail">
      <header><div><h2>{{ selected.displayName || selected.hostname }}</h2><span>{{ selected.primaryIp }} · {{ selected.agentVersion }}</span></div><el-button icon="el-icon-refresh" @click="load">刷新</el-button></header>
      <AgentMetricSummary :metrics="selected.latestMetrics || {}" />
      <AgentHistoryChart :points="history" />
    </section>
  </section>
</template>
<script>
import AgentStatusTable from '@/components/agents/AgentStatusTable.vue'
import AgentMetricSummary from '@/components/agents/AgentMetricSummary.vue'
import AgentHistoryChart from '@/components/agents/AgentHistoryChart.vue'
import { listProcessingAgents, getProcessingAgentHistory } from '@/api/ProcessingAgents'
export default {
  name: 'DeviceManagement', components: { AgentStatusTable, AgentMetricSummary, AgentHistoryChart },
  data: () => ({ agents: [], selected: null, history: [], loading: false, timer: null }),
  mounted () { this.load(); this.timer = setInterval(this.load, 10000) },
  beforeDestroy () { clearInterval(this.timer) },
  methods: {
    async load () { this.loading = true; try { const result = await listProcessingAgents(); this.agents = result.data || []; if (this.selected) this.selected = this.agents.find(a => a.agentId === this.selected.agentId) || null } finally { this.loading = false } },
    async selectAgent (agent) { this.selected = agent; const to = new Date(); const from = new Date(to.getTime() - 24 * 60 * 60 * 1000); const result = await getProcessingAgentHistory(agent.agentId, { from: from.toISOString(), to: to.toISOString() }); this.history = result.data || [] }
  }
}
</script>
<style scoped>
.agent-detail { margin-top: 24px; }
.agent-detail > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.agent-detail h2 { margin: 0 0 4px; color: #17324d; font-size: 18px; }
.agent-detail header span { color: #718096; font-size: 13px; }
</style>
