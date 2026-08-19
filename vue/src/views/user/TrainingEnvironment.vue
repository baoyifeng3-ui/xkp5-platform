<template>
  <section class="module-page">
    <header class="module-heading"><h1>实训环境</h1><p>选择课程环境并启动图像标注与代码编辑工作区。</p></header>
    <el-table v-loading="loading" :data="environments" empty-text="暂无已分配环境">
      <el-table-column prop="courseId" label="课程" min-width="100" />
      <el-table-column prop="actualState" label="运行状态" min-width="120">
        <template slot-scope="scope"><el-tag size="small" :type="tagType(scope.row.actualState)">{{ stateText(scope.row.actualState) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="right">
        <template slot-scope="scope"><el-button type="primary" size="small" :loading="busyId === scope.row.environmentId" :disabled="isPending(scope.row)" @click="start(scope.row)">一键上课</el-button></template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script>
import { listUserTrainingEnvironments, startUserTrainingEnvironment } from '@/api/TrainingEnvironments'

export default {
  data: () => ({ loading: false, busyId: '', environments: [] }),
  created () { this.load() },
  methods: {
    async load () { this.loading = true; try { const result = await listUserTrainingEnvironments(); this.environments = result.data || [] } finally { this.loading = false } },
    async start (row) { this.busyId = row.environmentId; try { const result = await startUserTrainingEnvironment(row.environmentId); this.$message.info(result.data && result.data.state === 'SUCCEEDED' ? '环境已运行' : '启动请求已提交，请等待处理服务器确认'); await this.load() } finally { this.busyId = '' } },
    isPending (row) { return ['CREATING', 'STARTING', 'STOPPING', 'RESTORING', 'WAITING_DEPENDENCY'].includes(row.actualState) },
    stateText (state) { return ({ RUNNING: '运行中', STOPPED: '已停止', DEGRADED: '部分异常', ERROR: '异常', STARTING: '启动中', STOPPING: '停止中', RESTORING: '还原中', WAITING_DEPENDENCY: '等待切换' })[state] || state || '未知' },
    tagType (state) { return state === 'RUNNING' ? 'success' : (state === 'ERROR' || state === 'DEGRADED' ? 'danger' : 'info') }
  }
}
</script>
