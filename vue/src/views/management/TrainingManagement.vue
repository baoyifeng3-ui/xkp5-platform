<template>
  <section class="module-page module-composed-page">
    <header class="module-heading"><div><h1>实训管理</h1><p>查看用户环境状态，控制启动、停止和容器还原。</p></div><el-button type="primary" icon="el-icon-plus" @click="createDialog=true">创建实训环境</el-button></header>
    <div class="module-table-wrap"><el-table v-loading="loading" :data="environments" empty-text="暂无实训环境">
      <el-table-column prop="userId" label="用户" width="90" />
      <el-table-column prop="courseId" label="课程" width="90" />
      <el-table-column prop="agentId" label="处理服务器" min-width="180" show-overflow-tooltip />
      <el-table-column prop="actualState" label="状态" width="120">
        <template slot-scope="scope"><el-tag size="small" :type="tagType(scope.row.actualState)">{{ stateText(scope.row.actualState) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="250" align="right">
        <template slot-scope="scope">
          <el-button size="small" :disabled="isPending(scope.row)" :loading="busyId === scope.row.environmentId" @click="operate(scope.row, 'start')">启动</el-button>
          <el-button size="small" :disabled="isPending(scope.row)" @click="operate(scope.row, 'stop')">停止</el-button>
          <el-button size="small" type="warning" :disabled="isPending(scope.row)" @click="restore(scope.row)">还原</el-button>
          <el-button size="small" type="danger" :disabled="isPending(scope.row) || scope.row.actualState !== 'STOPPED'" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table></div>
    <el-dialog title="创建实训环境" :visible.sync="createDialog" width="520px"><el-form :model="form" label-width="110px"><el-form-item label="用户编号"><el-input v-model.number="form.userId" type="number" /></el-form-item><el-form-item label="课程编号"><el-input v-model.number="form.courseId" type="number" /></el-form-item><el-form-item label="处理服务器"><el-input v-model="form.agentId" /></el-form-item><el-form-item label="槽位编号"><el-input v-model.number="form.slotNumber" type="number" /></el-form-item><el-form-item label="标注模板"><el-input v-model="form.annotationTemplateId" /></el-form-item><el-form-item label="标注版本"><el-input v-model.number="form.annotationTemplateVersion" type="number" /></el-form-item><el-form-item label="编辑模板"><el-input v-model="form.editorTemplateId" /></el-form-item><el-form-item label="编辑版本"><el-input v-model.number="form.editorTemplateVersion" type="number" /></el-form-item></el-form><span slot="footer"><el-button @click="createDialog=false">取消</el-button><el-button type="primary" :loading="creating" @click="create">创建</el-button></span></el-dialog>
  </section>
</template>

<script>
import { listAdminTrainingEnvironments, createAdminTrainingEnvironment, startAdminTrainingEnvironment, stopAdminTrainingEnvironment, restoreAdminTrainingEnvironment, deleteAdminTrainingEnvironment } from '@/api/TrainingEnvironments'

export default {
  data: () => ({ loading: false, busyId: '', creating: false, createDialog: false, environments: [], form: { userId: null, courseId: null, agentId: '', slotNumber: null, annotationTemplateId: '', annotationTemplateVersion: null, editorTemplateId: '', editorTemplateVersion: null } }),
  created () { this.load() },
  methods: {
    async load () { this.loading = true; try { const result = await listAdminTrainingEnvironments(); this.environments = result.data || [] } finally { this.loading = false } },
    async create () { this.creating = true; try { await createAdminTrainingEnvironment(this.form); this.createDialog = false; this.$message.success('实训环境创建任务已提交'); await this.load() } finally { this.creating = false } },
    async operate (row, action) { this.busyId = row.environmentId; try { const call = action === 'start' ? startAdminTrainingEnvironment : stopAdminTrainingEnvironment; await call(row.environmentId); this.$message.info('操作已提交，请等待处理服务器确认'); await this.load() } finally { this.busyId = '' } },
    async restore (row) { await this.$confirm('将重建两个容器，宿主机共享目录中的课程数据会保留。是否继续？', '确认还原', { type: 'warning' }); this.busyId = row.environmentId; try { await restoreAdminTrainingEnvironment(row.environmentId); this.$message.info('还原请求已提交，请等待处理服务器确认'); await this.load() } finally { this.busyId = '' } },
    async remove (row) { try { await this.$confirm('删除后将移除环境记录，是否继续？', '确认删除', { type: 'warning' }); await deleteAdminTrainingEnvironment(row.environmentId); this.$message.success('实训环境已删除'); await this.load() } catch (error) {} },
    isPending (row) { return ['CREATING', 'STARTING', 'STOPPING', 'RESTORING', 'WAITING_DEPENDENCY'].includes(row.actualState) },
    stateText (state) { return ({ RUNNING: '运行中', STOPPED: '已停止', DEGRADED: '部分异常', ERROR: '异常', STARTING: '启动中', STOPPING: '停止中', RESTORING: '还原中', WAITING_DEPENDENCY: '等待切换' })[state] || state || '未知' },
    tagType (state) { return state === 'RUNNING' ? 'success' : (state === 'ERROR' || state === 'DEGRADED' ? 'danger' : 'info') }
  }
}
</script>
