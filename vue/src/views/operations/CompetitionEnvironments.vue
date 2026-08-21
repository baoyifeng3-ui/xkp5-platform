<template>
  <section class="module-page competition-environments-page">
    <header class="module-heading action-heading"><div><h1>比赛容器</h1><p>按处理服务器维护四个固定比赛槽位及其双容器。</p></div><el-button icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button></header>
    <div class="agent-selector"><span>处理服务器</span><el-select v-model="selectedAgentId" filterable placeholder="选择服务器"><el-option v-for="agent in agents" :key="agent.agentId" :label="agent.displayName || agent.hostname || agent.agentId" :value="agent.agentId" /></el-select></div>
    <el-table v-loading="loading" :data="slotRows" row-key="slotNumber" empty-text="请先选择处理服务器">
      <el-table-column prop="slotNumber" label="槽位" width="70" />
      <el-table-column label="绑定" width="90"><template slot-scope="scope">{{ scope.row.userId || '未绑定' }}</template></el-table-column>
      <el-table-column label="容器与状态" min-width="250"><template slot-scope="scope"><div class="cell-stack"><span>{{ scope.row.annotationContainerName || '图像标注：未创建' }} <el-tag size="mini" :type="stateType(scope.row.annotationContainerState)">{{ scope.row.annotationContainerState || '-' }}</el-tag></span><span>{{ scope.row.editorContainerName || '代码编辑：未创建' }} <el-tag size="mini" :type="stateType(scope.row.editorContainerState)">{{ scope.row.editorContainerState || '-' }}</el-tag></span></div></template></el-table-column>
      <el-table-column label="固定模板" min-width="260"><template slot-scope="scope"><div class="cell-stack"><span>标注 {{ imageVersion(scope.row.annotationImageReference, scope.row.annotationTemplateVersion) }}</span><code>{{ shortFingerprint(scope.row.annotationConfigFingerprint) }}</code><span>编辑 {{ imageVersion(scope.row.editorImageReference, scope.row.editorTemplateVersion) }}</span><code>{{ shortFingerprint(scope.row.editorConfigFingerprint) }}</code></div></template></el-table-column>
      <el-table-column label="端口" min-width="180"><template slot-scope="scope"><div class="cell-stack"><span>标注 {{ ports(scope.row.annotationPorts) }}</span><span>编辑 {{ ports(scope.row.editorPorts) }}</span></div></template></el-table-column>
      <el-table-column label="就绪状态" min-width="170"><template slot-scope="scope"><div class="cell-stack"><el-tag size="small" :type="stateType(scope.row.readiness)">{{ scope.row.readiness || '未创建' }}</el-tag><span class="reason">{{ scope.row.failureSummary || scope.row.readinessCode || '-' }}</span></div></template></el-table-column>
      <el-table-column prop="workspaceRelativePath" label="工作目录" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="180" align="right"><template slot-scope="scope"><el-button v-if="!scope.row.environmentId" size="small" type="primary" @click="openCreate(scope.row)">创建</el-button><el-button v-else size="small" type="warning" :loading="busyId === scope.row.environmentId" @click="restore(scope.row)">恢复</el-button></template></el-table-column>
    </el-table>

    <section class="transition-recovery"><div><h2>迁移恢复</h2><p>输入失败的迁移编号，仅超级管理员可以重新派发。</p></div><el-input v-model.trim="transitionId" placeholder="迁移编号"><el-button slot="append" icon="el-icon-refresh-right" :loading="retrying" @click="retry">重试</el-button></el-input></section>

    <el-dialog title="创建比赛容器" :visible.sync="createDialog" width="560px">
      <el-form label-width="120px">
        <el-form-item label="槽位">{{ createForm.slotNumber }}</el-form-item>
        <el-form-item label="图像标注模板"><el-select v-model="annotationSelection" value-key="key" placeholder="选择固定版本"><el-option v-for="item in annotationTemplates" :key="item.key" :label="templateLabel(item)" :value="item" /></el-select></el-form-item>
        <el-form-item label="代码编辑模板"><el-select v-model="editorSelection" value-key="key" placeholder="选择固定版本"><el-option v-for="item in editorTemplates" :key="item.key" :label="templateLabel(item)" :value="item" /></el-select></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="createDialog = false">取消</el-button><el-button type="primary" :loading="creating" :disabled="!annotationSelection || !editorSelection" @click="create">创建容器</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import { listProcessingAgents } from '@/api/ProcessingAgents'
import { listContainerTemplates } from '@/api/ContainerTemplates'
import { listCompetitionEnvironments, createCompetitionEnvironment, restoreCompetitionEnvironment } from '@/api/CompetitionEnvironments'
import { retryModeTransition } from '@/api/PlatformMode'

export default {
  name: 'CompetitionEnvironments',
  data: () => ({ loading: false, creating: false, retrying: false, busyId: '', agents: [], environments: [], templates: [], selectedAgentId: '', createDialog: false, createForm: {}, annotationSelection: null, editorSelection: null, transitionId: '' }),
  computed: {
    slotRows () { if (!this.selectedAgentId) return []; const bySlot = this.environments.filter(item => item.agentId === this.selectedAgentId).reduce((map, item) => { map[item.slotNumber] = item; return map }, {}); return Array.from({ length: 4 }, (_, index) => ({ agentId: this.selectedAgentId, slotNumber: index + 1, ...(bySlot[index + 1] || {}) })) },
    annotationTemplates () { return this.templateOptions('ANNOTATION') },
    editorTemplates () { return this.templateOptions('EDITOR') }
  },
  created () { this.load() },
  methods: {
    async load () { this.loading = true; try { const [agents, environments, templates] = await Promise.all([listProcessingAgents(), listCompetitionEnvironments(), listContainerTemplates()]); this.agents = agents.data || []; this.environments = environments.data || []; this.templates = templates.data || []; if (!this.selectedAgentId && this.agents.length) this.selectedAgentId = this.agents[0].agentId } finally { this.loading = false } },
    templateOptions (type) { return this.templates.filter(item => item.componentType === type && item.enabled).map(item => ({ ...item, key: `${item.templateId}:${item.templateVersion}` })) },
    templateLabel (item) { return `${item.templateName || item.templateId} · v${item.templateVersion}` },
    imageVersion (image, version) { return image ? `${image} / v${version}` : '-' },
    shortFingerprint (value) { return value ? `${value.slice(0, 12)}…` : '-' },
    ports (items) { return items && items.length ? items.map(item => `${item.hostPort}:${item.containerPort}/${item.protocol}`).join(', ') : '-' },
    stateType (state) { return state === 'RUNNING' ? 'success' : (state === 'ERROR' || state === 'DEGRADED' ? 'danger' : 'info') },
    openCreate (row) { this.createForm = { agentId: row.agentId, slotNumber: row.slotNumber }; this.annotationSelection = this.annotationTemplates[0] || null; this.editorSelection = this.editorTemplates[0] || null; this.createDialog = true },
    async create () { this.creating = true; try { await createCompetitionEnvironment({ ...this.createForm, annotationTemplateId: this.annotationSelection.templateId, annotationTemplateVersion: this.annotationSelection.templateVersion, editorTemplateId: this.editorSelection.templateId, editorTemplateVersion: this.editorSelection.templateVersion }); this.$message.success('容器创建命令已提交'); this.createDialog = false; await this.load() } finally { this.creating = false } },
    async restore (row) { await this.$confirm('将按固定模板版本重建两个容器，并保留槽位工作目录。是否继续？', '恢复比赛容器', { type: 'warning' }); this.busyId = row.environmentId; try { await restoreCompetitionEnvironment(row.environmentId); this.$message.success('恢复命令已提交'); await this.load() } finally { this.busyId = '' } },
    async retry () { if (!this.transitionId) return this.$message.warning('请输入迁移编号'); this.retrying = true; try { await retryModeTransition(this.transitionId); this.$message.success('迁移重试已提交') } finally { this.retrying = false } }
  }
}
</script>

<style scoped>
.action-heading, .agent-selector, .transition-recovery { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.agent-selector { justify-content: flex-start; margin-bottom: 16px; }
.agent-selector .el-select { width: min(420px, 100%); }
.cell-stack { display: grid; gap: 5px; font-size: 12px; }
.cell-stack code { color: #617383; font-size: 11px; }
.reason { color: #73828e; overflow-wrap: anywhere; }
.transition-recovery { margin-top: 28px; padding: 18px 0; border-top: 1px solid #d9e2e8; border-bottom: 1px solid #d9e2e8; }
.transition-recovery h2 { margin: 0 0 5px; font-size: 18px; }
.transition-recovery p { margin: 0; color: #73828e; }
.transition-recovery .el-input { max-width: 480px; }
@media (max-width: 720px) { .action-heading, .transition-recovery { align-items: stretch; flex-direction: column; } }
</style>
