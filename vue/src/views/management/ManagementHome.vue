<template>
  <section class="module-page dashboard-home">
    <header class="module-heading"><div><h1>主页</h1><p>普通管理员的业务总览与快捷入口。</p></div><span v-if="stale" class="stale-state">数据暂未更新</span></header>
    <div class="overview-grid"><button v-for="action in actions" :key="action.label" type="button" class="overview-action" @click="$router.push(action.route)"><i :class="action.icon" /><span>{{ action.label }}</span></button></div>
    <LicenseStatusPanel />
    <div v-if="firstLoadFailed" class="overview-warning">概览服务暂不可用，请稍后重试。</div>
    <div class="metric-strip"><div v-for="metric in metrics" :key="metric.label"><small>{{ metric.label }}</small><strong>{{ metric.value }}</strong><span>{{ metric.note }}</span></div></div>
    <nav v-if="snapshot" class="alert-links" aria-label="告警和待处理事项">
      <router-link v-for="item in alertLinks" :key="item.label" :to="item.route"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><i class="el-icon-arrow-right" /></router-link>
    </nav>
    <section class="resource-section">
      <header><h2>处理服务器资源</h2><span>在线服务器最近一次有效采样的平均值</span></header>
      <div class="resource-grid"><div v-for="resource in resources" :key="resource.key" class="resource-row"><div class="resource-copy"><strong>{{ resource.label }}</strong><span>{{ resourceText(resource) }}</span></div><el-progress :percentage="resourcePercent(resource)" :show-text="false" :stroke-width="9" :color="resource.color" /><small>{{ resourceSampleText(resource) }}</small></div></div>
    </section>
  </section>
</template>

<script>
import LicenseStatusPanel from '@/components/LicenseStatusPanel.vue'
import { getDashboardOverview } from '@/api/DashboardOverview'
const { applyOverviewSuccess, applyOverviewFailure, resourceText } = require('@/services/dashboardOverviewState')

export default {
  components: { LicenseStatusPanel },
  data: () => ({
    actions: [{ label: '一键上课', icon: 'el-icon-video-play', route: '/management/training' }, { label: '切换比赛模式', icon: 'el-icon-refresh', route: '/Admin?tab=timer' }, { label: '进入课程平台', icon: 'el-icon-reading', route: '/management/courses' }, { label: '进入实训环境', icon: 'el-icon-monitor', route: '/management/training' }],
    snapshot: null,
    loading: false,
    stale: false,
    firstLoadFailed: false,
    refreshTimer: null
  }),
  computed: {
    metrics () {
      const data = this.snapshot || {}
      const agents = data.agents || data.processingAgents || {}
      const environments = data.environments || {}
      const alerts = data.alerts || {}
      return [{ label: '服务器在线数', value: this.value(agents.online), note: agents.enabled == null ? '总数不可用' : `共 ${agents.enabled} 台` }, { label: '运行环境数', value: this.value(environments.running), note: environments.transitional == null ? '状态不可用' : `${environments.transitional} 个处理中` }, { label: '告警和待处理事项', value: this.value(alerts.total), note: alerts.pendingWorkTotal == null ? '状态不可用' : `${alerts.pendingWorkTotal} 项待处理` }, { label: '用户在线数', value: this.value(data.onlineUsers), note: '最近五分钟活跃' }]
    },
    resources () {
      const data = (this.snapshot && this.snapshot.resources) || {}
      return [{ key: 'cpu', label: 'CPU', color: '#317e68', summary: data.cpu }, { key: 'gpu', label: 'GPU', color: '#376d94', summary: data.gpu }, { key: 'gpuMemory', label: 'GPU 显存', color: '#5c6599', summary: data.gpuMemory }, { key: 'memory', label: '内存', color: '#8a6c32', summary: data.memory }, { key: 'disk', label: '磁盘', color: '#8a4e4e', summary: data.disk }]
    },
    alertLinks () {
      const alerts = (this.snapshot && this.snapshot.alerts) || {}
      return [{ label: '离线服务器', value: alerts.offlineAgents || 0, route: '/management/devices' }, { label: '异常实训环境', value: (alerts.degradedEnvironments || 0) + (alerts.failedEnvironments || 0), route: '/management/training' }, { label: '待处理环境操作', value: (alerts.pendingOperations || 0) + (alerts.failedOperations || 0), route: '/management/training' }, { label: '待处理服务器命令', value: (alerts.pendingCommands || 0) + (alerts.failedCommands || 0), route: '/management/devices' }, { label: '授权提醒', value: (alerts.licenseUnusable || 0) + (alerts.licenseExpiring || 0), route: '/management/license' }]
    }
  },
  mounted () { this.loadOverview(); this.refreshTimer = setInterval(this.loadOverview, 15000) },
  beforeDestroy () { clearInterval(this.refreshTimer) },
  methods: {
    async loadOverview () {
      if (this.loading) return
      this.loading = true
      try {
        const response = await getDashboardOverview()
        applyOverviewSuccess(this, response.data)
      } catch (error) {
        applyOverviewFailure(this)
      } finally { this.loading = false }
    },
    value (value) { return value == null ? '--' : value },
    resourcePercent (resource) { const value = resource.summary && resource.summary.averagePercent; return value == null ? 0 : Math.max(0, Math.min(100, Number(value))) },
    resourceText (resource) { return resourceText(resource.summary) },
    resourceSampleText (resource) { const count = resource.summary && resource.summary.sampleCount; return count ? `${count} 台服务器` : '无有效采样' }
  }
}
</script>

<style scoped>
.module-heading{display:flex;align-items:flex-start;justify-content:space-between}.stale-state,.overview-warning{color:#8a641e;background:#fff8df;border:1px solid #ead9a2;border-radius:4px}.stale-state{padding:6px 10px;font-size:12px}.overview-warning{margin-bottom:14px;padding:10px 12px}.overview-grid{display:grid;grid-template-columns:repeat(4,minmax(150px,1fr));gap:12px;margin-bottom:18px}.overview-action{height:88px;padding:16px;border:1px solid #dce5eb;border-radius:6px;background:#fff;color:#294152;text-align:left;cursor:pointer}.overview-action:hover{border-color:#438d76}.overview-action i{display:block;margin-bottom:9px;color:#318063;font-size:24px}.overview-action span{font-size:15px}.metric-strip{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;margin-bottom:12px;background:#dfe7ed;border:1px solid #dfe7ed}.metric-strip>div{padding:18px;background:#fff}.metric-strip small,.metric-strip span{display:block;color:#7a8994}.metric-strip strong{display:block;margin:8px 0;font-size:26px}.alert-links{display:grid;grid-template-columns:repeat(5,1fr);margin-bottom:18px;border-top:1px solid #dfe7ed;border-bottom:1px solid #dfe7ed}.alert-links a{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:8px;padding:11px 12px;color:#536776;text-decoration:none;border-right:1px solid #e5ebef}.alert-links a:last-child{border-right:0}.alert-links a:hover{color:#25745b;background:#f5faf8}.alert-links strong{color:#263f50}.resource-section{padding:18px 0;border-top:1px solid #dfe7ed}.resource-section>header{display:flex;align-items:baseline;justify-content:space-between;margin-bottom:15px}.resource-section h2{margin:0;color:#294152;font-size:17px}.resource-section header span{color:#7a8994;font-size:12px}.resource-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 24px}.resource-row{display:grid;grid-template-columns:100px minmax(120px,1fr) 86px;align-items:center;gap:12px;min-height:48px}.resource-copy strong,.resource-copy span{display:block}.resource-copy span,.resource-row small{color:#71818d;font-size:12px}.resource-row small{text-align:right}@media(max-width:1100px){.alert-links{grid-template-columns:repeat(2,1fr)}.alert-links a{border-bottom:1px solid #e5ebef}}@media(max-width:900px){.overview-grid,.metric-strip{grid-template-columns:repeat(2,1fr)}.resource-grid{grid-template-columns:1fr}}@media(max-width:520px){.overview-grid,.metric-strip,.alert-links{grid-template-columns:1fr}.resource-section>header{display:block}.resource-section header span{display:block;margin-top:5px}.resource-row{grid-template-columns:76px minmax(80px,1fr) 72px}}
</style>
