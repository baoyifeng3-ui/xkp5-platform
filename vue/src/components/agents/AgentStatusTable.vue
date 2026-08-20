<template>
  <div class="agent-table" v-loading="loading">
    <el-table :data="agents" height="420" highlight-current-row @row-click="$emit('select', $event)">
      <el-table-column prop="displayName" label="服务器" min-width="150">
        <template slot-scope="scope"><strong>{{ scope.row.displayName || scope.row.hostname || scope.row.agentId }}</strong></template>
      </el-table-column>
      <el-table-column prop="primaryIp" label="固定 IP" width="150" />
      <el-table-column label="状态" width="100">
        <template slot-scope="scope"><el-tag size="small" :type="scope.row.online ? 'success' : 'info'">{{ scope.row.online ? '在线' : '离线' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="CPU" width="90"><template slot-scope="scope">{{ percent(scope.row.latestMetrics && scope.row.latestMetrics.cpuPercent) }}</template></el-table-column>
      <el-table-column label="GPU" width="90"><template slot-scope="scope">{{ percent(scope.row.latestMetrics && scope.row.latestMetrics.gpuPercent) }}</template></el-table-column>
      <el-table-column label="内存" width="90"><template slot-scope="scope">{{ percent(scope.row.latestMetrics && scope.row.latestMetrics.ramPercent) }}</template></el-table-column>
      <el-table-column prop="lastSeenAt" label="最后上报" min-width="170" />
      <template slot="empty"><div class="agent-empty"><i class="el-icon-cpu" /><span>暂无处理服务器</span></div></template>
    </el-table>
  </div>
</template>
<script>
export default {
  name: 'AgentStatusTable',
  props: { agents: { type: Array, default: () => [] }, loading: Boolean },
  methods: { percent (value) { return value === null || value === undefined ? '--' : `${value}%` } }
}
</script>
<style scoped>
.agent-table { min-height: 420px; border: 1px solid #dfe5ec; }
.agent-empty { height: 300px; display: flex; align-items: center; justify-content: center; gap: 10px; color: #8290a3; }
.agent-empty i { font-size: 24px; }
</style>
