<template>
  <div class="metric-summary">
    <div v-for="item in items" :key="item.label" class="metric"><span>{{ item.label }}</span><strong>{{ display(item.value) }}</strong></div>
    <el-alert v-if="errors.length" :title="`采集异常：${errors.join('、')}`" type="warning" :closable="false" show-icon />
  </div>
</template>
<script>
export default {
  name: 'AgentMetricSummary',
  props: { metrics: { type: Object, default: () => ({}) } },
  computed: {
    items () { return [
      { label: 'CPU', value: this.metrics.cpuPercent }, { label: 'GPU', value: this.metrics.gpuPercent },
      { label: '内存', value: this.metrics.ramPercent }, { label: '显存', value: this.metrics.gpuMemoryPercent },
      { label: '系统盘', value: this.metrics.systemDiskPercent }, { label: '工作盘', value: this.metrics.workspaceDiskPercent }
    ] },
    errors () { return Object.keys(this.metrics.collectorErrors || {}) }
  },
  methods: { display (value) { return value === null || value === undefined ? '--' : `${value}%` } }
}
</script>
<style scoped>
.metric-summary { display: grid; grid-template-columns: repeat(6, minmax(90px, 1fr)); border: 1px solid #dfe5ec; border-bottom: 0; }
.metric { padding: 16px; border-right: 1px solid #e5e9ef; }
.metric:last-of-type { border-right: 0; }
.metric span { display: block; color: #69778a; font-size: 13px; }
.metric strong { display: block; margin-top: 6px; color: #17324d; font-size: 20px; }
.el-alert { grid-column: 1 / -1; border-radius: 0; }
@media (max-width: 900px) { .metric-summary { grid-template-columns: repeat(3, 1fr); } }
</style>
