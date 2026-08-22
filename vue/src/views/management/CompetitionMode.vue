<template>
  <section class="module-page module-composed-page competition-mode-page">
    <header class="module-heading module-toolbar action-heading">
      <div><h1>比赛模式</h1><p>切换普通用户界面，并管理用户与比赛环境槽位的绑定。</p></div>
      <el-button icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button>
    </header>

    <section class="mode-band">
      <div><small>当前平台模式</small><strong>{{ modeText(mode.mode) }}</strong><span>代次 {{ mode.generation || 0 }} · {{ mode.changedAt || '尚无变更时间' }}</span></div>
      <el-button :type="mode.mode === 'COMPETITION' ? 'warning' : 'primary'" :loading="switching" @click="confirmSwitch">
        {{ mode.mode === 'COMPETITION' ? '退出比赛模式' : '进入比赛模式' }}
      </el-button>
    </section>

    <section class="section-block">
      <div class="section-heading"><div><h2>槽位与用户绑定</h2><p>只有已创建并验证的容器槽位可以绑定；未绑定用户仍可答题，但没有实操入口。</p></div></div>
      <div class="module-table-wrap"><el-table v-loading="loading" :data="slots" row-key="slotId" empty-text="暂无可见处理服务器">
        <el-table-column prop="agentId" label="处理服务器" min-width="180" show-overflow-tooltip />
        <el-table-column prop="slotNumber" label="槽位" width="72" />
        <el-table-column label="容器" min-width="210"><template slot-scope="scope"><div class="cell-stack"><span>{{ scope.row.annotationContainerName || '未创建图像标注容器' }}</span><span>{{ scope.row.editorContainerName || '未创建代码编辑容器' }}</span></div></template></el-table-column>
        <el-table-column label="就绪" width="120"><template slot-scope="scope"><el-tag size="small" :type="readinessType(scope.row.readiness)">{{ readinessText(scope.row) }}</el-tag></template></el-table-column>
        <el-table-column label="绑定用户" width="150"><template slot-scope="scope">{{ scope.row.userId || '未绑定' }}</template></el-table-column>
        <el-table-column label="操作" width="210" align="right"><template slot-scope="scope">
          <el-button v-if="scope.row.userId" size="small" type="danger" plain :loading="busySlot === scope.row.slotId" @click="unbind(scope.row)">解绑</el-button>
          <el-button v-else size="small" type="primary" :disabled="!scope.row.environmentId || scope.row.readiness !== 'READY'" @click="openBind(scope.row)">绑定用户</el-button>
        </template></el-table-column>
      </el-table></div>
    </section>

    <section class="section-block">
      <div class="section-heading"><div><h2>模式迁移</h2><p>查看每台处理服务器的停止、启动和恢复进度。</p></div></div>
      <div class="module-table-wrap"><el-table :data="transitions" row-key="transitionId" empty-text="暂无模式迁移记录">
        <el-table-column prop="requestedAt" label="发起时间" min-width="165" />
        <el-table-column prop="agentId" label="处理服务器" min-width="170" show-overflow-tooltip />
        <el-table-column label="方向" width="150"><template slot-scope="scope">{{ modeText(scope.row.sourceMode) }} → {{ modeText(scope.row.targetMode) }}</template></el-table-column>
        <el-table-column label="进度" width="150"><template slot-scope="scope"><el-progress :percentage="progress(scope.row)" :status="scope.row.state === 'FAILED' ? 'exception' : undefined" /></template></el-table-column>
        <el-table-column label="状态" width="120"><template slot-scope="scope"><el-tag size="small" :type="transitionType(scope.row.state)">{{ transitionText(scope.row.state) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90" align="right"><template slot-scope="scope"><el-button type="text" @click="openTransition(scope.row)">详情</el-button></template></el-table-column>
      </el-table></div>
    </section>

    <el-dialog title="绑定比赛槽位" :visible.sync="bindDialog" width="440px">
      <el-form label-width="90px"><el-form-item label="用户编号"><el-input-number v-model="bindUserId" :min="1" :max="2147483647" controls-position="right" /></el-form-item></el-form>
      <span slot="footer"><el-button @click="bindDialog = false">取消</el-button><el-button type="primary" :loading="binding" @click="bind">确认绑定</el-button></span>
    </el-dialog>

    <el-drawer title="迁移详情" :visible.sync="transitionDrawer" size="48%">
      <div v-if="activeTransition" class="drawer-content">
        <el-descriptions :column="2" border><el-descriptions-item label="处理服务器">{{ activeTransition.agentId }}</el-descriptions-item><el-descriptions-item label="状态">{{ transitionText(activeTransition.state) }}</el-descriptions-item><el-descriptions-item label="阶段进度">{{ completedSteps(activeTransition) }}/{{ (activeTransition.steps || []).length }}</el-descriptions-item><el-descriptions-item label="失败原因">{{ activeTransition.failureSummary || '-' }}</el-descriptions-item></el-descriptions>
        <div class="module-table-wrap"><el-table :data="activeTransition.steps || []" size="small"><el-table-column prop="phaseNumber" label="阶段" width="70" /><el-table-column prop="environmentKind" label="环境" width="100" /><el-table-column prop="actionType" label="动作" width="110" /><el-table-column prop="state" label="状态" width="100" /><el-table-column prop="resultMessage" label="结果" min-width="180" /></el-table></div>
      </div>
    </el-drawer>
  </section>
</template>

<script>
import { getPlatformMode, changePlatformMode, listModeTransitions, getModeTransition } from '@/api/PlatformMode'
import { listCompetitionSlots, bindCompetitionSlot, unbindCompetitionSlot } from '@/api/CompetitionEnvironments'

export default {
  name: 'CompetitionMode',
  data: () => ({ loading: false, switching: false, binding: false, busySlot: '', mode: {}, slots: [], transitions: [], bindDialog: false, bindUserId: 1, selectedSlot: null, transitionDrawer: false, activeTransition: null }),
  created () { this.load() },
  methods: {
    async load () { this.loading = true; try { const [mode, slots, transitions] = await Promise.all([getPlatformMode(), listCompetitionSlots(), listModeTransitions(100)]); this.mode = mode.data || {}; this.slots = slots.data || []; this.transitions = transitions.data || [] } finally { this.loading = false } },
    async confirmSwitch () { const target = this.mode.mode === 'COMPETITION' ? 'TRAINING' : 'COMPETITION'; const phrase = target === 'COMPETITION' ? 'ENTER COMPETITION' : 'EXIT COMPETITION'; const value = await this.$prompt(`输入 ${phrase} 确认切换。普通用户现有登录会失效。`, target === 'COMPETITION' ? '进入比赛模式' : '退出比赛模式', { confirmButtonText: '确认切换', inputPattern: new RegExp(`^${phrase}$`), inputErrorMessage: '确认文本不匹配' }); this.switching = true; try { await changePlatformMode(target); this.$message.success('模式切换已提交'); await this.load() } finally { this.switching = false } },
    openBind (slot) { this.selectedSlot = slot; this.bindUserId = 1; this.bindDialog = true },
    async bind () { this.binding = true; try { await bindCompetitionSlot(this.selectedSlot.slotId, this.bindUserId); this.$message.success('用户已绑定'); this.bindDialog = false; await this.load() } finally { this.binding = false } },
    async unbind (slot) { await this.$confirm(`确认解除用户 ${slot.userId} 与槽位 ${slot.slotNumber} 的绑定？`, '解除绑定', { type: 'warning' }); this.busySlot = slot.slotId; try { await unbindCompetitionSlot(slot.slotId, slot.userId); this.$message.success('绑定已解除'); await this.load() } finally { this.busySlot = '' } },
    async openTransition (row) { const result = await getModeTransition(row.transitionId); this.activeTransition = result.data || row; this.transitionDrawer = true },
    completedSteps (row) { return (row.steps || []).filter(step => step.state === 'SUCCEEDED' || step.state === 'SKIPPED').length },
    progress (row) { const total = (row.steps || []).length; return total ? Math.round(this.completedSteps(row) * 100 / total) : (row.state === 'SUCCEEDED' ? 100 : 0) },
    modeText (mode) { return mode === 'COMPETITION' ? '比赛模式' : '实训模式' },
    readinessText (row) { return ({ READY: '已就绪', UNBOUND: '未绑定', DEGRADED: '异常', STARTING: '准备中' })[row.readiness] || row.readinessCode || '未创建' },
    readinessType (value) { return value === 'READY' ? 'success' : (value === 'DEGRADED' ? 'danger' : 'info') },
    transitionText (value) { return ({ PENDING: '等待中', RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: '失败', DEGRADED: '需处理' })[value] || value || '未知' },
    transitionType (value) { return value === 'SUCCEEDED' ? 'success' : (value === 'FAILED' || value === 'DEGRADED' ? 'danger' : 'warning') }
  }
}
</script>

<style scoped>
.action-heading, .mode-band, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.mode-band { padding: 18px 0; border-top: 1px solid #d9e2e8; border-bottom: 1px solid #d9e2e8; }
.mode-band small, .mode-band strong, .mode-band span { display: block; }
.mode-band strong { margin: 5px 0; color: var(--ui-text); font-size: 22px; }
.mode-band span, .section-heading p { color: #73828e; }
.section-block { margin-top: 28px; }
.section-heading h2 { margin: 0; font-size: 18px; }
.section-heading p { margin: 5px 0 12px; }
.cell-stack { display: grid; gap: 4px; font-size: 12px; }
.drawer-content { padding: 0 22px 24px; }
.drawer-content .el-table { margin-top: 20px; }
@media (max-width: 720px) { .action-heading, .mode-band { align-items: flex-start; flex-direction: column; } }
</style>
