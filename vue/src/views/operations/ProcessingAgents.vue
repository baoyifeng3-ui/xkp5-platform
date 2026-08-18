<template>
  <section class="module-page">
    <header class="module-heading action-heading"><div><h1>处理服务器</h1><p>维护 Agent 注册、启停和移除。</p></div><el-button type="primary" icon="el-icon-plus" @click="tokenDialog = true">生成注册码</el-button></header>
    <AgentStatusTable :agents="agents" :loading="loading" @select="selected = $event" />
    <div v-if="selected" class="operations-bar">
      <strong>{{ selected.displayName || selected.hostname }}</strong><span class="grow" />
      <el-button v-if="selected.enabled" icon="el-icon-video-pause" @click="disableProcessingAgentAction">停用</el-button>
      <el-button v-else type="success" icon="el-icon-video-play" @click="enableAgent">启用</el-button>
      <el-button type="danger" plain icon="el-icon-delete" @click="removeAgent">移除</el-button>
    </div>
    <el-dialog title="生成一次性注册码" :visible.sync="tokenDialog" width="460px" @closed="clearToken">
      <el-input v-model="label" placeholder="用途，例如：A 机房" maxlength="80" />
      <div v-if="issuedToken" class="issued-token"><code>{{ issuedToken }}</code><el-button icon="el-icon-document-copy" @click="copyToken">复制</el-button></div>
      <span slot="footer"><el-button @click="tokenDialog = false">关闭</el-button><el-button type="primary" :loading="issuing" @click="issueToken">生成</el-button></span>
    </el-dialog>
  </section>
</template>
<script>
import AgentStatusTable from '@/components/agents/AgentStatusTable.vue'
import { listProcessingAgents, createRegistrationToken, enableProcessingAgent, disableProcessingAgent, removeProcessingAgent } from '@/api/ProcessingAgents'
export default {
  name: 'ProcessingAgents', components: { AgentStatusTable },
  data: () => ({ agents: [], selected: null, loading: false, tokenDialog: false, label: '', issuedToken: '', issuing: false }),
  mounted () { this.load() },
  methods: {
    async load () { this.loading = true; try { const result = await listProcessingAgents(); this.agents = result.data || [] } finally { this.loading = false } },
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
.issued-token { display: flex; align-items: center; gap: 10px; margin-top: 16px; }
.issued-token code { flex: 1; overflow-wrap: anywhere; padding: 12px; background: #f3f6f9; color: #17324d; }
</style>
