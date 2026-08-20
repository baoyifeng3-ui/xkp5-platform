<template><div ref="chart" class="history-chart" /></template>
<script>
import * as echarts from 'echarts'
export default {
  name: 'AgentHistoryChart',
  props: { points: { type: Array, default: () => [] } },
  watch: { points: { deep: true, handler () { this.render() } } },
  mounted () { this.chart = echarts.init(this.$refs.chart); this.render(); window.addEventListener('resize', this.resize) },
  beforeDestroy () { window.removeEventListener('resize', this.resize); if (this.chart) this.chart.dispose() },
  methods: {
    resize () { if (this.chart) this.chart.resize() },
    render () {
      if (!this.chart) return
      const series = [['CPU', 'cpuAverage'], ['GPU', 'gpuAverage'], ['内存', 'ramAverage'], ['工作盘', 'workspaceDiskAverage']]
      this.chart.setOption({
        color: ['#246bfd', '#00a870', '#f59e0b', '#7a5af8'],
        tooltip: { trigger: 'axis' }, legend: { top: 4 }, grid: { left: 45, right: 20, top: 42, bottom: 34 },
        xAxis: { type: 'category', data: this.points.map(p => p.bucketStart), axisLabel: { hideOverlap: true } },
        yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
        series: series.map(item => ({ name: item[0], type: 'line', symbol: 'none', connectNulls: false, data: this.points.map(p => p[item[1]]) }))
      }, true)
    }
  }
}
</script>
<style scoped>.history-chart { width: 100%; height: 320px; border: 1px solid #dfe5ec; }</style>
